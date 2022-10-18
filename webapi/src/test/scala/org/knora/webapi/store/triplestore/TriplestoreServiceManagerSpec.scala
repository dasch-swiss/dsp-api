/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore

import akka.testkit.ImplicitSender

import scala.concurrent.duration._

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.store.triplestoremessages.InsertGraphDataContentRequest
import org.knora.webapi.messages.store.triplestoremessages.InsertGraphDataContentResponse
import org.knora.webapi.messages.store.triplestoremessages.InsertRepositoryContent
import org.knora.webapi.messages.store.triplestoremessages.InsertTriplestoreContentACK
import org.knora.webapi.messages.store.triplestoremessages.NamedGraphDataRequest
import org.knora.webapi.messages.store.triplestoremessages.NamedGraphDataResponse
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.ResetRepositoryContent
import org.knora.webapi.messages.store.triplestoremessages.ResetRepositoryContentACK
import org.knora.webapi.messages.store.triplestoremessages.SimulateTimeoutRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlSelectRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlUpdateRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlUpdateResponse
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.store.triplestore.errors.TriplestoreTimeoutException

class TriplestoreServiceManagerSpec extends CoreSpec with ImplicitSender {

  private val timeout = 30.seconds

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

    "reset the data after receiving a 'ResetTriplestoreContent' request" in {
      appActor ! ResetRepositoryContent(rdfDataObjects)
      expectMsg(5.minutes, ResetRepositoryContentACK())

      appActor ! SparqlSelectRequest(countTriplesQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        afterLoadCount = msg.results.bindings.head.rowMap("no").toInt
        (afterLoadCount > 0) should ===(true)
      }
    }

    "provide data receiving a Named Graph request" in {
      appActor ! SparqlSelectRequest(namedGraphQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        msg.results.bindings.nonEmpty should ===(true)
      }
    }

    "execute an update" in {
      appActor ! SparqlSelectRequest(countTriplesQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        msg.results.bindings.head.rowMap("no").toInt should ===(afterLoadCount)
      }

      appActor ! SparqlUpdateRequest(insertQuery)
      expectMsg(SparqlUpdateResponse())

      appActor ! SparqlSelectRequest(checkInsertQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        msg.results.bindings.size should ===(3)
      }

      appActor ! SparqlSelectRequest(countTriplesQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        afterChangeCount = msg.results.bindings.head.rowMap("no").toInt
        (afterChangeCount - afterLoadCount) should ===(3)
      }
    }

    "revert back " in {
      appActor ! SparqlSelectRequest(countTriplesQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        msg.results.bindings.head.rowMap("no").toInt should ===(afterChangeCount)
      }

      appActor ! SparqlUpdateRequest(revertInsertQuery)
      expectMsg(SparqlUpdateResponse())

      appActor ! SparqlSelectRequest(countTriplesQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        msg.results.bindings.head.rowMap("no").toInt should ===(afterLoadCount)
      }

      appActor ! SparqlSelectRequest(checkInsertQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        msg.results.bindings.size should ===(0)
      }
    }

    "execute the search with the lucene index for 'knora-base:valueHasString' properties" in {
      within(1000.millis) {
        appActor ! SparqlSelectRequest(textSearchQueryFusekiValueHasString)
        expectMsgPF(timeout) { case msg: SparqlSelectResult =>
          msg.results.bindings.size should ===(3)
        }
      }
    }

    "execute the search with the lucene index for 'rdfs:label' properties" in {
      within(1000.millis) {
        appActor ! SparqlSelectRequest(textSearchQueryFusekiDRFLabel)
        expectMsgPF(timeout) { case msg: SparqlSelectResult =>
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
