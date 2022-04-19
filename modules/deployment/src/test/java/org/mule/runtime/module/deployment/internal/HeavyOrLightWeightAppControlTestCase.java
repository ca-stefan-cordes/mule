/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.module.deployment.internal;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.Test;
import org.mule.maven.client.api.model.MavenConfiguration;
import org.mule.runtime.module.deployment.impl.internal.builder.ApplicationFileBuilder;
import org.mule.runtime.module.deployment.impl.internal.builder.ArtifactPluginFileBuilder;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.iterateFiles;
import static org.apache.commons.io.filefilter.HiddenFileFilter.VISIBLE;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.mule.maven.client.api.model.MavenConfiguration.newMavenConfigurationBuilder;
import static org.mule.maven.client.api.model.RemoteRepository.newRemoteRepositoryBuilder;
import static org.mule.runtime.container.api.MuleFoldersUtil.getAppFolder;
import static org.mule.runtime.container.api.MuleFoldersUtil.getMuleBaseFolder;
import static org.mule.runtime.container.internal.ClasspathModuleDiscoverer.EXPORTED_CLASS_PACKAGES_PROPERTY;
import static org.mule.runtime.globalconfig.api.GlobalConfigLoader.setMavenConfig;
import static org.mule.test.allure.AllureConstants.DeploymentTypeFeature.DEPLOYMENT_TYPE;
import static org.mule.test.allure.AllureConstants.DeploymentTypeFeature.DeploymentTypeStory.HEAVYWEIGHT;
import static org.mule.test.allure.AllureConstants.DeploymentTypeFeature.DeploymentTypeStory.LIGHTWEIGHT;

@Feature(DEPLOYMENT_TYPE)
public class HeavyOrLightWeightAppControlTestCase extends AbstractApplicationDeploymentTestCase {

  private static final String APP_XML_FILE = "simple.xml";
  private static final String HEAVYWEIGHT_APP = "heavyweight";
  private static final String LIGHTWEIGHT_APP = "lightweight";
  private static final String MULE_CONNECTORS_GROUP_ID = "org.mule.connectors";
  private static final String MULE_PLUGIN_CLASSIFIER = "mule-plugin";
  private static final String MULESOFT_PUBLIC_REPOSITORY = "https://repository.mulesoft.org/nexus/content/repositories/public/";

  public HeavyOrLightWeightAppControlTestCase(boolean parallelDeployment) {
    super(parallelDeployment);
  }

  @Test
  @Story(LIGHTWEIGHT)
  public void lightweightAppDeploymentDownloadsDependenciesFromRemoteRepo() throws Exception {
    final File muleRepository = Paths.get(getMuleBaseFolder().getAbsolutePath(), "repository").toFile();

    final MavenConfiguration.MavenConfigurationBuilder mavenConfigurationBuilder = newMavenConfigurationBuilder();
    mavenConfigurationBuilder.remoteRepository(newRemoteRepositoryBuilder()
        .id("mulesoft-public")
        .url(new URL(MULESOFT_PUBLIC_REPOSITORY))
        .build());
    mavenConfigurationBuilder.localMavenRepositoryLocation(muleRepository);
    setMavenConfig(mavenConfigurationBuilder.build());

    final ArtifactPluginFileBuilder mulePlugin = new ArtifactPluginFileBuilder("mule-sockets-connector")
        .withGroupId(MULE_CONNECTORS_GROUP_ID)
        .withVersion("1.2.0")
        .withClassifier(MULE_PLUGIN_CLASSIFIER);

    final ApplicationFileBuilder artifactFileBuilder = appFileBuilder(LIGHTWEIGHT_APP)
        .definedBy(APP_XML_FILE)
        .dependingOn(mulePlugin)
        .usingLightWeightPackage();

    addPackedAppFromBuilder(artifactFileBuilder);
    startDeployment();

    final String applicationName = artifactFileBuilder.getDeployedPath();
    final File applicationRepository = Paths.get(getAppFolder(applicationName).toString(), "repository").toFile();
    final Collection<File> muleRepositoryContents = getRepositoryContents(muleRepository);
    final List<String> muleRepositoryContentNames = muleRepositoryContents.stream().map(File::getName).collect(toList());

    assertDeploymentSuccess(applicationDeploymentListener, artifactFileBuilder.getId());
    assertApplicationAnchorFileExists(applicationName);
    assertThat(applicationRepository.exists(), is(false));
    assertThat(muleRepositoryContentNames, hasItem(allOf(startsWith("mule-sockets-connector"), endsWith("-mule-plugin.jar"))));
    assertThat(muleRepositoryContentNames, not(hasItem(allOf(startsWith("mule-core"), endsWith(".jar")))));
  }

  @Test
  @Story(LIGHTWEIGHT)
  public void lightweightAppDeploymentFailsIfNoRemoteRepoConfigured() throws Exception {
    final ArtifactPluginFileBuilder mulePlugin = new ArtifactPluginFileBuilder("mule-sockets-connector")
        .withGroupId(MULE_CONNECTORS_GROUP_ID)
        .withVersion("1.2.0")
        .withClassifier(MULE_PLUGIN_CLASSIFIER);

    final ApplicationFileBuilder artifactFileBuilder = appFileBuilder(LIGHTWEIGHT_APP)
        .definedBy(APP_XML_FILE)
        .dependingOn(mulePlugin)
        .usingLightWeightPackage();

    addPackedAppFromBuilder(artifactFileBuilder);
    startDeployment();

    final String applicationName = artifactFileBuilder.getDeployedPath();
    final File applicationRepository = Paths.get(getAppFolder(applicationName).toString(), "repository").toFile();
    final File muleRepository = Paths.get(getMuleBaseFolder().getAbsolutePath(), "repository").toFile();

    assertDeploymentFailure(applicationDeploymentListener, artifactFileBuilder.getId());
    assertApplicationAnchorFileDoesNotExists(applicationName);
    assertThat(applicationRepository.exists(), is(false));
    assertThat(muleRepository.exists(), is(false));
  }

  @Test
  @Story(HEAVYWEIGHT)
  @Description("Test case for MULE-12298 and MULE-12317")
  public void heavyweightAppDeploymentDoesntDownloadDependenciesFromRemoteRepo() throws Exception {
    final ArtifactPluginFileBuilder mulePlugin = new ArtifactPluginFileBuilder("mule-http-connector")
        .withGroupId(MULE_CONNECTORS_GROUP_ID)
        .withVersion("1.6.1")
        .withClassifier(MULE_PLUGIN_CLASSIFIER)
        .configuredWith(EXPORTED_CLASS_PACKAGES_PROPERTY, "org.foo");

    final ApplicationFileBuilder artifactFileBuilder = appFileBuilder(HEAVYWEIGHT_APP)
        .definedBy(APP_XML_FILE)
        .dependingOn(mulePlugin);

    addPackedAppFromBuilder(artifactFileBuilder);
    startDeployment();

    final String applicationName = artifactFileBuilder.getDeployedPath();
    final File applicationRepository = Paths.get(getAppFolder(applicationName).toString(), "repository").toFile();
    final File muleRepository = Paths.get(getMuleBaseFolder().getAbsolutePath(), "repository").toFile();
    final List<String> applicationRepositoryContentNames =
        getRepositoryContents(applicationRepository).stream().map(File::getName).collect(toList());

    assertDeploymentSuccess(applicationDeploymentListener, artifactFileBuilder.getId());
    assertApplicationAnchorFileExists(applicationName);
    assertThat(applicationRepository.exists(), is(true));
    assertThat(applicationRepositoryContentNames,
               hasItem(allOf(startsWith("mule-http-connector"), endsWith("-mule-plugin.jar"))));
    assertThat(muleRepository.exists(), is(false));
  }

  private Collection<File> getRepositoryContents(File repository) {
    if (repository.exists() && repository.isDirectory()) {
      List<File> repoFiles = new LinkedList<>();

      final Iterator<File> iterateFiles = iterateFiles(repository, VISIBLE, VISIBLE);
      while (iterateFiles.hasNext()) {
        File current = iterateFiles.next();
        if (current.isFile()) {
          repoFiles.add(current);
        }
      }

      return repoFiles;
    } else {
      throw new NoSuchElementException("No internal repository found");
    }
  }
}
