/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.impl

import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object TriplestoreServiceLiveSpec extends E2EZSpec { self =>

  private val triplestore = ZIO.serviceWithZIO[TriplestoreService]

  override val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject("test_data/project_data/anything-data.ttl", "http://www.knora.org/data/0001/anything"),
  )

  private val countTriplesQuery: String =
    """
        SELECT (COUNT(*) AS ?no)
        WHERE
            {
                ?s ?p ?o .
            }
        """

  private val namedGraphQuery: String =
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

  private val insertQuery: String =
    """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX sub: <http://subotic.org/#>

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

  private val checkInsertQuery: String =
    """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX sub: <http://subotic.org/#>

        SELECT *
        WHERE {
            GRAPH <http://subotic.org/graph>
            {
                ?s rdf:type sub:Me .
                ?s ?p ?o .
            }
        }
        """

  private val revertInsertQuery: String =
    """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX sub: <http://subotic.org/#>

        WITH <http://subotic.org/graph>
        DELETE { ?s ?p ?o }
        WHERE
        {
            ?s rdf:type sub:Me .
            ?s ?p ?o .
        }
        """

  private val textSearchQueryFusekiValueHasString: String =
    s"""
        PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

        SELECT DISTINCT *
        WHERE {
            ?iri <http://jena.apache.org/text#query> 'test' .
            ?iri knora-base:valueHasString ?literal .
        }
    """

  private val textSearchQueryFusekiDRFLabel: String =
    s"""
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

        SELECT DISTINCT *
        WHERE {
            ?iri <http://jena.apache.org/text#query> 'Papa' .
            ?iri rdfs:label ?literal .
        }
    """

  private var afterLoadCount: Int   = -1
  private var afterChangeCount: Int = -1

  override val e2eSpec = suite("TriplestoreServiceLive")(
    test("reset the data after receiving a 'ResetTriplestoreContent' request") {
      triplestore(_.resetTripleStoreContent(rdfDataObjects))
        .timeoutFailCause(Cause.fail("Timeout while resetTripleStoreContent"))(5.minutes) *>
        triplestore(_.query(Select(countTriplesQuery))).map(msg =>
          self.afterLoadCount = msg.getFirstOrThrow("no").toInt
          assertTrue(self.afterLoadCount > 0),
        )
    },
    test("provide data receiving a Named Graph request") {
      triplestore(_.query(Select(namedGraphQuery)))
        .map(actual => assertTrue(actual.nonEmpty))
    },
    test("execute an update") {
      for {
        countTriplesBefore <- triplestore(_.query(Select(countTriplesQuery))).map(_.getFirstOrThrow("no").toInt)
        _                  <- triplestore(_.query(Update(insertQuery)))
        checkInsertActual  <- triplestore(_.query(Select(checkInsertQuery))).map(_.size)
        afterChangeCount   <- triplestore(_.query(Select(countTriplesQuery))).map(_.getFirstOrThrow("no").toInt)
        _                   = self.afterChangeCount = afterChangeCount
      } yield assertTrue(
        countTriplesBefore == self.afterLoadCount,
        checkInsertActual == 3,
        (self.afterChangeCount - self.afterLoadCount) == 3,
      )
    },
    test("revert back ") {
      for {
        countTriplesBefore      <- triplestore(_.query(Select(countTriplesQuery))).map(_.getFirstOrThrow("no").toInt)
        _                       <- triplestore(_.query(Update(revertInsertQuery)))
        countTriplesQueryActual <- triplestore(_.query(Select(countTriplesQuery))).map(_.getFirstOrThrow("no").toInt)
        checkInsertActual       <- triplestore(_.query(Select(checkInsertQuery))).map(_.size)
      } yield assertTrue(
        countTriplesBefore == self.afterChangeCount,
        countTriplesQueryActual == self.afterLoadCount,
        checkInsertActual == 0,
      )
    },
    test("execute the search with the lucene index for 'knora-base:valueHasString' properties") {
      triplestore(_.query(Select(textSearchQueryFusekiValueHasString)))
        .map(_.size)
        .timeoutFailCause(Cause.fail("Timeout while executing text search query"))(1.second)
        .map(actual => assertTrue(actual == 4))
    },
    test("execute the search with the lucene index for 'rdfs:label' properties") {
      triplestore(_.query(Select(textSearchQueryFusekiDRFLabel)))
        .map(_.size)
        .timeoutFailCause(Cause.fail("Timeout while executing text search query"))(1.second)
        .map(actual => assertTrue(actual == 1))
    },
    test("should allow empty Strings as values") {
      val iri         = Rdf.iri("http://example.com/resource-with-comment")
      val emptyString = ""
      val commentVar  = "emptyComment"

      val insertQuery = Queries
        .INSERT_DATA(iri.has(RDFS.COMMENT, emptyString))
        .into(Rdf.iri("allowemptystringgraph"))

      val selectQuery = Queries
        .SELECT(SparqlBuilder.`var`(commentVar))
        .where(iri.has(RDFS.COMMENT, SparqlBuilder.`var`(commentVar)))

      triplestore(t => t.insert(insertQuery) *> t.select(selectQuery))
        .map(actual => assertTrue(actual.getCol(commentVar) == Seq(emptyString)))
    },
  )
}
