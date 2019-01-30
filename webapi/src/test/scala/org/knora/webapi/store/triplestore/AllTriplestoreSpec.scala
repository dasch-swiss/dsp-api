/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.{CoreSpec, TriplestoreTypes}

import scala.concurrent.duration._
import scala.language.postfixOps

object AllTriplestoreSpec {

    private val config = ConfigFactory.parseString(
        """
         # akka.loglevel = "DEBUG"
         # akka.stdout-loglevel = "DEBUG"
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

    private val timeout = 30.seconds
    private val tsType = settings.triplestoreType

    // println(system.settings.config.getConfig("app").root().render())
    // println(system.settings.config.getConfig("app.triplestore").root().render())

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images")
    )

    val countTriplesQuery = if (tsType.startsWith("graphdb"))
        """
        SELECT (COUNT(*) AS ?no)
        FROM <http://www.ontotext.com/explicit>
        WHERE
            {
                ?s ?p ?o .
            }
        """
    else
        """
        SELECT (COUNT(*) AS ?no)
        WHERE
            {
                ?s ?p ?o .
            }
        """

    val namedGraphQuery =
        """
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

    val insertQuery =
        """
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

    val checkInsertQuery =
        """
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

    val revertInsertQuery =
        """
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

    val searchURI = if (tsType == TriplestoreTypes.HttpFuseki || tsType == TriplestoreTypes.EmbeddedJenaTdb) {
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

    var afterLoadCount = -1
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
                storeManager ! CheckRepositoryRequest()
                val response = expectMsgType[CheckRepositoryResponse](1.second)

                response.repositoryStatus should be (RepositoryStatus.ServiceAvailable)
            }
        }

        "receiving a Hello " should {
            "reply " in {
                within(1.seconds) {
                    storeManager ! HelloTriplestore(tsType)
                    expectMsg(HelloTriplestore(tsType))
                }
            }
        }
        "receiving a 'ResetTriplestoreContent' request " should {
            "reset the data " in {
                //println("==>> Reset test case start")
                storeManager ! ResetTriplestoreContent(rdfDataObjects)
                expectMsg(5 minutes, ResetTriplestoreContentACK())
                //println("==>> Reset test case end")

                storeManager ! SparqlSelectRequest(countTriplesQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println(msg)
                        afterLoadCount = msg.results.bindings.head.rowMap("no").toInt
                        (afterLoadCount > 0) should ===(true)
                    }
                }
            }
        }
        "receiving a Named Graph request " should {
            "provide data " in {
                //println("==>> Named Graph test case start")
                storeManager ! SparqlSelectRequest(namedGraphQuery)
                //println(result)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println(msg)
                        msg.results.bindings.nonEmpty should ===(true)
                    }
                }
                //println("==>> Named Graph test case end")
            }
        }
        "receiving an update request " should {
            "execute the update " in {
                //println("==>> Update 1 test case start")


                storeManager ! SparqlSelectRequest(countTriplesQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println("vor insert: " + msg)
                        msg.results.bindings.head.rowMap("no").toInt should ===(afterLoadCount)
                    }
                }


                storeManager ! SparqlUpdateRequest(insertQuery)
                expectMsg(SparqlUpdateResponse())

                storeManager ! SparqlSelectRequest(checkInsertQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println(msg)
                        msg.results.bindings.size should ===(3)
                    }
                }


                storeManager ! SparqlSelectRequest(countTriplesQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println("nach instert" + msg)
                        afterChangeCount = msg.results.bindings.head.rowMap("no").toInt
                        (afterChangeCount - afterLoadCount) should ===(3)
                    }
                }



                //println("==>> Update 1 test case end")
            }
            "revert back " in {
                //println("==>> Update 2 test case start")


                storeManager ! SparqlSelectRequest(countTriplesQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println("vor revert: " + msg)
                        msg.results.bindings.head.rowMap("no").toInt should ===(afterChangeCount)
                    }
                }

                storeManager ! SparqlUpdateRequest(revertInsertQuery)
                expectMsg(SparqlUpdateResponse())

                storeManager ! SparqlSelectRequest(countTriplesQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println("nach revert: " + msg)
                        msg.results.bindings.head.rowMap("no").toInt should ===(afterLoadCount)
                    }
                }


                storeManager ! SparqlSelectRequest(checkInsertQuery)
                expectMsgPF(timeout) {
                    case msg: SparqlSelectResponse => {
                        //println("check: " + msg)
                        msg.results.bindings.size should ===(0)
                    }
                }

                //println("==>> Update 2 test case end")
            }
        }
        "receiving a search request " should {
            "execute the search with the lucene index for 'knora-base:valueHasString' properties" in {
                within(1000.millis) {
                    tsType match {
                        case TriplestoreTypes.HttpGraphDBSE | TriplestoreTypes.HttpGraphDBFree => storeManager ! SparqlSelectRequest(textSearchQueryGraphDBValueHasString)
                        case _ => storeManager ! SparqlSelectRequest(textSearchQueryFusekiValueHasString)
                    }
                    expectMsgPF(timeout) {
                        case msg: SparqlSelectResponse => {
                            //println(msg)
                            msg.results.bindings.size should ===(35)
                        }
                    }
                }
            }

            "execute the search with the lucene index for 'rdfs:label' properties" in {
                within(1000.millis) {
                    tsType match {
                        case TriplestoreTypes.HttpGraphDBSE | TriplestoreTypes.HttpGraphDBFree => storeManager ! SparqlSelectRequest(textSearchQueryGraphDBRDFLabel)
                        case _ => storeManager ! SparqlSelectRequest(textSearchQueryFusekiDRFLabel)
                    }
                    expectMsgPF(timeout) {
                        case msg: SparqlSelectResponse => {
                            //println(msg)
                            msg.results.bindings.size should ===(1)
                        }
                    }
                }
            }
        }
    }

}