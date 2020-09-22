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

import com.typesafe.config.{Config, ConfigFactory}
import org.testcontainers.containers.{BindMode, GenericContainer}

/**
  * Provides all containers necessary for running tests.
  */
object TestContainers {

    val FusekiContainer = new GenericContainer("bazel/docker/knora-jena-fuseki:image")
    FusekiContainer.withExposedPorts(3030)
    FusekiContainer.withEnv("ADMIN_PASSWORD", "test")
    FusekiContainer.withEnv("JVM_ARGS", "-Xmx3G")
    FusekiContainer.start()

    val SipiContainer = new GenericContainer("bazel/docker/knora-sipi:image")
    SipiContainer.withExposedPorts(1024)
    SipiContainer.withEnv("SIPI_EXTERNAL_PROTOCOL", "http")
    SipiContainer.withEnv("SIPI_EXTERNAL_HOSTNAME", "sipi")
    SipiContainer.withEnv("SIPI_EXTERNAL_PORT", "1024")
    SipiContainer.withEnv("SIPI_WEBAPI_HOSTNAME", "api")
    SipiContainer.withEnv("SIPI_WEBAPI_PORT", "3333")
    SipiContainer.withCommand("--config=/sipi/config/sipi.knora-docker-config.lua")
    SipiContainer.withClasspathResourceMapping("/sipi/config/sipi.knora-docker-config.lua",
        "/sipi/config/sipi.knora-docker-config.lua",
        BindMode.READ_ONLY)
    SipiContainer.withClasspathResourceMapping("/sipi/config/sipi.knora-docker-config.lua",
        "/sipi/config/sipi.init-knora.lua",
        BindMode.READ_ONLY)
    SipiContainer.start()

    val RedisContainer = new GenericContainer("redis:5")
    RedisContainer.withExposedPorts(6379)
    RedisContainer.start()

    import scala.collection.JavaConverters._
    private val portMap = Map(
        "app.triplestore.fuseki.port" -> FusekiContainer.getFirstMappedPort,
        "app.sipi.internal-port" -> SipiContainer.getFirstMappedPort,
        "app.cache-service.redis.port" -> RedisContainer.getFirstMappedPort
    ).asJava

    // all tests need to be configured with these ports.
    val PortConfig: Config = ConfigFactory.parseMap(portMap, "Ports from ContainerizedSpec")
}
