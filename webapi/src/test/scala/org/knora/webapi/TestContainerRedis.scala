/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import com.typesafe.config.{Config, ConfigFactory}
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

import scala.jdk.CollectionConverters._

/**
 * Provides the Redis container necessary for running tests.
 */
object TestContainerRedis {

  val RedisImageName: DockerImageName = DockerImageName.parse("redis:5")
  val RedisContainer = new GenericContainer(RedisImageName)
  RedisContainer.withExposedPorts(6379)
  RedisContainer.start()

  private val portMap = Map(
    "app.cache-service.redis.port" -> RedisContainer.getFirstMappedPort
  ).asJava

  // all tests need to be configured with these ports.
  val PortConfig: Config =
    ConfigFactory.parseMap(portMap, "Ports from RedisTestContainer").withFallback(ConfigFactory.load())
}
