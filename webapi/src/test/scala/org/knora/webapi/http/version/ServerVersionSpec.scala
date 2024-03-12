/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.version

import org.apache.pekko.http.scaladsl.model.headers.Server
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

object ServerVersionSpec extends ZIOSpecDefault {

  val spec: Spec[Any, Nothing] = suite("ServerVersionSpec")(
    test("The server version header") {
      val header: Server = ServerVersion.serverVersionHeader
      assertTrue(header.toString.contains("webapi/"))
      assertTrue(header.toString.contains("pekko-http/"))
    },
  )
}
