/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.other.v1

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages._

import scala.concurrent.duration._

import spray.json._

object PermissionHandlingV1E2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-end test specification for testing the handling of permissions.
  */
class PermissionHandlingV1E2ESpec extends E2ESpec(PermissionHandlingV1E2ESpec.config) with TriplestoreJsonProtocol {

    private val rootUser = SharedAdminTestData.rootUser
    private val rootUserEmail = rootUser.userData.email.get

    private val imagesUser = SharedAdminTestData.imagesUser01
    private val imagesUserEmail = imagesUser.userData.email.get

    private val incunabulaUser = SharedAdminTestData.incunabulaProjectAdminUser
    private val incunabulaUserEmail = incunabulaUser.userData.email.get

    private val password = "test"

    private val rdfDataObjects: List[RdfDataObject] = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    "Load test data" in {
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }


    "The Permissions Handling" should {


        "allow a project member to create a resource" in {

            val params =
                """
                  |{
                  |    "restype_id": "http://www.knora.org/ontology/images#person",
                  |    "label": "Testperson",
                  |    "project_id": "http://data.knora.org/projects/images",
                  |    "properties": {
                  |        "http://www.knora.org/ontology/images#lastname": [{"richtext_value":{"utf8str":"Testname"}}],
                  |        "http://www.knora.org/ontology/images#firstname": [{"richtext_value":{"utf8str":"Name"}}]
                  |    }
                  |}
                """.stripMargin

            val request = Post(baseApiUrl + s"/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(imagesUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)

            assert(response.status === StatusCodes.OK)

        }

        "allow a system admin user not in the project to create a resource" in {

            val params =
                """
                  |{
                  |    "restype_id": "http://www.knora.org/ontology/images#person",
                  |    "label": "Testperson",
                  |    "project_id": "http://data.knora.org/projects/images",
                  |    "properties": {
                  |        "http://www.knora.org/ontology/images#lastname": [{"richtext_value":{"utf8str":"Testname"}}],
                  |        "http://www.knora.org/ontology/images#firstname": [{"richtext_value":{"utf8str":"Name"}}]
                  |    }
                  |}
                """.stripMargin

            val request = Post(baseApiUrl + s"/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(rootUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
        }

        "not allow a user from another project to create a resource" in {

            val params =
                """
                  |{
                  |    "restype_id": "http://www.knora.org/ontology/images#person",
                  |    "label": "Testperson",
                  |    "project_id": "http://data.knora.org/projects/images",
                  |    "properties": {
                  |        "http://www.knora.org/ontology/images#lastname": [{"richtext_value":{"utf8str":"Testname"}}],
                  |        "http://www.knora.org/ontology/images#firstname": [{"richtext_value":{"utf8str":"Name"}}]
                  |    }
                  |}
                """.stripMargin

            val request = Post(baseApiUrl + s"/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
        }
    }

}
