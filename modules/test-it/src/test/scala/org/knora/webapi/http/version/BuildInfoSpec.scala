/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.version

import zio.test.*

object BuildInfoSpec extends ZIOSpecDefault {
  val spec: Spec[Any, Nothing] = suite("The version info") {
    test("contain all the necessary information")(assertTrue(BuildInfo.name == "webapi", BuildInfo.version.nonEmpty))
  }
}
