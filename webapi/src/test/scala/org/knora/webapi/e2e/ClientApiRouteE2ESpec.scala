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

package org.knora.webapi.e2e

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject

import scala.concurrent.duration._

object ClientApiRouteE2ESpec {
    val config: Config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * Tests client code generation.
 */
class ClientApiRouteE2ESpec extends E2ESpec(ClientApiRouteE2ESpec.config) {
    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

    override lazy val rdfDataObjects: List[RdfDataObject] = List(
        RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
        RdfDataObject(path = "test_data/ontologies/minimal-onto.ttl", name = "http://www.knora.org/ontology/0001/minimal")
    )

    "The client API route" should {
        "generate a Zip file of client test data" in {
            val request = Get(baseApiUrl + s"/clientapitest")
            val response: HttpResponse = singleAwaitingRequest(request = request, duration = 40960.millis)
            val responseBytes: Array[Byte] = getResponseEntityBytes(response)
            val filenames: Set[String] = getZipContents(responseBytes)

            // Check that some expected filenames are included in the Zip file.

            val expectedFilenames: Set[String] = Set(
                "test-data/admin/groups/create-group-request.json",
                "test-data/admin/groups/get-group-response.json",
                "test-data/admin/lists/create-list-request.json",
                "test-data/admin/lists/get-list-response.json",
                "test-data/v2/lists/treelist.json",
                "test-data/v2/ontologies/knora-api-ontology.json",
                "test-data/v2/ontologies/delete-ontology-response.json",
                "test-data/v2/resources/create-resource-as-user.json",
                "test-data/v2/resources/resource-graph.json",
                "test-data/v2/resources/resource-preview.json",
                "test-data/v2/resources/testding.json",
                "test-data/v2/resources/thing-with-picture.json",
                "test-data/v2/search/things.json",
                "test-data/v2/search/thing-links.json",
                "test-data/v2/values/create-boolean-value-request.json",
                "test-data/v2/values/get-boolean-value-response.json"
            )

            assert(expectedFilenames.subsetOf(filenames))
        }
    }
}
