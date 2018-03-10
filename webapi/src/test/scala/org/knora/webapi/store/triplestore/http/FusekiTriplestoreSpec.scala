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

/*
package org.knora.rapier.store.triplestore.http

import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActorRef}
import com.typesafe.config.ConfigFactory
import org.knora.rapier.store._
import org.knora.rapier.{LiveActorMaker, CoreSpec}
import org.knora.rapier.messages.Hello
import org.knora.rapier.messages.v1respondermessages.triplestoremessages._
import org.knora.rapier.store.triplestore.TriplestoreManagerActor

import scala.concurrent.duration._


object FusekiTriplestoreSpec {
    val config = ConfigFactory.parseString(
        """
         app {
            triplestore {
                dbtype = "fuseki"

                fuseki {
                    port = 3030
                    repository-name = "knora-test-unit"
                }

                reload-on-start = true

                rdf-data = [
                        {
                            path = "_test_data/ontologies/knora-base.ttl"
                            name = "http://www.knora.org/ontology/knora-base"
                        }
                        {
                            path = "_test_data/ontologies/salsah-gui.ttl"
                            name = "http://www.knora.org/ontology/salsah-gui"
                        }
                        {
                            path = "_test_data/ontologies/incunabula-onto.ttl"
                            name = "http://www.knora.org/ontology/incunabula"
                        }
                        {
                            path = "_test_data/ontologies/dokubib-onto.ttl"
                            name = "http://www.knora.org/ontology/dokubib"
                        }
                    ]
            }
         }
        """.stripMargin)
}




/**
 * Tests [[HttpTriplestoreActor]] with settings for using the external Fuseki Triplestore.
 */
class FusekiTriplestoreSpec extends CoreSpec("FusekiTriplestoreTestSystem", FusekiTriplestoreSpec.config) with ImplicitSender {

    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    implicit val timeout = 3.seconds

    val rdfDataObjects = List (

        RdfDataObject(path = "_test_data/ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "_test_data/ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/dokubib-onto.ttl", name = "http://www.knora.org/ontology/dokubib")

    )

    val countQuery = "SELECT (COUNT(*) AS ?no) { ?s ?p ?o }"

    val insertQuery = """
        prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        prefix sub: <http://subotic.org/#>

        INSERT DATA
        {
            GRAPH <http://subotic.org/graph>
            {
                <http://ivan> sub:tries "something" ;
                              sub:hopes "success" ;
                              rdf:type sub:Me .
            }
        }
    """

    val checkInsertQuery = """
        prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        prefix sub: <http://subotic.org/#>

        SELECT *
        WHERE {
            GRAPH <http://subotic.org/graph>
            {
                ?s rdf:type sub:Me .
                ?s ?p ?o .
            }
        }
    """

    val revertInsertQuery = """
        prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        prefix sub: <http://subotic.org/#>

        WITH <http://subotic.org/graph>
        DELETE { ?s ?p ?o }
        WHERE
        {
            ?s rdf:type sub:Me .
            ?s ?p ?o .
        }
    """

    /*
    * Send message to actor under test and check the result.
    * All must complete under 1 second or the test will fail
    * The Akka documentation describes a bunch of other methods
    * but this is the one I the most
    */
    "The HttpTriplestoreActor using Fuseki " when {
        "receiving a 'Hello' request " must {
            "reply " in {
                storeManager ! HelloTriplestore("Message for the HttpTriplestoreActor")
                expectMsg(5.seconds, Hello("Back to sender from the HttpTriplestoreActor"))
            }
        }
        "receiving a 'DropAllTripleStoreContent' request " must {
            "remove all data from the triple store " in {
                //println("[1]==>> Remove all test case start")

                // remove all triples
                storeManager ! DropAllTriplestoreContent()
                expectMsg(5.seconds, DropAllTriplestoreContentACK())

                // count triples inside store
                storeManager ! SparqlSelectRequest(countQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println(s"[1]==>> msg: $msg")
                        msg.results.bindings.head.rowMap("no").toInt should === (0)
                    }
                }
                //println("[1]==>> Remove all test case end")
            }
        }
        "receiving a 'InsertTripleStoreContent' request " must {
            "add data to the triple store " in {
                // add triple
                storeManager ! InsertTriplestoreContent(rdfDataObjects)
                expectMsg(15.seconds, InsertTriplestoreContentACK())

                // count triples inside store
                storeManager ! SparqlSelectRequest(countQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println(s"[2]==>> msg: $msg")
                        msg.results.bindings.head.rowMap("no").toInt should === (2547)
                    }
                }
            }
        }
        "receiving a 'ResetTripleStoreContent' " must {
            "remove all data and then add data to the triple store " in {
                // do the reset
                storeManager ! ResetTriplestoreContent(rdfDataObjects)
                expectMsg(15.seconds, ResetTriplestoreContentACK())

                // count triples inside store
                storeManager ! SparqlSelectRequest(countQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println(s"[3]==>> msg: $msg")
                        msg.results.bindings.head.rowMap("no").toInt should === (2547)
                    }
                }
            }
        }
        "receiving an update request " must {
            "execute the update " in {
                //println("==>> Update 1 test case start")
                storeManager ! SparqlUpdateRequest(insertQuery)
                expectMsg(timeout, SparqlUpdateResponse())

                storeManager ! SparqlSelectRequest(checkInsertQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println(msg)
                        msg.results.bindings.size should === (3)
                    }
                }
                //println("==>> Update 1 test case end")
            }
            "revert back " in {
                //println("==>> Update 2 test case start")
                storeManager ! SparqlUpdateRequest(revertInsertQuery)
                expectMsg(timeout, SparqlUpdateResponse())

                storeManager ! SparqlSelectRequest(checkInsertQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println(msg)
                        msg.results.bindings.size should === (0)
                    }
                }
                //println("==>> Update 2 test case end")
            }
        }
    }

}*/
