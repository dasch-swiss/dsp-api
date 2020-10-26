/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import org.apache.jena.graph.Graph
import org.knora.webapi.{CoreSpec, RdfMediaTypes}
import org.knora.webapi.exceptions.ForbiddenException
import org.knora.webapi.messages.util.RdfFormatUtil
import org.knora.webapi.sharedtestdata.SharedTestDataADM

/**
 * Tests [[MetadataPutRequestV2]].
 */
class MetadataMessagesV2Spec extends CoreSpec() {
    private val graphDataContent: String =
        """
        @prefix dsp-repo: <http://ns.dasch.swiss/repository#> .
        @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
        @prefix anything: <http://www.knora.org/ontology/0001/anything#> .

        <anything> dsp-repo:hasDescription "A project to test Knora functionalities" .
        <anything> dsp-repo:hasShortcode "0001" .
        """

    // Parse the request to a Jena Graph.
    private val requestGraph: Graph = RdfFormatUtil.parseToJenaGraph(
        rdfStr = graphDataContent,
        mediaType = RdfMediaTypes.`text/turtle`
    )

    "MetadataPutRequestV2" should {
        "return ForbiddenException if the requesting user is not the project admin or a system admin" in {
            assertThrows[ForbiddenException](
                MetadataPutRequestV2(
                    graph = requestGraph,
                    projectADM = SharedTestDataADM.anythingProject,
                    requestingUser = SharedTestDataADM.anythingUser2,
                    apiRequestID = UUID.randomUUID()
                )
            )
        }
    }
}
