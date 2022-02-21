/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.version

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

/**
 * This spec is used to test 'ListAdminMessages'.
 */
class BuildInfoSpec extends AnyWordSpecLike with Matchers {
  "The version info" should {
    "contain all the necessary information" in {
      BuildInfo.name should be("webapi")
      BuildInfo.version should not be empty
    }
  }
}
