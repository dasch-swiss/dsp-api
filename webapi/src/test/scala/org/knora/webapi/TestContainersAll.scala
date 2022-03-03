/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import java.net.{NetworkInterface, UnknownHostException}

import com.typesafe.config.{Config, ConfigFactory}
import org.testcontainers.containers.{BindMode, GenericContainer}
import org.testcontainers.utility.DockerImageName

import scala.jdk.CollectionConverters._
import org.knora.webapi.http.version.BuildInfo

/**
 * Provides all containers necessary for running tests.
 */
object TestContainersAll {

  // get local IP address, which we need for SIPI
  val localIpAddress: String = NetworkInterface.getNetworkInterfaces.asScala.toSeq
    .filter(!_.isLoopback)
    .flatMap(_.getInetAddresses.asScala.toSeq.filter(_.getAddress.length == 4).map(_.toString))
    .headOption
    .getOrElse(throw new UnknownHostException("No suitable network interface found"))

  val FusekiImageName: DockerImageName = DockerImageName.parse(BuildInfo.fuseki)
  val FusekiContainer = new GenericContainer(FusekiImageName)
  FusekiContainer.withExposedPorts(3030)
  FusekiContainer.withEnv("ADMIN_PASSWORD", "test")
  FusekiContainer.withEnv("JVM_ARGS", "-Xmx3G")
  FusekiContainer.start()

  val SipiImageName: DockerImageName = DockerImageName.parse(s"daschswiss/knora-sipi:${BuildInfo.version}")
  val SipiContainer = new GenericContainer(SipiImageName)
  SipiContainer.withExposedPorts(1024)
  SipiContainer.withEnv("SIPI_EXTERNAL_PROTOCOL", "http")
  SipiContainer.withEnv("SIPI_EXTERNAL_HOSTNAME", "0.0.0.0")
  SipiContainer.withEnv("SIPI_EXTERNAL_PORT", "1024")
  SipiContainer.withEnv("SIPI_WEBAPI_HOSTNAME", localIpAddress)
  SipiContainer.withEnv("SIPI_WEBAPI_PORT", "3333")
  SipiContainer.withCommand("--config=/sipi/config/sipi.knora-docker-config.lua")
  SipiContainer.withClasspathResourceMapping(
    "/sipi/config/sipi.knora-docker-config.lua",
    "/sipi/config/sipi.knora-docker-config.lua",
    BindMode.READ_ONLY
  )
  SipiContainer.start()

  // Container needs to be started to get the random IP
  val sipiIp: String = SipiContainer.getHost
  val sipiPort: Int = SipiContainer.getFirstMappedPort

  // The new default is the inmem cache implementation, so no need
  // for a container
  //
  // val RedisImageName: DockerImageName = DockerImageName.parse("redis:5")
  // val RedisContainer = new GenericContainer(RedisImageName)
  // RedisContainer.withExposedPorts(6379)
  // RedisContainer.start()

  private val portMap = Map(
    "app.triplestore.fuseki.port" -> FusekiContainer.getFirstMappedPort,
    "app.sipi.internal-host" -> sipiIp,
    "app.sipi.internal-port" -> sipiPort
    // "app.cache-service.redis.port" -> RedisContainer.getFirstMappedPort
  ).asJava

  // all tests need to be configured with these ports.
  val PortConfig: Config = ConfigFactory.parseMap(portMap, "Ports from TestContainers")
}
