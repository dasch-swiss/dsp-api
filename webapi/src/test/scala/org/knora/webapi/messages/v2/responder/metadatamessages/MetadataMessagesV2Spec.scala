/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.metadatamessages

import java.util.UUID

import org.knora.webapi.CoreSpec
import org.knora.webapi.exceptions.ForbiddenException
import org.knora.webapi.messages.util.rdf.{RdfFeatureFactory, RdfFormatUtil, RdfModel, Turtle}
import org.knora.webapi.sharedtestdata.SharedTestDataADM

/**
 * Tests [[MetadataPutRequestV2]].
 */
class MetadataMessagesV2Spec extends CoreSpec() {
  private val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(defaultFeatureFactoryConfig)

  private val graphDataContent: String =
    """
        @prefix dsp-repo: <http://ns.dasch.swiss/repository#> .
        @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .

        dsp-repo:anything dsp-repo:hasDescription "A project to test Knora functionalities" .
        dsp-repo:anything dsp-repo:hasShortcode "0001" .
        """

  // Parse the request to an RdfModel.
  private val requestModel: RdfModel = rdfFormatUtil.parseToRdfModel(
    rdfStr = graphDataContent,
    rdfFormat = Turtle
  )

  "MetadataPutRequestV2" should {
    "return ForbiddenException if the requesting user is not the project admin or a system admin" in {
      assertThrows[ForbiddenException](
        MetadataPutRequestV2(
          rdfModel = requestModel,
          projectADM = SharedTestDataADM.anythingProject,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.anythingUser2,
          apiRequestID = UUID.randomUUID()
        )
      )
    }
  }
}
