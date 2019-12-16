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
import org.knora.webapi.SharedTestDataV1.IMAGES_PROJECT_IRI
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, StringLiteralV2, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.v1.routing.authenticationmessages.CredentialsADM
import org.knora.webapi.testing.tags.E2ETest
import org.knora.webapi.util.{AkkaHttpUtils, MutableTestIri}
import org.knora.webapi.{E2ESpec, SharedListsTestDataADM, SharedTestDataADM, SharedTestDataV1}

import scala.concurrent.duration._

object ListsADME2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing users endpoint.
  */
@E2ETest
class ListsADME2ESpec extends E2ESpec(ListsADME2ESpec.config) with SessionJsonProtocol with TriplestoreJsonProtocol with ListADMJsonProtocol {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds)

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    val rootCreds = CredentialsADM(
        SharedTestDataADM.rootUser,
        "test"
    )

    val normalUserCreds = CredentialsADM(
        SharedTestDataADM.normalUser,
        "test"
    )

    val images01UserCreds = CredentialsADM(
        SharedTestDataADM.imagesUser01,
        "test"
    )

    val images02UserCreds = CredentialsADM(
        SharedTestDataADM.imagesUser02,
        "test"
    )

    private val inactiveUserEmailEnc = java.net.URLEncoder.encode(SharedTestDataV1.inactiveUser.userData.email.get, "utf-8")


    private val normalUserIri = SharedTestDataADM.normalUser.id
    private val normalUserIriEnc = java.net.URLEncoder.encode(normalUserIri, "utf-8")

    private val multiUserIri = SharedTestDataADM.multiuserUser.id
    private val multiUserIriEnc = java.net.URLEncoder.encode(multiUserIri, "utf-8")

    private val wrongEmail = "wrong@example.com"
    private val wrongEmailEnc = java.net.URLEncoder.encode(wrongEmail, "utf-8")

    private val testPass = java.net.URLEncoder.encode("test", "utf-8")
    private val wrongPass = java.net.URLEncoder.encode("wrong", "utf-8")

    private val imagesProjectIri = SharedTestDataADM.imagesProject.id
    private val imagesProjectIriEnc = java.net.URLEncoder.encode(imagesProjectIri, "utf-8")

    private val imagesReviewerGroupIri = SharedTestDataADM.imagesReviewerGroup.id
    private val imagesReviewerGroupIriEnc = java.net.URLEncoder.encode(imagesReviewerGroupIri, "utf-8")


    private val treeListInfo: ListRootNodeInfoADM = SharedListsTestDataADM.treeListInfo
    private val treeListNodes: Seq[ListChildNodeADM] = SharedListsTestDataADM.treeListChildNodes

    val newListIri = new MutableTestIri
    val firstChildIri = new MutableTestIri
    val secondChildIri = new MutableTestIri
    val thirdChildIri = new MutableTestIri

    "The Lists Route ('/admin/lists')" when {

        "used to query information about lists" should {

            "return all lists" in {
                val request = Get(baseApiUrl + s"/admin/lists") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)

                // println(s"response: ${response.toString}")

                response.status should be(StatusCodes.OK)

                val lists: Seq[ListNodeInfoADM] = AkkaHttpUtils.httpResponseToJson(response).fields("lists").convertTo[Seq[ListNodeInfoADM]]

                // log.debug("lists: {}", lists)

                lists.size should be (7)
            }

            "return all lists belonging to the images project" in {
                val request = Get(baseApiUrl + s"/admin/lists?projectIri=http%3A%2F%2Frdfh.ch%2Fprojects%2F00FF") ~> addCredentials(rootCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")

                response.status should be(StatusCodes.OK)

                val lists: Seq[ListNodeInfoADM] = AkkaHttpUtils.httpResponseToJson(response).fields("lists").convertTo[Seq[ListNodeInfoADM]]

                // log.debug("lists: {}", lists)

                lists.size should be (4)
            }

            "return all lists belonging to the anything project" in {
                val request = Get(baseApiUrl + s"/admin/lists?projectIri=http%3A%2F%2Frdfh.ch%2Fprojects%2F0001") ~> addCredentials(rootCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")

                response.status should be(StatusCodes.OK)

                val lists: Seq[ListNodeInfoADM] = AkkaHttpUtils.httpResponseToJson(response).fields("lists").convertTo[Seq[ListNodeInfoADM]]

                // log.debug("lists: {}", lists)

                lists.size should be (2)
            }

            "return basic list information" in {
                val request = Get(baseApiUrl + s"/admin/lists/http%3A%2F%2Frdfh.ch%2Flists%2F0001%2FtreeList/Info") ~> addCredentials(rootCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")

                response.status should be(StatusCodes.OK)

                val receivedListInfo: ListRootNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListRootNodeInfoADM]

                val expectedListInfo: ListRootNodeInfoADM = SharedListsTestDataADM.treeListInfo

                receivedListInfo.sorted should be (expectedListInfo.sorted)
            }

            "return a complete list" in {
                val request = Get(baseApiUrl + s"/admin/lists/http%3A%2F%2Frdfh.ch%2Flists%2F0001%2FtreeList") ~> addCredentials(rootCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // println(s"response: ${response.toString}")

                response.status should be(StatusCodes.OK)

                val receivedList: ListADM = AkkaHttpUtils.httpResponseToJson(response).fields("list").convertTo[ListADM]
                receivedList.listinfo.sorted should be (treeListInfo.sorted)
                receivedList.children.map(_.sorted) should be (treeListNodes.map(_.sorted))
            }
        }

        "used to modify list information" should {

            "create a list" in {

                val params =
                    s"""
                       |{
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "labels": [{ "value": "Neue Liste", "language": "de"}],
                       |    "comments": []
                       |}
                """.stripMargin

                val request = Post(baseApiUrl + s"/admin/lists", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val receivedList: ListADM = AkkaHttpUtils.httpResponseToJson(response).fields("list").convertTo[ListADM]

                val listInfo = receivedList.listinfo
                listInfo.projectIri should be (IMAGES_PROJECT_IRI)

                val labels: Seq[StringLiteralV2] = listInfo.labels.stringLiterals
                labels.size should be (1)
                labels.head should be (StringLiteralV2(value = "Neue Liste", language = Some("de")))

                val comments = receivedList.listinfo.comments.stringLiterals
                comments.isEmpty should be (true)

                val children = receivedList.children
                children.size should be (0)

                // store list IRI for next test
                newListIri.set(listInfo.id)
            }

            "return a 'ForbiddenException' if the user creating the list is not project or system admin" in {
                val params =
                    s"""
                       |{
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "labels": [{ "value": "Neue Liste", "language": "de"}],
                       |    "comments": []
                       |}
                """.stripMargin

                val request = Post(baseApiUrl + s"/admin/lists", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(images02UserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.Forbidden)
            }

            "return a `BadRequestException` during list creation when payload is not correct" in {

                // no project IRI
                val params01 =
                    s"""
                       |{
                       |    "projectIri": "",
                       |    "labels": [{ "value": "Neue Liste", "language": "de"}],
                       |    "comments": []
                       |}
                """.stripMargin

                val request01 = Post(baseApiUrl + s"/admin/lists", HttpEntity(ContentTypes.`application/json`, params01))
                val response01: HttpResponse = singleAwaitingRequest(request01)
                // println(s"response: ${response01.toString}")
                response01.status should be(StatusCodes.BadRequest)


                // invalid project IRI
                val params02 =
                    s"""
                       |{
                       |    "projectIri": "notvalidIRI",
                       |    "labels": [{ "value": "Neue Liste", "language": "de"}],
                       |    "comments": []
                       |}
                """.stripMargin

                val request02 = Post(baseApiUrl + s"/admin/lists", HttpEntity(ContentTypes.`application/json`, params02))
                val response02: HttpResponse = singleAwaitingRequest(request02)
                // println(s"response: ${response02.toString}")
                response02.status should be(StatusCodes.BadRequest)


                // missing label
                val params03 =
                    s"""
                       |{
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "labels": [],
                       |    "comments": []
                       |}
                """.stripMargin

                val request03 = Post(baseApiUrl + s"/admin/lists", HttpEntity(ContentTypes.`application/json`, params03))
                val response03: HttpResponse = singleAwaitingRequest(request03)
                // println(s"response: ${response03.toString}")
                response03.status should be(StatusCodes.BadRequest)

            }

            "update basic list information: name" in {
                val paramsUpdate =
                    s"""
                       |{
                       |    "listIri": "${newListIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "newTestName",
                       |    "labels": [],
                       |    "comments": []
                       |}
                """.stripMargin

                val encodedListUrl = java.net.URLEncoder.encode(newListIri.get, "utf-8")

                val requestUpdate = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/ListInfoName", HttpEntity(ContentTypes.`application/json`, paramsUpdate)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val responseUpdate: HttpResponse = singleAwaitingRequest(requestUpdate)
                responseUpdate.status should be(StatusCodes.OK)

                val receivedListInfoUpdate: ListRootNodeInfoADM = AkkaHttpUtils.httpResponseToJson(responseUpdate).fields("listinfo").convertTo[ListRootNodeInfoADM]
                receivedListInfoUpdate.projectIri should be (IMAGES_PROJECT_IRI)
                receivedListInfoUpdate.name should be (Some("newTestName"))

                val paramsDelete =
                    s"""
                       |{
                       |    "listIri": "${newListIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "",
                       |    "labels": [],
                       |    "comments": []
                       |}
                """.stripMargin

                val requestDelete = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/ListInfoName", HttpEntity(ContentTypes.`application/json`, paramsDelete)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val responseDelete: HttpResponse = singleAwaitingRequest(requestDelete)
                responseDelete.status should be(StatusCodes.OK)

                val receivedListInfoDelete: ListRootNodeInfoADM = AkkaHttpUtils.httpResponseToJson(responseDelete).fields("listinfo").convertTo[ListRootNodeInfoADM]
                receivedListInfoDelete.projectIri should be (IMAGES_PROJECT_IRI)
                receivedListInfoDelete.name.isEmpty should be (true)
            }

            "update basic list information: label" in {
                val params =
                    s"""
                       |{
                       |    "listIri": "${newListIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "",
                       |    "labels": [{"value": "Neue geönderte Liste", "language": "de"}, {"value": "Changed list", "language": "en"}],
                       |    "comments": []
                       |}
                """.stripMargin

                val encodedListUrl = java.net.URLEncoder.encode(newListIri.get, "utf-8")

                val request = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/ListInfoLabel", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                response.status should be(StatusCodes.OK)

                val receivedListInfo: ListRootNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListRootNodeInfoADM]

                receivedListInfo.projectIri should be (IMAGES_PROJECT_IRI)

                val labels: Seq[StringLiteralV2] = receivedListInfo.labels.stringLiterals
                labels.size should be (2)
            }

            "update basic list information: comment" in {
                val paramsUpdate =
                    s"""
                       |{
                       |    "listIri": "${newListIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "",
                       |    "labels": [],
                       |    "comments": [{"value": "Neuer Kommentar", "language": "de"}, {"value": "New comment", "language": "en"}]
                       |}
                """.stripMargin

                val encodedListUrl = java.net.URLEncoder.encode(newListIri.get, "utf-8")

                val requestUpdate = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/ListInfoComment", HttpEntity(ContentTypes.`application/json`, paramsUpdate)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val responseUpdate: HttpResponse = singleAwaitingRequest(requestUpdate)
                responseUpdate.status should be(StatusCodes.OK)

                val receivedListInfoComment: ListRootNodeInfoADM = AkkaHttpUtils.httpResponseToJson(responseUpdate).fields("listinfo").convertTo[ListRootNodeInfoADM]
                receivedListInfoComment.projectIri should be (IMAGES_PROJECT_IRI)

                val commentsUpdate = receivedListInfoComment.comments.stringLiterals
                commentsUpdate.size should be (2)

                val paramsDelete =
                    s"""
                       |{
                       |    "listIri": "${newListIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "",
                       |    "labels": [],
                       |    "comments": []
                       |}
                """.stripMargin

                val requestDelete = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/ListInfoComment", HttpEntity(ContentTypes.`application/json`, paramsDelete)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val responseDelete: HttpResponse = singleAwaitingRequest(requestDelete)
                responseDelete.status should be(StatusCodes.OK)

                val receivedListInfoDelete: ListRootNodeInfoADM = AkkaHttpUtils.httpResponseToJson(responseDelete).fields("listinfo").convertTo[ListRootNodeInfoADM]
                receivedListInfoDelete.projectIri should be (IMAGES_PROJECT_IRI)

                val commentsDelete = receivedListInfoDelete.comments.stringLiterals
                commentsDelete.size should be (0)
            }

            "return a 'ForbiddenException' if the user updating the list is not project or system admin" in {
                val params =
                    s"""
                       |{
                       |    "listIri": "${newListIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "newTestName",
                       |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
                       |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
                       |}
                """.stripMargin

                val encodedListUrl = java.net.URLEncoder.encode(newListIri.get, "utf-8")

                val requestName = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/ListInfoName", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(images02UserCreds.basicHttpCredentials)
                val responseName: HttpResponse = singleAwaitingRequest(requestName)

                responseName.status should be(StatusCodes.Forbidden)

                val requestLabel = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/ListInfoLabel", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(images02UserCreds.basicHttpCredentials)
                val responseLabel: HttpResponse = singleAwaitingRequest(requestLabel)

                responseLabel.status should be(StatusCodes.Forbidden)

                val requestComment = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/ListInfoComment", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(images02UserCreds.basicHttpCredentials)
                val responseComment: HttpResponse = singleAwaitingRequest(requestComment)

                responseComment.status should be(StatusCodes.Forbidden)
            }

            "return a `BadRequestException` during list change when payload is not correct" in {

                val encodedListUrl = java.net.URLEncoder.encode(newListIri.get, "utf-8")

                // empty list IRI
                val params01 =
                    s"""
                       |{
                       |    "listIri": "",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "newTestName",
                       |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
                       |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
                       |}
                """.stripMargin

                val request01Name = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/ListInfoName", HttpEntity(ContentTypes.`application/json`, params01)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response01Name: HttpResponse = singleAwaitingRequest(request01Name)
                response01Name.status should be(StatusCodes.BadRequest)
                val request01Label = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/ListInfoLabel", HttpEntity(ContentTypes.`application/json`, params01)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response01Label: HttpResponse = singleAwaitingRequest(request01Label)
                response01Label.status should be(StatusCodes.BadRequest)
                val request01Comment = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/ListInfoComment", HttpEntity(ContentTypes.`application/json`, params01)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response01Comment: HttpResponse = singleAwaitingRequest(request01Comment)
                response01Comment.status should be(StatusCodes.BadRequest)

                // empty project
                val params02 =
                s"""
                   |{
                   |    "listIri": "${newListIri.get}",
                   |    "projectIri": "",
                   |    "name": "newTestName",
                   |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
                   |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
                   |}
                """.stripMargin

                val request02Name = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/ListInfoName", HttpEntity(ContentTypes.`application/json`, params02)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response02Name: HttpResponse = singleAwaitingRequest(request02Name)
                response02Name.status should be(StatusCodes.BadRequest)
                val request02Label = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/ListInfoLabel", HttpEntity(ContentTypes.`application/json`, params02)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response02Label: HttpResponse = singleAwaitingRequest(request02Label)
                response02Label.status should be(StatusCodes.BadRequest)
                val request02Comment = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/ListInfoComment", HttpEntity(ContentTypes.`application/json`, params02)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response02Comment: HttpResponse = singleAwaitingRequest(request02Comment)
                response02Comment.status should be(StatusCodes.BadRequest)

                // empty parameters (at least one label must be supplied)
                val params03 =
                    s"""
                       |{
                       |    "listIri": "${newListIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "",
                       |    "labels": [],
                       |    "comments": []
                       |}
                """.stripMargin

                val request03Label = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/ListInfoLabel", HttpEntity(ContentTypes.`application/json`, params03)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response03Label: HttpResponse = singleAwaitingRequest(request03Label)
                response03Label.status should be(StatusCodes.BadRequest)
            }
        }
    }

    "The Nodes Route ('/admin/nodes')" when {

        "used to query information about nodes" should {

            "return a list" ignore {

            }

            "return a sublist" ignore {

            }

            "return basic node information" ignore {

            }

        }

        "used to modify node information" should {

            "add child to list - to the root node" in {

                val encodedListUrl = java.net.URLEncoder.encode(newListIri.get, "utf-8")

                val params =
                    s"""
                       |{
                       |    "parentNodeIri": "${newListIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "first",
                       |    "labels": [{ "value": "New First Child List Node Value", "language": "en"}],
                       |    "comments": [{ "value": "New First Child List Node Comment", "language": "en"}]
                       |}
                """.stripMargin

                val request = Post(baseApiUrl + s"/admin/nodes", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // println(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val received: ListNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListNodeInfoADM]

                // check correct node info
                val childNodeInfo = received match {
                    case info: ListChildNodeInfoADM => info
                    case something => fail(s"expecting ListChildNodeInfoADM but got ${something.getClass.toString} instead.")
                }

                // check labels
                val labels: Seq[StringLiteralV2] = childNodeInfo.labels.stringLiterals
                labels.size should be (1)
                labels.sorted should be (Seq(StringLiteralV2(value = "New First Child List Node Value", language = Some("en"))))

                // check comments
                val comments = childNodeInfo.comments.stringLiterals
                comments.size should be (1)
                comments.sorted should be (Seq(StringLiteralV2(value = "New First Child List Node Comment", language = Some("en"))))

                // check position
                val position = childNodeInfo.position
                position should be (0)

                // check has root node
                val rootNode = childNodeInfo.hasRootNode
                rootNode should be (newListIri.get)

                firstChildIri.set(childNodeInfo.id)
            }

            "add second child to list - to the root node" in {

                val encodedListUrl = java.net.URLEncoder.encode(newListIri.get, "utf-8")

                val params =
                    s"""
                       |{
                       |    "parentNodeIri": "${newListIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "second",
                       |    "labels": [{ "value": "New Second Child List Node Value", "language": "en"}],
                       |    "comments": [{ "value": "New Second Child List Node Comment", "language": "en"}]
                       |}
                """.stripMargin

                val request = Post(baseApiUrl + s"/admin/nodes", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // println(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val received: ListNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListNodeInfoADM]

                // check correct node info
                val childNodeInfo = received match {
                    case info: ListChildNodeInfoADM => info
                    case something => fail(s"expecting ListChildNodeInfoADM but got ${something.getClass.toString} instead.")
                }

                // check labels
                val labels: Seq[StringLiteralV2] = childNodeInfo.labels.stringLiterals
                labels.size should be (1)
                labels.sorted should be (Seq(StringLiteralV2(value = "New Second Child List Node Value", language = Some("en"))))

                // check comments
                val comments = childNodeInfo.comments.stringLiterals
                comments.size should be (1)
                comments.sorted should be (Seq(StringLiteralV2(value = "New Second Child List Node Comment", language = Some("en"))))

                // check position
                val position = childNodeInfo.position
                position should be (1)

                // check has root node
                val rootNode = childNodeInfo.hasRootNode
                rootNode should be (newListIri.get)

                secondChildIri.set(childNodeInfo.id)

            }

            "add child to second child node" in {

                val encodedListUrl = java.net.URLEncoder.encode(secondChildIri.get, "utf-8")

                val params =
                    s"""
                       |{
                       |    "parentNodeIri": "${secondChildIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "third",
                       |    "labels": [{ "value": "New Third Child List Node Value", "language": "en"}],
                       |    "comments": [{ "value": "New Third Child List Node Comment", "language": "en"}]
                       |}
                """.stripMargin

                val request = Post(baseApiUrl + s"/admin/nodes", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // println(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val received: ListNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListNodeInfoADM]

                // check correct node info
                val childNodeInfo = received match {
                    case info: ListChildNodeInfoADM => info
                    case something => fail(s"expecting ListChildNodeInfoADM but got ${something.getClass.toString} instead.")
                }

                // check labels
                val labels: Seq[StringLiteralV2] = childNodeInfo.labels.stringLiterals
                labels.size should be (1)
                labels.sorted should be (Seq(StringLiteralV2(value = "New Third Child List Node Value", language = Some("en"))))

                // check comments
                val comments = childNodeInfo.comments.stringLiterals
                comments.size should be (1)
                comments.sorted should be (Seq(StringLiteralV2(value = "New Third Child List Node Comment", language = Some("en"))))

                // check position
                val position = childNodeInfo.position
                position should be (0)

                // check has root node
                val rootNode = childNodeInfo.hasRootNode
                rootNode should be (newListIri.get)

                thirdChildIri.set(childNodeInfo.id)
            }

            "return a 'ForbiddenException' if the user creating the node is not project or system admin" in {
                val params =
                    s"""
                       |{
                       |    "parentNodeIri": "${secondChildIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "first",
                       |    "labels": [{ "value": "New Child List Node Value", "language": "en"}],
                       |    "comments": []
                       |}
                """.stripMargin

                val request = Post(baseApiUrl + s"/admin/nodes", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(images02UserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                response.status should be(StatusCodes.Forbidden)
            }

            "return a `BadRequestException` during node creation when payload is not correct" in {
                // no parentNode IRI
                val params00 =
                    s"""
                       |{
                       |    "parentNodeIri": "",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "first",
                       |    "labels": [{ "value": "New Child List Node Value", "language": "en"}],
                       |    "comments": []
                       |}
                """.stripMargin

                val request00 = Post(baseApiUrl + s"/admin/nodes", HttpEntity(ContentTypes.`application/json`, params00)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response00: HttpResponse = singleAwaitingRequest(request00)
                response00.status should be(StatusCodes.BadRequest)


                // invalid parentNode IRI
                val params01 =
                    s"""
                       |{
                       |    "parentNodeIri": "invalidIRI",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "first",
                       |    "labels": [{ "value": "New Child List Node Value", "language": "en"}],
                       |    "comments": []
                       |}
                """.stripMargin

                val request01 = Post(baseApiUrl + s"/admin/nodes", HttpEntity(ContentTypes.`application/json`, params01)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response01: HttpResponse = singleAwaitingRequest(request01)
                response01.status should be(StatusCodes.BadRequest)


                // no project IRI
                val params02 =
                    s"""
                       |{
                       |    "parentNodeIri": "${secondChildIri.get}",
                       |    "projectIri": "",
                       |    "name": "first",
                       |    "labels": [{ "value": "New Child List Node Value", "language": "en"}],
                       |    "comments": []
                       |}
                """.stripMargin

                val request02 = Post(baseApiUrl + s"/admin/nodes", HttpEntity(ContentTypes.`application/json`, params02)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response02: HttpResponse = singleAwaitingRequest(request02)
                response02.status should be(StatusCodes.BadRequest)


                // invalid project IRI
                val params03 =
                s"""
                   |{
                   |    "parentNodeIri": "${secondChildIri.get}",
                   |    "projectIri": "invalidIRI",
                   |    "name": "first",
                   |    "labels": [{ "value": "New Child List Node Value", "language": "en"}],
                   |    "comments": []
                   |}
                """.stripMargin

                val request03 = Post(baseApiUrl + s"/admin/nodes", HttpEntity(ContentTypes.`application/json`, params03)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response03: HttpResponse = singleAwaitingRequest(request03)
                response03.status should be(StatusCodes.BadRequest)


                // missing label
                val params05 =
                    s"""
                       |{
                       |    "parentNodeIri": "${secondChildIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "first",
                       |    "labels": [],
                       |    "comments": []
                       |}
                """.stripMargin

                val request05 = Post(baseApiUrl + s"/admin/nodes", HttpEntity(ContentTypes.`application/json`, params05)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response05: HttpResponse = singleAwaitingRequest(request05)
                // println(s"response: ${response03.toString}")
                response05.status should be(StatusCodes.BadRequest)

            }

            "update basic list node information: name" in {
                val paramsUpdate =
                    s"""
                       |{
                       |    "nodeIri": "${firstChildIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "newTestName",
                       |    "labels": [],
                       |    "comments": []
                       |}
                """.stripMargin

                val encodedListNodeUrl = java.net.URLEncoder.encode(firstChildIri.get, "utf-8")

                val requestUpdate = Put(baseApiUrl + s"/admin/nodes/" + encodedListNodeUrl + "/NodeInfoName", HttpEntity(ContentTypes.`application/json`, paramsUpdate)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val responseUpdate: HttpResponse = singleAwaitingRequest(requestUpdate)
                responseUpdate.status should be(StatusCodes.OK)

                val receivedListNodeInfoUpdate: ListChildNodeInfoADM = AkkaHttpUtils.httpResponseToJson(responseUpdate).fields("nodeinfo").convertTo[ListChildNodeInfoADM]
                receivedListNodeInfoUpdate.name should be (Some("newTestName"))

                val paramsDelete =
                    s"""
                       |{
                       |    "nodeIri": "${firstChildIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "",
                       |    "labels": [],
                       |    "comments": []
                       |}
                """.stripMargin

                val requestDelete = Put(baseApiUrl + s"/admin/nodes/" + encodedListNodeUrl + "/NodeInfoName", HttpEntity(ContentTypes.`application/json`, paramsDelete)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val responseDelete: HttpResponse = singleAwaitingRequest(requestDelete)
                responseDelete.status should be(StatusCodes.OK)

                val receivedListNodeInfoDelete: ListChildNodeInfoADM = AkkaHttpUtils.httpResponseToJson(responseDelete).fields("nodeinfo").convertTo[ListChildNodeInfoADM]
                receivedListNodeInfoDelete.name.isEmpty should be (true)
            }

            "update basic list node information: label" in {
                val params =
                    s"""
                       |{
                       |    "nodeIri": "${firstChildIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "",
                       |    "labels": [{"value": "Neue geönderte Liste", "language": "de"}, {"value": "Changed list", "language": "en"}],
                       |    "comments": []
                       |}
                """.stripMargin

                val encodedListNodeUrl = java.net.URLEncoder.encode(firstChildIri.get, "utf-8")

                val request = Put(baseApiUrl + s"/admin/nodes/" + encodedListNodeUrl + "/NodeInfoLabel", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                response.status should be(StatusCodes.OK)

                val receivedListNodeInfo: ListChildNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListChildNodeInfoADM]

                val labels: Seq[StringLiteralV2] = receivedListNodeInfo.labels.stringLiterals
                labels.size should be (2)
            }

            "update basic list node information: comment" in {
                val paramsUpdate =
                    s"""
                       |{
                       |    "nodeIri": "${firstChildIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "",
                       |    "labels": [],
                       |    "comments": [{"value": "Neuer Kommentar", "language": "de"}, {"value": "New comment", "language": "en"}]
                       |}
                """.stripMargin

                val encodedListNodeUrl = java.net.URLEncoder.encode(firstChildIri.get, "utf-8")

                val requestUpdate = Put(baseApiUrl + s"/admin/nodes/" + encodedListNodeUrl + "/NodeInfoComment", HttpEntity(ContentTypes.`application/json`, paramsUpdate)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val responseUpdate: HttpResponse = singleAwaitingRequest(requestUpdate)
                responseUpdate.status should be(StatusCodes.OK)

                val receivedListInfoComment: ListChildNodeInfoADM = AkkaHttpUtils.httpResponseToJson(responseUpdate).fields("nodeinfo").convertTo[ListChildNodeInfoADM]
                val commentsUpdate = receivedListInfoComment.comments.stringLiterals
                commentsUpdate.size should be (2)

                val paramsDelete =
                    s"""
                       |{
                       |    "nodeIri": "${firstChildIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "",
                       |    "labels": [],
                       |    "comments": []
                       |}
                """.stripMargin

                val requestDelete = Put(baseApiUrl + s"/admin/nodes/" + encodedListNodeUrl + "/NodeInfoComment", HttpEntity(ContentTypes.`application/json`, paramsDelete)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val responseDelete: HttpResponse = singleAwaitingRequest(requestDelete)
                responseDelete.status should be(StatusCodes.OK)

                val receivedListInfoDelete: ListChildNodeInfoADM = AkkaHttpUtils.httpResponseToJson(responseDelete).fields("nodeinfo").convertTo[ListChildNodeInfoADM]
                val commentsDelete = receivedListInfoDelete.comments.stringLiterals
                commentsDelete.size should be (0)
            }

            "return a 'ForbiddenException' if the user updating the list node is not project or system admin" in {
                val params =
                    s"""
                       |{
                       |    "nodeIri": "${firstChildIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "newTestName",
                       |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
                       |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
                       |}
                """.stripMargin

                val encodedListNodeUrl = java.net.URLEncoder.encode(firstChildIri.get, "utf-8")

                val requestName = Put(baseApiUrl + s"/admin/nodes/" + encodedListNodeUrl + "/NodeInfoName", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(images02UserCreds.basicHttpCredentials)
                val responseName: HttpResponse = singleAwaitingRequest(requestName)
                responseName.status should be(StatusCodes.Forbidden)

                val requestLabel = Put(baseApiUrl + s"/admin/nodes/" + encodedListNodeUrl + "/NodeInfoLabel", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(images02UserCreds.basicHttpCredentials)
                val responseLabel: HttpResponse = singleAwaitingRequest(requestLabel)
                responseLabel.status should be(StatusCodes.Forbidden)

                val requestComment = Put(baseApiUrl + s"/admin/nodes/" + encodedListNodeUrl + "/NodeInfoComment", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(images02UserCreds.basicHttpCredentials)
                val responseComment: HttpResponse = singleAwaitingRequest(requestComment)
                responseComment.status should be(StatusCodes.Forbidden)
            }

            "return a `BadRequestException` during list node change when payload is not correct" in {
                val encodedListNodeUrl = java.net.URLEncoder.encode(firstChildIri.get, "utf-8")

                // empty node IRI
                val params01 =
                    s"""
                       |{
                       |    "nodeIri": "",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "newTestName",
                       |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
                       |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
                       |}
                """.stripMargin

                val request01Name = Put(baseApiUrl + s"/admin/nodes/" + encodedListNodeUrl + "/NodeInfoName", HttpEntity(ContentTypes.`application/json`, params01)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response01Name: HttpResponse = singleAwaitingRequest(request01Name)
                response01Name.status should be(StatusCodes.BadRequest)
                val request01Label = Put(baseApiUrl + s"/admin/nodes/" + encodedListNodeUrl + "/NodeInfoLabel", HttpEntity(ContentTypes.`application/json`, params01)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response01Label: HttpResponse = singleAwaitingRequest(request01Label)
                response01Label.status should be(StatusCodes.BadRequest)
                val request01Comment = Put(baseApiUrl + s"/admin/nodes/" + encodedListNodeUrl + "/NodeInfoComment", HttpEntity(ContentTypes.`application/json`, params01)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response01Comment: HttpResponse = singleAwaitingRequest(request01Comment)
                response01Comment.status should be(StatusCodes.BadRequest)

                // empty project
                val params02 =
                    s"""
                       |{
                       |    "nodeIri": "${firstChildIri.get}",
                       |    "projectIri": "",
                       |    "name": "newTestName",
                       |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
                       |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
                       |}
                """.stripMargin

                val request02Name = Put(baseApiUrl + s"/admin/nodes/" + encodedListNodeUrl + "/NodeInfoName", HttpEntity(ContentTypes.`application/json`, params02)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response02Name: HttpResponse = singleAwaitingRequest(request02Name)
                response02Name.status should be(StatusCodes.BadRequest)
                val request02Label = Put(baseApiUrl + s"/admin/nodes/" + encodedListNodeUrl + "/NodeInfoLabel", HttpEntity(ContentTypes.`application/json`, params02)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response02Label: HttpResponse = singleAwaitingRequest(request02Label)
                response02Label.status should be(StatusCodes.BadRequest)
                val request02Comment = Put(baseApiUrl + s"/admin/nodes/" + encodedListNodeUrl + "/NodeInfoComment", HttpEntity(ContentTypes.`application/json`, params02)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response02Comment: HttpResponse = singleAwaitingRequest(request02Comment)
                response02Comment.status should be(StatusCodes.BadRequest)

                // empty parameters (at least one label must be supplied)
                val params03 =
                    s"""
                       |{
                       |    "nodeIri": "${firstChildIri.get}",
                       |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                       |    "name": "",
                       |    "labels": [],
                       |    "comments": []
                       |}
                """.stripMargin

                val request03Label = Put(baseApiUrl + s"/admin/nodes/" + encodedListNodeUrl + "/NodeInfoLabel", HttpEntity(ContentTypes.`application/json`, params03)) ~> addCredentials(images01UserCreds.basicHttpCredentials)
                val response03Label: HttpResponse = singleAwaitingRequest(request03Label)
                response03Label.status should be(StatusCodes.BadRequest)
            }













            "add flat nodes" ignore {

            }

            "add hierarchical nodes" ignore {

            }

            "change node order" ignore {

            }

            "delete node if not in use" ignore {

            }
        }
    }
}
