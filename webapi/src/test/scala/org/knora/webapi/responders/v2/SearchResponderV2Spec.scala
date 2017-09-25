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
import org.knora.webapi.messages.v2.responder.ReadResourcesSequenceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesGetRequestV2
import org.knora.webapi.messages.v2.responder.searchmessages.{ExtendedSearchGetRequestV2, FulltextSearchGetRequestV2}
import org.knora.webapi.responders.v2.ResourcesResponderV2Spec.userProfile
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.MessageUtil
import org.knora.webapi.{CoreSpec, LiveActorMaker, SharedAdminTestData}
import ResponseCheckerV2.compareReadResourcesSequenceV2Response
import org.knora.webapi.util.search._

import scala.concurrent.duration._

object SearchResponderV2Spec {
    private val userProfile = SharedAdminTestData.incunabulaProjectAdminUser

}

/**
  * Tests [[SearchResponderV2]].
  */
class SearchResponderV2Spec extends CoreSpec() with ImplicitSender {

    import SearchResponderV2Spec._

    // Construct the actors needed for this test.
    private val actorUnderTest = TestActorRef[SearchResponderV2]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    // The default timeout for receiving reply messages from actors.
    private val timeout = 10.seconds

    /*"Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(userProfile)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }*/

    "The search responder v2" should {

        "perform a fulltext search for 'Narr'" in {

            actorUnderTest ! FulltextSearchGetRequestV2("Narr", SharedAdminTestData.anonymousUser)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>


                    // TODO: since the order is not defined in the response of a fulltext search, sort received and expected resources by their Iri before comparing them
                    compareReadResourcesSequenceV2Response(
                        received = response.copy(
                            resources = response.resources.sortBy(_.resourceIri)
                        ),
                        expected = SearchResponderV2SpecFullData.fulltextSearchForNarr.copy(
                            resources = SearchResponderV2SpecFullData.fulltextSearchForNarr.resources.sortBy(_.resourceIri)
                        )
                    )
            }
        }

        "perform a fulltext search for 'Dinge'" in {

            actorUnderTest ! FulltextSearchGetRequestV2("Dinge", SharedAdminTestData.anythingUser1)

            // TODO: only one resource is expected, so order does not matter.
            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(received = response, expected = SearchResponderV2SpecFullData.fulltextSearchForDinge)
            }

        }

        "perform an extended search for books that have the title 'Zeitglöcklein des Lebens'" in {


            actorUnderTest ! ExtendedSearchGetRequestV2(SearchResponderV2SpecFullData.constructQueryForBooksWithTitleZeitgloecklein, SharedAdminTestData.anonymousUser)

            // extended search sorty by resource Iri by default if no order criterion is indicated
            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(received = response, expected = SearchResponderV2SpecFullData.booksWithTitleZeitgloeckleinResponse)
            }

        }

        "perform an extended search for books that do not have the title 'Zeitglöcklein des Lebens'" in {

            actorUnderTest ! ExtendedSearchGetRequestV2(SearchResponderV2SpecFullData.constructQueryForBooksWithoutTitleZeitgloecklein, SharedAdminTestData.anonymousUser)

            // extended search sorty by resource Iri by default if no order criterion is indicated
            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    // TODO: do better testing once JSON-LD can be converted back into case classes
                    assert(response.numberOfResources == 18, s"18 books were expected, but ${response.numberOfResources} given.")
            }

        }


    }


}