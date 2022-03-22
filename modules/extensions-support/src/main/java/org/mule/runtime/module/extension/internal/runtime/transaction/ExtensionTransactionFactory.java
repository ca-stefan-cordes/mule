/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.transaction;

import org.mule.runtime.api.notification.NotificationDispatcher;
import org.mule.runtime.api.profiling.ProfilingService;
import org.mule.runtime.api.tx.TransactionException;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.SingleResourceTransactionFactoryManager;
import org.mule.runtime.core.api.transaction.Transaction;
import org.mule.runtime.core.api.transaction.TransactionFactory;
import org.mule.runtime.core.internal.context.MuleContextWithRegistry;
import org.mule.runtime.core.internal.profiling.CoreProfilingService;
import org.mule.runtime.core.privileged.registry.RegistrationException;

import javax.inject.Inject;
import javax.transaction.TransactionManager;

import static org.mule.runtime.core.api.config.i18n.CoreMessages.cannotStartTransaction;

/**
 * Creates instances of {@link ExtensionTransactionFactory}
 *
 * @since 4.0
 */
public class ExtensionTransactionFactory implements TransactionFactory {

  @Inject
  private CoreProfilingService profilingService;

  public ExtensionTransactionFactory() {
    this(null);
  }

  public ExtensionTransactionFactory(ProfilingService profilingService) {
    //this.profilingService = profilingService;
  }

  @Override
  public Transaction beginTransaction(MuleContext muleContext) throws TransactionException {
    try {
      return this.beginTransaction(muleContext.getConfiguration().getId(),
                                   ((MuleContextWithRegistry) muleContext).getRegistry()
                                       .lookupObject(NotificationDispatcher.class),
                                   muleContext.getTransactionFactoryManager(), muleContext.getTransactionManager());
    } catch (RegistrationException e) {
      throw new TransactionException(cannotStartTransaction("Extension"), e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Transaction beginTransaction(String applicationName, NotificationDispatcher notificationFirer,
                                      SingleResourceTransactionFactoryManager transactionFactoryManager,
                                      TransactionManager transactionManager)
      throws TransactionException {
    Transaction transaction = new ExtensionTransaction(applicationName, notificationFirer, profilingService);
    transaction.begin();

    return transaction;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isTransacted() {
    return true;
  }
}
