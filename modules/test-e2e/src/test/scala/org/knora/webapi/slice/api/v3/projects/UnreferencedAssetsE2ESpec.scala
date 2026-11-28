/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import org.junit.runner.RunWith
import sttp.client4.*
import sttp.model.StatusCode
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.models.filemodels.FileType
import org.knora.webapi.models.filemodels.UploadFileRequest
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.testservices.TestDspIngestClient

@RunWith(classOf[DspZTestJUnitRunner])
class UnreferencedAssetsE2ESpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = List(anythingRdfOntology)

  private val report = uri"/v3/projects/$anythingShortcode/assets/unreferenced"

  private def assetIdOf(internalFilename: String) = internalFilename.takeWhile(_ != '.')

  private def createPicture(internalFilename: String) =
    TestApiClient
      .postJsonLd(
        uri"/v2/resources",
        UploadFileRequest
          .make(FileType.StillImageFile(), internalFilename)
          .toJsonLd(className = Some("ThingPicture"), ontologyName = "anything"),
        anythingAdminUser,
      )
      .flatMap(_.assert200)

  private def fetch(page: Int, size: Int) =
    TestApiClient
      .getJson[PagedResponse[UnreferencedAssetResponse]](
        report.addParam("page", page.toString).addParam("page-size", size.toString),
        rootUser,
      )
      .flatMap(_.assert200)

  override val e2eSpec = suite("GET /v3/projects/{shortcode}/assets/unreferenced")(
    test("lists an uploaded asset that no resource references, and not one that a resource references") {
      for {
        orphan     <- TestDspIngestClient.createImageAsset(anythingShortcode)
        referenced <- TestDspIngestClient.createImageAsset(anythingShortcode)
        _          <- createPicture(referenced.internalFilename)
        all        <- fetch(1, 100)
        listed      = all.data.map(_.assetId)
      } yield assertTrue(
        listed.contains(assetIdOf(orphan.internalFilename)),
        !listed.contains(assetIdOf(referenced.internalFilename)),
        all.data.forall(!_.referencedOnlyByDeletedResources),
      )
    },
    test("returns one item for page-size=1 and the total of the whole list") {
      for {
        _     <- TestDspIngestClient.createImageAsset(anythingShortcode)
        _     <- TestDspIngestClient.createImageAsset(anythingShortcode)
        all   <- fetch(1, 100)
        first <- fetch(1, 1)
      } yield assertTrue(
        first.data.size == 1,
        first.pagination.totalItems == all.pagination.totalItems,
        all.pagination.totalItems >= 2,
        first.data.headOption == all.data.headOption,
      )
    },
    test("refuses a project admin who is not SystemAdmin with 403") {
      TestApiClient
        .getJson[PagedResponse[UnreferencedAssetResponse]](report, anythingAdminUser)
        .map(response => assertTrue(response.code == StatusCode.Forbidden))
    },
    test("answers 404 for an unknown project") {
      TestApiClient
        .getJson[PagedResponse[UnreferencedAssetResponse]](uri"/v3/projects/9999/assets/unreferenced", rootUser)
        .map(response => assertTrue(response.code == StatusCode.NotFound))
    },
  )
}
