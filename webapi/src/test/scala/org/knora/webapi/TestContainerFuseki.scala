/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

import com.typesafe.config.{Config, ConfigFactory}
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

import scala.jdk.CollectionConverters._

/**
 * Provides the Fuseki container necessary for running tests.
 */
object TestContainerFuseki {

  val FusekiImageName: DockerImageName = DockerImageName.parse("bazel/docker/knora-jena-fuseki:image")
  val FusekiContainer                  = new GenericContainer(FusekiImageName)

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
