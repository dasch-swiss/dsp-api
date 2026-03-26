/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*

import java.time.Instant

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.KnoraIris.ResourceIri

object DeleteResourceQuerySpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testProject = Project(
    ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
    Shortname.unsafeFrom("anything"),
    Shortcode.unsafeFrom("0001"),
    None,
    Seq(StringLiteralV2.from("Test project")),
    List.empty,
    None,
    Seq.empty,
    Status.Active,
    SelfJoin.CannotJoin,
    Set.empty,
    Set.empty,
  )

  private val dataNamedGraph = "http://www.knora.org/data/0001/anything"
  private val resourceIri    = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing-with-history".toSmartIri)
  private val currentTime    = Instant.parse("2024-01-01T10:00:00Z")
  private val requestingUser = UserIri.unsafeFrom("http://rdfh.ch/users/root")

  private def normalize(s: String): String =
    s.trim.linesIterator.map(_.trim).filter(_.nonEmpty).mkString("\n")

  override def spec: Spec[TestEnvironment, Any] = suite("DeleteResourceQuery")(
    test("build should produce the expected SPARQL query without delete comment") {
      val actual = normalize(
        DeleteResourceQuery
          .build(
            project = testProject,
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
             |DELETE { GRAPH <$dataNamedGraph> { <http://rdfh.ch/0001/thing-with-history> knora-base:lastModificationDate ?resourceLastModificationDate .
             |<http://rdfh.ch/0001/thing-with-history> knora-base:isDeleted false . } }
             |INSERT { GRAPH <$dataNamedGraph> { <http://rdfh.ch/0001/thing-with-history> knora-base:isDeleted true ;
             |knora-base:deletedBy <http://rdfh.ch/users/root> ;
             |knora-base:deleteDate "$currentTime"^^xsd:dateTime .
             |<http://rdfh.ch/0001/thing-with-history> knora-base:lastModificationDate "$currentTime"^^xsd:dateTime . } }
             |WHERE { { <http://rdfh.ch/0001/thing-with-history> rdf:type ?resourceClass ;
             |knora-base:isDeleted false .
             |?resourceClass rdfs:subClassOf* knora-base:Resource . }
             |OPTIONAL { <http://rdfh.ch/0001/thing-with-history> knora-base:lastModificationDate ?resourceLastModificationDate . } }""".stripMargin,
        ),
      )
    },
    test("build should produce the expected SPARQL query with delete comment") {
      val actual = normalize(
        DeleteResourceQuery
          .build(
            project = testProject,
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
             |DELETE { GRAPH <$dataNamedGraph> { <http://rdfh.ch/0001/thing-with-history> knora-base:lastModificationDate ?resourceLastModificationDate .
             |<http://rdfh.ch/0001/thing-with-history> knora-base:isDeleted false . } }
             |INSERT { GRAPH <$dataNamedGraph> { <http://rdfh.ch/0001/thing-with-history> knora-base:isDeleted true ;
             |knora-base:deletedBy <http://rdfh.ch/users/root> ;
             |knora-base:deleteDate "$currentTime"^^xsd:dateTime .
             |<http://rdfh.ch/0001/thing-with-history> knora-base:deleteComment "This resource is no longer needed" .
             |<http://rdfh.ch/0001/thing-with-history> knora-base:lastModificationDate "$currentTime"^^xsd:dateTime . } }
             |WHERE { { <http://rdfh.ch/0001/thing-with-history> rdf:type ?resourceClass ;
             |knora-base:isDeleted false .
             |?resourceClass rdfs:subClassOf* knora-base:Resource . }
             |OPTIONAL { <http://rdfh.ch/0001/thing-with-history> knora-base:lastModificationDate ?resourceLastModificationDate . } }""".stripMargin,
        ),
      )
    },
  )
}
