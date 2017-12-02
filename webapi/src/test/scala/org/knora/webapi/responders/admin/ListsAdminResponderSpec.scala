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

package org.knora.webapi.responders.admin

import akka.actor.Props
import akka.testkit._
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.SharedAdminTestData._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.responders._
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.MutableTestIri

import scala.concurrent.duration._


/**
  * Static data for testing [[ListsResponderADM]].
  */
object ListsAdminResponderSpec {
    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * Tests [[ListsResponderADM]].
  */
class ListsAdminResponderSpec extends CoreSpec(ListsAdminResponderSpec.config) with ImplicitSender {

    // Construct the actors needed for this test.
    private val actorUnderTest = TestActorRef[ListsResponderADM]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)

    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val log = akka.event.Logging(system, this.getClass())

    // The default timeout for receiving reply messages from actors.
    implicit val timeout = 5.seconds

    val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    // A test UserProfileV1.
    private val userProfile = SharedAdminTestData.incunabulaProjectAdminUser

    // A test UserDataV1.
    private val userData = userProfile.userData


    private val bigListInfo: ListInfo = SharedListsAdminTestData.bigListInfo

    private val summerNodeInfo: ListNodeInfo = SharedListsAdminTestData.summerNodeInfo

    private val otherTreeListInfo: ListInfo = SharedListsAdminTestData.otherTreeListInfo

    private val keywordChildNodes: Seq[ListNode] = Seq.empty[ListNode]

    private val bigListNodes: Seq[ListNode] = SharedListsAdminTestData.bigListNodes

    private val imageCategory = Seq.empty[ListNode]

    private val season = SharedListsAdminTestData.seasonListNodes

    private val nodePath = SharedListsAdminTestData.nodePath

    "Load test data " in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(userProfile)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The Lists Responder" when {

        "used to query information about lists" should {

            "return all lists" in {
                actorUnderTest ! ListsGetRequestADM(userProfile = userProfile)

                val received: ListsGetResponseADM = expectMsgType[ListsGetResponseADM](timeout)

                received.items.size should be(7)
            }

            "return all lists belonging to the images project" in {
                actorUnderTest ! ListsGetRequestADM(projectIri = Some(IMAGES_PROJECT_IRI), userProfile = userProfile)

                val received: ListsGetResponseADM = expectMsgType[ListsGetResponseADM](timeout)

                // log.debug("received: " + received)

                received.items.size should be(4)
            }

            "return all lists belonging to the anything project" in {
                actorUnderTest ! ListsGetRequestADM(projectIri = Some(ANYTHING_PROJECT_IRI), userProfile = userProfile)

                val received: ListsGetResponseADM = expectMsgType[ListsGetResponseADM](timeout)

                // log.debug("received: " + received)

                received.items.size should be(2)
            }

            "return basic list information (images list)" in {
                actorUnderTest ! ListInfoGetRequestADM(
                    iri = "http://rdfh.ch/lists/00FF/73d0ec0302",
                    userProfile = userProfile
                )

                val received: ListInfoGetResponseADM = expectMsgType[ListInfoGetResponseADM](timeout)

                // log.debug("returned basic keyword list information: {}", MessageUtil.toSource(received.items.head))

                received.listinfo.sorted should be(bigListInfo.sorted)
            }

            "return basic list information (anything list)" in {
                actorUnderTest ! ListInfoGetRequestADM(
                    iri = "http://data.knora.org/anything/otherTreeList",
                    userProfile = userProfile
                )

                val received: ListInfoGetResponseADM = expectMsgType[ListInfoGetResponseADM](timeout)

                // log.debug("returned basic keyword list information: {}", MessageUtil.toSource(received.items.head))

                received.listinfo.sorted should be(otherTreeListInfo.sorted)
            }

            "return basic node information (images list - sommer)" in {
                actorUnderTest ! ListNodeInfoGetRequestADM(
                    iri = "http://rdfh.ch/lists/00FF/526f26ed04",
                    userProfile = userProfile
                )

                val received: ListNodeInfoGetResponseADM = expectMsgType[ListNodeInfoGetResponseADM](timeout)

                // log.debug("returned basic keyword list information: {}", MessageUtil.toSource(received.items.head))

                received.nodeinfo.sorted should be(summerNodeInfo.sorted)
            }

            "return a full list response" in {
                actorUnderTest ! ListGetRequestADM(
                    iri = "http://rdfh.ch/lists/00FF/73d0ec0302",
                    userProfile = userProfile
                )

                val received: ListGetResponseADM = expectMsgType[ListGetResponseADM](timeout)

                // log.debug("returned whole keyword list: {}", MessageUtil.toSource(received.items.head))

                received.list.listinfo.sorted should be(bigListInfo.sorted)

                received.list.children.map(_.sorted) should be(bigListNodes.map(_.sorted))
            }
        }

        "used to modify lists" should {

            val newListIri = new MutableTestIri

            "create a list" ignore {

            }

            "update basic list information" ignore {

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
