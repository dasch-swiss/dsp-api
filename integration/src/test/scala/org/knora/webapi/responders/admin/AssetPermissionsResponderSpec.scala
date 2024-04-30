/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import org.apache.pekko.testkit._
import zio.ZIO

import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.api.model.PermissionCodeAndProjectRestrictedViewSettings
import org.knora.webapi.slice.admin.api.model.ProjectIdentifierADM.ShortcodeIdentifier
import org.knora.webapi.slice.admin.api.model.ProjectRestrictedViewSettingsADM
import org.knora.webapi.slice.admin.domain.model.User

/**
 * Tests [[AssetPermissionsResponder]].
 */
class AssetPermissionsResponderSpec extends CoreSpec with ImplicitSender {

  private def getFileInfoForSipiADM(shortcode: ShortcodeIdentifier, filename: String, user: User) =
    ZIO.serviceWithZIO[AssetPermissionsResponder](
      _.getPermissionCodeAndProjectRestrictedViewSettings(shortcode, filename, user),
    )

  override lazy val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/project_data/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula",
    ),
  )

  "The Sipi responder" should {
    "return details of a full quality file value" in {
      // http://localhost:3333/v1/files/http%3A%2F%2Frdfh.ch%2F8a0b1e75%2Freps%2F7e4ba672
      val actual = UnsafeZioRun.runOrThrow(
        getFileInfoForSipiADM(
          ShortcodeIdentifier.unsafeFrom("0803"),
          "incunabula_0000003328.jp2",
          SharedTestDataADM.incunabulaMemberUser,
        ),
      )

      actual shouldEqual PermissionCodeAndProjectRestrictedViewSettings(permissionCode = 6, None)
    }

    "return details of a restricted view file value" in {
      // http://localhost:3333/v1/files/http%3A%2F%2Frdfh.ch%2F8a0b1e75%2Freps%2F7e4ba672
      val actual = UnsafeZioRun.runOrThrow(
        getFileInfoForSipiADM(
          ShortcodeIdentifier.unsafeFrom("0803"),
          "incunabula_0000003328.jp2",
          SharedTestDataADM.anonymousUser,
        ),
      )

      actual shouldEqual PermissionCodeAndProjectRestrictedViewSettings(
        permissionCode = 1,
        Some(ProjectRestrictedViewSettingsADM(size = Some("!512,512"), watermark = false)),
      )
    }
  }
}
