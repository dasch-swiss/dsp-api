/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore

import akka.testkit.ImplicitSender
import org.knora.webapi.CoreSpec
import dsp.errors.TriplestoreTimeoutException
import org.knora.webapi.messages.store.triplestoremessages.SimulateTimeoutRequest

import scala.concurrent.duration._
import org.knora.webapi.messages.store.triplestoremessages.CheckTriplestoreRequest
import org.knora.webapi.messages.store.triplestoremessages.ResetRepositoryContent
import org.knora.webapi.messages.store.triplestoremessages.ResetRepositoryContentACK
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.CheckTriplestoreResponse
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreStatus
import org.knora.webapi.messages.store.triplestoremessages.SparqlSelectRequest
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.store.triplestoremessages.SparqlUpdateRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlUpdateResponse
import org.knora.webapi.messages.store.triplestoremessages.InsertRepositoryContent
import org.knora.webapi.messages.store.triplestoremessages.InsertTriplestoreContentACK
import org.knora.webapi.messages.store.triplestoremessages.InsertGraphDataContentRequest
import org.knora.webapi.messages.store.triplestoremessages.InsertGraphDataContentResponse
import org.knora.webapi.messages.store.triplestoremessages.NamedGraphDataRequest
import org.knora.webapi.messages.store.triplestoremessages.NamedGraphDataResponse

class TriplestoreServiceManagerSpec extends CoreSpec() with ImplicitSender {

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

  var afterLoadCount: Int         = -1
  var afterChangeCount: Int       = -1
  var afterChangeRevertCount: Int = -1

  "The TriplestoreServiceManager" should {

    "only start answering after initialization has finished " in {
      appActor ! CheckTriplestoreRequest()
      val response = expectMsgType[CheckTriplestoreResponse](1.second)

      response.triplestoreStatus should be(TriplestoreStatus.ServiceAvailable)
    }

    "reset the data after receiving a 'ResetTriplestoreContent' request" in {
      //println("==>> Reset test case start")
      appActor ! ResetRepositoryContent(rdfDataObjects)
      expectMsg(5.minutes, ResetRepositoryContentACK())
      //println("==>> Reset test case end")

      appActor ! SparqlSelectRequest(countTriplesQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        //println(msg)
        afterLoadCount = msg.results.bindings.head.rowMap("no").toInt
        (afterLoadCount > 0) should ===(true)
      }
    }

    "provide data receiving a Named Graph request" in {
      //println("==>> Named Graph test case start")
      appActor ! SparqlSelectRequest(namedGraphQuery)
      //println(result)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        //println(msg)
        msg.results.bindings.nonEmpty should ===(true)
      }
      //println("==>> Named Graph test case end")
    }

    "execute an update" in {
      //println("==>> Update 1 test case start")

      appActor ! SparqlSelectRequest(countTriplesQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        //println("vor insert: " + msg)
        msg.results.bindings.head.rowMap("no").toInt should ===(afterLoadCount)
      }

      appActor ! SparqlUpdateRequest(insertQuery)
      expectMsg(SparqlUpdateResponse())

      appActor ! SparqlSelectRequest(checkInsertQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        //println(msg)
        msg.results.bindings.size should ===(3)
      }

      appActor ! SparqlSelectRequest(countTriplesQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        //println("nach instert" + msg)
        afterChangeCount = msg.results.bindings.head.rowMap("no").toInt
        (afterChangeCount - afterLoadCount) should ===(3)
      }
      //println("==>> Update 1 test case end")
    }

    "revert back " in {
      //println("==>> Update 2 test case start")

      appActor ! SparqlSelectRequest(countTriplesQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        //println("vor revert: " + msg)
        msg.results.bindings.head.rowMap("no").toInt should ===(afterChangeCount)
      }

      appActor ! SparqlUpdateRequest(revertInsertQuery)
      expectMsg(SparqlUpdateResponse())

      appActor ! SparqlSelectRequest(countTriplesQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        //println("nach revert: " + msg)
        msg.results.bindings.head.rowMap("no").toInt should ===(afterLoadCount)
      }

      appActor ! SparqlSelectRequest(checkInsertQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        //println("check: " + msg)
        msg.results.bindings.size should ===(0)
      }

      //println("==>> Update 2 test case end")
    }

    "execute the search with the lucene index for 'knora-base:valueHasString' properties" in {
      within(1000.millis) {
        appActor ! SparqlSelectRequest(textSearchQueryFusekiValueHasString)
        expectMsgPF(timeout) { case msg: SparqlSelectResult =>
          //println(msg)
          msg.results.bindings.size should ===(3)
        }
      }
    }

    "execute the search with the lucene index for 'rdfs:label' properties" in {
      within(1000.millis) {
        appActor ! SparqlSelectRequest(textSearchQueryFusekiDRFLabel)
        expectMsgPF(timeout) { case msg: SparqlSelectResult =>
          //println(msg)
          msg.results.bindings.size should ===(1)
        }
      }
    }

    "insert RDF DataObjects" in {
      appActor ! InsertRepositoryContent(rdfDataObjects)
      expectMsg(5.minutes, InsertTriplestoreContentACK())
    }

    "put the graph data as turtle" in {
      appActor ! InsertGraphDataContentRequest(graphContent = graphDataContent, "http://jedi.org/graph")
      expectMsgType[InsertGraphDataContentResponse](10.second)
    }

    "read the graph data as turtle" in {
      appActor ! NamedGraphDataRequest(graphIri = "http://jedi.org/graph")
      val response = expectMsgType[NamedGraphDataResponse](1.second)
      response.turtle.length should be > 0
    }

    "report a connection timeout with an appropriate error message" in {
      appActor ! SimulateTimeoutRequest()

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        assert(msg.cause.isInstanceOf[TriplestoreTimeoutException])
        assert(
          msg.cause.getMessage == "The triplestore took too long to process a request. This can happen because the triplestore needed too much time to search through the data that is currently in the triplestore. Query optimisation may help."
        )
      }
    }
  }
}
