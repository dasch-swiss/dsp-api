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

package org.knora.webapi.e2e.admin

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi.e2e.v1.UsersV1E2ESpec
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.v1.routing.authenticationmessages.CredentialsV1
import org.knora.webapi.util.{AkkaHttpUtils, MutableTestIri}
import org.knora.webapi.{E2ESpec, IRI, SharedAdminTestData}
import spray.json._

import scala.concurrent.duration._


object UsersADME2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing users endpoint.
  */
class UsersADME2ESpec extends E2ESpec(UsersADME2ESpec.config) with SessionJsonProtocol with TriplestoreJsonProtocol {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(30.seconds)

    implicit override lazy val log = akka.event.Logging(system, this.getClass())

    private val rdfDataObjects = List.empty[RdfDataObject]

    val rootCreds = CredentialsV1(
        SharedAdminTestData.rootUser.userData.user_id.get,
        SharedAdminTestData.rootUser.userData.email.get,
        "test"
    )

    val normalUserCreds = CredentialsV1(
        SharedAdminTestData.normalUser.userData.user_id.get,
        SharedAdminTestData.normalUser.userData.email.get,
        "test"
    )

    val inactiveUserEmailEnc = java.net.URLEncoder.encode(SharedAdminTestData.inactiveUser.userData.email.get, "utf-8")


    val normalUserIri = SharedAdminTestData.normalUser.userData.user_id.get
    val normalUserIriEnc = java.net.URLEncoder.encode(normalUserIri, "utf-8")

    val multiUserIri = SharedAdminTestData.multiuserUser.userData.user_id.get
    val multiUserIriEnc = java.net.URLEncoder.encode(multiUserIri, "utf-8")

    val wrongEmail = "wrong@example.com"
    val wrongEmailEnc = java.net.URLEncoder.encode(wrongEmail, "utf-8")

    val testPass = java.net.URLEncoder.encode("test", "utf-8")
    val wrongPass = java.net.URLEncoder.encode("wrong", "utf-8")

    val imagesProjectIri = SharedAdminTestData.imagesProjectInfo.id
    val imagesProjectIriEnc = java.net.URLEncoder.encode(imagesProjectIri, "utf-8")

    val imagesReviewerGroupIri = SharedAdminTestData.imagesReviewerGroupInfo.id
    val imagesReviewerGroupIriEnc = java.net.URLEncoder.encode(imagesReviewerGroupIri, "utf-8")

    /**
      * Convenience method returning the users project memberships.
      *
      * @param userIri     the user's IRI.
      * @param credentials the credentials of the user making the request.
      */
    private def getUserProjectMemberships(userIri: IRI, credentials: CredentialsV1): Seq[IRI] = {
        val userIriEnc = java.net.URLEncoder.encode(userIri, "utf-8")
        val request = Get(baseApiUrl + "/v1/users/projects/" + userIriEnc) ~> addCredentials(BasicHttpCredentials(credentials.email, credentials.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[IRI]]
    }

    /**
      * Convenience method returning the users project-admin memberships.
      *
      * @param userIri     the user's IRI.
      * @param credentials the credentials of the user making the request.
      */
    private def getUserProjectAdminMemberships(userIri: IRI, credentials: CredentialsV1): Seq[IRI] = {
        val userIriEnc = java.net.URLEncoder.encode(userIri, "utf-8")
        val request = Get(baseApiUrl + "/v1/users/projects-admin/" + userIriEnc) ~> addCredentials(BasicHttpCredentials(credentials.email, credentials.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[IRI]]
    }

    /**
      * Convenience method returning the users group memberships.
      *
      * @param userIri     the user's IRI.
      * @param credentials the credentials of the user making the request.
      */
    private def getUserGroupMemberships(userIri: IRI, credentials: CredentialsV1): Seq[IRI] = {
        val userIriEnc = java.net.URLEncoder.encode(userIri, "utf-8")
        val request = Get(baseApiUrl + "/v1/users/groups/" + userIriEnc) ~> addCredentials(BasicHttpCredentials(credentials.email, credentials.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        AkkaHttpUtils.httpResponseToJson(response).fields("groups").convertTo[Seq[IRI]]
    }

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "The Users Route ('v1/users')" when {

        "used to query user information" should {

            "return all users" in {
                val request = Get(baseApiUrl + s"/v1/users") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)
            }

            "return a single user profile identified by iri" in {
                /* Correct username and password */
                val request = Get(baseApiUrl + s"/v1/users/${rootCreds.urlEncodedIri}") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)
            }

            "return a single user profile identified by email" in {
                /* Correct username and password */
                val request = Get(baseApiUrl + s"/v1/users/${rootCreds.urlEncodedEmail}?identifier=email") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)
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
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val jsonResult: Map[String, JsValue] = AkkaHttpUtils.httpResponseToJson(response).fields("userProfile").asJsObject.fields("userData").asJsObject.fields
                jsonResult("email").convertTo[String] should be("donald.duck@example.org")
                jsonResult("firstname").convertTo[String] should be("Donald")
                jsonResult("lastname").convertTo[String] should be("Duck")
                jsonResult("status").convertTo[Boolean] should be (true)
                jsonResult("lang").convertTo[String] should be("en")


                val iri = jsonResult("user_id").convertTo[String]
                donaldIri.set(iri)
                // log.debug(s"iri: ${donaldIri.get}")
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
                val request = Put(baseApiUrl + s"/v1/users/" + userIriEncoded, HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val jsonResult: Map[String, JsValue] = AkkaHttpUtils.httpResponseToJson(response).fields("userProfile").asJsObject.fields("userData").asJsObject.fields
                jsonResult("email").convertTo[String] should be("donald.big.duck@example.org")
                jsonResult("firstname").convertTo[String] should be("Big Donald")
                jsonResult("lastname").convertTo[String] should be("Duckmann")
                jsonResult("lang").convertTo[String] should be("de")
            }

            "update the user's password" in {

                val params01 =
                    s"""
                    {
                        "oldPassword": "test",
                        "newPassword": "test1234"
                    }
                    """.stripMargin


                val request1 = Put(baseApiUrl + s"/v1/users/" + rootCreds.urlEncodedIri, HttpEntity(ContentTypes.`application/json`, params01)) ~> addCredentials(BasicHttpCredentials(rootCreds.email, "test")) // old password
                val response1: HttpResponse = singleAwaitingRequest(request1)
                response1.status should be(StatusCodes.OK)

                val params02 =
                    s"""
                    {
                        "oldPassword": "test1234",
                        "newPassword": "test"
                    }
                    """.stripMargin


                val request2 = Put(baseApiUrl + s"/v1/users/" + rootCreds.urlEncodedIri, HttpEntity(ContentTypes.`application/json`, params02)) ~> addCredentials(BasicHttpCredentials(rootCreds.email, "test1234")) // new password
                val response2: HttpResponse = singleAwaitingRequest(request2)
                response2.status should be(StatusCodes.OK)
            }

            "delete the user by making him inactive" in {

                val donaldIriEncoded = java.net.URLEncoder.encode(donaldIri.get, "utf-8")

                val params =
                    s"""
                    {
                        "status": false
                    }
                    """.stripMargin


                val request = Put(baseApiUrl + s"/v1/users/" + donaldIriEncoded, HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val jsonResult: Map[String, JsValue] = AkkaHttpUtils.httpResponseToJson(response).fields("userProfile").asJsObject.fields("userData").asJsObject.fields
                jsonResult("status").convertTo[Boolean] should be(false)
            }

            "update the user's system admin membership status" in {
                val donaldIriEncoded = java.net.URLEncoder.encode(donaldIri.get, "utf-8")

                val params =
                    s"""
                    {
                        "systemAdmin": true
                    }
                    """.stripMargin


                val request = Put(baseApiUrl + s"/v1/users/" + donaldIriEncoded, HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val jsonResult: Map[String, JsValue] = AkkaHttpUtils.httpResponseToJson(response).fields("userProfile").asJsObject.fields("permissionData").asJsObject.fields("groupsPerProject").asJsObject.fields
                jsonResult("http://www.knora.org/ontology/knora-base#SystemProject").convertTo[List[String]].head should equal("http://www.knora.org/ontology/knora-base#SystemAdmin")
                // log.debug(jsonResult)

            }
        }

        "used to query project memberships" should {

            "return all projects the user is a member of" in {
                val request = Get(baseApiUrl + s"/v1/users/projects/$multiUserIriEnc") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val projects: Seq[IRI] = AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[List[IRI]]
                projects should contain allElementsOf Seq(SharedAdminTestData.IMAGES_PROJECT_IRI, SharedAdminTestData.INCUNABULA_PROJECT_IRI, SharedAdminTestData.ANYTHING_PROJECT_IRI)

                // testing getUserProjectMemberships method, which should return the same result
                projects should contain allElementsOf getUserProjectMemberships(multiUserIri, rootCreds)
            }
        }

        "used to modify project membership" should {


            "add user to project" in {
                val membershipsBeforeUpdate = getUserProjectMemberships(normalUserCreds.userIri, rootCreds)
                membershipsBeforeUpdate should equal(Seq())

                val request = Post(baseApiUrl + "/v1/users/projects/" + normalUserCreds.urlEncodedIri + "/" + imagesProjectIriEnc) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val membershipsAfterUpdate = getUserProjectMemberships(normalUserIri, rootCreds)
                membershipsAfterUpdate should equal(Seq(SharedAdminTestData.IMAGES_PROJECT_IRI))
            }

            "remove user from project" in {

                val membershipsBeforeUpdate = getUserProjectMemberships(normalUserCreds.userIri, rootCreds)
                membershipsBeforeUpdate should equal(Seq(SharedAdminTestData.IMAGES_PROJECT_IRI))

                val request = Delete(baseApiUrl + "/v1/users/projects/" + normalUserCreds.urlEncodedIri + "/" + imagesProjectIriEnc) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val membershipsAfterUpdate = getUserProjectMemberships(normalUserIri, rootCreds)
                membershipsAfterUpdate should equal(Seq())
            }
        }

        "used to query project admin group memberships" should {

            "return all projects the user is a member of the project admin group" in {
                val request = Get(baseApiUrl + s"/v1/users/projects-admin/$multiUserIriEnc") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val projects: Seq[IRI] = AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[List[IRI]]
                projects should contain allElementsOf Seq(SharedAdminTestData.IMAGES_PROJECT_IRI, SharedAdminTestData.INCUNABULA_PROJECT_IRI, SharedAdminTestData.ANYTHING_PROJECT_IRI)

                // explicitly testing 'getUserProjectsAdminMemberships' method, which should return the same result
                projects should contain allElementsOf getUserProjectAdminMemberships(multiUserIri, rootCreds)
            }
        }

        "used to modify project admin group membership" should {

            "add user to project admin group" in {
                val membershipsBeforeUpdate = getUserProjectAdminMemberships(normalUserCreds.userIri, rootCreds)
                //log.debug(s"membershipsBeforeUpdate: $membershipsBeforeUpdate")
                membershipsBeforeUpdate should equal(Seq())

                val request = Post(baseApiUrl + "/v1/users/projects-admin/" + normalUserCreds.urlEncodedIri + "/" + imagesProjectIriEnc) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                //log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val membershipsAfterUpdate = getUserProjectAdminMemberships(normalUserCreds.userIri, rootCreds)
                //log.debug(s"membershipsAfterUpdate: $membershipsAfterUpdate")
                membershipsAfterUpdate should equal(Seq(SharedAdminTestData.IMAGES_PROJECT_IRI))
            }

            "remove user from project admin group" in {

                val membershipsBeforeUpdate = getUserProjectAdminMemberships(normalUserCreds.userIri, rootCreds)
                log.debug(s"membershipsBeforeUpdate: $membershipsBeforeUpdate")
                membershipsBeforeUpdate should equal(Seq(SharedAdminTestData.IMAGES_PROJECT_IRI))

                val request = Delete(baseApiUrl + "/v1/users/projects-admin/" + normalUserCreds.urlEncodedIri + "/" + imagesProjectIriEnc) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                //og.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val membershipsAfterUpdate = getUserProjectAdminMemberships(normalUserCreds.userIri, rootCreds)
                log.debug(s"membershipsAfterUpdate: $membershipsAfterUpdate")
                membershipsAfterUpdate should equal(Seq())
            }

        }

        "used to query group memberships" should {

            "return all groups the user is a member of" in {
                val request = Get(baseApiUrl + s"/v1/users/groups/$multiUserIriEnc") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                //log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val groups: Seq[IRI] = AkkaHttpUtils.httpResponseToJson(response).fields("groups").convertTo[List[IRI]]
                groups should contain allElementsOf Seq("http://rdfh.ch/groups/00FF/images-reviewer")

                // testing getUserGroupMemberships method, which should return the same result
                groups should contain allElementsOf getUserGroupMemberships(multiUserIri, rootCreds)
            }
        }

        "used to modify group membership" should {

            "add user to group" in {

                val membershipsBeforeUpdate = getUserGroupMemberships(normalUserCreds.userIri, rootCreds)
                membershipsBeforeUpdate should equal(Seq())

                val request = Post(baseApiUrl + "/v1/users/groups/" + normalUserCreds.urlEncodedIri + "/" + imagesReviewerGroupIriEnc) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val membershipsAfterUpdate = getUserGroupMemberships(normalUserIri, rootCreds)
                membershipsAfterUpdate should equal(Seq(imagesReviewerGroupIri))
            }

            "remove user from group" in {

                val membershipsBeforeUpdate = getUserGroupMemberships(normalUserCreds.userIri, rootCreds)
                membershipsBeforeUpdate should equal(Seq(imagesReviewerGroupIri))

                val request = Delete(baseApiUrl + "/v1/users/groups/" + normalUserCreds.urlEncodedIri + "/" + imagesReviewerGroupIriEnc) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val membershipsAfterUpdate = getUserProjectMemberships(normalUserIri, rootCreds)
                membershipsAfterUpdate should equal(Seq())
            }
        }
    }
}
