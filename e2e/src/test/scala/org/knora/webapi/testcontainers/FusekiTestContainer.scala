/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testcontainers

import org.testcontainers.containers.GenericContainer

import org.knora.webapi.http.version.BuildInfo

final class FusekiTestContainer extends GenericContainer[FusekiTestContainer](BuildInfo.fuseki)

object FusekiTestContainer {
  val layer = ZioTestContainers.layer {
    new FusekiTestContainer()
      .withExposedPorts(3030)
      .withEnv("ADMIN_PASSWORD", "test")
      .withEnv("JVM_ARGS", "-Xmx3G")
  }
}
