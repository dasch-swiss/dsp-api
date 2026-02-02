/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.ZIO
import zio.test.*

import org.knora.webapi.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.api.admin.model.PermissionCodeAndProjectRestrictedViewSettings
import org.knora.webapi.slice.api.admin.model.ProjectRestrictedViewSettingsADM

object AssetPermissionsResponderSpec extends E2EZSpec {

  private val assetPermissionResponder = ZIO.serviceWithZIO[AssetPermissionsResponder]
  private val asset                    = "incunabula_0000003328.jp2"

  override val rdfDataObjects: List[RdfDataObject] = List(incunabulaRdfData)

  override val e2eSpec = suite("The AssetPermissionsResponder")(
    test("return details of a full quality file value") {
      assetPermissionResponder(
        _.getPermissionCodeAndProjectRestrictedViewSettings(incunabulaMemberUser)(incunabulaProject.shortcode, asset),
      ).map { actual =>
        assertTrue(actual == PermissionCodeAndProjectRestrictedViewSettings(permissionCode = 6, None))
      }
    },
    test("return details of a restricted view file value") {
      // http://localhost:3333/v1/files/http%3A%2F%2Frdfh.ch%2F8a0b1e75%2Freps%2F7e4ba672
      assetPermissionResponder(
        _.getPermissionCodeAndProjectRestrictedViewSettings(anonymousUser)(incunabulaProject.shortcode, asset),
      ).map(actual =>
        assertTrue(
          actual == PermissionCodeAndProjectRestrictedViewSettings(
            permissionCode = 1,
            Some(ProjectRestrictedViewSettingsADM(size = Some("!512,512"), watermark = false)),
          ),
        ),
      )
    },
  )
}
