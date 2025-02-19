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

object LegalInfoServiceSpec extends ZIOSpecDefault {

  private val service = ZIO.serviceWithZIO[LegalInfoService]

  def spec = suite("LegalInfoService")(
    test("findByProjectShortcode should return all licenses for project") {
      for {
        actual <- service(_.findLicenses(Shortcode.unsafeFrom("0001")))
      } yield assert(actual)(hasSameElements(License.BUILT_IN))
    },
  ).provide(LegalInfoService.layer, LicenseRepo.layer)
}
