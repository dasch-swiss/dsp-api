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

import scala.concurrent.Await
import scala.concurrent.duration._

import org.knora.webapi.E2ESpec
import org.knora.webapi.e2e.ClientTestDataCollector
import org.knora.webapi.e2e.TestDataFileContent
import org.knora.webapi.e2e.TestDataFilePath
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsADMJsonProtocol
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.AkkaHttpUtils
import org.knora.webapi.util.MutableTestIri

object GroupsADME2ESpec {
  val config: Config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * End-to-End (E2E) test specification for testing groups endpoint.
 */
class GroupsADME2ESpec extends E2ESpec(GroupsADME2ESpec.config) with GroupsADMJsonProtocol with SessionJsonProtocol {

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(30.seconds)
  // Directory path for generated client test data
  private val clientTestDataPath: Seq[String] = Seq("admin", "groups")

  // Collects client test data
  private val clientTestDataCollector = new ClientTestDataCollector(settings)

  private val imagesUser01Email = SharedTestDataADM.imagesUser01.email
  private val testPass          = SharedTestDataADM.testPass

  private val groupIri    = SharedTestDataADM.imagesReviewerGroup.id
  private val groupIriEnc = java.net.URLEncoder.encode(groupIri, "utf-8")

  "The Groups Route ('admin/groups')" when {
    "used to query for group information" should {
      "return all groups" in {
        val request =
          Get(baseApiUrl + s"/admin/groups") ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-groups-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return the group's information" in {
        val request = Get(baseApiUrl + s"/admin/groups/$groupIriEnc") ~> addCredentials(
          BasicHttpCredentials(imagesUser01Email, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-group-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }
    }

    "given a custom Iri" should {
      val customGroupIri = "http://rdfh.ch/groups/00FF/gNdJSNYrTDu2lGpPUs94nQ"
      "create a group with the provided custom IRI " in {
        val createGroupWithCustomIriRequest: String =
          s"""{   "id": "$customGroupIri",
             |    "name": "NewGroupWithCustomIri",
             |    "descriptions": [{"value": "A new group with a custom Iri", "language": "en"}],
             |    "project": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
             |    "status": true,
             |    "selfjoin": false
             |}""".stripMargin

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-group-with-custom-Iri-request",
              fileExtension = "json"
            ),
            text = createGroupWithCustomIriRequest
          )
        )

        val request = Post(
          baseApiUrl + s"/admin/groups",
          HttpEntity(ContentTypes.`application/json`, createGroupWithCustomIriRequest)
        ) ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val result: GroupADM = AkkaHttpUtils.httpResponseToJson(response).fields("group").convertTo[GroupADM]

        // check that the custom IRI is correctly assigned
        result.id should be(customGroupIri)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-group-with-custom-Iri-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return 'BadRequest' if the supplied IRI for the group is not unique" in {
        val params =
          s"""{             "id": "$customGroupIri",
             |    "name": "NewGroupWithDuplicateCustomIri",
             |    "descriptions": [{"value": "A new group with a duplicate custom Iri", "language": "en"}],
             |    "project": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
             |    "status": true,
             |    "selfjoin": false
             |}""".stripMargin

        val request =
          Post(baseApiUrl + s"/admin/groups", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
            BasicHttpCredentials(imagesUser01Email, testPass)
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)

        val errorMessage: String = Await.result(Unmarshal(response.entity).to[String], 1.second)
        val invalidIri: Boolean  = errorMessage.contains(s"IRI: '$customGroupIri' already exists, try another one.")
        invalidIri should be(true)
      }
    }

    "used to modify group information" should {
      val newGroupIri = new MutableTestIri

      "CREATE a new group" in {
        val createGroupRequest: String =
          s"""{
             |    "name": "NewGroup",
             |    "descriptions": [{"value": "NewGroupDescription", "language": "en"}],
             |    "project": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
             |    "status": true,
             |    "selfjoin": false
             |}""".stripMargin

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-group-request",
              fileExtension = "json"
            ),
            text = createGroupRequest
          )
        )

        val request = Post(
          baseApiUrl + "/admin/groups",
          HttpEntity(ContentTypes.`application/json`, createGroupRequest)
        ) ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        response.status should be(StatusCodes.OK)

        val groupInfo: GroupADM = AkkaHttpUtils.httpResponseToJson(response).fields("group").convertTo[GroupADM]

        groupInfo.name should be("NewGroup")
        groupInfo.descriptions should be(Seq(StringLiteralV2("NewGroupDescription", Some("en"))))
        groupInfo.project should be(SharedTestDataADM.imagesProject)
        groupInfo.status should be(true)
        groupInfo.selfjoin should be(false)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-group-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )

        val iri = groupInfo.id
        newGroupIri.set(iri)
        // log.debug("newGroupIri: {}", newGroupIri.get)
      }

      "UPDATE a group" in {
        val updateGroupRequest: String =
          s"""{
             |    "name": "UpdatedGroupName",
             |    "descriptions": [{"value": "UpdatedGroupDescription", "language": "en"}]
             |}""".stripMargin

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-group-request",
              fileExtension = "json"
            ),
            text = updateGroupRequest
          )
        )
        val groupIriEnc = java.net.URLEncoder.encode(newGroupIri.get, "utf-8")
        val request = Put(
          baseApiUrl + "/admin/groups/" + groupIriEnc,
          HttpEntity(ContentTypes.`application/json`, updateGroupRequest)
        ) ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        logger.debug(s"response: {}", response)
        response.status should be(StatusCodes.OK)

        val groupInfo: GroupADM = AkkaHttpUtils.httpResponseToJson(response).fields("group").convertTo[GroupADM]

        groupInfo.name should be("UpdatedGroupName")
        groupInfo.descriptions should be(Seq(StringLiteralV2("UpdatedGroupDescription", Some("en"))))
        groupInfo.project should be(SharedTestDataADM.imagesProject)
        groupInfo.status should be(true)
        groupInfo.selfjoin should be(false)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-group-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "DELETE a group" in {
        val groupIriEnc = java.net.URLEncoder.encode(newGroupIri.get, "utf-8")
        val request = Delete(baseApiUrl + "/admin/groups/" + groupIriEnc) ~> addCredentials(
          BasicHttpCredentials(imagesUser01Email, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        logger.debug(s"response: {}", response)
        response.status should be(StatusCodes.OK)

        val groupInfo: GroupADM = AkkaHttpUtils.httpResponseToJson(response).fields("group").convertTo[GroupADM]

        groupInfo.name should be("UpdatedGroupName")
        groupInfo.descriptions should be(Seq(StringLiteralV2("UpdatedGroupDescription", Some("en"))))
        groupInfo.project should be(SharedTestDataADM.imagesProject)
        groupInfo.status should be(false)
        groupInfo.selfjoin should be(false)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "delete-group-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )

      }

      "CHANGE status of a group" in {
        val changeGroupStatusRequest: String =
          s"""{
             |    "status": true
             |}""".stripMargin

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "change-group-status-request",
              fileExtension = "json"
            ),
            text = changeGroupStatusRequest
          )
        )

        val groupIriEnc = java.net.URLEncoder.encode(newGroupIri.get, "utf-8")
        val request = Put(
          baseApiUrl + "/admin/groups/" + groupIriEnc + "/status",
          HttpEntity(ContentTypes.`application/json`, changeGroupStatusRequest)
        ) ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        logger.debug(s"response: {}", response)
        response.status should be(StatusCodes.OK)

        val groupInfo: GroupADM = AkkaHttpUtils.httpResponseToJson(response).fields("group").convertTo[GroupADM]

        groupInfo.name should be("UpdatedGroupName")
        groupInfo.descriptions should be(Seq(StringLiteralV2("UpdatedGroupDescription", Some("en"))))
        groupInfo.project should be(SharedTestDataADM.imagesProject)
        groupInfo.status should be(true)
        groupInfo.selfjoin should be(false)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "change-group-status-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }
    }

    "used to query members" should {
      "return all members of a group" in {
        val request = Get(baseApiUrl + s"/admin/groups/$groupIriEnc/members") ~> addCredentials(
          BasicHttpCredentials(imagesUser01Email, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-group-members-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }
    }
  }
}
