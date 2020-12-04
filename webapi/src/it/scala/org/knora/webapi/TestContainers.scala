/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi

import java.net.{NetworkInterface, UnknownHostException}

import com.typesafe.config.{Config, ConfigFactory}
import org.testcontainers.containers.{BindMode, GenericContainer}
import org.testcontainers.utility.DockerImageName

import scala.collection.JavaConverters._

/**
  * Provides all containers necessary for running tests.
  */
object TestContainers {

  // get local IP address, which we need for SIPI
  val localIpAddress: String = NetworkInterface.getNetworkInterfaces.asScala.toSeq
    .filter(!_.isLoopback)
    .flatMap(_.getInetAddresses.asScala.toSeq.filter(_.getAddress.length == 4).map(_.toString))
    .headOption
    .getOrElse(throw new UnknownHostException("No suitable network interface found"))

  val FusekiImageName: DockerImageName = DockerImageName.parse("bazel/docker/knora-jena-fuseki:image")
  val FusekiContainer = new GenericContainer(FusekiImageName)
  FusekiContainer.withExposedPorts(3030)
  FusekiContainer.withEnv("ADMIN_PASSWORD", "test")
  FusekiContainer.withEnv("JVM_ARGS", "-Xmx3G")
  FusekiContainer.start()

  val SipiImageName: DockerImageName = DockerImageName.parse("bazel/docker/knora-sipi:image")
  val SipiContainer = new GenericContainer(SipiImageName)
  SipiContainer.withExposedPorts(1024)
  SipiContainer.withEnv("SIPI_WEBAPI_HOSTNAME", localIpAddress)
  SipiContainer.withEnv("SIPI_WEBAPI_PORT", "3333")
  SipiContainer.withCommand("--config=/sipi/config/sipi.knora-docker-config.lua")
  SipiContainer.withClasspathResourceMapping("/sipi/config/sipi.knora-docker-config.lua",
                                             "/sipi/config/sipi.knora-docker-config.lua",
                                             BindMode.READ_ONLY)
  SipiContainer.start()

  // Container needs to be started to get the random IP
  val sipiIp: String = SipiContainer.getHost
  val sipiPort: Int = SipiContainer.getFirstMappedPort

  val RedisImageName: DockerImageName = DockerImageName.parse("redis:5")
  val RedisContainer = new GenericContainer(RedisImageName)
  RedisContainer.withExposedPorts(6379)
  RedisContainer.start()

  import scala.collection.JavaConverters._
  private val portMap = Map(
    "app.triplestore.fuseki.port" -> FusekiContainer.getFirstMappedPort,
    "app.sipi.external-host" -> sipiIp,
    "app.sipi.external-port" -> sipiPort,
    "app.sipi.internal-host" -> sipiIp,
    "app.sipi.internal-port" -> sipiPort,
    "app.cache-service.redis.port" -> RedisContainer.getFirstMappedPort
  ).asJava

  // all tests need to be configured with these ports.
  val PortConfig: Config = ConfigFactory.parseMap(portMap, "Ports from ContainerizedSpec")
}
