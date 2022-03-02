/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import com.typesafe.config.{Config, ConfigFactory}
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

import scala.jdk.CollectionConverters._
import org.knora.webapi.http.version.BuildInfo

/**
 * Provides the Fuseki container necessary for running tests.
 */
object TestContainerFuseki {

  val FusekiImageName: DockerImageName = DockerImageName.parse(BuildInfo.fuseki)
  val FusekiContainer = new GenericContainer(FusekiImageName)

  FusekiContainer.withExposedPorts(3030)
  FusekiContainer.withEnv("ADMIN_PASSWORD", "test")
  FusekiContainer.withEnv("JVM_ARGS", "-Xmx3G")
  FusekiContainer.start()

  private val portMap = Map(
    "app.triplestore.fuseki.port" -> FusekiContainer.getFirstMappedPort
  ).asJava

  // all tests need to be configured with these ports.
  val PortConfig: Config =
    ConfigFactory.parseMap(portMap, "Ports from RedisTestContainer").withFallback(ConfigFactory.load())
}
