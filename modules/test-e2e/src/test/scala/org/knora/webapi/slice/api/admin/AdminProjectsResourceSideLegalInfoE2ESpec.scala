/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.client4.*
import sttp.model.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.api.admin.model.ProjectGetResponse
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

object AdminProjectsResourceSideLegalInfoE2ESpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = List(anythingRdfData)

  private val resourceSideUri = uri"/admin/projects/shortcode/$anythingShortcode/legal-info/resource-side"
  private val projectUri      = uri"/admin/projects/shortcode/$anythingShortcode"

  private val validInfo = ResourceSideLegalInfo(
    dataLicense = Some(LicenseIri.CC_BY_4_0),
    dataCopyrightHolder = Some(CopyrightHolder.unsafeFrom("University of Basel")),
    dataAuthorship = List("Lotte Reiniger", "Hilma af Klint").map(Authorship.unsafeFrom),
  )

  val e2eSpec = suite("The resource-side legal info admin endpoint")(
    test("setting it as a system admin returns the saved values") {
      for {
        saved <- TestApiClient
                   .putJson[ResourceSideLegalInfo, ResourceSideLegalInfo](resourceSideUri, validInfo, rootUser)
                   .flatMap(_.assert200)
      } yield assertTrue(saved == validInfo)
    },
    test("setting it as a project admin returns the saved values") {
      for {
        saved <- TestApiClient
                   .putJson[ResourceSideLegalInfo, ResourceSideLegalInfo](resourceSideUri, validInfo, anythingAdminUser)
                   .flatMap(_.assert200)
      } yield assertTrue(saved == validInfo)
    },
    test("after setting it, the project GET response contains the saved resource-side legal info") {
      for {
        _ <- TestApiClient
               .putJson[ResourceSideLegalInfo, ResourceSideLegalInfo](resourceSideUri, validInfo, rootUser)
               .flatMap(_.assert200)
        prj <- TestApiClient.getJson[ProjectGetResponse](projectUri, rootUser).flatMap(_.assert200).map(_.project)
      } yield assertTrue(
        prj.dataLicense.contains(LicenseIri.CC_BY_4_0),
        prj.dataCopyrightHolder.contains(CopyrightHolder.unsafeFrom("University of Basel")),
        prj.dataAuthorship == List("Lotte Reiniger", "Hilma af Klint").map(Authorship.unsafeFrom),
      )
    },
    test("a non-Creative-Commons license (CC0) is rejected with 400") {
      val withCc0 = validInfo.copy(dataLicense = Some(LicenseIri.CC_0_1_0))
      for {
        response <-
          TestApiClient.putJson[ResourceSideLegalInfo, ResourceSideLegalInfo](resourceSideUri, withCc0, rootUser)
      } yield assertTrue(response.code == StatusCode.BadRequest)
    },
    test("a user who is neither system nor project admin gets 403") {
      for {
        response <-
          TestApiClient.putJson[ResourceSideLegalInfo, ResourceSideLegalInfo](resourceSideUri, validInfo, anythingUser2)
      } yield assertTrue(response.code == StatusCode.Forbidden)
    },
  )
}
