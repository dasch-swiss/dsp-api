/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testcontainers

import org.testcontainers.containers.GenericContainer
import zio.ULayer
import zio.http.URL

import org.knora.webapi.http.version.BuildInfo
import org.knora.webapi.testcontainers.TestContainerOps.toLayer

final class FusekiTestContainer extends GenericContainer[FusekiTestContainer](BuildInfo.fuseki) {

  def baseUrl: URL = {
    val urlString = s"http://$getHost:$getFirstMappedPort"
    URL.decode(urlString).getOrElse(throw new IllegalStateException(s"Invalid URL $urlString"))
  }

  def credentials: (String, String) = ("admin", FusekiTestContainer.adminPassword)
}

object FusekiTestContainer {

  val adminPassword = "test"

  def make: FusekiTestContainer = new FusekiTestContainer()
    .withExposedPorts(3030)
    .withEnv("ADMIN_PASSWORD", adminPassword)
    .withEnv("JVM_ARGS", "-Xmx3G")

  val layer: ULayer[FusekiTestContainer] = make.toLayer
}
