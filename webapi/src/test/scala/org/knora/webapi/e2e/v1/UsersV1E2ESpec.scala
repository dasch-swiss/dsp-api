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
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.util.{AkkaHttpUtils, MutableTestIri}
import org.knora.webapi.{E2ESpec, SharedAdminTestData}
import spray.json._

import scala.concurrent.duration._


object UsersV1E2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing users endpoint.
  */
class UsersV1E2ESpec extends E2ESpec(UsersV1E2ESpec.config) with SessionJsonProtocol with TriplestoreJsonProtocol {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds)

    private val rdfDataObjects = List.empty[RdfDataObject]

    val rootEmail = SharedAdminTestData.rootUser.userData.email.get
    val rootEmailEnc = java.net.URLEncoder.encode(rootEmail, "utf-8")
    val inactiveUserEmailEnc = java.net.URLEncoder.encode(SharedAdminTestData.inactiveUser.userData.email.get, "utf-8")
    val wrongEmail = "wrong@example.com"
    val wrongEmailEnc = java.net.URLEncoder.encode(wrongEmail, "utf-8")
    val testPass = java.net.URLEncoder.encode("test", "utf-8")
    val wrongPass = java.net.URLEncoder.encode("wrong", "utf-8")

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "The Users Route ('v1/users')" when {

        "used to query user information" should {

            "return all users" in {
                val request = Get(baseApiUrl + s"/v1/users") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // println(s"response: ${response.toString}")
                response.status should be (StatusCodes.OK)
            }

            "return a single user profile" in {
                /* Correct username and password */
                val request = Get(baseApiUrl + s"/v1/users/email/$rootEmailEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // println(s"response: ${response.toString}")
                response.status should be (StatusCodes.OK)
            }

        }

        "used to modify user information" should {

            val donaldIri = new MutableTestIri

            "create the user and return it's profile if the supplied email is unique " in {

                val params =
                    s"""
                   |{
                   |    "email": "donald.duck@example.org",
                   |    "givenName": "Donald",
                   |    "familyName": "Duck",
                   |    "password": "test",
                   |    "status": true,
                   |    "lang": "en",
                   |    "systemAdmin": false
                   |}
                """.stripMargin

                val request = Post(baseApiUrl + s"/v1/users", HttpEntity(ContentTypes.`application/json`, params))
                val response: HttpResponse = singleAwaitingRequest(request)
                //println(s"response: ${response.toString}")
                response.status should be (StatusCodes.OK)

                val jsonResult: Map[String, JsValue] = AkkaHttpUtils.httpResponseToJson(response).fields("userProfile").asJsObject.fields("userData").asJsObject.fields
                jsonResult("email").convertTo[String] should be ("donald.duck@example.org")
                jsonResult("firstname").convertTo[String] should be ("Donald")
                jsonResult("lastname").convertTo[String] should be ("Duck")
                jsonResult("lang").convertTo[String] should be ("en")

                val iri = jsonResult("user_id").convertTo[String]
                donaldIri.set(iri)
                //println(s"iri: ${donaldIri.get}")
            }

            "update the user's basic information" in {

                val params =
                    s"""
                    {
                        "email": "donald.big.duck@example.org",
                        "givenName": "Big Donald",
                        "familyName": "Duckmann",
                        "lang": "de"
                    }
                    """.stripMargin

                val userIriEncoded = java.net.URLEncoder.encode(donaldIri.get, "utf-8")
                val request = Put(baseApiUrl + s"/v1/users/" + userIriEncoded, HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                //println(s"response: ${response.toString}")
                response.status should be (StatusCodes.OK)

                val jsonResult: Map[String, JsValue] = AkkaHttpUtils.httpResponseToJson(response).fields("userProfile").asJsObject.fields("userData").asJsObject.fields
                jsonResult("email").convertTo[String] should be ("donald.big.duck@example.org")
                jsonResult("firstname").convertTo[String] should be ("Big Donald")
                jsonResult("lastname").convertTo[String] should be ("Duckmann")
                jsonResult("lang").convertTo[String] should be ("de")
            }

            "update the user's password" in {

                val userIriEncoded = java.net.URLEncoder.encode(donaldIri.get, "utf-8")

                val params01 =
                    s"""
                    {
                        "oldPassword": "test",
                        "newPassword": "test1234"
                    }
                    """.stripMargin


                val request1 = Put(baseApiUrl + s"/v1/users/" + userIriEncoded, HttpEntity(ContentTypes.`application/json`, params01)) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response1: HttpResponse = singleAwaitingRequest(request1)
                response1.status should be (StatusCodes.OK)

                val params02 =
                    s"""
                    {
                        "oldPassword": "test1234",
                        "newPassword": "test"
                    }
                    """.stripMargin


                val request2 = Put(baseApiUrl + s"/v1/users/" + userIriEncoded, HttpEntity(ContentTypes.`application/json`, params02)) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response2: HttpResponse = singleAwaitingRequest(request2)
                response2.status should be (StatusCodes.OK)
            }

            "deleting the user by making him inactive" ignore {

            }

            "update the user's system admin membership status" ignore {

            }

            "change (adding and removing) project membership" ignore {

            }

            "change (adding and removing) group membership for built in groups (ProjectMember, ProjectAdmin)" ignore {

            }
        }
    }
}
