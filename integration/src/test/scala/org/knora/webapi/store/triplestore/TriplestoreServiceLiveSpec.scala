/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore

import org.apache.pekko
import org.apache.pekko.testkit.ImplicitSender
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.ZIO

import scala.concurrent.duration.*

import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

class TriplestoreServiceLiveSpec extends E2ESpec with ImplicitSender {

  private val triplestore = ZIO.serviceWithZIO[TriplestoreService]

  override implicit val timeout: FiniteDuration = 30.seconds

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject("test_data/project_data/anything-data.ttl", "http://www.knora.org/data/0001/anything"),
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

  "The TriplestoreService" should {

    "reset the data after receiving a 'ResetTriplestoreContent' request" in {
      UnsafeZioRun.runOrThrow(
        triplestore(_.resetTripleStoreContent(rdfDataObjects))
          .timeout(java.time.Duration.ofMinutes(5)),
      )

      val msg = UnsafeZioRun.runOrThrow(triplestore(_.query(Select(countTriplesQuery))))
      afterLoadCount = msg.getFirstOrThrow("no").toInt
      (afterLoadCount > 0) should ===(true)
    }

    "provide data receiving a Named Graph request" in {
      val actual = UnsafeZioRun.runOrThrow(triplestore(_.query(Select(namedGraphQuery))))
      actual.nonEmpty should ===(true)
    }

    "execute an update" in {
      val countTriplesBefore = UnsafeZioRun.runOrThrow(
        triplestore(_.query(Select(countTriplesQuery)))
          .map(_.getFirstOrThrow("no").toInt),
      )
      countTriplesBefore should ===(afterLoadCount)

      UnsafeZioRun.runOrThrow(triplestore(_.query(Update(insertQuery))))

      val checkInsertActual = UnsafeZioRun.runOrThrow(
        triplestore(_.query(Select(checkInsertQuery))).map(_.size),
      )
      checkInsertActual should ===(3)

      afterChangeCount = UnsafeZioRun.runOrThrow(
        triplestore(_.query(Select(countTriplesQuery)))
          .map(_.getFirstOrThrow("no").toInt),
      )
      (afterChangeCount - afterLoadCount) should ===(3)
    }

    "revert back " in {
      val countTriplesBefore = UnsafeZioRun.runOrThrow(
        triplestore(_.query(Select(countTriplesQuery)))
          .map(_.getFirstOrThrow("no").toInt),
      )
      countTriplesBefore should ===(afterChangeCount)

      UnsafeZioRun.runOrThrow(triplestore(_.query(Update(revertInsertQuery))))

      val countTriplesQueryActual = UnsafeZioRun.runOrThrow(
        triplestore(_.query(Select(countTriplesQuery)))
          .map(_.getFirstOrThrow("no").toInt),
      )
      countTriplesQueryActual should ===(afterLoadCount)

      val checkInsertActual = UnsafeZioRun.runOrThrow(
        triplestore(_.query(Select(checkInsertQuery)))
          .map(_.size),
      )
      checkInsertActual should ===(0)
    }

    "execute the search with the lucene index for 'knora-base:valueHasString' properties" in {
      within(1000.millis) {
        val actual = UnsafeZioRun.runOrThrow(
          triplestore(_.query(Select(textSearchQueryFusekiValueHasString)))
            .map(_.size),
        )
        actual should ===(4)
      }
    }

    "execute the search with the lucene index for 'rdfs:label' properties" in {
      within(1000.millis) {
        val actual = UnsafeZioRun.runOrThrow(
          triplestore(_.query(Select(textSearchQueryFusekiDRFLabel)))
            .map(_.size),
        )
        actual should ===(1)
      }
    }

    "should allow empty Strings as values" in {
      val iri         = Rdf.iri("http://example.com/resource-with-comment")
      val emptyString = ""
      val commentVar  = "emptyComment"

      val insertQuery = Queries
        .INSERT_DATA(iri.has(RDFS.COMMENT, emptyString))
        .into(Rdf.iri("allowemptystringgraph"))

      val selectQuery = Queries
        .SELECT(SparqlBuilder.`var`(commentVar))
        .where(iri.has(RDFS.COMMENT, SparqlBuilder.`var`(commentVar)))

      val actual =
        UnsafeZioRun.runOrThrow(triplestore(t => t.insert(insertQuery) *> t.select(selectQuery)))
      actual.getCol(commentVar) shouldBe Seq(emptyString)
    }
  }
}
