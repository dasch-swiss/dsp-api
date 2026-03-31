/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import eu.timepit.refined.types.string.NonEmptyString
import zio.test.*

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris.OntologyIri

object CreateOntologyQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val ontologyIri     = OntologyIri.unsafeFrom("http://www.knora.org/ontology/0001/anything".toSmartIri)
  private val projectIri      = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val ontologyLabel   = "The anything ontology"
  private val ontologyComment = Some(NonEmptyString.unsafeFrom("A test ontology"))

  override def spec: Spec[Any, Nothing] = suite("CreateOntologyQuery")(
    test("build should produce the expected SPARQL query with comment") {
      CreateOntologyQuery
        .build(
          ontologyIri = ontologyIri,
          projectIri = projectIri,
          isShared = false,
          ontologyLabel = ontologyLabel,
          ontologyComment = ontologyComment,
        )
        .map { case (lmd, update) =>
          val ts = lmd.value.toString
          assertTrue(
            update.sparql ==
              s"""PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                 |    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
                 |    knora-base:isShared false ;
                 |    rdfs:label "The anything ontology"^^xsd:string ;
                 |    rdfs:comment "A test ontology"^^xsd:string ;
                 |    knora-base:lastModificationDate "$ts"^^xsd:dateTime . } }
                 |WHERE { FILTER NOT EXISTS { <http://www.knora.org/ontology/0001/anything> a ?existingOntologyType . } }""".stripMargin,
          )
        }
    },
    test("build should produce the expected SPARQL query without comment") {
      CreateOntologyQuery
        .build(
          ontologyIri = ontologyIri,
          projectIri = projectIri,
          isShared = false,
          ontologyLabel = ontologyLabel,
          ontologyComment = None,
        )
        .map { case (lmd, update) =>
          val ts = lmd.value.toString
          assertTrue(
            update.sparql ==
              s"""PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                 |    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
                 |    knora-base:isShared false ;
                 |    rdfs:label "The anything ontology"^^xsd:string ;
                 |    knora-base:lastModificationDate "$ts"^^xsd:dateTime . } }
                 |WHERE { FILTER NOT EXISTS { <http://www.knora.org/ontology/0001/anything> a ?existingOntologyType . } }""".stripMargin,
          )
        }
    },
    test("build should produce isShared true when shared") {
      CreateOntologyQuery
        .build(
          ontologyIri = ontologyIri,
          projectIri = projectIri,
          isShared = true,
          ontologyLabel = ontologyLabel,
          ontologyComment = None,
        )
        .map { case (_, update) =>
          assertTrue(update.sparql.contains("knora-base:isShared true"))
        }
    },
    test("build should return a LastModificationDate matching the clock") {
      CreateOntologyQuery
        .build(
          ontologyIri = ontologyIri,
          projectIri = projectIri,
          isShared = false,
          ontologyLabel = ontologyLabel,
          ontologyComment = None,
        )
        .map { case (lmd, update) =>
          assertTrue(
            update.sparql.contains(s""""${lmd.value}"^^xsd:dateTime"""),
          )
        }
    },
  )
}
