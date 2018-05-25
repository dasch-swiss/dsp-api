/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import akka.actor.Props
import akka.actor.Status.Failure
import akka.testkit._
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.SharedTestDataV1._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK, StringLiteralV2}
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.responders._
import org.knora.webapi.responders.admin.ListsResponderADM._
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
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

    // Construct the actors needed for this test.
    private val actorUnderTest = TestActorRef[ListsResponderADM]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)

    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val log = akka.event.Logging(system, this.getClass())

    // The default timeout for receiving reply messages from actors.
    implicit val timeout = 5.seconds

    val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    private val bigListInfo: ListInfoADM = SharedListsTestDataADM.bigListInfo

    private val summerNodeInfo: ListNodeInfoADM = SharedListsTestDataADM.summerNodeInfo

    private val otherTreeListInfo: ListInfoADM = SharedListsTestDataADM.otherTreeListInfo

    private val keywordChildNodes: Seq[ListNodeADM] = Seq.empty[ListNodeADM]

    private val bigListNodes: Seq[ListNodeADM] = SharedListsTestDataADM.bigListNodes

    private val imageCategory = Seq.empty[ListNodeADM]

    private val season = SharedListsTestDataADM.seasonListNodes

    private val nodePath = SharedListsTestDataADM.nodePath

    "Load test data " in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(SharedTestDataADM.rootUser)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The Lists Responder" when {

        "used to query information about lists" should {

            "return all lists" in {
                actorUnderTest ! ListsGetRequestADM(requestingUser = SharedTestDataADM.imagesUser01)

                val received: ListsGetResponseADM = expectMsgType[ListsGetResponseADM](timeout)

                received.lists.size should be(7)
            }

            "return all lists belonging to the images project" in {
                actorUnderTest ! ListsGetRequestADM(projectIri = Some(IMAGES_PROJECT_IRI), requestingUser = SharedTestDataADM.imagesUser01)

                val received: ListsGetResponseADM = expectMsgType[ListsGetResponseADM](timeout)

                // log.debug("received: " + received)

                received.lists.size should be(4)
            }

            "return all lists belonging to the anything project" in {
                actorUnderTest ! ListsGetRequestADM(projectIri = Some(ANYTHING_PROJECT_IRI), requestingUser = SharedTestDataADM.imagesUser01)

                val received: ListsGetResponseADM = expectMsgType[ListsGetResponseADM](timeout)

                // log.debug("received: " + received)

                received.lists.size should be(2)
            }

            "return basic list information (images list)" in {
                actorUnderTest ! ListInfoGetRequestADM(
                    iri = "http://rdfh.ch/lists/00FF/73d0ec0302",
                    requestingUser = SharedTestDataADM.imagesUser01
                )

                val received: ListInfoGetResponseADM = expectMsgType[ListInfoGetResponseADM](timeout)

                // log.debug("returned basic keyword list information: {}", MessageUtil.toSource(received.items.head))

                received.listinfo.sorted should be(bigListInfo.sorted)
            }

            "return basic list information (anything list)" in {
                actorUnderTest ! ListInfoGetRequestADM(
                    iri = "http://rdfh.ch/lists/0001/otherTreeList",
                    requestingUser = SharedTestDataADM.imagesUser01
                )

                val received: ListInfoGetResponseADM = expectMsgType[ListInfoGetResponseADM](timeout)

                // log.debug("returned basic keyword list information: {}", MessageUtil.toSource(received.items.head))

                received.listinfo.sorted should be(otherTreeListInfo.sorted)
            }

            "return basic node information (images list - sommer)" in {
                actorUnderTest ! ListNodeInfoGetRequestADM(
                    iri = "http://rdfh.ch/lists/00FF/526f26ed04",
                    requestingUser = SharedTestDataADM.imagesUser01
                )

                val received: ListNodeInfoGetResponseADM = expectMsgType[ListNodeInfoGetResponseADM](timeout)

                // log.debug("returned basic keyword list information: {}", MessageUtil.toSource(received.items.head))

                received.nodeinfo.sorted should be(summerNodeInfo.sorted)
            }

            "return a full list response" in {
                actorUnderTest ! ListGetRequestADM(
                    iri = "http://rdfh.ch/lists/00FF/73d0ec0302",
                    requestingUser = SharedTestDataADM.imagesUser01
                )

                val received: ListGetResponseADM = expectMsgType[ListGetResponseADM](timeout)

                // log.debug("returned whole keyword list: {}", MessageUtil.toSource(received.items.head))

                received.list.listinfo.sorted should be(bigListInfo.sorted)

                received.list.children.map(_.sorted) should be(bigListNodes.map(_.sorted))
            }
        }

        "used to modify lists" should {

            val newListIri = new MutableTestIri

            "create a list" in {
                actorUnderTest ! ListCreateRequestADM(
                    createListRequest = CreateListApiRequestADM(
                        projectIri = IMAGES_PROJECT_IRI,
                        labels = Seq(StringLiteralV2(value = "Neue Liste", language = Some("de"))),
                        comments = Seq.empty[StringLiteralV2]
                    ),
                    requestingUser = SharedTestDataADM.imagesUser01,
                    apiRequestID = UUID.randomUUID
                )

                val received: ListGetResponseADM = expectMsgType[ListGetResponseADM](timeout)

                val listInfo = received.list.listinfo
                listInfo.projectIri should be (IMAGES_PROJECT_IRI)

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
                actorUnderTest ! ListCreateRequestADM(
                    createListRequest = CreateListApiRequestADM(
                        projectIri = IMAGES_PROJECT_IRI,
                        labels = Seq(StringLiteralV2(value = "Neue Liste", language = Some("de"))),
                        comments = Seq.empty[StringLiteralV2]
                    ),
                    requestingUser = SharedTestDataADM.imagesUser02,
                    apiRequestID = UUID.randomUUID
                )

                expectMsg(Failure(ForbiddenException(LIST_CREATE_PERMISSION_ERROR)))
            }

            "update basic list information" in {
                actorUnderTest ! ListInfoChangeRequestADM(
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
                actorUnderTest ! ListInfoChangeRequestADM(
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
