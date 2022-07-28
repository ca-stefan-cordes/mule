/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.internal.routing.forkjoin;

import static java.time.Duration.ofMillis;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.toList;

import static org.mule.runtime.core.api.event.CoreEvent.builder;
import static org.mule.runtime.core.internal.exception.ErrorHandlerContextManager.ERROR_HANDLER_CONTEXT;
import static org.mule.runtime.core.internal.profiling.tracing.event.span.CoreEventSpanUtils.getSpanName;
import static org.mule.runtime.core.internal.routing.ForkJoinStrategy.RoutingPair.of;
import static org.mule.runtime.core.privileged.processor.MessageProcessors.processWithChildContextDontComplete;
import static reactor.core.Exceptions.propagate;
import static reactor.core.publisher.Flux.from;
import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.just;

import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.message.ErrorType;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.CollectionDataType;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.util.Pair;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.message.GroupCorrelation;
import org.mule.runtime.core.api.processor.ReactiveProcessor;
import org.mule.runtime.core.api.processor.strategy.ProcessingStrategy;
import org.mule.runtime.core.internal.event.DefaultEventBuilder;
import org.mule.runtime.core.internal.exception.MessagingException;
import org.mule.runtime.core.internal.message.ErrorBuilder;
import org.mule.runtime.core.internal.profiling.NoOpProfilingService;
import org.mule.runtime.core.internal.profiling.tracing.event.span.CoreEventSpanCustomizer;
import org.mule.runtime.core.internal.profiling.tracing.event.tracer.CoreEventTracer;
import org.mule.runtime.core.internal.routing.ForkJoinStrategy;
import org.mule.runtime.core.internal.routing.ForkJoinStrategy.RoutingPair;
import org.mule.runtime.core.internal.routing.ForkJoinStrategyFactory;
import org.mule.runtime.core.privileged.exception.EventProcessingException;
import org.mule.runtime.core.privileged.routing.CompositeRoutingException;
import org.mule.runtime.core.privileged.routing.RoutingResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Abstract {@link ForkJoinStrategy} that provides the base behavior for strategies that will perform parallel invocation of
 * {@link RoutingPair}'s that wish to use the following common behaviour:
 * <ul>
 * <li>Emit a single result event once all routes complete.
 * <li>Merge variables using a last-wins strategy.
 * <li>Use of an optional timeout.
 * <li>Delay error behavior, where all errors are collated and thrown as a composite exception.
 * </ul>
 */
public abstract class AbstractForkJoinStrategyFactory implements ForkJoinStrategyFactory {

  public static final String TIMEOUT_EXCEPTION_DESCRIPTION = "Route Timeout";
  public static final String TIMEOUT_EXCEPTION_DETAILED_DESCRIPTION_PREFIX = "Timeout while processing route/part:";
  private static final CoreEventSpanCustomizer ROUTE_SPAN_CUSTOMIZER = new RouteSpanCustomizer();
  private final boolean mergeVariables;
  private CoreEventTracer coreEventTracer = new NoOpProfilingService.NoOpCoreEventTracer();
  private Component component;

  public AbstractForkJoinStrategyFactory() {
    this(true);
  }

  public AbstractForkJoinStrategyFactory(boolean mergeVariables) {
    this.mergeVariables = mergeVariables;
  }

  public AbstractForkJoinStrategyFactory(CoreEventTracer coreEventTracer, Component component) {
    this(true);
    this.coreEventTracer = coreEventTracer;
    this.component = component;
  }

  @Override
  public ForkJoinStrategy createForkJoinStrategy(ProcessingStrategy processingStrategy, int maxConcurrency, boolean delayErrors,
                                                 long timeout, Scheduler timeoutScheduler, ErrorType timeoutErrorType,
                                                 boolean isDetailedLogEnabled) {
    reactor.core.scheduler.Scheduler reactorTimeoutScheduler = Schedulers.fromExecutorService(timeoutScheduler);
    return (original, routingPairs) -> {
      final AtomicInteger count = new AtomicInteger();
      final CoreEvent.Builder resultBuilder = builder(original);
      return from(routingPairs)
          .map(addSequence(count))
          .flatMapSequential(processRoutePair(processingStrategy, maxConcurrency, delayErrors, timeout, reactorTimeoutScheduler,
                                              timeoutErrorType, coreEventTracer),
                             maxConcurrency)
          .reduce(new Pair<List<Pair<CoreEvent, EventProcessingException>>, Boolean>(new ArrayList<>(), false),
                  (listBooleanPair, coreEventExceptionPair) -> {
                    // Accumulates events and check if there is a (new) error within those events
                    listBooleanPair.getFirst().add(coreEventExceptionPair);
                    boolean hasNewError =
                        coreEventExceptionPair.getFirst().getError().map(err -> !isOriginalError(err, original.getError()))
                            .orElse(false);
                    return new Pair<>(listBooleanPair.getFirst(), listBooleanPair.getSecond() || hasNewError);
                  })
          .doOnNext(listBooleanPair -> {
            coreEventTracer.endCurrentSpan(listBooleanPair.getFirst().get(0).getFirst());
            if (listBooleanPair.getSecond()) {
              throw propagate(createCompositeRoutingException(listBooleanPair.getFirst().stream()
                  .map(coreEventExceptionPair -> removeOriginalError(coreEventExceptionPair,
                                                                     original.getError()))
                  .collect(toList()), isDetailedLogEnabled));
            }
          })
          .map(listBooleanPair -> listBooleanPair.getFirst().stream().map(Pair::getFirst).collect(Collectors.toList()))
          .doOnNext(mergeVariables(original, resultBuilder))
          .map(createResultEvent(original, resultBuilder));
    };
  }

  private boolean isOriginalError(Error newError, Optional<Error> originalError) {
    return originalError.map(error -> error.equals(newError)).orElse(false);
  }

  private Pair<CoreEvent, EventProcessingException> removeOriginalError(Pair<CoreEvent, EventProcessingException> coreEventExceptionPair,
                                                                        Optional<Error> originalError) {
    CoreEvent coreEvent = coreEventExceptionPair.getFirst();
    EventProcessingException eventProcessingException = coreEventExceptionPair.getSecond();
    return coreEvent.getError()
        .map(err -> isOriginalError(err, originalError)
            ? new Pair<>(CoreEvent.builder(coreEvent).error(null).build(), eventProcessingException)
            : new Pair<>(coreEvent, eventProcessingException))
        .orElse(coreEventExceptionPair);
  }

  /**
   * Template method to be implemented by implementations that defines how the list of result {@link CoreEvent}'s should be
   * aggregated into a result {@link CoreEvent}
   *
   * @param original      the original event
   * @param resultBuilder a result builder with the current state of result event builder including flow variable
   * @return the result event
   */
  protected abstract Function<List<CoreEvent>, CoreEvent> createResultEvent(CoreEvent original,
                                                                            CoreEvent.Builder resultBuilder);

  private Function<RoutingPair, RoutingPair> addSequence(AtomicInteger count) {
    return pair -> of(builder(pair.getEvent()).groupCorrelation(Optional.of(GroupCorrelation.of(count.getAndIncrement())))
        .build(), pair.getRoute());
  }

  private Function<RoutingPair, Publisher<Pair<CoreEvent, EventProcessingException>>> processRoutePair(ProcessingStrategy processingStrategy,
                                                                                                       int maxConcurrency,
                                                                                                       boolean delayErrors,
                                                                                                       long timeout,
                                                                                                       reactor.core.scheduler.Scheduler timeoutScheduler,
                                                                                                       ErrorType timeoutErrorType,
                                                                                                       CoreEventTracer coreEventTracer) {

    return pair -> {
      ReactiveProcessor route = publisher -> from(publisher)
          .doOnNext(coreEvent -> coreEventTracer
              .startComponentSpan(coreEvent, component, new RouteSpanCustomizer()))
          .transform(pair.getRoute());
      return from(processWithChildContextDontComplete(pair.getEvent(),
                                                      applyProcessingStrategy(processingStrategy, route, maxConcurrency),
                                                      empty(),
                                                      (coreEvent -> coreEventTracer
                                                          .endCurrentSpan(coreEvent))))
                                                              .timeout(ofMillis(timeout),
                                                                       onTimeout(processingStrategy, delayErrors,
                                                                                 timeoutErrorType,
                                                                                 pair),
                                                                       timeoutScheduler)
                                                              .map(coreEvent -> new Pair<CoreEvent, EventProcessingException>(
                                                                                                                              ((DefaultEventBuilder) CoreEvent
                                                                                                                                  .builder(coreEvent))
                                                                                                                                      .removeInternalParameter(ERROR_HANDLER_CONTEXT)
                                                                                                                                      .build(),
                                                                                                                              null))
                                                              .onErrorResume(MessagingException.class,
                                                                             me -> {
                                                                               // coreEventTracer
                                                                               // .endCurrentSpan(me.getEvent());
                                                                               return getPublisher(delayErrors, me);
                                                                             });
    };
  }

  private Publisher<Pair<CoreEvent, EventProcessingException>> getPublisher(boolean delayErrors, EventProcessingException me) {
    Pair<CoreEvent, EventProcessingException> pair = new Pair<>(me.getEvent(), me);
    return delayErrors ? just(pair) : error(me);
  }


  private Mono<CoreEvent> onTimeout(ProcessingStrategy processingStrategy, boolean delayErrors, ErrorType timeoutErrorType,
                                    RoutingPair pair) {
    return defer(() -> delayErrors ? just(createTimeoutErrorEvent(timeoutErrorType, pair))
        : error(new TimeoutException(buildDetailedDescription(pair))))
            .transform(processingStrategy.onPipeline(p -> p));
  }

  private ReactiveProcessor applyProcessingStrategy(ProcessingStrategy processingStrategy, ReactiveProcessor processor,
                                                    int maxConcurrency) {
    if (maxConcurrency > 1) {
      return processingStrategy.onPipeline(processor);
    } else {
      return processor;
    }
  }

  private CoreEvent createTimeoutErrorEvent(ErrorType timeoutErrorType, RoutingPair pair) {
    final String detailedDescription = buildDetailedDescription(pair);

    return builder(pair.getEvent()).message(Message.of(null))
        .error(ErrorBuilder.builder().errorType(timeoutErrorType)
            .exception(new TimeoutException(detailedDescription))
            .description(TIMEOUT_EXCEPTION_DESCRIPTION)
            .detailedDescription(detailedDescription)
            .build())
        .build();
  }

  private String buildDetailedDescription(RoutingPair pair) {
    return TIMEOUT_EXCEPTION_DETAILED_DESCRIPTION_PREFIX + " '"
        + pair.getEvent().getGroupCorrelation().get().getSequence() + "'";
  }

  private CompositeRoutingException createCompositeRoutingException(List<Pair<CoreEvent, EventProcessingException>> results,
                                                                    boolean isDetailedLogEnabled) {
    Map<String, Message> successMap = new LinkedHashMap<>();
    Map<String, Pair<Error, EventProcessingException>> errorMap = new LinkedHashMap<>();

    for (Pair<CoreEvent, EventProcessingException> eventExceptionPair : results) {
      String key = Integer.toString(eventExceptionPair.getFirst().getGroupCorrelation().get().getSequence());
      if (eventExceptionPair.getFirst().getError().isPresent()) {
        errorMap.put(key, new Pair<>(eventExceptionPair.getFirst().getError().get(),
                                     eventExceptionPair.getSecond()));
      } else {
        successMap.put(key, eventExceptionPair.getFirst().getMessage());
      }
    }
    if (isDetailedLogEnabled) {
      return new CompositeRoutingException(RoutingResult.routingResultWithException(successMap, errorMap));
    } else {
      Map<String, Error> previousErrorMap =
          errorMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, pair -> pair.getValue().getFirst()));
      return new CompositeRoutingException(new RoutingResult(successMap, previousErrorMap));
    }


  }

  private Consumer<List<CoreEvent>> mergeVariables(CoreEvent original, CoreEvent.Builder result) {
    return list -> {
      if (!mergeVariables) {
        return;
      }
      Map<String, TypedValue> routeVars = new HashMap<>();
      list.forEach(event -> event.getVariables().forEach((key, value) -> {
        // Only merge variables that have been added or mutated in routes
        if (!value.equals(original.getVariables().get(key))) {
          if (!routeVars.containsKey(key)) {
            // A new variable that hasn't already been set by another route is added as a simple entry.
            routeVars.put(key, value);
          } else {
            // If a variable already exists from before route, or was set in a previous route, then it's added to a list of 1.
            if (!(routeVars.get(key).getValue() instanceof List)) {
              List newList = new ArrayList();
              newList.add(routeVars.get(key).getValue());
              routeVars.put(key, new TypedValue(newList, DataType.builder().collectionType(List.class)
                  .itemType(routeVars.get(key).getDataType().getType()).build()));
            }
            List valueList = (List) routeVars.get(key).getValue();
            valueList.add(value.getValue());
            if (((CollectionDataType) routeVars.get(key).getDataType()).getItemDataType().isCompatibleWith(value.getDataType())) {
              // If item types are compatible then data type is conserved
              routeVars.put(key, new TypedValue(valueList, routeVars.get(key).getDataType()));
            } else {
              // Else Object item type is used.
              routeVars.put(key, new TypedValue(valueList, DataType.builder().collectionType(List.class).build()));
            }
          }
        }
      }));
      routeVars.forEach((s, typedValue) -> result.addVariable(s, typedValue));
    };
  }

  /**
   * A {@link CoreEventSpanCustomizer} for routes.
   */
  private static class RouteSpanCustomizer implements CoreEventSpanCustomizer {

    public static final String ROUTE = ":route";

    @Override
    public String getName(CoreEvent coreEvent, Component component) {
      return getSpanName(component.getIdentifier()) + ROUTE;
    }
  }
}
