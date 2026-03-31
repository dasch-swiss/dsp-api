/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import eu.timepit.refined.types.string.NonEmptyString
import zio.test.*

import java.time.Instant

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris.OntologyIri

object CreateOntologyQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val ontologyIri     = OntologyIri.unsafeFrom("http://www.knora.org/ontology/0001/anything".toSmartIri)
  private val projectIri      = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val currentTime     = Instant.parse("2024-01-01T12:00:00Z")
  private val ontologyLabel   = "The anything ontology"
  private val ontologyComment = Some(NonEmptyString.unsafeFrom("A test ontology"))

  override def spec: Spec[Any, Nothing] = suite("CreateOntologyQuery")(
    test("build should produce the expected SPARQL query with comment") {
      val actual = CreateOntologyQuery.build(
        ontologyIri = ontologyIri,
        projectIri = projectIri,
        isShared = false,
        ontologyLabel = ontologyLabel,
        ontologyComment = ontologyComment,
        currentTime = currentTime,
      )
      assertTrue(
        actual ==
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX owl: <http://www.w3.org/2002/07/owl#>
            |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
            |    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
            |    knora-base:isShared false ;
            |    rdfs:label "The anything ontology"^^xsd:string ;
            |    rdfs:comment "A test ontology"^^xsd:string ;
            |    knora-base:lastModificationDate "2024-01-01T12:00:00Z"^^xsd:dateTime . } }
            |WHERE { FILTER NOT EXISTS { <http://www.knora.org/ontology/0001/anything> a ?existingOntologyType . } }""".stripMargin,
      )
    },
    test("build should produce the expected SPARQL query without comment") {
      val actual = CreateOntologyQuery.build(
        ontologyIri = ontologyIri,
        projectIri = projectIri,
        isShared = false,
        ontologyLabel = ontologyLabel,
        ontologyComment = None,
        currentTime = currentTime,
      )
      assertTrue(
        actual ==
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX owl: <http://www.w3.org/2002/07/owl#>
            |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
            |    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
            |    knora-base:isShared false ;
            |    rdfs:label "The anything ontology"^^xsd:string ;
            |    knora-base:lastModificationDate "2024-01-01T12:00:00Z"^^xsd:dateTime . } }
            |WHERE { FILTER NOT EXISTS { <http://www.knora.org/ontology/0001/anything> a ?existingOntologyType . } }""".stripMargin,
      )
    },
    test("build should produce isShared true when shared") {
      val actual = CreateOntologyQuery.build(
        ontologyIri = ontologyIri,
        projectIri = projectIri,
        isShared = true,
        ontologyLabel = ontologyLabel,
        ontologyComment = None,
        currentTime = currentTime,
      )
      assertTrue(actual.contains("knora-base:isShared true"))
    },
  )
}
