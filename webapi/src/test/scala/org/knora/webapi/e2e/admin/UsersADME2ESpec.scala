/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import org.knora.webapi.e2e.ClientTestDataCollector
import org.knora.webapi.e2e.TestDataFileContent
import org.knora.webapi.e2e.TestDataFilePath
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsADMJsonProtocol
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsADMJsonProtocol
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.v1.routing.authenticationmessages.CredentialsV1
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataV1
import org.knora.webapi.util.AkkaHttpUtils
import org.knora.webapi.util.MutableTestIri

import scala.concurrent.Await
import scala.concurrent.duration._

object UsersADME2ESpec {
  val config: Config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * End-to-End (E2E) test specification for testing users endpoint.
 */
class UsersADME2ESpec
    extends E2ESpec(UsersADME2ESpec.config)
    with ProjectsADMJsonProtocol
    with GroupsADMJsonProtocol
    with SessionJsonProtocol
    with TriplestoreJsonProtocol {

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(30.seconds)

  val rootCreds: CredentialsV1 = CredentialsV1(
    SharedTestDataADM.rootUser.id,
    SharedTestDataADM.rootUser.email,
    "test"
  )

  val projectAdminCreds: CredentialsV1 = CredentialsV1(
    SharedTestDataADM.imagesUser01.id,
    SharedTestDataADM.imagesUser01.email,
    "test"
  )

  val normalUserCreds: CredentialsV1 = CredentialsV1(
    SharedTestDataADM.normalUser.id,
    SharedTestDataADM.normalUser.email,
    "test"
  )

  private val normalUserIri    = SharedTestDataV1.normalUser.userData.user_id.get
  private val normalUserIriEnc = java.net.URLEncoder.encode(normalUserIri, "utf-8")

  private val multiUserIri    = SharedTestDataV1.multiuserUser.userData.user_id.get
  private val multiUserIriEnc = java.net.URLEncoder.encode(multiUserIri, "utf-8")

  private val imagesProjectIri    = SharedTestDataADM.imagesProject.id
  private val imagesProjectIriEnc = java.net.URLEncoder.encode(imagesProjectIri, "utf-8")

  private val imagesReviewerGroupIri    = SharedTestDataADM.imagesReviewerGroup.id
  private val imagesReviewerGroupIriEnc = java.net.URLEncoder.encode(imagesReviewerGroupIri, "utf-8")

  private val customUserIri      = "http://rdfh.ch/users/14pxW-LAQIaGcCRiNCPJcQ"
  private val otherCustomUserIri = "http://rdfh.ch/users/v8_12VcJRlGNFCjYzqJ5cA"

  private val donaldIri            = new MutableTestIri
  private val systemUserIriEncoded = java.net.URLEncoder.encode(KnoraSystemInstances.Users.SystemUser.id, "utf-8")

  // Directory path for generated client test data
  private val clientTestDataPath: Seq[String] = Seq("admin", "users")

  // Collects client test data
  private val clientTestDataCollector = new ClientTestDataCollector(settings)

  /**
   * Convenience method returning the users project memberships.
   *
   * @param userIri     the user's IRI.
   * @param credentials the credentials of the user making the request.
   */
  private def getUserProjectMemberships(userIri: IRI, credentials: CredentialsV1): Seq[ProjectADM] = {
    val userIriEnc = java.net.URLEncoder.encode(userIri, "utf-8")
    val request = Get(baseApiUrl + s"/admin/users/iri/$userIriEnc/project-memberships") ~> addCredentials(
      BasicHttpCredentials(credentials.email, credentials.password)
    )
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
    val request = Get(baseApiUrl + s"/admin/users/iri/$userIriEnc/project-admin-memberships") ~> addCredentials(
      BasicHttpCredentials(credentials.email, credentials.password)
    )
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
    val request = Get(baseApiUrl + s"/admin/users/iri/$userIriEnc/group-memberships") ~> addCredentials(
      BasicHttpCredentials(credentials.email, credentials.password)
    )
    val response: HttpResponse = singleAwaitingRequest(request)
    AkkaHttpUtils.httpResponseToJson(response).fields("groups").convertTo[Seq[GroupADM]]
  }

  "The Users Route ('admin/users')" when {
    "used to query user information [FUNCTIONALITY]" should {
      "return all users" in {
        val request =
          Get(baseApiUrl + s"/admin/users") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        logger.debug(s"response: ${response.toString}")
        response.status should be(StatusCodes.OK)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-users-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return a single user profile identified by iri" in {
        /* Correct username and password */
        val request = Get(baseApiUrl + s"/admin/users/iri/${rootCreds.urlEncodedIri}") ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-user-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return a single user profile identified by email" in {
        /* Correct username and password */
        val request = Get(baseApiUrl + s"/admin/users/email/${rootCreds.urlEncodedEmail}") ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)
      }

      "return a single user profile identified by username" in {
        /* Correct username and password */
        val request = Get(
          baseApiUrl + s"/admin/users/username/${SharedTestDataADM.rootUser.username}"
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)
      }

    }

    "used to query user information [PERMISSIONS]" should {
      "return single user for SystemAdmin" in {
        val request = Get(baseApiUrl + s"/admin/users/iri/$normalUserIriEnc") ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-user-for-SystemAdmin-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return single user for itself" in {
        val request = Get(baseApiUrl + s"/admin/users/iri/$normalUserIriEnc") ~> addCredentials(
          BasicHttpCredentials(normalUserCreds.email, normalUserCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-user-for-itself-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return only public information for single user for non SystemAdmin and self" in {
        val request = Get(baseApiUrl + s"/admin/users/iri/$normalUserIriEnc") ~> addCredentials(
          BasicHttpCredentials(projectAdminCreds.email, projectAdminCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val result: UserADM = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[UserADM]
        result.givenName should be(SharedTestDataADM.normalUser.givenName)
        result.familyName should be(SharedTestDataADM.normalUser.familyName)
        result.status should be(false)
        result.email should be("")
        result.username should be("")
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-user-for-nonSystemAdmin-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return only public information for single user with anonymous access" in {
        val request                = Get(baseApiUrl + s"/admin/users/iri/$normalUserIriEnc")
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val result: UserADM = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[UserADM]
        result.givenName should be(SharedTestDataADM.normalUser.givenName)
        result.familyName should be(SharedTestDataADM.normalUser.familyName)
        result.status should be(false)
        result.email should be("")
        result.username should be("")
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-user-for-anonymous-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return all users for SystemAdmin" in {
        val request =
          Get(baseApiUrl + s"/admin/users") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-users-for-SystemAdmin-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return all users for ProjectAdmin" in {
        val request = Get(baseApiUrl + s"/admin/users") ~> addCredentials(
          BasicHttpCredentials(projectAdminCreds.email, projectAdminCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-users-for-ProjectAdmin-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return 'Forbidden' for all users for normal user" in {
        val request = Get(baseApiUrl + s"/admin/users") ~> addCredentials(
          BasicHttpCredentials(normalUserCreds.email, normalUserCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.Forbidden)
      }

    }

    "given a custom Iri" should {
      "create a user with the provided custom IRI " in {
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

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-user-with-custom-Iri-request",
              fileExtension = "json"
            ),
            text = createUserWithCustomIriRequest
          )
        )
        val request = Post(
          baseApiUrl + s"/admin/users",
          HttpEntity(ContentTypes.`application/json`, createUserWithCustomIriRequest)
        )
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val result: UserADM = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[UserADM]

        // check that the custom IRI is correctly assigned
        result.id should be(customUserIri)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-user-with-custom-Iri-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
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

        val request                = Post(baseApiUrl + s"/admin/users", HttpEntity(ContentTypes.`application/json`, params))
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
          HttpEntity(ContentTypes.`application/json`, createUserWithApostropheRequest)
        )
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val result: UserADM = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[UserADM]

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
          HttpEntity(ContentTypes.`application/json`, updateUserRequest)
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val result: UserADM = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[UserADM]
        result.givenName should be("Updated\tGivenName")
        result.familyName should be("Updated\"FamilyName")
      }

      "return the special characters correctly when getting a user with special characters in givenName and familyName" in {
        val userIriEncoded = java.net.URLEncoder.encode(otherCustomUserIri, "utf-8")

        val request = Get(baseApiUrl + s"/admin/users/iri/$userIriEncoded") ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val result: UserADM = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[UserADM]
        result.givenName should be("Updated\tGivenName")
        result.familyName should be("Updated\"FamilyName")
      }

    }

    "used to create a user" should {
      "create the user if the supplied email and username are unique " in {
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

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-user-request",
              fileExtension = "json"
            ),
            text = createUserRequest
          )
        )
        val request                = Post(baseApiUrl + s"/admin/users", HttpEntity(ContentTypes.`application/json`, createUserRequest))
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val result: UserADM = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[UserADM]
        result.username should be("donald.duck")
        result.email should be("donald.duck@example.org")
        result.givenName should be("Donald")
        result.familyName should be("Duck")
        result.status should be(true)
        result.lang should be("en")

        donaldIri.set(result.id)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-user-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return a 'BadRequest' if the supplied username is not unique " in {
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

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-user-request-duplicate-username",
              fileExtension = "json"
            ),
            text = createUserRequest
          )
        )
        val request                = Post(baseApiUrl + s"/admin/users", HttpEntity(ContentTypes.`application/json`, createUserRequest))
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.BadRequest)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-user-response-duplicate-username",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return a 'BadRequest' if the supplied email is not unique " in {
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

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-user-request-duplicate-email",
              fileExtension = "json"
            ),
            text = createUserRequest
          )
        )
        val request                = Post(baseApiUrl + s"/admin/users", HttpEntity(ContentTypes.`application/json`, createUserRequest))
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.BadRequest)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-user-response-duplicate-email",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "authenticate the newly created user using HttpBasicAuth" in {
        val request = Get(baseApiUrl + s"/v2/authentication") ~> addCredentials(
          BasicHttpCredentials("donald.duck@example.org", "test")
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

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-user-request",
              fileExtension = "json"
            ),
            text = updateUserRequest
          )
        )

        val userIriEncoded = java.net.URLEncoder.encode(donaldIri.get, "utf-8")
        val request = Put(
          baseApiUrl + s"/admin/users/iri/$userIriEncoded/BasicUserInformation",
          HttpEntity(ContentTypes.`application/json`, updateUserRequest)
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val result: UserADM = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[UserADM]
        result.username should be("donald.big.duck")
        result.email should be("donald.big.duck@example.org")
        result.givenName should be("Big Donald")
        result.familyName should be("Duckmann")
        result.lang should be("de")

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-user-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return 'BadRequest' if user IRI is None and 'NotFound' if user IRI is '' in update user request" in {
        val updateUserRequest: String =
          s"""{
             |    "username": "donald.without.iri.duck"
             |}""".stripMargin

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-user-request-without-iri",
              fileExtension = "json"
            ),
            text = updateUserRequest
          )
        )

        val missingUserIri = ""
        val request = Put(
          baseApiUrl + s"/admin/users/iri/$missingUserIri/BasicUserInformation",
          HttpEntity(ContentTypes.`application/json`, updateUserRequest)
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.NotFound)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-user-response-without-iri-1",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )

        val missingUserIriNone = None
        val request2 = Put(
          baseApiUrl + s"/admin/users/iri/$missingUserIriNone/BasicUserInformation",
          HttpEntity(ContentTypes.`application/json`, updateUserRequest)
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response2: HttpResponse = singleAwaitingRequest(request2)

        response2.status should be(StatusCodes.BadRequest)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-user-response-without-iri-2",
              fileExtension = "json"
            ),
            text = responseToString(response2)
          )
        )

      }

      "update the user's password (by himself)" in {
        val changeUserPasswordRequest: String =
          s"""{
             |    "requesterPassword": "test",
             |    "newPassword": "test123456"
             |}""".stripMargin

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-user-password-request",
              fileExtension = "json"
            ),
            text = changeUserPasswordRequest
          )
        )

        val request1 = Put(
          baseApiUrl + s"/admin/users/iri/${normalUserCreds.urlEncodedIri}/Password",
          HttpEntity(ContentTypes.`application/json`, changeUserPasswordRequest)
        ) ~> addCredentials(BasicHttpCredentials(normalUserCreds.email, "test")) // requester's password
        val response1: HttpResponse = singleAwaitingRequest(request1)
        logger.debug(s"response: ${response1.toString}")
        response1.status should be(StatusCodes.OK)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-user-password-response",
              fileExtension = "json"
            ),
            text = responseToString(response1)
          )
        )

        // check if the password was changed, i.e. if the new one is accepted
        val request2 = Get(baseApiUrl + s"/v2/authentication") ~> addCredentials(
          BasicHttpCredentials(normalUserCreds.email, "test123456")
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
          baseApiUrl + s"/admin/users/iri/${normalUserCreds.urlEncodedIri}/Password",
          HttpEntity(ContentTypes.`application/json`, params01)
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, "test")) // requester's password
        val response1: HttpResponse = singleAwaitingRequest(request1)
        logger.debug(s"response: ${response1.toString}")
        response1.status should be(StatusCodes.OK)

        // check if the password was changed, i.e. if the new one is accepted
        val request2 = Get(baseApiUrl + s"/v2/authentication") ~> addCredentials(
          BasicHttpCredentials(normalUserCreds.email, "test654321")
        ) // new password
        val response2: HttpResponse = singleAwaitingRequest(request2)
        response2.status should be(StatusCodes.OK)
      }

      "return 'BadRequest' if new password in change password request is missing" in {
        val changeUserPasswordRequest: String =
          s"""{
             |    "requesterPassword": "test"
             |}""".stripMargin

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "incomplete-update-user-password-request",
              fileExtension = "json"
            ),
            text = changeUserPasswordRequest
          )
        )

        val request1 = Put(
          baseApiUrl + s"/admin/users/iri/${normalUserCreds.urlEncodedIri}/Password",
          HttpEntity(ContentTypes.`application/json`, changeUserPasswordRequest)
        ) ~> addCredentials(BasicHttpCredentials(normalUserCreds.email, "test")) // requester's password
        val response1: HttpResponse = singleAwaitingRequest(request1)

        response1.status should be(StatusCodes.BadRequest)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "incomplete-update-user-password-response",
              fileExtension = "json"
            ),
            text = responseToString(response1)
          )
        )

        // check that the password was not changed, i.e. the old one is still accepted
        val request2 = Get(baseApiUrl + s"/v2/authentication") ~> addCredentials(
          BasicHttpCredentials(normalUserCreds.email, "test654321")
        ) // old password (taken from previous test)
        val response2: HttpResponse = singleAwaitingRequest(request2)
        response2.status should be(StatusCodes.OK)
      }

      "return 'BadRequest' if requester's password in change password request is missing" in {
        val changeUserPasswordRequest: String =
          s"""{
             |    "newPassword": "testABC"
             |}""".stripMargin

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "incomplete-update-user-password-request-2",
              fileExtension = "json"
            ),
            text = changeUserPasswordRequest
          )
        )

        val request1 = Put(
          baseApiUrl + s"/admin/users/iri/${normalUserCreds.urlEncodedIri}/Password",
          HttpEntity(ContentTypes.`application/json`, changeUserPasswordRequest)
        ) ~> addCredentials(BasicHttpCredentials(normalUserCreds.email, "test")) // requester's password
        val response1: HttpResponse = singleAwaitingRequest(request1)

        response1.status should be(StatusCodes.BadRequest)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "incomplete-update-user-password-response-2",
              fileExtension = "json"
            ),
            text = responseToString(response1)
          )
        )

        // check that the password was not changed, i.e. the old one is still accepted
        val request2 = Get(baseApiUrl + s"/v2/authentication") ~> addCredentials(
          BasicHttpCredentials(normalUserCreds.email, "test654321")
        ) // old password
        val response2: HttpResponse = singleAwaitingRequest(request2)
        response2.status should be(StatusCodes.OK)
      }

      "change user's status" in {
        val changeUserStatusRequest: String =
          s"""{
             |    "status": false
             |}""".stripMargin

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-user-status-request",
              fileExtension = "json"
            ),
            text = changeUserStatusRequest
          )
        )
        val donaldIriEncoded = java.net.URLEncoder.encode(donaldIri.get, "utf-8")
        val request = Put(
          baseApiUrl + s"/admin/users/iri/$donaldIriEncoded/Status",
          HttpEntity(ContentTypes.`application/json`, changeUserStatusRequest)
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val result: UserADM = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[UserADM]
        result.status should be(false)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-user-status-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return 'BadRequest' if more than 1 parameter is provided in update status request" in {
        val updateUserRequest: String =
          s"""{
             |    "status": false,
             |    "username": "parameterDuck"
             |}""".stripMargin

        val donaldIriEncoded = java.net.URLEncoder.encode(donaldIri.get, "utf-8")
        val request = Put(
          baseApiUrl + s"/admin/users/iri/$donaldIriEncoded/Status",
          HttpEntity(ContentTypes.`application/json`, updateUserRequest)
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)

      }

      "update the user's system admin membership status" in {
        val changeUserSystemAdminMembershipRequest: String =
          s"""{
             |    "systemAdmin": true
             |}""".stripMargin

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-user-system-admin-membership-request",
              fileExtension = "json"
            ),
            text = changeUserSystemAdminMembershipRequest
          )
        )
        val donaldIriEncoded = java.net.URLEncoder.encode(donaldIri.get, "utf-8")
        val request = Put(
          baseApiUrl + s"/admin/users/iri/$donaldIriEncoded/SystemAdmin",
          HttpEntity(ContentTypes.`application/json`, changeUserSystemAdminMembershipRequest)
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val result: UserADM = AkkaHttpUtils.httpResponseToJson(response).fields("user").convertTo[UserADM]
        result.permissions.groupsPerProject
          .get("http://www.knora.org/ontology/knora-admin#SystemProject")
          .head should equal(List("http://www.knora.org/ontology/knora-admin#SystemAdmin"))

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-user-system-admin-membership-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )

        // Throw BadRequest exception if user is built-in user
        val badRequest = Put(
          baseApiUrl + s"/admin/users/iri/$systemUserIriEncoded/SystemAdmin",
          HttpEntity(ContentTypes.`application/json`, changeUserSystemAdminMembershipRequest)
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
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
          HttpEntity(ContentTypes.`application/json`, changeUserSystemAdminMembershipRequest)
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
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
          HttpEntity(ContentTypes.`application/json`, params)
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
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
          HttpEntity(ContentTypes.`application/json`, params)
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)
      }

      "delete a user" in {
        val userIriEncoded = java.net.URLEncoder.encode(customUserIri, "utf-8")
        val request = Delete(baseApiUrl + s"/admin/users/iri/$userIriEncoded") ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "delete-user-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "not allow deleting the system user" in {
        val request = Delete(baseApiUrl + s"/admin/users/iri/$systemUserIriEncoded") ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)
      }

      "not allow deleting the anonymous user" in {
        val anonymousUserIriEncoded = java.net.URLEncoder.encode(KnoraSystemInstances.Users.AnonymousUser.id, "utf-8")

        val request = Delete(baseApiUrl + s"/admin/users/iri/$anonymousUserIriEncoded") ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)
      }

    }

    "used to query project memberships" should {
      "return all projects the user is a member of" in {
        val request = Get(baseApiUrl + s"/admin/users/iri/$multiUserIriEnc/project-memberships") ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK)

        val projects: Seq[ProjectADM] =
          AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[List[ProjectADM]]
        projects should contain allElementsOf Seq(
          SharedTestDataADM.imagesProject,
          SharedTestDataADM.incunabulaProject,
          SharedTestDataADM.anythingProject
        )

        // testing getUserProjectMemberships method, which should return the same result
        projects should contain allElementsOf getUserProjectMemberships(multiUserIri, rootCreds)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-user-project-memberships-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }
    }

    "used to modify project membership" should {
      "add user to project" in {
        val membershipsBeforeUpdate = getUserProjectMemberships(normalUserCreds.userIri, rootCreds)
        membershipsBeforeUpdate should equal(Seq())

        val request = Post(
          baseApiUrl + s"/admin/users/iri/${normalUserCreds.urlEncodedIri}/project-memberships/$imagesProjectIriEnc"
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK)

        val membershipsAfterUpdate = getUserProjectMemberships(normalUserIri, rootCreds)
        membershipsAfterUpdate should equal(Seq(SharedTestDataADM.imagesProject))

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "add-user-to-project-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "don't add user to project if user is already a member" in {
        val membershipsBeforeTryUpdate = getUserProjectMemberships(normalUserCreds.userIri, rootCreds)

        val request = Post(
          baseApiUrl + s"/admin/users/iri/${normalUserCreds.urlEncodedIri}/project-memberships/$imagesProjectIriEnc"
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.BadRequest)

        // verify that users's project memberships weren't changed
        val membershipsAfterTryUpdate = getUserProjectMemberships(normalUserIri, rootCreds)
        membershipsAfterTryUpdate should equal(membershipsBeforeTryUpdate)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "user-already-member-of-project-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "remove user from project" in {
        val membershipsBeforeUpdate = getUserProjectMemberships(normalUserCreds.userIri, rootCreds)
        membershipsBeforeUpdate should equal(Seq(SharedTestDataADM.imagesProject))

        val request = Delete(
          baseApiUrl + s"/admin/users/iri/${normalUserCreds.urlEncodedIri}/project-memberships/$imagesProjectIriEnc"
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK)

        val membershipsAfterUpdate = getUserProjectMemberships(normalUserIri, rootCreds)
        membershipsAfterUpdate should equal(Seq.empty[ProjectADM])

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "remove-user-from-project-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

    }

    "used to query project admin group memberships" should {
      "return all projects the user is a member of the project admin group" in {
        val request = Get(
          baseApiUrl + s"/admin/users/iri/$multiUserIriEnc/project-admin-memberships"
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)

        val projects: Seq[ProjectADM] =
          AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[ProjectADM]]
        projects should contain allElementsOf Seq(
          SharedTestDataADM.imagesProject,
          SharedTestDataADM.incunabulaProject,
          SharedTestDataADM.anythingProject
        )

        // explicitly testing 'getUserProjectsAdminMemberships' method, which should return the same result
        projects should contain allElementsOf getUserProjectAdminMemberships(multiUserIri, rootCreds)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-user-project-admin-group-memberships-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

    }

    "used to modify project admin group membership" should {
      "add user to project admin group" in {
        val membershipsBeforeUpdate = getUserProjectAdminMemberships(normalUserCreds.userIri, rootCreds)
        membershipsBeforeUpdate should equal(Seq())

        val request = Post(
          baseApiUrl + s"/admin/users/iri/${normalUserCreds.urlEncodedIri}/project-admin-memberships/$imagesProjectIriEnc"
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK)

        val membershipsAfterUpdate = getUserProjectAdminMemberships(normalUserCreds.userIri, rootCreds)

        membershipsAfterUpdate should equal(Seq(SharedTestDataADM.imagesProject))

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "add-user-to-project-admin-group-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "remove user from project admin group" in {
        val membershipsBeforeUpdate = getUserProjectAdminMemberships(normalUserCreds.userIri, rootCreds)

        membershipsBeforeUpdate should equal(Seq(SharedTestDataADM.imagesProject))

        val request = Delete(
          baseApiUrl + s"/admin/users/iri/${normalUserCreds.urlEncodedIri}/project-admin-memberships/$imagesProjectIriEnc"
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK)

        val membershipsAfterUpdate = getUserProjectAdminMemberships(normalUserCreds.userIri, rootCreds)

        membershipsAfterUpdate should equal(Seq.empty[ProjectADM])

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "remove-user-from-project-admin-group-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

    }

    "used to query group memberships" should {
      "return all groups the user is a member of" in {
        val request = Get(baseApiUrl + s"/admin/users/iri/$multiUserIriEnc/group-memberships") ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK)

        val groups: Seq[GroupADM] =
          AkkaHttpUtils.httpResponseToJson(response).fields("groups").convertTo[List[GroupADM]]
        groups should contain allElementsOf Seq(SharedTestDataADM.imagesReviewerGroup)

        // testing getUserGroupMemberships method, which should return the same result
        groups should contain allElementsOf getUserGroupMemberships(multiUserIri, rootCreds)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-user-group-memberships-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

    }

    "used to modify group membership" should {
      "add user to group" in {
        val membershipsBeforeUpdate = getUserGroupMemberships(normalUserCreds.userIri, rootCreds)
        membershipsBeforeUpdate should equal(Seq.empty[GroupADM])

        val request = Post(
          baseApiUrl + s"/admin/users/iri/${normalUserCreds.urlEncodedIri}/group-memberships/$imagesReviewerGroupIriEnc"
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK)

        val membershipsAfterUpdate = getUserGroupMemberships(normalUserIri, rootCreds)
        membershipsAfterUpdate should equal(Seq(SharedTestDataADM.imagesReviewerGroup))

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "add-user-to-group-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "remove user from group" in {
        val membershipsBeforeUpdate = getUserGroupMemberships(normalUserCreds.userIri, rootCreds)
        membershipsBeforeUpdate should equal(Seq(SharedTestDataADM.imagesReviewerGroup))

        val request = Delete(
          baseApiUrl + s"/admin/users/iri/${normalUserCreds.urlEncodedIri}/group-memberships/$imagesReviewerGroupIriEnc"
        ) ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK)

        val membershipsAfterUpdate = getUserProjectMemberships(normalUserIri, rootCreds)
        membershipsAfterUpdate should equal(Seq.empty[GroupADM])

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "remove-user-from-group-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }
    }
  }
}
