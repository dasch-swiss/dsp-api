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

package org.knora.webapi.store.triplestore

import akka.actor.Props
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.knora.webapi.SettingsConstants._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.store.{StoreManager, _}
import org.knora.webapi.{CoreSpec, LiveActorMaker}

import scala.concurrent.duration._

object AllTriplestoreSpec {

    private val config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
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
class AllTriplestoreSpec extends CoreSpec(AllTriplestoreSpec.config) with ImplicitSender {

    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), STORE_MANAGER_ACTOR_NAME)

    private val timeout = 30.seconds
    private val tsType = settings.triplestoreType

    // println(system.settings.config.getConfig("app").root().render())
    // println(system.settings.config.getConfig("app.triplestore").root().render())

    val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images")
    )

    val namedGraphQuery =
        """
        SELECT ?namedGraph ?s ?p ?o ?lang
        WHERE {
                {
              GRAPH ?namedGraph {
                BIND(IRI("http://www.knora.org/ontology/incunabula#page") as ?s)
                ?s ?p ?obj
                BIND(str(?obj) as ?o)
                BIND(lang(?obj) as ?lang)
              }
            }
        }
        """.stripMargin

    val insertQuery =
        """
        prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        prefix sub: <http://knora.subotic.org/#>

        INSERT DATA
        {
            GRAPH <http://knora.subotic.org/graph>
            {
                <http://ivan> sub:tries "something" ;
                              sub:hopes "success" ;
                              rdf:type sub:Me .
            }
        }
        """

    val checkInsertQuery =
        """
        prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        prefix sub: <http://knora.subotic.org/#>

        SELECT *
        WHERE {
            GRAPH <http://knora.subotic.org/graph>
            {
                ?s rdf:type sub:Me .
                ?s ?p ?o .
            }
        }
        """

    val revertInsertQuery =
        """
        prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        prefix sub: <http://knora.subotic.org/#>

        WITH <http://knora.subotic.org/graph>
        DELETE { ?s ?p ?o }
        WHERE
        {
            ?s rdf:type sub:Me .
            ?s ?p ?o .
        }
        """

    val searchURI = if (tsType == HTTP_FUSEKI_TS_TYPE || tsType == EMBEDDED_JENA_TDB_TS_TYPE) {
        "<http://jena.apache.org/text#query>"
    } else {
        //GraphDB
        "<http://www.ontotext.com/owlim/lucene#fullTextSearchIndex>"
    }

    val textSearchQueryGraphDBValueHasString =
        s"""
        PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

        SELECT DISTINCT *
        WHERE {
            ?iri knora-base:valueHasString ?literal .
            ?literal <http://www.ontotext.com/owlim/lucene#fullTextSearchIndex> 'narrenschiff' .
        }
    """

    val textSearchQueryGraphDBRDFLabel =
        s"""
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

        SELECT DISTINCT *
        WHERE {
            ?iri rdfs:label ?literal .
            ?literal <http://www.ontotext.com/owlim/lucene#fullTextSearchIndex> 'Reise' .
        }
    """

    val textSearchQueryFusekiValueHasString =
        s"""
        PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

        SELECT DISTINCT *
        WHERE {
            ?iri <http://jena.apache.org/text#query> 'narrenschiff' .
            ?iri knora-base:valueHasString ?literal .
        }
    """

    val textSearchQueryFusekiDRFLabel =
        s"""
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

        SELECT DISTINCT *
        WHERE {
            ?iri <http://jena.apache.org/text#query> 'Reise' .
            ?iri rdfs:label ?literal .
        }
    """

    val afterLoadCount = 355082
    var afterChangeCount = -1
    var afterChangeRevertCount = -1

    /*
    * Send message to actor under test and check the result.
    * All must complete under 1 second or the test will fail
    * The Akka documentation describes a bunch of other methods
    * but this is the one I the most
    */
    s"The Triplestore ($tsType) Actor " when {
        "started " should {
            "only start answering after initialization has finished " in {
                storeManager ! Initialized()
                expectMsg(60.seconds, InitializedResponse(true))
            }
        }

        "receiving a Hello " should {
            "reply " in {
                within(1.seconds) {
                    storeManager ! HelloTriplestore(tsType)
                    expectMsg(1.second, HelloTriplestore(tsType))
                }
            }
        }

        "receiving a 'ResetTriplestoreContent' request " should {
            "reset the data " in {

                storeManager ! DropAllTriplestoreContent()
                expectMsg(400.seconds, DropAllTriplestoreContentACK())

                storeManager ! TriplestoreStatusRequest
                val res1 = expectMsgType[TriplestoreStatusResponse]
                val graphCountBefore = res1.nrOfGraphs
                val tripleCountBefore = res1.nrOfTriples

                graphCountBefore should be (0)
                tripleCountBefore should be (0)

                storeManager ! ResetTriplestoreContent(rdfDataObjects)
                expectMsg(400.seconds, ResetTriplestoreContentACK())

                storeManager ! TriplestoreStatusRequest
                val res2 = expectMsgType[TriplestoreStatusResponse]
                val graphCountAfter = res2.nrOfGraphs
                val tripleCountAfter = res2.nrOfTriples

                graphCountAfter should be (15)
                tripleCountAfter should be (355082)

            }
        }

        "receiving a Named Graph request " should {
            "provide data " in {
                storeManager ! SparqlSelectRequest(namedGraphQuery)
                expectMsgType[SparqlSelectResponse].results.bindings.nonEmpty should be (true)
            }
        }

        "receiving an update request " should {
            "execute the update " in {
                storeManager ! TriplestoreStatusRequest
                val tripleCountAfterLoad = expectMsgType[TriplestoreStatusResponse].nrOfTriples
                tripleCountAfterLoad should be (afterLoadCount)

                storeManager ! SparqlUpdateRequest(insertQuery)
                expectMsg(SparqlUpdateResponse())

                storeManager ! SparqlSelectRequest(checkInsertQuery)
                expectMsgType[SparqlSelectResponse].results.bindings.size should be (3)

                storeManager ! TriplestoreStatusRequest
                afterChangeCount = expectMsgType[TriplestoreStatusResponse].nrOfTriples

                afterChangeCount should be (afterLoadCount + 3)
            }

            "revert back " in {
                storeManager ! TriplestoreStatusRequest
                val beforeRevert = expectMsgType[TriplestoreStatusResponse].nrOfTriples
                beforeRevert should be (afterChangeCount)

                storeManager ! SparqlUpdateRequest(revertInsertQuery)
                expectMsg(SparqlUpdateResponse())

                storeManager ! SparqlSelectRequest(checkInsertQuery)
                expectMsgType[SparqlSelectResponse].results.bindings.size should be (0)

                storeManager ! TriplestoreStatusRequest
                val afterRevert = expectMsgType[TriplestoreStatusResponse].nrOfTriples
                afterRevert should be (afterLoadCount)

                //println("==>> Update 2 test case end")
            }
        }
        "receiving a search request " should {
            "execute the search with the lucene index for 'knora-base:valueHasString' properties" in {
                within(1000.millis) {
                    tsType match {
                        case HTTP_GRAPH_DB_TS_TYPE | HTTP_GRAPH_DB_FREE_TS_TYPE => storeManager ! SparqlSelectRequest(textSearchQueryGraphDBValueHasString)
                        case _ => storeManager ! SparqlSelectRequest(textSearchQueryFusekiValueHasString)
                    }
                    expectMsgType[SparqlSelectResponse].results.bindings.size should be (35)
                }
            }

            "execute the search with the lucene index for 'rdfs:label' properties" in {
                within(1000.millis) {
                    tsType match {
                        case HTTP_GRAPH_DB_TS_TYPE | HTTP_GRAPH_DB_FREE_TS_TYPE => storeManager ! SparqlSelectRequest(textSearchQueryGraphDBRDFLabel)
                        case _ => storeManager ! SparqlSelectRequest(textSearchQueryFusekiDRFLabel)
                    }
                    expectMsgType[SparqlSelectResponse].results.bindings.size should be (1)
                }
            }
        }
    }

}