/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.e2e.v1

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.{E2ESpec, SharedAdminTestData}
import spray.json._

import scala.concurrent.duration._


object ProjectsRouteV1E2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing groups endpoint.
  */
class ProjectsRouteV1E2ESpec extends E2ESpec(ProjectsRouteV1E2ESpec.config) with SessionJsonProtocol with TriplestoreJsonProtocol {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds)

    private val rdfDataObjects = List.empty[RdfDataObject]

    val rootEmail = SharedAdminTestData.rootUser.userData.email.get
    val rootEmailEnc = java.net.URLEncoder.encode(rootEmail, "utf-8")
    val testPass = java.net.URLEncoder.encode("test", "utf-8")
    val imagesShortName = SharedAdminTestData.imagesProjectInfo.shortname
    val projectShortnameEnc = java.net.URLEncoder.encode(imagesShortName, "utf-8")


    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "The Projects Route ('v1/projects') with credentials supplied via Basic Auth" should {

        "return all project's information" in {
            /* Correct username and password */
            val request = Get(baseApiUrl + s"/v1/projects") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
            val response: HttpResponse = singleAwaitingRequest(request)
            log.debug(s"response: ${response.toString}")
            assert(response.status === StatusCodes.OK)
        }

        "return the project's information" in {
            /* Correct username and password */
            val request = Get(baseApiUrl + s"/v1/projects/shortname/$projectShortnameEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
            val response: HttpResponse = singleAwaitingRequest(request)
            log.debug(s"response: ${response.toString}")
            assert(response.status === StatusCodes.OK)
        }

    }
}
