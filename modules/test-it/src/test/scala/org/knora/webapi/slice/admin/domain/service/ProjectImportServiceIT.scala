/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*
import zio.nio.file.*
import zio.test.*

import java.io.IOException

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

object ProjectImportServiceE2ESpec extends E2EZSpec {

  // this test itself does not use the data of the rdfDataObjects, but we need a triplestore and have to import a small set
  override val rdfDataObjects: List[RdfDataObject] = List.empty

  private val projectImportService = ZIO.serviceWithZIO[ProjectImportService]
  private val triplestore          = ZIO.serviceWithZIO[TriplestoreService]

  private val trigContent =
    """
      |@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
      |@prefix owl: <http://www.w3.org/2002/07/owl#> .
      |
      |<http://example.org/graph> {
      |  <http://www.knora.org/ontology/0001/freetest>  rdf:type  owl:Ontology .
      |}
      |""".stripMargin

  override val e2eSpec =
    suite("ImportService")(test("should import a trig file into a named graph and the default graph") {
      val queryDefaultGraph = Select(
        """
          |SELECT ?subject ?predicate ?object
          |WHERE {
          |  ?subject ?predicate ?object.
          |}
          |""".stripMargin,
      )
      for {
        sizeDefaultGraphBefore <- triplestore(_.query(queryDefaultGraph)).map(_.size)
        filePath               <- FileTestUtil.createTempTextFileScoped(trigContent, ".trig")
        _                      <- projectImportService(_.importTrigFile(filePath))
        nrResultsInNamedGraph  <- triplestore(
                                   _.query(
                                     Select(
                                       """
                                         |SELECT ?subject ?predicate ?object
                                         |FROM NAMED <http://example.org/graph>
                                         |WHERE {
                                         |  GRAPH <http://example.org/graph> {
                                         |    ?subject ?predicate ?object.
                                         |  }
                                         |}
                                         |""".stripMargin,
                                     ),
                                   ),
                                 ).map(_.size)
        sizeDefaultGraphAfter <- triplestore(_.query(queryDefaultGraph)).map(_.size)
      } yield assertTrue(nrResultsInNamedGraph == 1, sizeDefaultGraphAfter == sizeDefaultGraphBefore + 1)
    })
}

object FileTestUtil {
  def createTempTextFileScoped(content: String, suffix: String): ZIO[Scope, IOException, Path] = for {
    filePath <- Files.createTempFileScoped(suffix)
    _        <- Files.writeBytes(filePath, Chunk.fromIterable(content.getBytes))
  } yield filePath
}
