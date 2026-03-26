/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*

import java.time.Instant

object DeleteResourceQuerySpec extends ZIOSpecDefault {

  private val dataNamedGraph = "http://www.knora.org/data/0001/anything"
  private val resourceIri    = "http://rdfh.ch/0001/thing-with-history"
  private val currentTime    = Instant.parse("2024-01-01T10:00:00Z")
  private val requestingUser = "http://rdfh.ch/users/root"

  private def normalize(s: String): String =
    s.trim.linesIterator.map(_.trim).filter(_.nonEmpty).mkString("\n")

  override def spec: Spec[TestEnvironment, Any] = suite("DeleteResourceQuery")(
    test("build should produce the expected SPARQL query without delete comment") {
      val actual = normalize(
        DeleteResourceQuery
          .build(
            dataNamedGraph = dataNamedGraph,
            resourceIri = resourceIri,
            maybeDeleteComment = None,
            currentTime = currentTime,
            requestingUser = requestingUser,
          )
          .getQueryString,
      )
      assertTrue(
        actual == normalize(
          s"""PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
             |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
             |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
             |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
             |DELETE { GRAPH <$dataNamedGraph> { <$resourceIri> knora-base:lastModificationDate ?resourceLastModificationDate .
             |<$resourceIri> knora-base:isDeleted false . } }
             |INSERT { GRAPH <$dataNamedGraph> { <$resourceIri> knora-base:isDeleted true ;
             |knora-base:deletedBy <$requestingUser> ;
             |knora-base:deleteDate "$currentTime"^^xsd:dateTime .
             |<$resourceIri> knora-base:lastModificationDate "$currentTime"^^xsd:dateTime . } }
             |WHERE { { <$resourceIri> rdf:type ?resourceClass ;
             |knora-base:isDeleted false .
             |?resourceClass rdfs:subClassOf* knora-base:Resource . }
             |OPTIONAL { <$resourceIri> knora-base:lastModificationDate ?resourceLastModificationDate . } }""".stripMargin,
        ),
      )
    },
    test("build should produce the expected SPARQL query with delete comment") {
      val actual = normalize(
        DeleteResourceQuery
          .build(
            dataNamedGraph = dataNamedGraph,
            resourceIri = resourceIri,
            maybeDeleteComment = Some("This resource is no longer needed"),
            currentTime = currentTime,
            requestingUser = requestingUser,
          )
          .getQueryString,
      )
      assertTrue(
        actual == normalize(
          s"""PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
             |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
             |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
             |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
             |DELETE { GRAPH <$dataNamedGraph> { <$resourceIri> knora-base:lastModificationDate ?resourceLastModificationDate .
             |<$resourceIri> knora-base:isDeleted false . } }
             |INSERT { GRAPH <$dataNamedGraph> { <$resourceIri> knora-base:isDeleted true ;
             |knora-base:deletedBy <$requestingUser> ;
             |knora-base:deleteDate "$currentTime"^^xsd:dateTime .
             |<$resourceIri> knora-base:deleteComment "This resource is no longer needed" .
             |<$resourceIri> knora-base:lastModificationDate "$currentTime"^^xsd:dateTime . } }
             |WHERE { { <$resourceIri> rdf:type ?resourceClass ;
             |knora-base:isDeleted false .
             |?resourceClass rdfs:subClassOf* knora-base:Resource . }
             |OPTIONAL { <$resourceIri> knora-base:lastModificationDate ?resourceLastModificationDate . } }""".stripMargin,
        ),
      )
    },
  )
}
