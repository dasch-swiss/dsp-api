/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api
import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.LegalInfo
import org.knora.webapi.testservices.ResponseOps.*
import org.knora.webapi.testservices.TestDspIngestClient
import org.knora.webapi.testservices.TestMetadataApiClient
import org.knora.webapi.testservices.TestResourcesApiClient

object MetadataEndpointsE2ESpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = List(anythingRdfOntology) 

  private val createThingPicture = for {
    file <- TestDspIngestClient.createImageAsset(anythingShortcode)
    response <- TestResourcesApiClient.createStillImageRepresentation(
                  anythingShortcode,
                  anythingOntologyIri,
                  "ThingPicture",
                  file,
                  anythingAdminUser,
                  LegalInfo.empty,
                )
    _ <- response.assert200
  } yield ()

  val e2eSpec = suite("MetadataEndpointsE2ESpec")(
    test("getResourcesMetadata should return metadata for resources in a project") {
      val noResources = 10
      createThingPicture.repeatN(noResources - 1) *> TestMetadataApiClient
        .getResourcesMetadata(anythingShortcode, anythingAdminUser)
        .flatMap(_.assert200)
        .map(seq => assertTrue(seq.size == noResources))
    },
  )
}
