/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.e2e.v1

import akka.http.scaladsl.model.{HttpEntity, _}
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.util.TestingUtilities
import spray.json._

import scala.concurrent.duration._


/**
  * End-to-End (E2E) test specification for testing Knora-Sipi integration. Sipi must be running with the config file
  * `sipi.knora-docker-it-config.lua`.
  */
class KnoraSipiPermissionsV1ITSpec extends ITKnoraLiveSpec(KnoraSipiIntegrationV1ITSpec.config) with TriplestoreJsonProtocol with TestingUtilities {

    private val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    private val username = "root@example.com"
    private val password = "test"

    // creates tmp directory if not found
    createTmpFileDir()

    "Check if Sipi is running" in {
        // This requires that (1) fileserver.docroot is set in Sipi's config file and (2) it contains a file test.html.
        val request = Get(baseSipiUrl + "/server/test.html")
        val response = singleAwaitingRequest(request)
        assert(response.status == StatusCodes.OK, s"Sipi is probably not running: ${response.status}")
    }

    "Load test data" in {
        val request = Post(baseApiUrl + "/admin/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "Requesting Image" should {

        "returned as a restricted image in a smaller size" ignore {
            // TODO: https://github.com/dhlab-basel/Knora/issues/894
        }

        "denied with '401 Unauthorized' if the user does not have permission to see the image" ignore {
            // TODO: https://github.com/dhlab-basel/Knora/issues/894

        }
    }
}


