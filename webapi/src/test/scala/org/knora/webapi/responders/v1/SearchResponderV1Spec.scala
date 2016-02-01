/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.responders.v1

import akka.actor.Props
import akka.testkit._
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.searchmessages._
import org.knora.webapi.messages.v1respondermessages.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.messages.v1respondermessages.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.responders._
import org.knora.webapi.store._
import org.knora.webapi.util.MessageUtil

import scala.concurrent.duration._

/**
  * Static data for testing [[SearchResponderV1]].
  */
object SearchResponderV1Spec {

    // A test UserDataV1.
    private val userData = UserDataV1(
        email = Some("test@test.ch"),
        lastname = Some("Test"),
        firstname = Some("User"),
        username = Some("testuser"),
        token = None,
        user_id = Some("http://data.knora.org/users/b83acc5f05"),
        lang = "de"
    )

    // A test UserProfileV1.
    private val userProfile = UserProfileV1(
        projects = Vector("http://data.knora.org/projects/77275339"),
        groups = Nil,
        userData = userData
    )
}


/**
  * Tests [[SearchResponderV1]].
  */
class SearchResponderV1Spec extends CoreSpec() with ImplicitSender {

    // Construct the actors needed for this test.
    private val actorUnderTest = TestActorRef[SearchResponderV1]
    private val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula")
    )

    // The default timeout for receiving reply messages from actors.
    private val timeout = 30.seconds

    // An expected response consisting of two books with the title "Zeitglöcklein des Lebens und Leidens Christi".
    private val twoZeitglöckleinBooksResponse = SearchGetResponseV1(
        thumb_max = SearchPreviewDimensionsV1(
            ny = 32,
            nx = 32
        ),
        paging = Vector(SearchResultPage(
            show_nrows = 2,
            start_at = 0,
            current = true
        )),
        nhits = "2",
        subjects = Vector(
            SearchResultRowV1(
                rights = Some(6),
                preview_ny = 32,
                preview_nx = 32,
                value = Vector(
                    "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
                    "Zeitgl\u00F6cklein des Lebens und Leidens Christi"
                ),
                valuelabel = Vector(
                    "Label",
                    "Titel"
                ),
                valuetype_id = Vector(
                    "http://www.w3.org/2000/01/rdf-schema#label",
                    "http://www.knora.org/ontology/knora-base#TextValue"
                ),
                iconlabel = Some("Buch"),
                icontitle = Some("Buch"),
                iconsrc = Some("book.gif"),
                preview_path = "book.gif",
                obj_id = "http://data.knora.org/c5058f3a"
            ),
            SearchResultRowV1(
                rights = Some(6),
                preview_ny = 32,
                preview_nx = 32,
                value = Vector(
                    "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
                    "Zeitgl\u00F6cklein des Lebens und Leidens Christi"
                ),
                valuelabel = Vector(
                    "Label",
                    "Titel"
                ),
                valuetype_id = Vector(
                    "http://www.w3.org/2000/01/rdf-schema#label",
                    "http://www.knora.org/ontology/knora-base#TextValue"
                ),
                iconlabel = Some("Buch"),
                icontitle = Some("Buch"),
                iconsrc = Some("book.gif"),
                preview_path = "book.gif",
                obj_id = "http://data.knora.org/ff17e5ef9601"
            )
        ),
        userdata = SearchResponderV1Spec.userData
    )

    val bertholdResponse = SearchGetResponseV1(
        userdata = SearchResponderV1Spec.userData,
        subjects = Vector(
            SearchResultRowV1(
                obj_id = "http://data.knora.org/c5058f3a",
                preview_path = "book.gif",
                iconsrc = Some("http://localhost:3333/v1/assets/book.gif"),
                icontitle = Some("Buch"),
                iconlabel = Some("Buch"),
                valuetype_id = Vector("http://www.w3.org/2000/01/rdf-schema#label", "http://www.knora.org/ontology/knora-base#TextValue"),
                valuelabel = Vector("Label", "Creator"),
                value = Vector("Zeitglöcklein des Lebens und Leidens Christi", "Berthold, der Bruder"),
                preview_nx = 32,
                preview_ny = 32,
                rights = Some(6)
            ),
            SearchResultRowV1(
                obj_id = "http://data.knora.org/ff17e5ef9601",
                preview_path = "book.gif",
                iconsrc = Some("http://localhost:3333/v1/assets/book.gif"),
                icontitle = Some("Buch"),
                iconlabel = Some("Buch"),
                valuetype_id = Vector("http://www.w3.org/2000/01/rdf-schema#label", "http://www.knora.org/ontology/knora-base#TextValue"),
                valuelabel = Vector("Label", "Creator"),
                value = Vector("Zeitglöcklein des Lebens und Leidens Christi", "Berthold, der Bruder"),
                preview_nx = 32,
                preview_ny = 32,
                rights = Some(6)
            )
        ),
        nhits = "2",
        paging = Vector(SearchResultPage(current = true, 0, 2)),
        thumb_max = SearchPreviewDimensionsV1(32, 32)
    )

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())
    }

    "The search responder" should {
        "return 3 results when we do a simple search for the word 'Zeitglöcklein' in the Incunabula test data" in {
            // http://localhost:3333/v1/search/Zeitglöcklein?searchtype=fulltext
            actorUnderTest ! FulltextSearchGetRequestV1(
                searchValue = "Zeitglöcklein",
                userProfile = SearchResponderV1Spec.userProfile,
                startAt = 0,
                showNRows = 25
            )

            expectMsgPF(timeout) {
                case response: SearchGetResponseV1 if response.subjects.size == 3 => ()
            }
        }

        "return 2 results when we do a simple search for the words 'Zeitglöcklein' and 'Lebens' in the Incunabula test data" in {
            // http://localhost:3333/v1/search/Zeitglöcklein%20Lebens?searchtype=fulltext
            actorUnderTest ! FulltextSearchGetRequestV1(
                searchValue = "Zeitglöcklein Lebens",
                userProfile = SearchResponderV1Spec.userProfile,
                startAt = 0,
                showNRows = 25
            )

            expectMsgPF(timeout) {
                case response: SearchGetResponseV1 if response.subjects.size == 2 => ()
            }
        }

        "return 0 results when we do a simple search for the words 'Zeitglöcklein' for the type incunabula:page in the Incunabula test data" in {
            // http://localhost:3333/v1/search/Zeitglöcklein%20Lebens?searchtype=fulltext&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page
            actorUnderTest ! FulltextSearchGetRequestV1(
                searchValue = "Zeitglöcklein Lebens",
                userProfile = SearchResponderV1Spec.userProfile,
                startAt = 0,
                showNRows = 25,
                filterByRestype = Some("http://www.knora.org/ontology/incunabula#page")
            )

            expectMsgPF(timeout) {
                case response: SearchGetResponseV1 if response.subjects.isEmpty => ()
            }
        }

        "return 1 result when we do a simple search for the word 'Orationes' (the rdfs:label and title of a book) in the Incunabula test data" in {
            // http://localhost:3333/v1/search/Orationes?searchtype=fulltext
            // TODO: Fuseki and GraphDB actually return different results here: Fuseki returns the match for the resource label, while GraphDB returns
            // the one for the text value. Both appear to be correct: we are using SAMPLE, so each triplestore is returning a different random result.
            // Try to find another approach so that they return the same result. Also, GraphDB returns the wrong label (again, because it seems to be
            // selecting a random one). Instead of getting labels from the search query, the search responder can ask the ontology responder for them.
            actorUnderTest ! FulltextSearchGetRequestV1(
                searchValue = "Orationes",
                userProfile = SearchResponderV1Spec.userProfile,
                startAt = 0,
                showNRows = 25
            )

            expectMsgPF(timeout) {
                case response: SearchGetResponseV1 if response.subjects.size == 1 => ()
            }
        }

        "return 2 results when we do a simple search for the words 'Berthold and Bruder' in the Incunabula test data" in {
            // http://localhost:3333/v1/search/Berthold%20Bruder?searchtype=fulltext
            actorUnderTest ! FulltextSearchGetRequestV1(
                searchValue = "Berthold Bruder",
                userProfile = SearchResponderV1Spec.userProfile,
                startAt = 0,
                showNRows = 25
            )

            expectMsg(timeout, bertholdResponse)
        }

        "return 2 books with the title 'Zeitglöcklein des Lebens und Leidens Christi' when we search for book titles containing the string 'Zeitglöcklein' (using a regular expression) in the Incunabula test data" in {
            // http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=LIKE&searchval=Zeitglöcklein
            actorUnderTest ! ExtendedSearchGetRequestV1(
                userProfile = SearchResponderV1Spec.userProfile,
                searchValue = Vector("Zeitglöcklein"),
                compareProps = Vector(SearchComparisonOperatorV1.LIKE),
                propertyIri = Vector("http://www.knora.org/ontology/incunabula#title"),
                filterByRestype = Some("http://www.knora.org/ontology/incunabula#book"),
                startAt = 0,
                showNRows = 25
            )

            expectMsg(timeout, twoZeitglöckleinBooksResponse)
        }


        "return 2 books with the title 'Zeitglöcklein des Lebens und Leidens Christi' when we search for book titles containing the word 'Zeitglöcklein' (using the full-text search index) in the Incunabula test data" in {
            // http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=MATCH&searchval=Zeitglöcklein
            actorUnderTest ! ExtendedSearchGetRequestV1(
                userProfile = SearchResponderV1Spec.userProfile,
                searchValue = Vector("Zeitglöcklein"),
                compareProps = Vector(SearchComparisonOperatorV1.MATCH),
                propertyIri = Vector("http://www.knora.org/ontology/incunabula#title"),
                filterByRestype = Some("http://www.knora.org/ontology/incunabula#book"),
                startAt = 0,
                showNRows = 25
            )

            expectMsg(timeout, twoZeitglöckleinBooksResponse)
        }

        "return 1 books with the title 'Zeitglöcklein des Lebens und Leidens Christi' that was published in 1490 (Julian Calendar) when we search for book titles containing the word 'Zeitglöcklein' (using the full-text search index) in the Incunabula test data" in {
            // http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=MATCH&searchval=Zeitglöcklein&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23pubdate&compop=EQ&searchval=
            actorUnderTest ! ExtendedSearchGetRequestV1(
                userProfile = SearchResponderV1Spec.userProfile,
                searchValue = Vector("Zeitglöcklein", "JULIAN:1490"),
                compareProps = Vector(SearchComparisonOperatorV1.MATCH, SearchComparisonOperatorV1.EQ),
                propertyIri = Vector("http://www.knora.org/ontology/incunabula#title", "http://www.knora.org/ontology/incunabula#pubdate"),
                filterByRestype = Some("http://www.knora.org/ontology/incunabula#book"),
                startAt = 0,
                showNRows = 25
            )

            expectMsgPF(timeout) {
                case response: SearchGetResponseV1 => response.subjects.size should ===(1)
            }
        }

        "return 2 books with the title 'Zeitglöcklein des Lebens und Leidens Christi' when we search for book titles containing the word 'Lebens' but not containing the word 'walfart' (using MATCH BOOLEAN) in the Incunabula test data" in {
            // http://localhost:3333/v1/search/?searchtype=extended&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=MATCH_BOOLEAN&searchval=%2BLebens+-walfart&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book
            actorUnderTest ! ExtendedSearchGetRequestV1(
                userProfile = SearchResponderV1Spec.userProfile,
                searchValue = Vector("+Lebens -walfart"),
                compareProps = Vector(SearchComparisonOperatorV1.MATCH_BOOLEAN),
                propertyIri = Vector("http://www.knora.org/ontology/incunabula#title"),
                filterByRestype = Some("http://www.knora.org/ontology/incunabula#book"),
                startAt = 0,
                showNRows = 25
            )

            expectMsg(timeout, twoZeitglöckleinBooksResponse)
        }

        /*

        Previously we used a FILTER NOT EXISTS statement here, but it did not return a value for ?anyLiteral
        So now we use just a negated regex
        Problem: if a resource has two instances of the same property and one of them matches and the other does not,
        it will be contained in the search results (now we have 18 instead of 17 books returned)

        */

        "return 18 books when we search for book titles that do not include the string 'Zeitglöcklein' (using a regular expression) in the Incunabula test data" in {
            // http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=!LIKE&searchval=Zeitgl%C3%B6cklein
            actorUnderTest ! ExtendedSearchGetRequestV1(
                userProfile = SearchResponderV1Spec.userProfile,
                searchValue = Vector("Zeitglöcklein"),
                compareProps = Vector(SearchComparisonOperatorV1.NOT_LIKE),
                propertyIri = Vector("http://www.knora.org/ontology/incunabula#title"),
                filterByRestype = Some("http://www.knora.org/ontology/incunabula#book"),
                startAt = 0,
                showNRows = 25
            )

            expectMsgPF(timeout) {
                case response: SearchGetResponseV1 => response.subjects.size should ===(18)
            }
        }

        "return 2 books with the title 'Zeitglöcklein des Lebens und Leidens Christi' when we search for exactly that book title in the Incunabula test data" in {
            // http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=EQ&searchval=Zeitgl%C3%B6cklein%20des%20Lebens%20und%20Leidens%20Christi
            actorUnderTest ! ExtendedSearchGetRequestV1(
                userProfile = SearchResponderV1Spec.userProfile,
                searchValue = Vector("Zeitglöcklein des Lebens und Leidens Christi"),
                compareProps = Vector(SearchComparisonOperatorV1.EQ),
                propertyIri = Vector("http://www.knora.org/ontology/incunabula#title"),
                filterByRestype = Some("http://www.knora.org/ontology/incunabula#book"),
                startAt = 0,
                showNRows = 25
            )

            expectMsg(timeout, twoZeitglöckleinBooksResponse)
        }

        "return 18 books when we search for all books that have a title that is not exactly 'Zeitglöcklein des Lebens und Leidens Christi' (although they may have another title that is) in the Incunabula test data" in {
            // http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=!EQ&searchval=Zeitgl%C3%B6cklein%20des%20Lebens%20und%20Leidens%20Christi
            actorUnderTest ! ExtendedSearchGetRequestV1(
                userProfile = SearchResponderV1Spec.userProfile,
                searchValue = Vector("Zeitglöcklein des Lebens und Leidens Christi"),
                compareProps = Vector(SearchComparisonOperatorV1.NOT_EQ),
                propertyIri = Vector("http://www.knora.org/ontology/incunabula#title"),
                filterByRestype = Some("http://www.knora.org/ontology/incunabula#book"),
                startAt = 0,
                showNRows = 25
            )

            expectMsgPF(timeout) {
                case response: SearchGetResponseV1 => response.subjects.size should ===(18)
            }
        }

        "return 19 books when we search for all books that have a title in the Incunabula test data" in {
            // http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=EXISTS&searchval
            actorUnderTest ! ExtendedSearchGetRequestV1(
                userProfile = SearchResponderV1Spec.userProfile,
                searchValue = Vector(""),
                compareProps = Vector(SearchComparisonOperatorV1.EXISTS),
                propertyIri = Vector("http://www.knora.org/ontology/incunabula#title"),
                filterByRestype = Some("http://www.knora.org/ontology/incunabula#book"),
                startAt = 0,
                showNRows = 25
            )

            expectMsgPF(timeout) {
                case response: SearchGetResponseV1 => response.subjects.size should ===(19)
            }
        }

        "return 19 pages when we search for all pages that have a sequence number of 1 in the Incunabula test data" in {
            // http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23seqnum&compop=EQ&searchval=1
            actorUnderTest ! ExtendedSearchGetRequestV1(
                userProfile = SearchResponderV1Spec.userProfile,
                searchValue = Vector("1"),
                compareProps = Vector(SearchComparisonOperatorV1.EQ),
                propertyIri = Vector("http://www.knora.org/ontology/incunabula#seqnum"),
                filterByRestype = Some("http://www.knora.org/ontology/incunabula#page"),
                startAt = 0,
                showNRows = 25
            )

            expectMsgPF(timeout) {
                case response: SearchGetResponseV1 => response.subjects.size should ===(19)
            }
        }

        "return 79 pages when we search for all pages that have a sequence number greater than 450 in the Incunabula test data" in {
            // http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23seqnum&compop=GT&searchval=450
            actorUnderTest ! ExtendedSearchGetRequestV1(
                userProfile = SearchResponderV1Spec.userProfile,
                searchValue = Vector("450"),
                compareProps = Vector(SearchComparisonOperatorV1.GT),
                propertyIri = Vector("http://www.knora.org/ontology/incunabula#seqnum"),
                filterByRestype = Some("http://www.knora.org/ontology/incunabula#page"),
                startAt = 0,
                showNRows = 100
            )

            expectMsgPF(timeout) {
                case response: SearchGetResponseV1 => response.subjects.size should ===(79)
            }
        }

        "return 2 books when we search for all books that were published in January 1495 (Julian date) in the Incunabula test data" in {
            // http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23seqnum&compop=EQ&searchval=1
            actorUnderTest ! ExtendedSearchGetRequestV1(
                userProfile = SearchResponderV1Spec.userProfile,
                searchValue = Vector("JULIAN:1495-01"),
                compareProps = Vector(SearchComparisonOperatorV1.EQ),
                propertyIri = Vector("http://www.knora.org/ontology/incunabula#pubdate"),
                filterByRestype = Some("http://www.knora.org/ontology/incunabula#book"),
                startAt = 0,
                showNRows = 25
            )

            expectMsgPF(timeout) {
                case response: SearchGetResponseV1 => response.subjects.size should ===(2)
            }
        }

        "return 5 books when we search for all books whose publication date is greater than or equal to January 1495 (Julian date) in the Incunabula test data" in {
            // http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23pubdate&compop=GT_EQ&searchval=JULIAN:1495-01
            actorUnderTest ! ExtendedSearchGetRequestV1(
                userProfile = SearchResponderV1Spec.userProfile,
                searchValue = Vector("JULIAN:1495-01"),
                compareProps = Vector(SearchComparisonOperatorV1.GT_EQ),
                propertyIri = Vector("http://www.knora.org/ontology/incunabula#pubdate"),
                filterByRestype = Some("http://www.knora.org/ontology/incunabula#book"),
                startAt = 0,
                showNRows = 25
            )

            expectMsgPF(timeout) {
                case response: SearchGetResponseV1 => response.subjects.size should ===(5)
            }
        }

        "return 13 books when we search for all books whose publication date is less than or equal to December 1495 (Julian date) in the Incunabula test data" in {
            // http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23pubdate&compop=LT_EQ&searchval=JULIAN:1495-12
            actorUnderTest ! ExtendedSearchGetRequestV1(
                userProfile = SearchResponderV1Spec.userProfile,
                searchValue = Vector("JULIAN:1495-12"),
                compareProps = Vector(SearchComparisonOperatorV1.LT_EQ),
                propertyIri = Vector("http://www.knora.org/ontology/incunabula#pubdate"),
                filterByRestype = Some("http://www.knora.org/ontology/incunabula#book"),
                startAt = 0,
                showNRows = 25
            )

            expectMsgPF(timeout) {
                case response: SearchGetResponseV1 => response.subjects.size should ===(13)
            }
        }

        "return all the pages that are part of Zeitglöcklein des Lebens" in {
            // http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23partOf&compop=EQ&searchval=http%3A%2F%2Fdata.knora.org%2Fc5058f3a
            actorUnderTest ! ExtendedSearchGetRequestV1(
                userProfile = SearchResponderV1Spec.userProfile,
                searchValue = Vector("http://data.knora.org/c5058f3a"),
                compareProps = Vector(SearchComparisonOperatorV1.EQ),
                propertyIri = Vector("http://www.knora.org/ontology/incunabula#partOf"),
                filterByRestype = Some("http://www.knora.org/ontology/incunabula#page"),
                startAt = 0,
                showNRows = 500
            )

            expectMsgPF(timeout) {
                case response: SearchGetResponseV1 => response.subjects.size should ===(402)
            }
        }

        "return all the pages that have a sequence number of 1 and are part of some book" in {
            // http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23seqnum&compop=EQ&searchval=1&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23partOf&compop=EXISTS&searchval=
            actorUnderTest ! ExtendedSearchGetRequestV1(
                userProfile = SearchResponderV1Spec.userProfile,
                searchValue = Vector("1", ""),
                compareProps = Vector(SearchComparisonOperatorV1.EQ, SearchComparisonOperatorV1.EXISTS),
                propertyIri = Vector("http://www.knora.org/ontology/incunabula#seqnum", "http://www.knora.org/ontology/incunabula#partOf"),
                filterByRestype = Some("http://www.knora.org/ontology/incunabula#page"),
                startAt = 0,
                showNRows = 25
            )

            expectMsgPF(timeout) {
                case response: SearchGetResponseV1 => response.subjects.size should ===(19)
            }
        }

        "return all the pages that are part of Zeitglöcklein des Lebens and have a seqnum" in {
            // http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23partOf&compop=EQ&searchval=http%3A%2F%2Fdata.knora.org%2Fc5058f3a&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23seqnum&compop=EXISTS&searchval=
            actorUnderTest ! ExtendedSearchGetRequestV1(
                userProfile = SearchResponderV1Spec.userProfile,
                searchValue = Vector("http://data.knora.org/c5058f3a", ""),
                compareProps = Vector(SearchComparisonOperatorV1.EQ, SearchComparisonOperatorV1.EXISTS),
                propertyIri = Vector("http://www.knora.org/ontology/incunabula#partOf", "http://www.knora.org/ontology/incunabula#partOf"),
                filterByRestype = Some("http://www.knora.org/ontology/incunabula#page"),
                startAt = 0,
                showNRows = 500
            )

            expectMsgPF(timeout) {
                case response: SearchGetResponseV1 => response.subjects.size should ===(402)
            }

        }
    }
}
