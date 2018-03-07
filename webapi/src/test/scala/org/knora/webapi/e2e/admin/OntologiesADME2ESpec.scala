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

package org.knora.webapi.e2e.admin

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.{E2ESpec, OntologyConstants, SharedTestDataV1}
import spray.json._

import scala.concurrent.duration._


object OntologiesADME2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing the 'v1/permissions' route.
  *
  * This spec tests the 'v1/store' route.
  */
class OntologiesADME2ESpec extends E2ESpec(OntologiesADME2ESpec.config) with TriplestoreJsonProtocol {

    private val rdfDataObjects: List[RdfDataObject] = List.empty[RdfDataObject]

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/admin/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 600.seconds)
    }

    "The Ontologies Route ('admin/ontologies')" should {

        "return ontologies" in {
            val projectIri = java.net.URLEncoder.encode(SharedTestDataV1.imagesProjectInfo.id, "utf-8")
            val groupIri = java.net.URLEncoder.encode(OntologyConstants.KnoraBase.ProjectMember, "utf-8")

            val request = Get(baseApiUrl + s"/admin/ontologies")
            val response = singleAwaitingRequest(request, 1.seconds)
            // log.debug("==>> " + response.toString)
            assert(response.status === StatusCodes.OK)
        }
    }
}
