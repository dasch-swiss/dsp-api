/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.version

import akka.http.scaladsl.model.headers.Server
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

/**
 * This spec is used to test 'ListAdminMessages'.
 */
class ServerVersionSpec extends AnyWordSpecLike with Matchers {

  "The server version header" should {

    "contain the necessary information" in {
      val header: Server = ServerVersion.serverVersionHeader
      header.toString() should include("webapi/")
      header.toString() should include("akka-http/")
    }
  }
}
