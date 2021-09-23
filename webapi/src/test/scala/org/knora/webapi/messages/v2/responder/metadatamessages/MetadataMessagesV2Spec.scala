/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
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
