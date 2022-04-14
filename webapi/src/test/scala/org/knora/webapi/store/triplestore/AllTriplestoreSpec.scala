/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore

import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.rdf.SparqlSelectResult

import scala.concurrent.duration._
import scala.language.postfixOps

object AllTriplestoreSpec {

  private val config = ConfigFactory.parseString("""
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

  // println(system.settings.config.getConfig("app").root().render())
  // println(system.settings.config.getConfig("app.triplestore").root().render())

  override lazy val rdfDataObjects = List(
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  val countTriplesQuery: String =
      """
        SELECT (COUNT(*) AS ?no)
        WHERE
            {
                ?s ?p ?o .
            }
        """

  val namedGraphQuery: String =
    """
        SELECT ?namedGraph ?s ?p ?o ?lang
        WHERE {
                {
              GRAPH ?namedGraph {
                BIND(IRI("http://www.knora.org/ontology/0001/anything#Thing") as ?s)
                ?s ?p ?obj
                BIND(str(?obj) as ?o)
                BIND(lang(?obj) as ?lang)
              }
            }
        }
        """.stripMargin

  val insertQuery: String =
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

  val graphDataContent: String =
    """
        prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        prefix jedi: <http://jedi.org/#>

        <http://luke> jedi:tries "force for the first time" ;
                      jedi:hopes "to power the lightsaber" ;
                      rdf:type jedi:Skywalker .
        """

  val checkInsertQuery: String =
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

  val revertInsertQuery: String =
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

  val searchURI: String = "<http://jena.apache.org/text#query>"

  val textSearchQueryFusekiValueHasString: String =
    s"""
        PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

        SELECT DISTINCT *
        WHERE {
            ?iri <http://jena.apache.org/text#query> 'test' .
            ?iri knora-base:valueHasString ?literal .
        }
    """

  val textSearchQueryFusekiDRFLabel: String =
    s"""
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

        SELECT DISTINCT *
        WHERE {
            ?iri <http://jena.apache.org/text#query> 'Papa' .
            ?iri rdfs:label ?literal .
        }
    """

  var afterLoadCount: Int = -1
  var afterChangeCount: Int = -1
  var afterChangeRevertCount: Int = -1

  /*
   * Send message to actor under test and check the result.
   * All must complete under 1 second or the test will fail
   * The Akka documentation describes a bunch of other methods
   * but this is the one I the most
   */
  s"The Triplestore ($settings.triplestoreType) Actor " when {
    "started " should {
      "only start answering after initialization has finished " in {
        storeManager ! CheckTriplestoreRequest()
        val response = expectMsgType[CheckTriplestoreResponse](1.second)

        response.triplestoreStatus should be(TriplestoreStatus.ServiceAvailable)
      }
    }

    "receiving a Hello " should {
      "reply " in {
        within(1.seconds) {
          storeManager ! HelloTriplestore(settings.triplestoreType)
          expectMsg(HelloTriplestore(settings.triplestoreType))
        }
      }
    }
    "receiving a 'ResetTriplestoreContent' request " should {
      "reset the data " in {
        //println("==>> Reset test case start")
        storeManager ! ResetRepositoryContent(rdfDataObjects)
        expectMsg(5 minutes, ResetRepositoryContentACK())
        //println("==>> Reset test case end")

        storeManager ! SparqlSelectRequest(countTriplesQuery)
        expectMsgPF(timeout) { case msg: SparqlSelectResult =>
          //println(msg)
          afterLoadCount = msg.results.bindings.head.rowMap("no").toInt
          (afterLoadCount > 0) should ===(true)
        }
      }
    }
    "receiving a Named Graph request " should {
      "provide data " in {
        //println("==>> Named Graph test case start")
        storeManager ! SparqlSelectRequest(namedGraphQuery)
        //println(result)
        expectMsgPF(timeout) { case msg: SparqlSelectResult =>
          //println(msg)
          msg.results.bindings.nonEmpty should ===(true)
        }
        //println("==>> Named Graph test case end")
      }
    }
    "receiving an update request " should {
      "execute the update " in {
        //println("==>> Update 1 test case start")

        storeManager ! SparqlSelectRequest(countTriplesQuery)
        expectMsgPF(timeout) { case msg: SparqlSelectResult =>
          //println("vor insert: " + msg)
          msg.results.bindings.head.rowMap("no").toInt should ===(afterLoadCount)
        }

        storeManager ! SparqlUpdateRequest(insertQuery)
        expectMsg(SparqlUpdateResponse())

        storeManager ! SparqlSelectRequest(checkInsertQuery)
        expectMsgPF(timeout) { case msg: SparqlSelectResult =>
          //println(msg)
          msg.results.bindings.size should ===(3)
        }

        storeManager ! SparqlSelectRequest(countTriplesQuery)
        expectMsgPF(timeout) { case msg: SparqlSelectResult =>
          //println("nach instert" + msg)
          afterChangeCount = msg.results.bindings.head.rowMap("no").toInt
          (afterChangeCount - afterLoadCount) should ===(3)
        }
        //println("==>> Update 1 test case end")
      }
      "revert back " in {
        //println("==>> Update 2 test case start")

        storeManager ! SparqlSelectRequest(countTriplesQuery)
        expectMsgPF(timeout) { case msg: SparqlSelectResult =>
          //println("vor revert: " + msg)
          msg.results.bindings.head.rowMap("no").toInt should ===(afterChangeCount)
        }

        storeManager ! SparqlUpdateRequest(revertInsertQuery)
        expectMsg(SparqlUpdateResponse())

        storeManager ! SparqlSelectRequest(countTriplesQuery)
        expectMsgPF(timeout) { case msg: SparqlSelectResult =>
          //println("nach revert: " + msg)
          msg.results.bindings.head.rowMap("no").toInt should ===(afterLoadCount)
        }

        storeManager ! SparqlSelectRequest(checkInsertQuery)
        expectMsgPF(timeout) { case msg: SparqlSelectResult =>
          //println("check: " + msg)
          msg.results.bindings.size should ===(0)
        }

        //println("==>> Update 2 test case end")
      }
    }
    "receiving a search request " should {
      "execute the search with the lucene index for 'knora-base:valueHasString' properties" in {
        within(1000.millis) {
          storeManager ! SparqlSelectRequest(textSearchQueryFusekiValueHasString)
          expectMsgPF(timeout) { case msg: SparqlSelectResult =>
            //println(msg)
            msg.results.bindings.size should ===(3)
          }
        }
      }

      "execute the search with the lucene index for 'rdfs:label' properties" in {
        within(1000.millis) {
          storeManager ! SparqlSelectRequest(textSearchQueryFusekiDRFLabel)
          expectMsgPF(timeout) { case msg: SparqlSelectResult =>
            //println(msg)
            msg.results.bindings.size should ===(1)
          }
        }
      }
    }

    "receiving insert rdf data objects request" should {
      "insert RDF DataObjects" in {
        storeManager ! InsertRepositoryContent(rdfDataObjects)
        expectMsg(5 minutes, InsertTriplestoreContentACK())
      }

    }

    "receiving named graph data requests" should {
      "put the graph data as turtle" in {
        storeManager ! InsertGraphDataContentRequest(graphContent = graphDataContent, "http://jedi.org/graph")
        expectMsgType[InsertGraphDataContentResponse](10.second)
      }

      "read the graph data as turtle" in {
        storeManager ! NamedGraphDataRequest(graphIri = "http://jedi.org/graph")
        val response = expectMsgType[NamedGraphDataResponse](1.second)
        response.turtle.length should be > 0
      }
    }
  }

}
