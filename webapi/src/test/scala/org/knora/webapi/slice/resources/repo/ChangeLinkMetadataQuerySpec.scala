/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*
import zio.test.Assertion.*

import java.time.Instant

import dsp.errors.SparqlGenerationException
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.twirl.SparqlTemplateLinkUpdate
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.KnoraIris.ResourceIri

object ChangeLinkMetadataQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

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
  private val testLinkSourceIri   = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing1".toSmartIri)
  private val testLinkPropertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri
  private val testLinkTargetIri   = "http://rdfh.ch/0001/thing2"
  private val testNewLinkValueIri = "http://rdfh.ch/0001/thing1/values/newLinkValue"
  private val testRefCount        = 1
  private val testNewLinkCreator  = "http://rdfh.ch/users/creator1"
  private val testNewLinkPerms    = "CR knora-admin:Creator"
  private val testCurrentTime     = Instant.parse("2024-01-15T10:30:00Z")

  private def createLinkUpdate(
    linkValueExists: Boolean = true,
    directLinkExists: Boolean = true,
  ): SparqlTemplateLinkUpdate = SparqlTemplateLinkUpdate(
    linkPropertyIri = testLinkPropertyIri,
    directLinkExists = directLinkExists,
    insertDirectLink = false,
    deleteDirectLink = false,
    linkValueExists = linkValueExists,
    linkTargetExists = true,
    newLinkValueIri = testNewLinkValueIri,
    linkTargetIri = testLinkTargetIri,
    currentReferenceCount = testRefCount,
    newReferenceCount = testRefCount,
    newLinkValueCreator = testNewLinkCreator,
    newLinkValuePermissions = testNewLinkPerms,
  )

  override def spec: Spec[TestEnvironment, Any] = suite("ChangeLinkMetadataQuery")(
    suite("build")(
      test("should produce correct query without comment") {
        for {
          result <- TestClock.setTime(testCurrentTime) *>
                      ChangeLinkMetadataQuery.build(
                        testProject,
                        testLinkSourceIri,
                        createLinkUpdate(),
                        None,
                      )
          (instant, query) = result
        } yield {
          val expected =
            """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
              |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?linkSourceLastModificationDate .
              |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?currentLinkValue .
              |?currentLinkValue knora-base:valueHasUUID ?currentLinkUUID . } }
              |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1/values/newLinkValue> a knora-base:LinkValue .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:subject <http://rdfh.ch/0001/thing1> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:predicate <http://www.knora.org/ontology/0001/anything#hasOtherThing> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:object <http://rdfh.ch/0001/thing2> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasString "http://rdfh.ch/0001/thing2"^^xsd:string .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasRefCount 1 .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueCreationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:previousValue ?currentLinkValue .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasUUID ?currentLinkUUID .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:isDeleted false .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:attachedToUser <http://rdfh.ch/users/creator1> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:hasPermissions "CR knora-admin:Creator"^^xsd:string .
              |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> <http://rdfh.ch/0001/thing1/values/newLinkValue> .
              |<http://rdfh.ch/0001/thing1> knora-base:lastModificationDate "2024-01-15T10:30:00Z"^^xsd:dateTime . } }
              |WHERE { <http://rdfh.ch/0001/thing1> a ?linkSourceClass ;
              |    knora-base:isDeleted false .
              |?linkSourceClass rdfs:subClassOf* knora-base:Resource .
              |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThing> <http://rdfh.ch/0001/thing2> .
              |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?currentLinkValue .
              |?currentLinkValue a knora-base:LinkValue ;
              |    rdf:subject <http://rdfh.ch/0001/thing1> ;
              |    rdf:predicate <http://www.knora.org/ontology/0001/anything#hasOtherThing> ;
              |    rdf:object <http://rdfh.ch/0001/thing2> ;
              |    knora-base:valueHasRefCount 1 ;
              |    knora-base:isDeleted false ;
              |    knora-base:valueHasUUID ?currentLinkUUID .
              |OPTIONAL { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?linkSourceLastModificationDate . } }""".stripMargin
          assertTrue(instant == testCurrentTime) && assertTrue(query.sparql == expected)
        }
      },
      test("should produce correct query with comment") {
        for {
          result <- TestClock.setTime(testCurrentTime) *>
                      ChangeLinkMetadataQuery.build(
                        testProject,
                        testLinkSourceIri,
                        createLinkUpdate(),
                        Some("Updated metadata"),
                      )
          (instant, query) = result
        } yield {
          val expected =
            """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
              |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?linkSourceLastModificationDate .
              |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?currentLinkValue .
              |?currentLinkValue knora-base:valueHasUUID ?currentLinkUUID . } }
              |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1/values/newLinkValue> a knora-base:LinkValue .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:subject <http://rdfh.ch/0001/thing1> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:predicate <http://www.knora.org/ontology/0001/anything#hasOtherThing> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:object <http://rdfh.ch/0001/thing2> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasString "http://rdfh.ch/0001/thing2"^^xsd:string .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasRefCount 1 .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueCreationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:previousValue ?currentLinkValue .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasUUID ?currentLinkUUID .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:isDeleted false .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasComment "Updated metadata" .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:attachedToUser <http://rdfh.ch/users/creator1> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:hasPermissions "CR knora-admin:Creator"^^xsd:string .
              |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> <http://rdfh.ch/0001/thing1/values/newLinkValue> .
              |<http://rdfh.ch/0001/thing1> knora-base:lastModificationDate "2024-01-15T10:30:00Z"^^xsd:dateTime . } }
              |WHERE { <http://rdfh.ch/0001/thing1> a ?linkSourceClass ;
              |    knora-base:isDeleted false .
              |?linkSourceClass rdfs:subClassOf* knora-base:Resource .
              |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThing> <http://rdfh.ch/0001/thing2> .
              |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?currentLinkValue .
              |?currentLinkValue a knora-base:LinkValue ;
              |    rdf:subject <http://rdfh.ch/0001/thing1> ;
              |    rdf:predicate <http://www.knora.org/ontology/0001/anything#hasOtherThing> ;
              |    rdf:object <http://rdfh.ch/0001/thing2> ;
              |    knora-base:valueHasRefCount 1 ;
              |    knora-base:isDeleted false ;
              |    knora-base:valueHasUUID ?currentLinkUUID .
              |OPTIONAL { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?linkSourceLastModificationDate . } }""".stripMargin
          assertTrue(instant == testCurrentTime) && assertTrue(query.sparql == expected)
        }
      },
    ),
    suite("validation")(
      test("should fail when linkValueExists is false") {
        val effect = ChangeLinkMetadataQuery.build(
          testProject,
          testLinkSourceIri,
          createLinkUpdate(linkValueExists = false),
          None,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("linkValueExists must be true"))),
        )
      },
      test("should fail when directLinkExists is false") {
        val effect = ChangeLinkMetadataQuery.build(
          testProject,
          testLinkSourceIri,
          createLinkUpdate(directLinkExists = false),
          None,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("directLinkExists must be true"))),
        )
      },
    ),
  )
}
