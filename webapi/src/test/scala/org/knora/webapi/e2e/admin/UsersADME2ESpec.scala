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

package org.knora.webapi.e2e.admin

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.groupsmessages.{GroupADM, GroupsADMJsonProtocol}
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectsADMJsonProtocol}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.v1.routing.authenticationmessages.CredentialsV1
import org.knora.webapi.util.{AkkaHttpUtils, MutableTestIri}

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
class UsersADME2ESpec extends E2ESpec(UsersADME2ESpec.config) with ProjectsADMJsonProtocol with GroupsADMJsonProtocol with SessionJsonProtocol with TriplestoreJsonProtocol {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(30.seconds)

    implicit override lazy val log = akka.event.Logging(system, this.getClass())

    val rootCreds = CredentialsV1(
        SharedTestDataV1.rootUser.userData.user_id.get,
        SharedTestDataV1.rootUser.userData.email.get,
        "test"
    )

    val normalUserCreds = CredentialsV1(
        SharedTestDataV1.normalUser.userData.user_id.get,
        SharedTestDataV1.normalUser.userData.email.get,
        "test"
    )

    private val inactiveUserEmailEnc = java.net.URLEncoder.encode(SharedTestDataV1.inactiveUser.userData.email.get, "utf-8")

    private val normalUserIri = SharedTestDataV1.normalUser.userData.user_id.get
    private val normalUserIriEnc = java.net.URLEncoder.encode(normalUserIri, "utf-8")

    private val multiUserIri = SharedTestDataV1.multiuserUser.userData.user_id.get
    private val multiUserIriEnc = java.net.URLEncoder.encode(multiUserIri, "utf-8")

    private val wrongEmail = "wrong@example.com"
    private val wrongEmailEnc = java.net.URLEncoder.encode(wrongEmail, "utf-8")

    private val testPass = java.net.URLEncoder.encode("test", "utf-8")
    private val wrongPass = java.net.URLEncoder.encode("wrong", "utf-8")

    private val imagesProjectIri = SharedTestDataADM.imagesProject.id
    private val imagesProjectIriEnc = java.net.URLEncoder.encode(imagesProjectIri, "utf-8")

    private val imagesReviewerGroupIri = SharedTestDataADM.imagesReviewerGroup.id
    private val imagesReviewerGroupIriEnc = java.net.URLEncoder.encode(imagesReviewerGroupIri, "utf-8")

    /**
      * Convenience method returning the users project memberships.
      *
      * @param userIri     the user's IRI.
      * @param credentials the credentials of the user making the request.
      */
    private def getUserProjectMemberships(userIri: IRI, credentials: CredentialsV1): Seq[ProjectADM] = {
        val userIriEnc = java.net.URLEncoder.encode(userIri, "utf-8")
        val request = Get(baseApiUrl + "/admin/users/projects/" + userIriEnc) ~> addCredentials(BasicHttpCredentials(credentials.email, credentials.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[ProjectADM]]
    }

    /**
      * Convenience method returning the users project-admin memberships.
      *
      * @param userIri     the user's IRI.
      * @param credentials the credentials of the user making the request.
      */
    private def getUserProjectAdminMemberships(userIri: IRI, credentials: CredentialsV1): Seq[ProjectADM] = {
        val userIriEnc = java.net.URLEncoder.encode(userIri, "utf-8")
        val request = Get(baseApiUrl + "/admin/users/projects-admin/" + userIriEnc) ~> addCredentials(BasicHttpCredentials(credentials.email, credentials.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[ProjectADM]]
    }

    /**
      * Convenience method returning the users group memberships.
      *
      * @param userIri     the user's IRI.
      * @param credentials the credentials of the user making the request.
      */
    private def getUserGroupMemberships(userIri: IRI, credentials: CredentialsV1): Seq[GroupADM] = {
        val userIriEnc = java.net.URLEncoder.encode(userIri, "utf-8")
        val request = Get(baseApiUrl + "/admin/users/groups/" + userIriEnc) ~> addCredentials(BasicHttpCredentials(credentials.email, credentials.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        AkkaHttpUtils.httpResponseToJson(response).fields("groups").convertTo[Seq[GroupADM]]
    }

    "The Users Route ('v1/users')" when {

        "used to query user information" should {

            "return all users" in {
                val request = Get(baseApiUrl + s"/admin/users") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)
            }

            "return a single user profile identified by iri" in {
                /* Correct username and password */
                val request = Get(baseApiUrl + s"/admin/users/${rootCreds.urlEncodedIri}") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)
            }

            "return a single user profile identified by email" in {
                /* Correct username and password */
                val request = Get(baseApiUrl + s"/admin/users/${rootCreds.urlEncodedEmail}?identifier=email") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)
            }

        }

        "used to modify user information" should {

            val donaldIri = new MutableTestIri

            "create the user if the supplied email is unique " in {

                val params =
                    s"""
                   |{
                   |    "username": "donald.duck",
                   |    "email": "donald.duck@example.org",
                   |    "givenName": "Donald",
                   |    "familyName": "Duck",
                   |    "password": "test",
                   |    "status": true,
                   |    "lang": "en",
                   |    "systemAdmin": false
                   |}
                """.stripMargin

                val request = Post(baseApiUrl + s"/admin/users", HttpEntity(ContentTypes.`application/json`, params))
                val response: HttpResponse = singleAwaitingRequest(request)

                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val result: UserADM = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[UserADM]
                result.username should be("donald.duck")
                result.email should be("donald.duck@example.org")
                result.givenName should be("Donald")
                result.familyName should be("Duck")
                result.status should be (true)
                result.lang should be("en")

                donaldIri.set(result.id)
                // log.debug(s"iri: ${donaldIri.get}")
            }

            "update the user's basic information" in {

                val params =
                    s"""
                    {
                        "username": "donald.big.duck",
                        "email": "donald.big.duck@example.org",
                        "givenName": "Big Donald",
                        "familyName": "Duckmann",
                        "lang": "de"
                    }
                    """.stripMargin

                val userIriEncoded = java.net.URLEncoder.encode(donaldIri.get, "utf-8")
                val request = Put(baseApiUrl + s"/admin/users/" + userIriEncoded, HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val result: UserADM = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[UserADM]
                result.username should be("donald.big.duck")
                result.email should be("donald.big.duck@example.org")
                result.givenName should be("Big Donald")
                result.familyName should be("Duckmann")
                result.lang should be("de")
            }

            "update the user's password (by himself)" in {

                val params01 =
                    s"""
                    {
                        "requesterPassword": "test",
                        "newPassword": "test123456"
                    }
                    """.stripMargin


                val request1 = Put(baseApiUrl + s"/admin/users/" + normalUserCreds.urlEncodedIri, HttpEntity(ContentTypes.`application/json`, params01)) ~> addCredentials(BasicHttpCredentials(normalUserCreds.email, "test")) // requester's password
                val response1: HttpResponse = singleAwaitingRequest(request1)
                log.debug(s"response: ${response1.toString}")
                response1.status should be(StatusCodes.OK)

                // check if the password was changed, i.e. if the new one is accepted
                val request2 = Get(baseApiUrl + s"/v2/authentication") ~> addCredentials(BasicHttpCredentials(normalUserCreds.email, "test123456")) // new password
                val response2: HttpResponse = singleAwaitingRequest(request2)
                response2.status should be(StatusCodes.OK)
            }

            "update the user's password (by a system admin)" in {

                val params01 =
                    s"""
                    {
                        "requesterPassword": "test",
                        "newPassword": "test654321"
                    }
                    """.stripMargin


                val request1 = Put(baseApiUrl + s"/admin/users/" + normalUserCreds.urlEncodedIri, HttpEntity(ContentTypes.`application/json`, params01)) ~> addCredentials(BasicHttpCredentials(rootCreds.email, "test")) // requester's password
                val response1: HttpResponse = singleAwaitingRequest(request1)
                log.debug(s"response: ${response1.toString}")
                response1.status should be(StatusCodes.OK)

                // check if the password was changed, i.e. if the new one is accepted
                val request2 = Get(baseApiUrl + s"/v2/authentication") ~> addCredentials(BasicHttpCredentials(normalUserCreds.email, "test654321")) // new password
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


                val request = Put(baseApiUrl + s"/admin/users/" + donaldIriEncoded, HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val result: UserADM = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[UserADM]
                result.status should be(false)
            }

            "update the user's system admin membership status" in {
                val donaldIriEncoded = java.net.URLEncoder.encode(donaldIri.get, "utf-8")

                val params =
                    s"""
                    {
                        "systemAdmin": true
                    }
                    """.stripMargin


                val request = Put(baseApiUrl + s"/admin/users/" + donaldIriEncoded, HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val result: UserADM = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[UserADM]
                result.permissions.groupsPerProject.get("http://www.knora.org/ontology/knora-base#SystemProject").head should equal(List("http://www.knora.org/ontology/knora-base#SystemAdmin"))
                // log.debug(jsonResult)

            }

            "not allow changing the system user" in {

                val systemUserIriEncoded = java.net.URLEncoder.encode(KnoraSystemInstances.Users.SystemUser.id, "utf-8")

                val params =
                    s"""
                    {
                        "status": false
                    }
                    """.stripMargin


                val request = Put(baseApiUrl + s"/admin/users/" + systemUserIriEncoded, HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                response.status should be(StatusCodes.BadRequest)
            }

            "not allow changing the anonymous user" in {

                val anonymousUserIriEncoded = java.net.URLEncoder.encode(KnoraSystemInstances.Users.AnonymousUser.id, "utf-8")

                val params =
                    s"""
                    {
                        "status": false
                    }
                    """.stripMargin


                val request = Put(baseApiUrl + s"/admin/users/" + anonymousUserIriEncoded, HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                response.status should be(StatusCodes.BadRequest)
            }

            "not allow deleting the system user" in {
                val systemUserIriEncoded = java.net.URLEncoder.encode(KnoraSystemInstances.Users.SystemUser.id, "utf-8")

                val params =
                    s"""
                    {
                        "status": false
                    }
                    """.stripMargin


                val request = Delete(baseApiUrl + s"/admin/users/" + systemUserIriEncoded) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                response.status should be(StatusCodes.BadRequest)
            }

            "not allow deleting the anonymous user" in {
                val anonymousUserIriEncoded = java.net.URLEncoder.encode(KnoraSystemInstances.Users.AnonymousUser.id, "utf-8")

                val request = Delete(baseApiUrl + s"/admin/users/" + anonymousUserIriEncoded) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                response.status should be(StatusCodes.BadRequest)
            }

        }

        "used to query project memberships" should {

            "return all projects the user is a member of" in {
                val request = Get(baseApiUrl + s"/admin/users/projects/$multiUserIriEnc") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val projects: Seq[ProjectADM] = AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[List[ProjectADM]]
                projects should contain allElementsOf Seq(SharedTestDataADM.imagesProject, SharedTestDataADM.incunabulaProject, SharedTestDataADM.anythingProject)

                // testing getUserProjectMemberships method, which should return the same result
                projects should contain allElementsOf getUserProjectMemberships(multiUserIri, rootCreds)
            }
        }

        "used to modify project membership" should {


            "add user to project" in {
                val membershipsBeforeUpdate = getUserProjectMemberships(normalUserCreds.userIri, rootCreds)
                membershipsBeforeUpdate should equal(Seq())

                val request = Post(baseApiUrl + "/admin/users/projects/" + normalUserCreds.urlEncodedIri + "/" + imagesProjectIriEnc) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val membershipsAfterUpdate = getUserProjectMemberships(normalUserIri, rootCreds)
                membershipsAfterUpdate should equal(Seq(SharedTestDataADM.imagesProject))
            }

            "remove user from project" in {

                val membershipsBeforeUpdate = getUserProjectMemberships(normalUserCreds.userIri, rootCreds)
                membershipsBeforeUpdate should equal(Seq(SharedTestDataADM.imagesProject))

                val request = Delete(baseApiUrl + "/admin/users/projects/" + normalUserCreds.urlEncodedIri + "/" + imagesProjectIriEnc) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val membershipsAfterUpdate = getUserProjectMemberships(normalUserIri, rootCreds)
                membershipsAfterUpdate should equal(Seq.empty[ProjectADM])
            }
        }

        "used to query project admin group memberships" should {

            "return all projects the user is a member of the project admin group" in {
                val request = Get(baseApiUrl + s"/admin/users/projects-admin/$multiUserIriEnc") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val projects: Seq[ProjectADM] = AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[ProjectADM]]
                projects should contain allElementsOf Seq(SharedTestDataADM.imagesProject, SharedTestDataADM.incunabulaProject, SharedTestDataADM.anythingProject)

                // explicitly testing 'getUserProjectsAdminMemberships' method, which should return the same result
                projects should contain allElementsOf getUserProjectAdminMemberships(multiUserIri, rootCreds)
            }
        }

        "used to modify project admin group membership" should {

            "add user to project admin group" in {
                val membershipsBeforeUpdate = getUserProjectAdminMemberships(normalUserCreds.userIri, rootCreds)
                //log.debug(s"membershipsBeforeUpdate: $membershipsBeforeUpdate")
                membershipsBeforeUpdate should equal(Seq())

                val request = Post(baseApiUrl + "/admin/users/projects-admin/" + normalUserCreds.urlEncodedIri + "/" + imagesProjectIriEnc) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                //log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val membershipsAfterUpdate = getUserProjectAdminMemberships(normalUserCreds.userIri, rootCreds)
                //log.debug(s"membershipsAfterUpdate: $membershipsAfterUpdate")
                membershipsAfterUpdate should equal(Seq(SharedTestDataADM.imagesProject))
            }

            "remove user from project admin group" in {

                val membershipsBeforeUpdate = getUserProjectAdminMemberships(normalUserCreds.userIri, rootCreds)
                // log.debug(s"membershipsBeforeUpdate: $membershipsBeforeUpdate")
                membershipsBeforeUpdate should equal(Seq(SharedTestDataADM.imagesProject))

                val request = Delete(baseApiUrl + "/admin/users/projects-admin/" + normalUserCreds.urlEncodedIri + "/" + imagesProjectIriEnc) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val membershipsAfterUpdate = getUserProjectAdminMemberships(normalUserCreds.userIri, rootCreds)
                // log.debug(s"membershipsAfterUpdate: $membershipsAfterUpdate")
                membershipsAfterUpdate should equal(Seq.empty[ProjectADM])
            }

        }

        "used to query group memberships" should {

            "return all groups the user is a member of" in {
                val request = Get(baseApiUrl + s"/admin/users/groups/$multiUserIriEnc") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val groups: Seq[GroupADM] = AkkaHttpUtils.httpResponseToJson(response).fields("groups").convertTo[List[GroupADM]]
                groups should contain allElementsOf Seq(SharedTestDataADM.imagesReviewerGroup)

                // testing getUserGroupMemberships method, which should return the same result
                groups should contain allElementsOf getUserGroupMemberships(multiUserIri, rootCreds)
            }
        }

        "used to modify group membership" should {

            "add user to group" in {

                val membershipsBeforeUpdate = getUserGroupMemberships(normalUserCreds.userIri, rootCreds)
                membershipsBeforeUpdate should equal(Seq.empty[GroupADM])

                val request = Post(baseApiUrl + "/admin/users/groups/" + normalUserCreds.urlEncodedIri + "/" + imagesReviewerGroupIriEnc) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val membershipsAfterUpdate = getUserGroupMemberships(normalUserIri, rootCreds)
                membershipsAfterUpdate should equal(Seq(SharedTestDataADM.imagesReviewerGroup))
            }

            "remove user from group" in {

                val membershipsBeforeUpdate = getUserGroupMemberships(normalUserCreds.userIri, rootCreds)
                membershipsBeforeUpdate should equal(Seq(SharedTestDataADM.imagesReviewerGroup))

                val request = Delete(baseApiUrl + "/admin/users/groups/" + normalUserCreds.urlEncodedIri + "/" + imagesReviewerGroupIriEnc) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)

                val membershipsAfterUpdate = getUserProjectMemberships(normalUserIri, rootCreds)
                membershipsAfterUpdate should equal(Seq.empty[GroupADM])
            }
        }
    }
}
