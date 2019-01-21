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

package org.knora.webapi.responders.admin

import java.util.UUID

import akka.actor.Status.Failure
import akka.testkit._
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.SharedTestDataV1._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, StringLiteralV2}
import org.knora.webapi.responders.admin.ListsResponderADM._
import org.knora.webapi.util.MutableTestIri

import scala.concurrent.duration._
/**
  * Static data for testing [[ListsResponderADM]].
  */
object ListsResponderADMSpec {
    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * Tests [[ListsResponderADM]].
  */
class ListsResponderADMSpec extends CoreSpec(ListsResponderADMSpec.config) with ImplicitSender {

    // The default timeout for receiving reply messages from actors.
    implicit val timeout = 5.seconds

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    private val treeListInfo: ListRootNodeInfoADM = SharedListsTestDataADM.treeListInfo

    private val summerNodeInfo: ListNodeInfoADM = SharedListsTestDataADM.summerNodeInfo

    private val otherTreeListInfo: ListRootNodeInfoADM = SharedListsTestDataADM.otherTreeListInfo

    private val keywordChildNodes: Seq[ListNodeADM] = Seq.empty[ListNodeADM]

    private val treeListChildNodes: Seq[ListNodeADM] = SharedListsTestDataADM.treeListChildNodes

    private val imageCategory = Seq.empty[ListNodeADM]

    private val season = SharedListsTestDataADM.seasonListNodes

    private val nodePath = SharedListsTestDataADM.nodePath

    "The Lists Responder" when {

        "used to query information about lists" should {

            "return all lists" in {
                responderManager ! ListsGetRequestADM(requestingUser = SharedTestDataADM.imagesUser01)

                val received: ListsGetResponseADM = expectMsgType[ListsGetResponseADM](timeout)

                received.lists.size should be(7)
            }

            "return all lists belonging to the images project" in {
                responderManager ! ListsGetRequestADM(projectIri = Some(IMAGES_PROJECT_IRI), requestingUser = SharedTestDataADM.imagesUser01)

                val received: ListsGetResponseADM = expectMsgType[ListsGetResponseADM](timeout)

                // log.debug("received: " + received)

                received.lists.size should be(4)
            }

            "return all lists belonging to the anything project" in {
                responderManager ! ListsGetRequestADM(projectIri = Some(ANYTHING_PROJECT_IRI), requestingUser = SharedTestDataADM.imagesUser01)

                val received: ListsGetResponseADM = expectMsgType[ListsGetResponseADM](timeout)

                // log.debug("received: " + received)

                received.lists.size should be(2)
            }

            "return basic list information (anything list)" in {
                responderManager ! ListInfoGetRequestADM(
                    iri = "http://rdfh.ch/lists/0001/treeList",
                    requestingUser = SharedTestDataADM.anythingUser1
                )

                val received: ListInfoGetResponseADM = expectMsgType[ListInfoGetResponseADM](timeout)

                // log.debug("returned basic keyword list information: {}", MessageUtil.toSource(received.items.head))

                received.listinfo.sorted should be(treeListInfo.sorted)
            }

            "return basic list information (anything other list)" in {
                responderManager ! ListInfoGetRequestADM(
                    iri = "http://rdfh.ch/lists/0001/otherTreeList",
                    requestingUser = SharedTestDataADM.anythingUser1
                )

                val received: ListInfoGetResponseADM = expectMsgType[ListInfoGetResponseADM](timeout)

                // log.debug("returned basic keyword list information: {}", MessageUtil.toSource(received.items.head))

                received.listinfo.sorted should be(otherTreeListInfo.sorted)
            }

            "return basic node information (images list - sommer)" in {
                responderManager ! ListNodeInfoGetRequestADM(
                    iri = "http://rdfh.ch/lists/00FF/526f26ed04",
                    requestingUser = SharedTestDataADM.imagesUser01
                )

                val received: ListNodeInfoGetResponseADM = expectMsgType[ListNodeInfoGetResponseADM](timeout)

                // log.debug("returned basic keyword list information: {}", MessageUtil.toSource(received.items.head))

                received.nodeinfo.sorted should be(summerNodeInfo.sorted)
            }

            "return a full list response" in {
                responderManager ! ListGetRequestADM(
                    iri = "http://rdfh.ch/lists/0001/treeList",
                    requestingUser = SharedTestDataADM.anythingUser1
                )

                val received: ListGetResponseADM = expectMsgType[ListGetResponseADM](timeout)

                // log.debug("returned whole keyword list: {}", MessageUtil.toSource(received.items.head))

                received.list.listinfo.sorted should be(treeListInfo.sorted)

                received.list.children.map(_.sorted) should be(treeListChildNodes.map(_.sorted))
            }
        }

        "used to modify lists" should {

            val newListIri = new MutableTestIri
            val firstChildIri = new MutableTestIri
            val secondChildIri = new MutableTestIri
            val thirdChildIri = new MutableTestIri

            "create a list" in {
                responderManager ! ListCreateRequestADM(
                    createListRequest = CreateListApiRequestADM(
                        projectIri = IMAGES_PROJECT_IRI,
                        name = Some("neuelistename"),
                        labels = Seq(StringLiteralV2(value = "Neue Liste", language = Some("de"))),
                        comments = Seq.empty[StringLiteralV2]
                    ),
                    requestingUser = SharedTestDataADM.imagesUser01,
                    apiRequestID = UUID.randomUUID
                )

                val received: ListGetResponseADM = expectMsgType[ListGetResponseADM](timeout)

                val listInfo = received.list.listinfo
                listInfo.projectIri should be (IMAGES_PROJECT_IRI)

                listInfo.name should be (Some("neuelistename"))

                val labels: Seq[StringLiteralV2] = listInfo.labels.stringLiterals
                labels.size should be (1)
                labels.head should be (StringLiteralV2(value = "Neue Liste", language = Some("de")))

                val comments = received.list.listinfo.comments.stringLiterals
                comments.isEmpty should be (true)

                val children = received.list.children
                children.size should be (0)

                // store list IRI for next test
                newListIri.set(listInfo.id)
            }

            "return a 'ForbiddenException' if the user creating the list is not project or system admin" in {
                responderManager ! ListCreateRequestADM(
                    createListRequest = CreateListApiRequestADM(
                        projectIri = IMAGES_PROJECT_IRI,
                        name = None,
                        labels = Seq(StringLiteralV2(value = "Neue Liste", language = Some("de"))),
                        comments = Seq.empty[StringLiteralV2]
                    ),
                    requestingUser = SharedTestDataADM.imagesUser02,
                    apiRequestID = UUID.randomUUID
                )

                expectMsg(Failure(ForbiddenException(LIST_CREATE_PERMISSION_ERROR)))
            }

            "update basic list information" in {
                responderManager ! ListInfoChangeRequestADM(
                    listIri = newListIri.get,
                    changeListRequest = ChangeListInfoApiRequestADM(
                        listIri = newListIri.get,
                        projectIri = IMAGES_PROJECT_IRI,
                        labels = Seq(
                            StringLiteralV2(value = "Neue geänderte Liste", language = Some("de")),
                            StringLiteralV2(value = "Changed list", language = Some("en"))
                        ),
                        comments = Seq(
                            StringLiteralV2(value = "Neuer Kommentar", language = Some("de")),
                            StringLiteralV2(value = "New comment", language = Some("en"))
                        )
                    ),
                    requestingUser = SharedTestDataADM.imagesUser01,
                    apiRequestID = UUID.randomUUID
                )

                val received: ListInfoGetResponseADM = expectMsgType[ListInfoGetResponseADM](timeout)

                val listInfo = received.listinfo
                listInfo.projectIri should be (IMAGES_PROJECT_IRI)

                val labels: Seq[StringLiteralV2] = listInfo.labels.stringLiterals
                labels.size should be (2)
                labels.sorted should be (Seq(
                    StringLiteralV2(value = "Neue geänderte Liste", language = Some("de")),
                    StringLiteralV2(value = "Changed list", language = Some("en"))
                ).sorted)

                val comments = listInfo.comments.stringLiterals
                comments.size should be (2)
                comments.sorted should be (Seq(
                    StringLiteralV2(value = "Neuer Kommentar", language = Some("de")),
                    StringLiteralV2(value = "New comment", language = Some("en"))
                ).sorted)

            }

            "return a 'ForbiddenException' if the user changing the list is not project or system admin" in {
                responderManager ! ListInfoChangeRequestADM(
                    listIri = newListIri.get,
                    changeListRequest = ChangeListInfoApiRequestADM(
                        listIri = newListIri.get,
                        projectIri = IMAGES_PROJECT_IRI,
                        labels = Seq(
                            StringLiteralV2(value = "Neue geänderte Liste", language = Some("de")),
                            StringLiteralV2(value = "Changed list", language = Some("en"))
                        ),
                        comments = Seq(
                            StringLiteralV2(value = "Neuer Kommentar", language = Some("de")),
                            StringLiteralV2(value = "New comment", language = Some("en"))
                        )
                    ),
                    requestingUser = SharedTestDataADM.imagesUser02,
                    apiRequestID = UUID.randomUUID
                )

                expectMsg(Failure(ForbiddenException(LIST_CHANGE_PERMISSION_ERROR)))
            }

            "add child to list - to the root node" in {
                responderManager ! ListChildNodeCreateRequestADM(
                    parentNodeIri = newListIri.get,
                    createChildNodeRequest = CreateChildNodeApiRequestADM(
                        parentNodeIri = newListIri.get,
                        projectIri = IMAGES_PROJECT_IRI,
                        name = Some("first"),
                        labels = Seq(StringLiteralV2(value = "New First Child List Node Value", language = Some("en"))),
                        comments = Seq(StringLiteralV2(value = "New First Child List Node Comment", language = Some("en")))
                    ),
                    requestingUser = SharedTestDataADM.imagesUser01,
                    apiRequestID = UUID.randomUUID
                )

                val received: ListNodeInfoGetResponseADM = expectMsgType[ListNodeInfoGetResponseADM](timeout)
                val nodeInfo = received.nodeinfo

                // check correct node info
                val childNodeInfo = nodeInfo match {
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
                responderManager ! ListChildNodeCreateRequestADM(
                   parentNodeIri = newListIri.get,
                   createChildNodeRequest = CreateChildNodeApiRequestADM(
                       parentNodeIri = newListIri.get,
                       projectIri = IMAGES_PROJECT_IRI,
                       name = Some("second"),
                       labels = Seq(StringLiteralV2(value = "New Second Child List Node Value", language = Some("en"))),
                       comments = Seq(StringLiteralV2(value = "New Second Child List Node Comment", language = Some("en")))
                   ),
                   requestingUser = SharedTestDataADM.imagesUser01,
                   apiRequestID = UUID.randomUUID
               )

               val received: ListNodeInfoGetResponseADM = expectMsgType[ListNodeInfoGetResponseADM](timeout)
               val nodeInfo = received.nodeinfo

               // check correct node info
               val childNodeInfo = nodeInfo match {
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
               responderManager ! ListChildNodeCreateRequestADM(
                   parentNodeIri = secondChildIri.get,
                   createChildNodeRequest = CreateChildNodeApiRequestADM(
                       parentNodeIri = secondChildIri.get,
                       projectIri = IMAGES_PROJECT_IRI,
                       name = Some("third"),
                       labels = Seq(StringLiteralV2(value = "New Third Child List Node Value", language = Some("en"))),
                       comments = Seq(StringLiteralV2(value = "New Third Child List Node Comment", language = Some("en")))
                   ),
                   requestingUser = SharedTestDataADM.imagesUser01,
                   apiRequestID = UUID.randomUUID
               )

               val received: ListNodeInfoGetResponseADM = expectMsgType[ListNodeInfoGetResponseADM](timeout)
               val nodeInfo = received.nodeinfo

               // check correct node info
               val childNodeInfo = nodeInfo match {
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


           "change node order" ignore {

           }

           "delete node if not in use" ignore {

           }

        }
    }
}
