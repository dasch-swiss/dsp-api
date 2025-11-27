/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.*
import zio.NonEmptyChunk
import zio.test.*

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object CreateOntologyQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testOntologyIri: OntologyIri =
    OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/test-onto/v2".toSmartIri)

  private val testProject = KnoraProject(
    ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
    Shortname.unsafeFrom("test-project"),
    Shortcode.unsafeFrom("0001"),
    Some(Longname.unsafeFrom("Test Project")),
    NonEmptyChunk(Description.unsafeFrom("A test project", Some("en"))),
    List(Keyword.unsafeFrom("test")),
    Some(Logo.unsafeFrom("logo.png")),
    Status.Active,
    SelfJoin.CannotJoin,
    RestrictedView.default,
    Set(CopyrightHolder.unsafeFrom("Test Holder")),
    Set(LicenseIri.CC_BY_4_0),
  )

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("CreateOntologyQuerySpec")(
    test("should produce correct query with comment") {
      CreateOntologyQuery
        .build(
          ontologyIri = testOntologyIri,
          project = testProject,
          isShared = false,
          label = """"Test" Ontology""",
          comment = Some("""A test "ontology""""),
        )
        .map { case (actual: Update, _) =>
          assertTrue(
            actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                               |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                               |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                               |INSERT { GRAPH <http://www.knora.org/ontology/0001/test-onto> { <http://www.knora.org/ontology/0001/test-onto> a owl:Ontology ;
                               |    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
                               |    knora-base:isShared false ;
                               |    rdfs:label "\"Test\" Ontology" ;
                               |    knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                               |<http://www.knora.org/ontology/0001/test-onto> rdfs:comment "A test \"ontology\"" . } }
                               |WHERE { FILTER NOT EXIST { <http://www.knora.org/ontology/0001/test-onto> a ?existingOntologyType . } }""".stripMargin,
          )
        }
    },
    test("should produce correct query without comment") {
      CreateOntologyQuery
        .build(
          ontologyIri = testOntologyIri,
          project = testProject,
          isShared = true,
          label = "Test Ontology",
          comment = None,
        )
        .map { case (actual: Update, _) =>
          assertTrue(
            actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                               |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                               |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                               |INSERT { GRAPH <http://www.knora.org/ontology/0001/test-onto> { <http://www.knora.org/ontology/0001/test-onto> a owl:Ontology ;
                               |    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
                               |    knora-base:isShared true ;
                               |    rdfs:label "Test Ontology" ;
                               |    knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime . } }
                               |WHERE { FILTER NOT EXIST { <http://www.knora.org/ontology/0001/test-onto> a ?existingOntologyType . } }""".stripMargin,
          )
        }
    },
  )
}
