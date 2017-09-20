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

package org.knora.webapi.responders.v2

import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActorRef}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v2.responder.{ReadResourceV2, ReadResourcesSequenceV2, ReadValueV2}
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesGetRequestV2
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.MessageUtil
import org.knora.webapi.{CoreSpec, IRI, LiveActorMaker, SharedAdminTestData}

import scala.collection.immutable.ListMap
import scala.concurrent.duration._

object ResourcesResponderV2Spec {
    private val userProfile = SharedAdminTestData.incunabulaProjectAdminUser
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

    private val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    // The default timeout for receiving reply messages from actors.
    private val timeout = 10.seconds

    /**
      * Compares the response to a full resource request with the expected response.
      *
      * @param received the response returned by the resource responder.
      * @param expected the expected response.
      */
    private def compareResourceFullResponses(received: ReadResourcesSequenceV2, expected: ReadResourcesSequenceV2): Unit = {
        assert(received.numberOfResources == expected.numberOfResources, "number of resources are not equal")

        // compare the resources one by one: resources have to returned in the correct order
        received.resources.zip(expected.resources).map {
            case (receivedResource: ReadResourceV2, expectedResource: ReadResourceV2) =>

                // compare resource information
                assert(receivedResource.resourceIri == expectedResource.resourceIri, "resource Iri does not match")
                assert(receivedResource.label == expectedResource.label, "label does not match")
                assert(receivedResource.resourceClass == expectedResource.resourceClass, "resource class does not match")

                // compare the properties
                // convert Map to a sequence of tuples and sort by property Iri)
                receivedResource.values.toSeq.sortBy(_._1).zip(expectedResource.values.toSeq.sortBy(_._1)).map {
                    case ((receivedPropIri: IRI, receivedPropValues: Seq[ReadValueV2]), (expectedPropIri: IRI, expectedPropValues: Seq[ReadValueV2])) =>

                        assert(receivedPropIri == expectedPropIri)

                        assert(receivedPropValues.sortBy(_.valueIri) == expectedPropValues.sortBy(_.valueIri))

                }

        }
    }

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(userProfile)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The resources responder v2" should {
        "return a full description of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://data.knora.org/c5058f3a"), userProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 => compareResourceFullResponses(received = response, expected = ResourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein)
            }

        }

        "return two full description of the book 'Zeitglöcklein des Lebens und Leidens Christi' and the book 'Reise ins Heilige Land' in the Incunabula test data" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://data.knora.org/c5058f3a", "http://data.knora.org/2a6221216701"), userProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 => compareResourceFullResponses(received = response, expected = ResourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloeckleinAndReise)
            }

        }

        "return two full description of the 'Reise ins Heilige Land' and the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data (inversed order)" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://data.knora.org/2a6221216701", "http://data.knora.org/c5058f3a"), userProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 => compareResourceFullResponses(received = response, expected = ResourcesResponderV2SpecFullData.expectedFullResourceResponseForReiseInversedAndZeitgloeckleinInversedOrder)
            }

        }

        "return two full description of the book 'Zeitglöcklein des Lebens und Leidens Christi' and the book 'Reise ins Heilige Land' in the Incunabula test data providing redundant resource Iris" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://data.knora.org/c5058f3a", "http://data.knora.org/c5058f3a", "http://data.knora.org/2a6221216701"), userProfile)

            // the redundant Iri should be ignored (distinct)
            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 => compareResourceFullResponses(received = response, expected = ResourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloeckleinAndReise)
            }

        }

    }


}