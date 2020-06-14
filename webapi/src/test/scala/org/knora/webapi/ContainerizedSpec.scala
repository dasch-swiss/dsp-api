package org.knora.webapi

import org.testcontainers.containers.GenericContainer

object ContainerizedSpec {
    val RedisContainer = new GenericContainer("redis:5")
}

/**
 * This abstract class providing access to Docker containers
 * needed for running the tests.
 */
abstract class ContainerizedSpec {

}
