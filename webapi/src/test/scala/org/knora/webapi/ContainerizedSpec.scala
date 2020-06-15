package org.knora.webapi

import org.testcontainers.containers.GenericContainer

object ContainerizedSpec {

    val FusekiContainer = new GenericContainer("daschswiss/knora-jena-fuseki:latest")
    FusekiContainer.withExposedPorts(3030)
    FusekiContainer.withEnv("ADMIN_PASSWORD", "test")
    FusekiContainer.withEnv("JVM_ARGS", "-Xmx3G")
    FusekiContainer.start()

    val SipiContainer = new GenericContainer("daschswiss/knora-sipi:latest")
    SipiContainer.withExposedPorts(1024)
    SipiContainer.withEnv("SIPI_EXTERNAL_PROTOCOL", "http")
    SipiContainer.withEnv("SIPI_EXTERNAL_HOSTNAME", "sipi")
    SipiContainer.withEnv("SIPI_EXTERNAL_PORT", "1024")
    SipiContainer.withEnv("SIPI_WEBAPI_HOSTNAME", "api")
    SipiContainer.withEnv("SIPI_WEBAPI_PORT", "3333")
    SipiContainer.withCommand("--config=/sipi/config/sipi.knora-docker-config.lua")
    SipiContainer.start()

    val RedisContainer = new GenericContainer("redis:5")
    RedisContainer.withExposedPorts(6379)
    RedisContainer.start()
}

/**
 * This abstract class providing access to Docker containers
 * needed for running the tests.
 */
abstract class ContainerizedSpec {
    val FusekiContainer = ContainerizedSpec.FusekiContainer
    val SipiContainer = ContainerizedSpec.SipiContainer
    val RedisContainer = ContainerizedSpec.RedisContainer
}
