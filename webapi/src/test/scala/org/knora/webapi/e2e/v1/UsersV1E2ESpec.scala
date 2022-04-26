/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v1

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.v1.routing.authenticationmessages.CredentialsV1
import org.knora.webapi.sharedtestdata.SharedTestDataV1
import org.knora.webapi.util.AkkaHttpUtils
import org.knora.webapi.{E2ESpec, IRI}

import scala.concurrent.duration._

object UsersV1E2ESpec {
  val config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * End-to-End (E2E) test specification for testing users endpoint.
 */
class UsersV1E2ESpec extends E2ESpec(UsersV1E2ESpec.config) with SessionJsonProtocol with TriplestoreJsonProtocol {

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(30.seconds)

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

  private val inactiveUserEmailEnc =
    java.net.URLEncoder.encode(SharedTestDataV1.inactiveUser.userData.email.get, "utf-8")

  private val normalUserIri    = SharedTestDataV1.normalUser.userData.user_id.get
  private val normalUserIriEnc = java.net.URLEncoder.encode(normalUserIri, "utf-8")

  private val multiUserIri    = SharedTestDataV1.multiuserUser.userData.user_id.get
  private val multiUserIriEnc = java.net.URLEncoder.encode(multiUserIri, "utf-8")

  private val wrongEmail    = "wrong@example.com"
  private val wrongEmailEnc = java.net.URLEncoder.encode(wrongEmail, "utf-8")

  private val testPass  = java.net.URLEncoder.encode("test", "utf-8")
  private val wrongPass = java.net.URLEncoder.encode("wrong", "utf-8")

  private val imagesProjectIri    = SharedTestDataV1.imagesProjectInfo.id
  private val imagesProjectIriEnc = java.net.URLEncoder.encode(imagesProjectIri, "utf-8")

  /**
   * Convenience method returning the users project memberships.
   *
   * @param userIri     the user's IRI.
   * @param credentials the credentials of the user making the request.
   */
  private def getUserProjectMemberships(userIri: IRI, credentials: CredentialsV1): Seq[IRI] = {
    val userIriEnc = java.net.URLEncoder.encode(userIri, "utf-8")
    val request = Get(baseApiUrl + "/v1/users/projects/" + userIriEnc) ~> addCredentials(
      BasicHttpCredentials(credentials.email, credentials.password)
    )
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
    val request = Get(baseApiUrl + "/v1/users/projects-admin/" + userIriEnc) ~> addCredentials(
      BasicHttpCredentials(credentials.email, credentials.password)
    )
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
    val request = Get(baseApiUrl + "/v1/users/groups/" + userIriEnc) ~> addCredentials(
      BasicHttpCredentials(credentials.email, credentials.password)
    )
    val response: HttpResponse = singleAwaitingRequest(request)
    AkkaHttpUtils.httpResponseToJson(response).fields("groups").convertTo[Seq[IRI]]
  }

  "The Users Route ('v1/users')" when {

    "used to query user information" should {

      "return all users" in {
        val request =
          Get(baseApiUrl + s"/v1/users") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        // logger.debug(s"response: ${response.toString}")
        response.status should be(StatusCodes.OK)
      }

      "return a single user profile identified by iri" in {
        /* Correct username and password */
        val request = Get(baseApiUrl + s"/v1/users/${rootCreds.urlEncodedIri}") ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // logger.debug(s"response: ${response.toString}")
        response.status should be(StatusCodes.OK)
      }

      "return a single user profile identified by email" in {
        /* Correct username and password */
        val request = Get(baseApiUrl + s"/v1/users/${rootCreds.urlEncodedEmail}?identifier=email") ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // logger.debug(s"response: ${response.toString}")
        response.status should be(StatusCodes.OK)
      }

    }

    "used to query project memberships" should {

      "return all projects the user is a member of" in {
        val request = Get(baseApiUrl + s"/v1/users/projects/$multiUserIriEnc") ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        logger.debug(s"response: ${response.toString}")
        assert(response.status === StatusCodes.OK)

        val projects: Seq[IRI] = AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[List[IRI]]
        projects should contain allElementsOf Seq(
          SharedTestDataV1.IMAGES_PROJECT_IRI,
          SharedTestDataV1.INCUNABULA_PROJECT_IRI,
          SharedTestDataV1.ANYTHING_PROJECT_IRI
        )

        // testing getUserProjectMemberships method, which should return the same result
        projects should contain allElementsOf getUserProjectMemberships(multiUserIri, rootCreds)
      }
    }

    "used to query project admin group memberships" should {

      "return all projects the user is a member of the project admin group" in {
        val request = Get(baseApiUrl + s"/v1/users/projects-admin/$multiUserIriEnc") ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        logger.debug(s"response: ${response.toString}")
        assert(response.status === StatusCodes.OK)

        val projects: Seq[IRI] = AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[List[IRI]]
        projects should contain allElementsOf Seq(
          SharedTestDataV1.IMAGES_PROJECT_IRI,
          SharedTestDataV1.INCUNABULA_PROJECT_IRI,
          SharedTestDataV1.ANYTHING_PROJECT_IRI
        )

        // explicitly testing 'getUserProjectsAdminMemberships' method, which should return the same result
        projects should contain allElementsOf getUserProjectAdminMemberships(multiUserIri, rootCreds)
      }
    }

    "used to query group memberships" should {

      "return all groups the user is a member of" in {
        val request = Get(baseApiUrl + s"/v1/users/groups/$multiUserIriEnc") ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        //log.debug(s"response: ${response.toString}")
        assert(response.status === StatusCodes.OK)

        val groups: Seq[IRI] = AkkaHttpUtils.httpResponseToJson(response).fields("groups").convertTo[List[IRI]]
        groups should contain allElementsOf Seq("http://rdfh.ch/groups/00FF/images-reviewer")

        // testing getUserGroupMemberships method, which should return the same result
        groups should contain allElementsOf getUserGroupMemberships(multiUserIri, rootCreds)
      }
    }
  }
}
