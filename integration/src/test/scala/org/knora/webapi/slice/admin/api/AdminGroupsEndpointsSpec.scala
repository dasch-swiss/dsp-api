/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import org.apache.pekko
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

import scala.concurrent.Await
import scala.concurrent.duration.*

import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.admin.responder.IntegrationTestAdminJsonProtocol.*
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.util.AkkaHttpUtils
import org.knora.webapi.util.MutableTestIri

import pekko.http.scaladsl.model.*
import pekko.http.scaladsl.model.headers.*
import pekko.http.scaladsl.testkit.RouteTestTimeout
import pekko.http.scaladsl.unmarshalling.Unmarshal

/**
 * End-to-End (E2E) test specification for testing groups endpoint.
 */
class AdminGroupsEndpointsSpec extends E2ESpec with SprayJsonSupport {

  implicit def default: RouteTestTimeout = RouteTestTimeout(30.seconds)

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
        assert(response.status === StatusCodes.OK)
      }

      "return the group's information" in {
        val request = Get(baseApiUrl + s"/admin/groups/$groupIriEnc") ~> addCredentials(
          BasicHttpCredentials(imagesUser01Email, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
      }
    }

    "given a custom Iri" should {
      val customGroupIri = "http://rdfh.ch/groups/00FF/gNdJSNYrTDu2lGpPUs94nQ"
      "create a group with the provided custom IRI " in {
        val createGroupWithCustomIriRequest: String =
          s"""{   "id": "$customGroupIri",
             |    "name": "NewGroupWithCustomIri",
             |    "descriptions": [{"value": "A new group with a custom Iri", "language": "en"}],
             |    "project": "${SharedTestDataADM.imagesProjectIri}",
             |    "status": true,
             |    "selfjoin": false
             |}""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/groups",
          HttpEntity(ContentTypes.`application/json`, createGroupWithCustomIriRequest),
        ) ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val result: Group = AkkaHttpUtils.httpResponseToJson(response).fields("group").convertTo[Group]

        // check that the custom IRI is correctly assigned
        result.id should be(customGroupIri)
      }

      "return 'BadRequest' if the supplied IRI for the group is not unique" in {
        val params =
          s"""{             "id": "$customGroupIri",
             |    "name": "NewGroupWithDuplicateCustomIri",
             |    "descriptions": [{"value": "A new group with a duplicate custom Iri", "language": "en"}],
             |    "project": "${SharedTestDataADM.imagesProjectIri}",
             |    "status": true,
             |    "selfjoin": false
             |}""".stripMargin

        val request =
          Post(baseApiUrl + s"/admin/groups", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
            BasicHttpCredentials(imagesUser01Email, testPass),
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
             |    "project": "${SharedTestDataADM.imagesProjectIri}",
             |    "status": true,
             |    "selfjoin": false
             |}""".stripMargin

        val request = Post(
          baseApiUrl + "/admin/groups",
          HttpEntity(ContentTypes.`application/json`, createGroupRequest),
        ) ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val group: Group = AkkaHttpUtils.httpResponseToJson(response).fields("group").convertTo[Group]

        group.name should be("NewGroup")
        group.descriptions should be(Seq(StringLiteralV2.from("NewGroupDescription", Some("en"))))
        group.project should be(Some(SharedTestDataADM.imagesProjectExternal))
        group.status should be(true)
        group.selfjoin should be(false)
        newGroupIri.set(group.id)
      }

      "UPDATE a group" in {
        val updateGroupRequest: String =
          s"""{
             |    "name": "UpdatedGroupName",
             |    "descriptions": [{"value": "UpdatedGroupDescription", "language": "en"}]
             |}""".stripMargin
        val groupIriEnc = java.net.URLEncoder.encode(newGroupIri.get, "utf-8")
        val request = Put(
          baseApiUrl + "/admin/groups/" + groupIriEnc,
          HttpEntity(ContentTypes.`application/json`, updateGroupRequest),
        ) ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val groupInfo: Group = AkkaHttpUtils.httpResponseToJson(response).fields("group").convertTo[Group]

        groupInfo.name should be("UpdatedGroupName")
        groupInfo.descriptions should be(Seq(StringLiteralV2.from("UpdatedGroupDescription", Some("en"))))
        groupInfo.project should be(Some(SharedTestDataADM.imagesProjectExternal))
        groupInfo.status should be(true)
        groupInfo.selfjoin should be(false)
      }

      "DELETE a group" in {
        val groupIriEnc = java.net.URLEncoder.encode(newGroupIri.get, "utf-8")
        val request = Delete(baseApiUrl + "/admin/groups/" + groupIriEnc) ~> addCredentials(
          BasicHttpCredentials(imagesUser01Email, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val groupInfo: Group = AkkaHttpUtils.httpResponseToJson(response).fields("group").convertTo[Group]

        groupInfo.name should be("UpdatedGroupName")
        groupInfo.descriptions should be(Seq(StringLiteralV2.from("UpdatedGroupDescription", Some("en"))))
        groupInfo.project should be(Some(SharedTestDataADM.imagesProjectExternal))
        groupInfo.status should be(false)
        groupInfo.selfjoin should be(false)
      }

      "CHANGE status of a group" in {
        val changeGroupStatusRequest: String =
          s"""{
             |    "status": true
             |}""".stripMargin

        val groupIriEnc = java.net.URLEncoder.encode(newGroupIri.get, "utf-8")
        val request = Put(
          baseApiUrl + "/admin/groups/" + groupIriEnc + "/status",
          HttpEntity(ContentTypes.`application/json`, changeGroupStatusRequest),
        ) ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val groupInfo: Group = AkkaHttpUtils.httpResponseToJson(response).fields("group").convertTo[Group]

        groupInfo.name should be("UpdatedGroupName")
        groupInfo.descriptions should be(Seq(StringLiteralV2.from("UpdatedGroupDescription", Some("en"))))
        groupInfo.project should be(Some(SharedTestDataADM.imagesProjectExternal))
        groupInfo.status should be(true)
        groupInfo.selfjoin should be(false)
      }
    }

    "used to query members" should {
      "return all members of a group" in {
        val request = Get(baseApiUrl + s"/admin/groups/$groupIriEnc/members") ~> addCredentials(
          BasicHttpCredentials(imagesUser01Email, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK, responseToString(response))
      }
    }
  }
}
