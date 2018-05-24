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

package org.knora.webapi.responders.v2

import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActorRef}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.listsmessages.{ListsGetRequestV2, ListGetResponseV2, NodeGetRequestV2, NodeGetResponseV2}
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{CoreSpec, KnoraSystemInstances, LiveActorMaker, SharedTestDataADM}

import scala.concurrent.duration._

object ListsResponderV2Spec {
    private val userProfile = SharedTestDataADM.anythingUser2
}

/**
  * Tests [[ListsResponderV2]].
  */
class ListsResponderV2Spec extends CoreSpec() with ImplicitSender {

    import ListsResponderV2Spec._

    // Construct the actors needed for this test.
    private val actorUnderTest = TestActorRef[ListsResponderV2]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    private val listsResponderV2SpecFullData = new ListsResponderV2SpecFullData

    private val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    // The default timeout for receiving reply messages from actors.
    private val timeout = 10.seconds


    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser)
        expectMsgType[SuccessResponseV2](10.seconds)
    }

    "The lists responder v2" should {

        "return a list" in {

            actorUnderTest ! ListsGetRequestV2("http://rdfh.ch/lists/0001/treeList", userProfile)

            expectMsgPF(timeout) {
                case response: ListGetResponseV2 =>
                    assert(response == listsResponderV2SpecFullData.treeList)

            }
        }

        "return a node" in {

            actorUnderTest ! NodeGetRequestV2("http://rdfh.ch/lists/0001/treeList11", userProfile)

            expectMsgPF(timeout) {
                case response: NodeGetResponseV2 =>
                    assert(response == listsResponderV2SpecFullData.treeNode)
            }

        }


    }


}