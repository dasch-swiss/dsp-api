/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin

import org.apache.pekko
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

import java.net.URLEncoder
import scala.concurrent.Await
import scala.concurrent.duration.*

import org.knora.webapi.*
import org.knora.webapi.messages.admin.responder.IntegrationTestAdminJsonProtocol.*
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM2
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.util.AkkaHttpUtils
import org.knora.webapi.util.MutableTestIri

import pekko.http.scaladsl.model.*
import pekko.http.scaladsl.model.headers.*
import pekko.http.scaladsl.unmarshalling.Unmarshal

/**
 * End-to-End (E2E) test specification for testing users endpoint.
 */
class UsersADME2ESpec extends E2ESpec with SprayJsonSupport {

  private val rootUser                         = SharedTestDataADM.rootUser
  private val projectAdminUser                 = SharedTestDataADM.imagesUser01
  private val normalUser                       = SharedTestDataADM.normalUser
  private def addRootUserCredentials()         = addCredentials(rootUser)
  private def addProjectAdminUserCredentials() = addCredentials(projectAdminUser)
  private def addNormalUserCredentials()       = addCredentials(normalUser)
  private def addCredentials(user: User): RequestTransformer = addCredentials(
    BasicHttpCredentials(user.email, "test"),
  )

  private val normalUserIri    = SharedTestDataADM2.normalUser.userData.user_id.get
  private val normalUserIriEnc = java.net.URLEncoder.encode(normalUserIri, "utf-8")

  private val multiUserIri    = SharedTestDataADM2.multiuserUser.userData.user_id.get
  private val multiUserIriEnc = java.net.URLEncoder.encode(multiUserIri, "utf-8")

  private val imagesProjectIri    = SharedTestDataADM.imagesProject.id
  private val imagesProjectIriEnc = java.net.URLEncoder.encode(imagesProjectIri.value, "utf-8")

  private val imagesReviewerGroupIri    = SharedTestDataADM.imagesReviewerGroup.id
  private val imagesReviewerGroupIriEnc = java.net.URLEncoder.encode(imagesReviewerGroupIri, "utf-8")

  private val customUserIri      = "http://rdfh.ch/users/14pxW-LAQIaGcCRiNCPJcQ"
  private val otherCustomUserIri = "http://rdfh.ch/users/v8_12VcJRlGNFCjYzqJ5cA"

  private val donaldIri            = new MutableTestIri
  private val systemUserIriEncoded = java.net.URLEncoder.encode(KnoraSystemInstances.Users.SystemUser.id, "utf-8")

  /**
   * Convenience method returning the users project memberships.
   *
   * @param userIri the user's IRI.
   */
  private def getUserProjectMemberships(userIri: IRI) = {
    val userIriEnc = java.net.URLEncoder.encode(userIri, "utf-8")
    val request    = Get(baseApiUrl + s"/admin/users/iri/$userIriEnc/project-memberships") ~> addRootUserCredentials()
    val response   = singleAwaitingRequest(request)
    AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[Project]]
  }

  /**
   * Convenience method returning the users project-admin memberships.
   *
   * @param userIri     the user's IRI.
   */
  private def getUserProjectAdminMemberships(userIri: IRI) = {
    val userIriEnc = java.net.URLEncoder.encode(userIri, "utf-8")
    val request =
      Get(baseApiUrl + s"/admin/users/iri/$userIriEnc/project-admin-memberships") ~> addRootUserCredentials()
    val response = singleAwaitingRequest(request)
    AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[Project]]
  }

  /**
   * Convenience method returning the users group memberships.
   *
   * @param userIri     the user's IRI.
   */
  private def getUserGroupMemberships(userIri: IRI) = {
    val userIriEnc = java.net.URLEncoder.encode(userIri, "utf-8")
    val request    = Get(baseApiUrl + s"/admin/users/iri/$userIriEnc/group-memberships") ~> addRootUserCredentials()
    val response   = singleAwaitingRequest(request)
    AkkaHttpUtils.httpResponseToJson(response).fields("groups").convertTo[Seq[Group]]
  }

  "The Users Route ('admin/users')" when {
    "used to query user information [FUNCTIONALITY]" should {
      "return all users" in {
        val request =
          Get(baseApiUrl + s"/admin/users") ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
      }

      "return a single user profile identified by iri" in {
        /* Correct username and password */
        val request =
          Get(baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(rootUser.id, "utf-8")}") ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)
      }

      "return a single user profile identified by email" in {
        /* Correct username and password */
        val request =
          Get(
            baseApiUrl + s"/admin/users/email/${URLEncoder.encode(rootUser.email, "utf-8")}",
          ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)
      }

      "return a single user profile identified by username" in {
        /* Correct username and password */
        val request = Get(
          baseApiUrl + s"/admin/users/username/${SharedTestDataADM.rootUser.username}",
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)
      }

    }

    "used to query user information [PERMISSIONS]" should {
      "return single user for SystemAdmin" in {
        val request                = Get(baseApiUrl + s"/admin/users/iri/$normalUserIriEnc") ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
      }

      "return single user for itself" in {
        val request                = Get(baseApiUrl + s"/admin/users/iri/$normalUserIriEnc") ~> addNormalUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
      }

      "return only public information for single user for non SystemAdmin and self" in {
        val request                = Get(baseApiUrl + s"/admin/users/iri/$normalUserIriEnc") ~> addProjectAdminUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val result: User = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[User]
        result.givenName should be(SharedTestDataADM.normalUser.givenName)
        result.familyName should be(SharedTestDataADM.normalUser.familyName)
        result.status should be(false)
        result.email should be("")
        result.username should be("")
      }

      "return only public information for single user with anonymous access" in {
        val request                = Get(baseApiUrl + s"/admin/users/iri/$normalUserIriEnc")
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val result: User = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[User]
        result.givenName should be(SharedTestDataADM.normalUser.givenName)
        result.familyName should be(SharedTestDataADM.normalUser.familyName)
        result.status should be(false)
        result.email should be("")
        result.username should be("")
      }

      "return all users for SystemAdmin" in {
        val request                = Get(baseApiUrl + s"/admin/users") ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
      }

      "return all users for ProjectAdmin" in {
        val request                = Get(baseApiUrl + s"/admin/users") ~> addProjectAdminUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
      }

      "return 'Forbidden' for all users for normal user" in {
        val request                = Get(baseApiUrl + s"/admin/users") ~> addNormalUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.Forbidden)
      }

    }

    "given a custom Iri" should {
      "given no credentials in the request when creating a user it must be forbidden" in {
        val validUserCreationRequest: String =
          s"""{
             |    "id": "$customUserIri",
             |    "username": "userWithCustomIri",
             |    "email": "userWithCustomIri@example.org",
             |    "givenName": "a user",
             |    "familyName": "with a custom Iri",
             |    "password": "test",
             |    "status": true,
             |    "lang": "en",
             |    "systemAdmin": false
             |}""".stripMargin
        val request = Post(
          baseApiUrl + s"/admin/users",
          HttpEntity(ContentTypes.`application/json`, validUserCreationRequest),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.Unauthorized)
      }

      "create a user with the provided custom IRI" in {
        val createUserWithCustomIriRequest: String =
          s"""{
             |    "id": "$customUserIri",
             |    "username": "userWithCustomIri",
             |    "email": "userWithCustomIri@example.org",
             |    "givenName": "a user",
             |    "familyName": "with a custom Iri",
             |    "password": "test",
             |    "status": true,
             |    "lang": "en",
             |    "systemAdmin": false
             |}""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/users",
          HttpEntity(ContentTypes.`application/json`, createUserWithCustomIriRequest),
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val result: User = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[User]

        // check that the custom IRI is correctly assigned
        result.id should be(customUserIri)
      }

      "return 'BadRequest' if the supplied IRI for the user is not unique" in {
        val params =
          s"""{
             |    "id": "$customUserIri",
             |    "username": "userWithDuplicateCustomIri",
             |    "email": "userWithDuplicateCustomIri@example.org",
             |    "givenName": "a user",
             |    "familyName": "with a duplicate custom Iri",
             |    "password": "test",
             |    "status": true,
             |    "lang": "en",
             |    "systemAdmin": false
             |}""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/users",
          HttpEntity(ContentTypes.`application/json`, params),
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)

        val errorMessage: String = Await.result(Unmarshal(response.entity).to[String], 1.second)
        val invalidIri: Boolean =
          errorMessage.contains(s"IRI: '$customUserIri' already exists, try another one.")
        invalidIri should be(true)
      }

    }

    "dealing with special characters" should {
      "escape special characters when creating the user" in {
        val createUserWithApostropheRequest: String =
          s"""{
             |    "id": "$otherCustomUserIri",
             |    "username": "userWithApostrophe",
             |    "email": "userWithApostrophe@example.org",
             |    "givenName": "M\\"Given 'Name",
             |    "familyName": "M\\tFamily Name",
             |    "password": "test",
             |    "status": true,
             |    "lang": "en",
             |    "systemAdmin": false
             |}""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/users",
          HttpEntity(ContentTypes.`application/json`, createUserWithApostropheRequest),
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val result: User = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[User]

        // check that the special characters were escaped correctly
        result.id should equal(otherCustomUserIri)
        result.givenName should equal("M\"Given 'Name")
        result.familyName should equal("M\tFamily Name")

      }

      "escape special characters when updating the user" in {
        val updateUserRequest: String =
          s"""{
             |    "givenName": "Updated\\tGivenName",
             |    "familyName": "Updated\\"FamilyName"
             |}""".stripMargin

        val userIriEncoded = java.net.URLEncoder.encode(otherCustomUserIri, "utf-8")
        val request = Put(
          baseApiUrl + s"/admin/users/iri/$userIriEncoded/BasicUserInformation",
          HttpEntity(ContentTypes.`application/json`, updateUserRequest),
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val result: User = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[User]
        result.givenName should be("Updated\tGivenName")
        result.familyName should be("Updated\"FamilyName")
      }

      "return the special characters correctly when getting a user with special characters in givenName and familyName" in {
        val userIriEncoded = java.net.URLEncoder.encode(otherCustomUserIri, "utf-8")

        val request                = Get(baseApiUrl + s"/admin/users/iri/$userIriEncoded") ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val result: User = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[User]
        result.givenName should be("Updated\tGivenName")
        result.familyName should be("Updated\"FamilyName")
      }

    }

    "used to create a user" should {
      "not allow a projectAdmin to create a System Admin" in {
        val createUserRequest: String =
          s"""{
             |    "username": "daisy.duck",
             |    "email": "daisy.duck@example.org",
             |    "givenName": "Daisy",
             |    "familyName": "Duck",
             |    "password": "test",
             |    "status": true,
             |    "lang": "en",
             |    "systemAdmin": true
             |}""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/users",
          HttpEntity(ContentTypes.`application/json`, createUserRequest),
        ) ~> addProjectAdminUserCredentials()

        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.Forbidden)
      }

      "create the user if the supplied email and username are unique" in {
        val createUserRequest: String =
          s"""{
             |    "username": "donald.duck",
             |    "email": "donald.duck@example.org",
             |    "givenName": "Donald",
             |    "familyName": "Duck",
             |    "password": "test",
             |    "status": true,
             |    "lang": "en",
             |    "systemAdmin": false
             |}""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/users",
          HttpEntity(ContentTypes.`application/json`, createUserRequest),
        ) ~> addRootUserCredentials()

        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val result: User = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[User]
        result.username should be("donald.duck")
        result.email should be("donald.duck@example.org")
        result.givenName should be("Donald")
        result.familyName should be("Duck")
        result.status should be(true)
        result.lang should be("en")

        donaldIri.set(result.id)
      }

      "return a 'BadRequest' if the supplied username is not unique" in {
        val createUserRequest: String =
          s"""{
             |    "username": "donald.duck",
             |    "email": "new.donald.duck@example.org",
             |    "givenName": "NewDonald",
             |    "familyName": "NewDuck",
             |    "password": "test",
             |    "status": true,
             |    "lang": "en",
             |    "systemAdmin": false
             |}""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/users",
          HttpEntity(ContentTypes.`application/json`, createUserRequest),
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.BadRequest)
      }

      "return a 'BadRequest' if the supplied email is not unique" in {
        val createUserRequest: String =
          s"""{
             |    "username": "new.donald.duck",
             |    "email": "donald.duck@example.org",
             |    "givenName": "NewDonald",
             |    "familyName": "NewDuck",
             |    "password": "test",
             |    "status": true,
             |    "lang": "en",
             |    "systemAdmin": false
             |}""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/users",
          HttpEntity(ContentTypes.`application/json`, createUserRequest),
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.BadRequest)
      }

      "authenticate the newly created user using HttpBasicAuth" in {
        val request = Get(baseApiUrl + s"/v2/authentication") ~> addCredentials(
          BasicHttpCredentials("donald.duck@example.org", "test"),
        )
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)
      }

      "authenticate the newly created user during login" in {
        val params =
          s"""
                    {
                        "email": "donald.duck@example.org",
                        "password": "test"
                    }
                    """.stripMargin

        val request                = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)
      }

    }

    "used to modify user information" should {
      "update the user's basic information" in {
        val updateUserRequest: String =
          s"""{
             |    "username": "donald.big.duck",
             |    "email": "donald.big.duck@example.org",
             |    "givenName": "Big Donald",
             |    "familyName": "Duckmann",
             |    "lang": "de"
             |}""".stripMargin

        val userIriEncoded = java.net.URLEncoder.encode(donaldIri.get, "utf-8")
        val request = Put(
          baseApiUrl + s"/admin/users/iri/$userIriEncoded/BasicUserInformation",
          HttpEntity(ContentTypes.`application/json`, updateUserRequest),
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val result: User = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[User]
        result.username should be("donald.big.duck")
        result.email should be("donald.big.duck@example.org")
        result.givenName should be("Big Donald")
        result.familyName should be("Duckmann")
        result.lang should be("de")
      }

      "return 'BadRequest' if user IRI is None and 'NotFound' if user IRI is '' in update user request" in {
        val updateUserRequest: String =
          s"""{
             |    "username": "donald.without.iri.duck"
             |}""".stripMargin

        val missingUserIri = ""
        val request = Put(
          baseApiUrl + s"/admin/users/iri/$missingUserIri/BasicUserInformation",
          HttpEntity(ContentTypes.`application/json`, updateUserRequest),
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.NotFound)

        val missingUserIriNone = None
        val request2 = Put(
          baseApiUrl + s"/admin/users/iri/$missingUserIriNone/BasicUserInformation",
          HttpEntity(ContentTypes.`application/json`, updateUserRequest),
        ) ~> addRootUserCredentials()
        val response2: HttpResponse = singleAwaitingRequest(request2)

        response2.status should be(StatusCodes.BadRequest)
      }

      "return 'Forbidden' when updating another user's password if a requesting user is not a SystemAdmin" in {
        val changeUserPasswordRequest: String =
          s"""{
             |    "requesterPassword": "test",
             |    "newPassword": "will-be-ignored"
             |}""".stripMargin

        val request1 = Put(
          baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(customUserIri, "utf-8")}/Password",
          HttpEntity(ContentTypes.`application/json`, changeUserPasswordRequest),
        ) ~> addCredentials(BasicHttpCredentials(normalUser.email, "test"))
        val response1: HttpResponse = singleAwaitingRequest(request1)
        response1.status should be(StatusCodes.Forbidden)
      }

      "update the user's password (by himself)" in {
        val changeUserPasswordRequest: String =
          s"""{
             |    "requesterPassword": "test",
             |    "newPassword": "test123456"
             |}""".stripMargin

        val request1 = Put(
          baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(normalUser.id, "utf-8")}/Password",
          HttpEntity(ContentTypes.`application/json`, changeUserPasswordRequest),
        ) ~> addCredentials(BasicHttpCredentials(normalUser.email, "test")) // requester's password
        val response1: HttpResponse = singleAwaitingRequest(request1)
        response1.status should be(StatusCodes.OK)

        // check if the password was changed, i.e. if the new one is accepted
        val request2 = Get(baseApiUrl + s"/v2/authentication") ~> addCredentials(
          BasicHttpCredentials(normalUser.email, "test123456"),
        ) // new password
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

        val request1 = Put(
          baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(normalUser.id, "utf-8")}/Password",
          HttpEntity(ContentTypes.`application/json`, params01),
        ) ~> addCredentials(BasicHttpCredentials(rootUser.email, "test")) // requester's password
        val response1: HttpResponse = singleAwaitingRequest(request1)
        response1.status should be(StatusCodes.OK)

        // check if the password was changed, i.e. if the new one is accepted
        val request2 = Get(baseApiUrl + s"/v2/authentication") ~> addCredentials(
          BasicHttpCredentials(normalUser.email, "test654321"),
        ) // new password
        val response2: HttpResponse = singleAwaitingRequest(request2)
        response2.status should be(StatusCodes.OK)
      }

      "return 'BadRequest' if new password in change password request is missing" in {
        val changeUserPasswordRequest: String =
          s"""{
             |    "requesterPassword": "test"
             |}""".stripMargin

        val request1 = Put(
          baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(normalUser.id, "utf-8")}/Password",
          HttpEntity(ContentTypes.`application/json`, changeUserPasswordRequest),
        ) ~> addCredentials(BasicHttpCredentials(normalUser.email, "test654321")) // requester's password
        val response1: HttpResponse = singleAwaitingRequest(request1)

        response1.status should be(StatusCodes.BadRequest)

        // check that the password was not changed, i.e. the old one is still accepted
        val request2 = Get(baseApiUrl + s"/v2/authentication") ~> addCredentials(
          BasicHttpCredentials(normalUser.email, "test654321"),
        ) // old password (taken from previous test)
        val response2: HttpResponse = singleAwaitingRequest(request2)
        response2.status should be(StatusCodes.OK)
      }

      "return 'BadRequest' if requester's password in change password request is missing" in {
        val changeUserPasswordRequest: String =
          s"""{
             |    "newPassword": "testABC"
             |}""".stripMargin

        val request1 = Put(
          baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(normalUser.id, "utf-8")}/Password",
          HttpEntity(ContentTypes.`application/json`, changeUserPasswordRequest),
        ) ~> addCredentials(BasicHttpCredentials(normalUser.email, "test654321")) // requester's password
        val response1: HttpResponse = singleAwaitingRequest(request1)

        response1.status should be(StatusCodes.BadRequest)

        // check that the password was not changed, i.e. the old one is still accepted
        val request2 = Get(baseApiUrl + s"/v2/authentication") ~> addCredentials(
          BasicHttpCredentials(normalUser.email, "test654321"),
        ) // old password
        val response2: HttpResponse = singleAwaitingRequest(request2)
        response2.status should be(StatusCodes.OK)
      }

      "change user's status" in {
        val changeUserStatusRequest: String =
          s"""{
             |    "status": false
             |}""".stripMargin

        val donaldIriEncoded = java.net.URLEncoder.encode(donaldIri.get, "utf-8")
        val request = Put(
          baseApiUrl + s"/admin/users/iri/$donaldIriEncoded/Status",
          HttpEntity(ContentTypes.`application/json`, changeUserStatusRequest),
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val result: User = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[User]
        result.status should be(false)
      }

      "update the user's system admin membership status" in {
        val changeUserSystemAdminMembershipRequest: String =
          s"""{
             |    "systemAdmin": true
             |}""".stripMargin

        val donaldIriEncoded = java.net.URLEncoder.encode(donaldIri.get, "utf-8")
        val request = Put(
          baseApiUrl + s"/admin/users/iri/$donaldIriEncoded/SystemAdmin",
          HttpEntity(ContentTypes.`application/json`, changeUserSystemAdminMembershipRequest),
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val result: User = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[User]
        result.permissions.groupsPerProject
          .get("http://www.knora.org/ontology/knora-admin#SystemProject")
          .head should equal(List("http://www.knora.org/ontology/knora-admin#SystemAdmin"))

        // Throw BadRequest exception if user is built-in user
        val badRequest = Put(
          baseApiUrl + s"/admin/users/iri/$systemUserIriEncoded/SystemAdmin",
          HttpEntity(ContentTypes.`application/json`, changeUserSystemAdminMembershipRequest),
        ) ~> addRootUserCredentials()
        val badResponse: HttpResponse = singleAwaitingRequest(badRequest)
        badResponse.status should be(StatusCodes.BadRequest)
      }

      "not allow updating the system user's system admin membership status" in {
        val changeUserSystemAdminMembershipRequest: String =
          s"""{
             |    "systemAdmin": true
             |}""".stripMargin

        val request = Put(
          baseApiUrl + s"/admin/users/iri/$systemUserIriEncoded/SystemAdmin",
          HttpEntity(ContentTypes.`application/json`, changeUserSystemAdminMembershipRequest),
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)
      }

      "not allow changing the system user's status" in {
        val params =
          s"""
                    {
                        "status": false
                    }
                    """.stripMargin

        val request = Put(
          baseApiUrl + s"/admin/users/iri/$systemUserIriEncoded/Status",
          HttpEntity(ContentTypes.`application/json`, params),
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)
      }

      "not allow changing the anonymous user's status" in {
        val anonymousUserIriEncoded = java.net.URLEncoder.encode(KnoraSystemInstances.Users.AnonymousUser.id, "utf-8")
        val params =
          s"""
                    {
                        "status": false
                    }
                    """.stripMargin

        val request = Put(
          baseApiUrl + s"/admin/users/iri/$anonymousUserIriEncoded/Status",
          HttpEntity(ContentTypes.`application/json`, params),
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)
      }

      "delete a user" in {
        val userIriEncoded         = java.net.URLEncoder.encode(customUserIri, "utf-8")
        val request                = Delete(baseApiUrl + s"/admin/users/iri/$userIriEncoded") ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

      }

      "not allow deleting the system user" in {
        val request                = Delete(baseApiUrl + s"/admin/users/iri/$systemUserIriEncoded") ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)
      }

      "not allow deleting the anonymous user" in {
        val anonymousUserIriEncoded = java.net.URLEncoder.encode(KnoraSystemInstances.Users.AnonymousUser.id, "utf-8")

        val request                = Delete(baseApiUrl + s"/admin/users/iri/$anonymousUserIriEncoded") ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)
      }

    }

    "used to query project memberships" should {
      "return all projects the user is a member of" in {
        val request =
          Get(baseApiUrl + s"/admin/users/iri/$multiUserIriEnc/project-memberships") ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK)

        val projects: Seq[Project] =
          AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[List[Project]]
        projects should contain allElementsOf Seq(
          SharedTestDataADM.imagesProjectExternal,
          SharedTestDataADM.incunabulaProjectExternal,
          SharedTestDataADM.anythingProjectExternal,
        )

        // testing getUserProjectMemberships method, which should return the same result
        projects should contain allElementsOf getUserProjectMemberships(multiUserIri)
      }
    }

    "used to modify project membership" should {

      "NOT add a user to project if the requesting user is not a SystemAdmin or ProjectAdmin" in {
        val request = Post(
          baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(normalUser.id, "utf-8")}/project-memberships/$imagesProjectIriEnc",
        ) ~> addCredentials(BasicHttpCredentials(normalUser.email, "test654321"))
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.Forbidden)
      }

      "add user to project" in {
        val membershipsBeforeUpdate = getUserProjectMemberships(normalUser.id)
        membershipsBeforeUpdate should equal(Seq())

        val request = Post(
          baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(normalUser.id, "utf-8")}/project-memberships/$imagesProjectIriEnc",
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK)

        val membershipsAfterUpdate = getUserProjectMemberships(normalUserIri)
        membershipsAfterUpdate should equal(Seq(SharedTestDataADM.imagesProjectExternal))
      }

      "don't add user to project if user is already a member" in {
        val membershipsBeforeTryUpdate = getUserProjectMemberships(normalUser.id)

        val request = Post(
          baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(normalUser.id, "utf-8")}/project-memberships/$imagesProjectIriEnc",
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.BadRequest)

        // verify that users's project memberships weren't changed
        val membershipsAfterTryUpdate = getUserProjectMemberships(normalUserIri)
        membershipsAfterTryUpdate should equal(membershipsBeforeTryUpdate)
      }

      "remove user from project" in {
        val membershipsBeforeUpdate = getUserProjectMemberships(normalUser.id)
        membershipsBeforeUpdate should equal(Seq(SharedTestDataADM.imagesProjectExternal))

        val request = Delete(
          baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(normalUser.id, "utf-8")}/project-memberships/$imagesProjectIriEnc",
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK)

        val membershipsAfterUpdate = getUserProjectMemberships(normalUserIri)
        membershipsAfterUpdate should equal(Seq.empty[Project])
      }
    }

    "used to query project admin group memberships" should {
      "return all projects the user is a member of the project admin group" in {
        val request = Get(
          baseApiUrl + s"/admin/users/iri/$multiUserIriEnc/project-admin-memberships",
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)

        val projects: Seq[Project] =
          AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[Project]]
        projects should contain allElementsOf Seq(
          SharedTestDataADM.imagesProjectExternal,
          SharedTestDataADM.incunabulaProjectExternal,
          SharedTestDataADM.anythingProjectExternal,
        )

        // explicitly testing 'getUserProjectsAdminMemberships' method, which should return the same result
        projects should contain allElementsOf getUserProjectAdminMemberships(multiUserIri)
      }
    }

    "used to modify project admin group membership" should {
      "add user to project admin group only if he is already member of that project" in {
        // add user as project admin to images project - returns a BadRequest because user is not member of the project
        val requestWithoutBeingMember = Post(
          baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(normalUser.id, "utf-8")}/project-admin-memberships/$imagesProjectIriEnc",
        ) ~> addRootUserCredentials()
        val responseWithoutBeingMember: HttpResponse = singleAwaitingRequest(requestWithoutBeingMember)

        assert(responseWithoutBeingMember.status === StatusCodes.BadRequest)

        // add user as member to images project
        val requestAddUserToProject = Post(
          baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(normalUser.id, "utf-8")}/project-memberships/$imagesProjectIriEnc",
        ) ~> addRootUserCredentials()
        val responseAddUserToProject: HttpResponse = singleAwaitingRequest(requestAddUserToProject)

        assert(responseAddUserToProject.status === StatusCodes.OK)

        // verify that user is not yet project admin in images project
        val membershipsBeforeUpdate = getUserProjectAdminMemberships(normalUser.id)
        membershipsBeforeUpdate should equal(Seq())

        // add user as project admin to images project
        val request = Post(
          baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(normalUser.id, "utf-8")}/project-admin-memberships/$imagesProjectIriEnc",
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK)

        // verify that user has been added as project admin to images project
        val membershipsAfterUpdate = getUserProjectAdminMemberships(normalUser.id)
        membershipsAfterUpdate should equal(Seq(SharedTestDataADM.imagesProjectExternal))
      }

      "remove user from project admin group" in {
        val membershipsBeforeUpdate = getUserProjectAdminMemberships(normalUser.id)

        membershipsBeforeUpdate should equal(Seq(SharedTestDataADM.imagesProjectExternal))

        val request = Delete(
          baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(normalUser.id, "utf-8")}/project-admin-memberships/$imagesProjectIriEnc",
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK)

        val membershipsAfterUpdate = getUserProjectAdminMemberships(normalUser.id)

        membershipsAfterUpdate should equal(Seq.empty[Project])
      }

      "remove user from project which also removes him from project admin group" in {
        // add user as project admin to images project
        val requestAddUserAsProjectAdmin = Post(
          baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(normalUser.id, "utf-8")}/project-admin-memberships/$imagesProjectIriEnc",
        ) ~> addRootUserCredentials()
        val responseAddUserAsProjectAdmin: HttpResponse = singleAwaitingRequest(requestAddUserAsProjectAdmin)

        assert(responseAddUserAsProjectAdmin.status === StatusCodes.OK)

        // verify that user has been added as project admin to images project
        val membershipsBeforeUpdate = getUserProjectAdminMemberships(normalUser.id)
        membershipsBeforeUpdate should equal(Seq(SharedTestDataADM.imagesProjectExternal))

        // remove user as project member from images project
        val request = Delete(
          baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(normalUser.id, "utf-8")}/project-memberships/$imagesProjectIriEnc",
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK)

        // verify that user has also been removed as project admin from images project
        val projectAdminMembershipsAfterUpdate = getUserProjectAdminMemberships(normalUser.id)

        projectAdminMembershipsAfterUpdate should equal(Seq())
      }

    }

    "used to query group memberships" should {
      "return all groups the user is a member of" in {
        val request =
          Get(baseApiUrl + s"/admin/users/iri/$multiUserIriEnc/group-memberships") ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK)

        val groups: Seq[Group] =
          AkkaHttpUtils.httpResponseToJson(response).fields("groups").convertTo[List[Group]]
        groups should contain allElementsOf Seq(SharedTestDataADM.imagesReviewerGroupExternal)

        // testing getUserGroupMemberships method, which should return the same result
        groups should contain allElementsOf getUserGroupMemberships(multiUserIri)
      }
    }

    "used to modify group membership" should {
      "add user to group" in {
        val membershipsBeforeUpdate = getUserGroupMemberships(normalUser.id)
        membershipsBeforeUpdate should equal(Seq.empty[Group])

        val request = Post(
          baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(normalUser.id, "utf-8")}/group-memberships/$imagesReviewerGroupIriEnc",
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK)

        val membershipsAfterUpdate = getUserGroupMemberships(normalUserIri)
        membershipsAfterUpdate should equal(Seq(SharedTestDataADM.imagesReviewerGroupExternal))
      }

      "remove user from group" in {
        val membershipsBeforeUpdate = getUserGroupMemberships(normalUser.id)
        membershipsBeforeUpdate should equal(Seq(SharedTestDataADM.imagesReviewerGroupExternal))

        val request = Delete(
          baseApiUrl + s"/admin/users/iri/${URLEncoder.encode(normalUser.id, "utf-8")}/group-memberships/$imagesReviewerGroupIriEnc",
        ) ~> addRootUserCredentials()
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK)

        val membershipsAfterUpdate = getUserProjectMemberships(normalUserIri)
        membershipsAfterUpdate should equal(Seq.empty[Group])
      }
    }
  }
}
