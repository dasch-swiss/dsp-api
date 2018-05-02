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
package org.knora.rapier.store.triplestore.embedded

import akka.testkit.{ImplicitSender, TestActorRef}
import com.typesafe.config.ConfigFactory
import org.knora.rapier.CoreSpec
import org.knora.rapier.messages.v1respondermessages.triplestoremessages._

import scala.concurrent.duration._

/*
 * Custom config used in the test. Here you can define ond override everything which you would
 * normaly write in application.conf
 */
object JenaTDBActorSpec {
    val config = ConfigFactory.parseString(
        """
         app {
            triplestore {
                dbtype = "embedded-jena-tdb"
                //dbtype = "fuseki"

                embedded-jena-tdb {
                    persisted = true // "true" -> disk, "false" -> in-memory
                    storage-path = "_TMP" // ignored if "in-memory"
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
                            name = "http://www.knora.org/ontology/0803/incunabula"
                        }
                        {
                            path = "_test_data/ontologies/dokubib-onto.ttl"
                            name = "http://www.knora.org/ontology/0804/dokubib"
                        }
                    ]
            }
         }
        """.stripMargin)
}

/*
 * In this simple test case, we start our actor under test, send it a message, and test if the message
 * we got in response is the one we expexted.
 *
 * The naming is usualy the class name appended by the word 'spec' all in camel case.
 *
 * All test cases are subclasses of CoreSpec and need to provide parameters
 * providing the actor system name and config.
 *
 * to execute, type 'test' in sbt
 */
class JenaTDBActorSpec extends CoreSpec("JenaTDBActorTestSystem", JenaTDBActorSpec.config) with ImplicitSender {

    val actorUnderTest = TestActorRef[JenaTDBActor]

    val underlyingActor = actorUnderTest.underlyingActor
    private val timeout = 30.seconds

    val mainConfig = ConfigFactory.load()
    // println(mainConfig.getConfig("app.triplestore").toString)

    val rdfDataObjects = List (
        RdfDataObject(path = "_test_data/ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "_test_data/ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/0803/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/dokubib-onto.ttl", name = "http://www.knora.org/ontology/0804/dokubib")
    )

    val countTriplesQuery = """
        SELECT (COUNT(*) AS ?no)
            {
                ?s ?p ?o .
            }
    """

    val namedGraphQuery = """
        SELECT ?namedGraph ?s ?p ?o ?lang
        WHERE {
                {
              GRAPH ?namedGraph {
                BIND(IRI("http://www.knora.org/ontology/0803/incunabula#page") as ?s)
                ?s ?p ?obj
                BIND(str(?obj) as ?o)
                BIND(lang(?obj) as ?lang)
              }
            }
        }
    """.stripMargin

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

    val textSearchQuery = """
        prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        prefix knora-base: <http://www.knora.org/ontology/knora-base#>
        prefix text:       <http://jena.apache.org/text#>

        SELECT DISTINCT *
        WHERE {
            ?iri text:query 'narrenschiff' .
            ?iri knora-base:valueHasString ?literal .
        }
        LIMIT 25
    """


    /*
    * Send message to actor under test and check the result.
    * All must complete under 1 second or the test will fail
    * The Akka documentation describes a bunch of other methods
    * but this is the one I the most
    */
    "The JenaTDBActor " when {
        "started " must {
            "only start answering after initialization has finished " in {
                actorUnderTest ! Initialized()
                expectMsg(InitializedResponse(true))
            }
        }

        "sent a Hello " must {
            "reply " in {
                within(1000.millis) {
                    actorUnderTest ! HelloTriplestore("JenaTDBActor")
                    expectMsg(HelloTriplestore("JenaTDBActor"))
                }
            }
        }
        "sent ResetTripleStoreContent request " must {
            "reset the data " in {
                //println("==>> Reset test case start")
                actorUnderTest ! ResetTriplestoreContent(rdfDataObjects)
                expectMsg(120.seconds, ResetTriplestoreContentACK())
                //println("==>> Reset test case end")

                actorUnderTest ! SparqlSelectRequest(countTriplesQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println(msg)
                        msg.results.bindings.head.rowMap("no").toInt should === (378945)
                    }
                }
            }
        }
        "sent a Named Graph request " must {
            "provide data " in {
                //println("==>> Named Graph test case start")
                actorUnderTest ! SparqlSelectRequest(namedGraphQuery)
                //println(result)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println(msg)
                        msg.results.bindings.size should === (27)
                    }
                }
                //println("==>> Named Graph test case end")
            }
        }
        "sent an update request " must {
            "execute the update " in {
                //println("==>> Update 1 test case start")
                actorUnderTest ! SparqlSelectRequest(countTriplesQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println("vor insert: " + msg)
                        msg.results.bindings.head.rowMap("no").toInt should === (378945)
                    }
                }

                actorUnderTest ! SparqlUpdateRequest(insertQuery)
                expectMsg(SparqlUpdateResponse())

                actorUnderTest ! SparqlSelectRequest(checkInsertQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println(msg)
                        msg.results.bindings.size should === (3)
                    }
                }

                actorUnderTest ! SparqlSelectRequest(countTriplesQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println("nach instert" + msg)
                        msg.results.bindings.head.rowMap("no").toInt should === (378948)
                    }
                }


                //println("==>> Update 1 test case end")
            }
            "revert back " in {
                //println("==>> Update 2 test case start")

                actorUnderTest ! SparqlSelectRequest(countTriplesQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println("vor revert: " + msg)
                        msg.results.bindings.head.rowMap("no").toInt should === (378948)
                    }
                }

                actorUnderTest ! SparqlUpdateRequest(revertInsertQuery)
                expectMsg(SparqlUpdateResponse())

                actorUnderTest ! SparqlSelectRequest(countTriplesQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println("nach revert: " + msg)
                        msg.results.bindings.head.rowMap("no").toInt should === (378945)
                    }
                }

                actorUnderTest ! SparqlSelectRequest(checkInsertQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println("check: " + msg)
                        msg.results.bindings.size should === (0)
                    }
                }

                //println("==>> Update 2 test case end")
            }
        }
        "sent an search request " must {
            "execute the search by using the lucene index " in {
                within(1000.millis) {
                    actorUnderTest ! SparqlSelectRequest(textSearchQuery)
                    expectMsgPF(timeout) {
                        case msg: SparqlSelectResponse => {
                            //println(msg)
                            msg.results.bindings.size should ===(25)
                        }
                    }
                }
            }
        }
    }

}*/
