/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.ZIO
import zio.test.*
import zio.test.Assertion.hasSameElements

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.admin.repo.LicenseRepo

object LicenseServiceSpec extends ZIOSpecDefault {

  private val service = ZIO.serviceWithZIO[LicenseService]

  def spec = suite("LicenseService")(
    test("findByProjectShortcode should return all licenses for project") {
      for {
        actual <- service(_.findByProjectShortcode(Shortcode.unsafeFrom("0001")))
      } yield assert(actual)(hasSameElements(License.BUILT_IN))
    },
  ).provide(LicenseService.layer, LicenseRepo.layer)
}
