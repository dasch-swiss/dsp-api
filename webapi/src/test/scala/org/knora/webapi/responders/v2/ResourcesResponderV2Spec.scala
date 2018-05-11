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
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.responders.v2.ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{CoreSpec, KnoraSystemInstances, LiveActorMaker, SharedTestDataADM}

import scala.concurrent.duration._

object ResourcesResponderV2Spec {
    private val userProfile = SharedTestDataADM.incunabulaProjectAdminUser
}

/**
  * Tests [[ResourcesResponderV2]].
  */
class ResourcesResponderV2Spec extends CoreSpec() with ImplicitSender {
    import ResourcesResponderV2Spec._

    // Construct the actors needed for this test.
    private val actorUnderTest = TestActorRef[ResourcesResponderV2]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    private val resourcesResponderV2SpecFullData = new ResourcesResponderV2SpecFullData

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

    "The resources responder v2" should {
        "return a full description of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://rdfh.ch/c5058f3a"), userProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 => compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein, received = response)
            }

        }

        "return a full description of the book 'Reise ins Heilige Land' in the Incunabula test data" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://rdfh.ch/2a6221216701"), userProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 => compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForReise, received = response)
            }

        }

        "return two full description of the book 'Zeitglöcklein des Lebens und Leidens Christi' and the book 'Reise ins Heilige Land' in the Incunabula test data" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://rdfh.ch/c5058f3a", "http://rdfh.ch/2a6221216701"), userProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 => compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloeckleinAndReise, received = response)
            }

        }

        "return two full description of the 'Reise ins Heilige Land' and the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data (inversed order)" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://rdfh.ch/2a6221216701", "http://rdfh.ch/c5058f3a"), userProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 => compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForReiseInversedAndZeitgloeckleinInversedOrder, received = response)
            }

        }

        "return two full description of the book 'Zeitglöcklein des Lebens und Leidens Christi' and the book 'Reise ins Heilige Land' in the Incunabula test data providing redundant resource Iris" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://rdfh.ch/c5058f3a", "http://rdfh.ch/c5058f3a", "http://rdfh.ch/2a6221216701"), userProfile)

            // the redundant Iri should be ignored (distinct)
            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 => compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloeckleinAndReise, received = response)
            }

        }

    }


}