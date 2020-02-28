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

package org.knora.webapi.util

import java.io.File

import akka.pattern.ask
import akka.testkit.ImplicitSender
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse
import org.knora.webapi.messages.v2.responder.resourcemessages.{ReadResourceV2, ReadResourcesSequenceV2, ResourcesGetRequestV2}
import org.knora.webapi.responders.v2.{ResourcesResponderV2SpecFullData, ResourcesResponseCheckerV2}
import org.knora.webapi.util.ConstructResponseUtilV2.RdfResources

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
 * Tests [[ConstructResponseUtilV2]].
 */
class ConstructResponseUtilV2Spec extends CoreSpec() with ImplicitSender {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    private implicit val timeout: Timeout = 10.seconds
    private val incunabulaUser = SharedTestDataADM.incunabulaProjectAdminUser
    private val resourcesResponderV2SpecFullData = new ResourcesResponderV2SpecFullData

    "ConstructResponseUtilV2" should {

        "convert a Turtle response into a resource" in {
            val resourceIri: IRI = "http://rdfh.ch/0803/c5058f3a"
            val turtleStr: String = FileUtil.readTextFile(new File("src/test/resources/test-data/constructResponseUtilV2/Zeitglöcklein.ttl"))
            val resourceRequestResponse: SparqlExtendedConstructResponse = SparqlExtendedConstructResponse.parseTurtleResponse(turtleStr, log).get
            val queryResultsSeparated: RdfResources = ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(constructQueryResults = resourceRequestResponse, requestingUser = incunabulaUser)

            val resourceFuture: Future[ReadResourceV2] = for {
                resourceResponse <- ConstructResponseUtilV2.createFullResourceResponse(
                    resourceIri = resourceIri,
                    resourceRdfData = queryResultsSeparated(resourceIri),
                    mappings = Map.empty,
                    queryStandoff = false,
                    versionDate = None,
                    responderManager = responderManager,
                    targetSchema = ApiV2Complex,
                    settings = settings,
                    requestingUser = incunabulaUser
                )
            } yield resourceResponse

            val resource: ReadResourceV2 = Await.result(resourceFuture, 10.seconds)
            val resourceSequence = ReadResourcesSequenceV2(numberOfResources = 1, resources = Seq(resource))
            ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein, received = resourceSequence)
        }
    }
}
