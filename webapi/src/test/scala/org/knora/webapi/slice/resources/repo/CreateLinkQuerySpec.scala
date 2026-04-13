/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*
import zio.test.Assertion.*

import java.time.Instant
import java.util.UUID

import dsp.errors.SparqlGenerationException
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.slice.resources.repo.model.SparqlTemplateLinkUpdate

object CreateLinkQuerySpec extends ZIOSpecDefault {

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

  private val testResourceIri         = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing1".toSmartIri)
  private val testLinkPropertyIri     = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri
  private val testLinkTargetIri       = "http://rdfh.ch/0001/thing2"
  private val testNewLinkValueIri     = "http://rdfh.ch/0001/thing1/values/newLinkValue"
  private val testNewLinkValueCreator = "http://rdfh.ch/users/creator1"
  private val testNewLinkValuePerms   = "CR knora-admin:Creator"
  private val testNewReferenceCount   = 1
  private val testCreationDate        = Instant.parse("2024-01-15T10:30:00Z")
  private val testNewValueUUID        = UUID.fromString("4b47a956-a532-4252-86d4-e78ec3606c8c")
  private val testBase64UUID          = UuidUtil.base64Encode(testNewValueUUID)

  private def createValidSparqlTemplateLinkUpdate(
    linkTargetExists: Boolean = true,
  ): SparqlTemplateLinkUpdate = SparqlTemplateLinkUpdate(
    linkPropertyIri = testLinkPropertyIri,
    directLinkExists = false,
    insertDirectLink = true,
    deleteDirectLink = false,
    linkValueExists = false,
    linkTargetExists = linkTargetExists,
    newLinkValueIri = testNewLinkValueIri,
    linkTargetIri = testLinkTargetIri,
    currentReferenceCount = 0,
    newReferenceCount = testNewReferenceCount,
    newLinkValueCreator = testNewLinkValueCreator,
    newLinkValuePermissions = testNewLinkValuePerms,
  )

  override def spec: Spec[TestEnvironment, Any] = suite("CreateLinkQuerySpec")(
    suite("build")(
      test("should produce correct query when linkTargetExists and no comment") {
        for {
          query <- CreateLinkQuery.build(
                     testProject,
                     testResourceIri,
                     createValidSparqlTemplateLinkUpdate(),
                     testNewValueUUID,
                     testCreationDate,
                     None,
                   )
        } yield assertTrue(
          query.getQueryString ==
            s"""PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX owl: <http://www.w3.org/2002/07/owl#>
               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?resourceLastModificationDate . } }
               |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThing> <http://rdfh.ch/0001/thing2> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> a knora-base:LinkValue .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:subject <http://rdfh.ch/0001/thing1> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:predicate <http://www.knora.org/ontology/0001/anything#hasOtherThing> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:object <http://rdfh.ch/0001/thing2> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasString "http://rdfh.ch/0001/thing2"^^xsd:string .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasRefCount 1 .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasOrder ?nextOrder .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:isDeleted false .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasUUID "$testBase64UUID" .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueCreationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:attachedToUser <http://rdfh.ch/users/creator1> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:hasPermissions "CR knora-admin:Creator"^^xsd:string .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> <http://rdfh.ch/0001/thing1/values/newLinkValue> . } }
               |WHERE { <http://rdfh.ch/0001/thing1> a ?resourceClass ;
               |    knora-base:isDeleted false .
               |?resourceClass rdfs:subClassOf* knora-base:Resource .
               |OPTIONAL { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?resourceLastModificationDate . }
               |<http://rdfh.ch/0001/thing2> a ?linkTargetClass .
               |?linkTargetClass rdfs:subClassOf* knora-base:Resource .
               |<http://www.knora.org/ontology/0001/anything#hasOtherThing> knora-base:objectClassConstraint ?expectedTargetClass .
               |?linkTargetClass rdfs:subClassOf* ?expectedTargetClass .
               |<http://rdfh.ch/0001/thing2> knora-base:isDeleted false .
               |?resourceClass rdfs:subClassOf* ?restriction .
               |?restriction a owl:Restriction .
               |?restriction owl:onProperty <http://www.knora.org/ontology/0001/anything#hasOtherThing> .
               |{ SELECT ( MAX( ?order ) AS ?maxOrder ) ( IF( BOUND( ?maxOrder ), ( ?maxOrder + 1 ), 0 ) AS ?nextOrder )
               |WHERE { <http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?otherLinkValue .
               |?otherLinkValue knora-base:valueHasOrder ?order ;
               |    knora-base:isDeleted false . }
               | } }""".stripMargin,
        )
      },
      test("should produce correct query when linkTargetExists with comment") {
        for {
          query <- CreateLinkQuery.build(
                     testProject,
                     testResourceIri,
                     createValidSparqlTemplateLinkUpdate(),
                     testNewValueUUID,
                     testCreationDate,
                     Some("This is a test comment"),
                   )
        } yield assertTrue(
          query.getQueryString ==
            s"""PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX owl: <http://www.w3.org/2002/07/owl#>
               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?resourceLastModificationDate . } }
               |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThing> <http://rdfh.ch/0001/thing2> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> a knora-base:LinkValue .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:subject <http://rdfh.ch/0001/thing1> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:predicate <http://www.knora.org/ontology/0001/anything#hasOtherThing> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:object <http://rdfh.ch/0001/thing2> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasString "http://rdfh.ch/0001/thing2"^^xsd:string .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasRefCount 1 .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasOrder ?nextOrder .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:isDeleted false .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasUUID "$testBase64UUID" .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueCreationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:attachedToUser <http://rdfh.ch/users/creator1> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:hasPermissions "CR knora-admin:Creator"^^xsd:string .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> <http://rdfh.ch/0001/thing1/values/newLinkValue> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasComment "This is a test comment" . } }
               |WHERE { <http://rdfh.ch/0001/thing1> a ?resourceClass ;
               |    knora-base:isDeleted false .
               |?resourceClass rdfs:subClassOf* knora-base:Resource .
               |OPTIONAL { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?resourceLastModificationDate . }
               |<http://rdfh.ch/0001/thing2> a ?linkTargetClass .
               |?linkTargetClass rdfs:subClassOf* knora-base:Resource .
               |<http://www.knora.org/ontology/0001/anything#hasOtherThing> knora-base:objectClassConstraint ?expectedTargetClass .
               |?linkTargetClass rdfs:subClassOf* ?expectedTargetClass .
               |<http://rdfh.ch/0001/thing2> knora-base:isDeleted false .
               |?resourceClass rdfs:subClassOf* ?restriction .
               |?restriction a owl:Restriction .
               |?restriction owl:onProperty <http://www.knora.org/ontology/0001/anything#hasOtherThing> .
               |{ SELECT ( MAX( ?order ) AS ?maxOrder ) ( IF( BOUND( ?maxOrder ), ( ?maxOrder + 1 ), 0 ) AS ?nextOrder )
               |WHERE { <http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?otherLinkValue .
               |?otherLinkValue knora-base:valueHasOrder ?order ;
               |    knora-base:isDeleted false . }
               | } }""".stripMargin,
        )
      },
      test("should produce correct query when linkTargetExists is false") {
        for {
          query <- CreateLinkQuery.build(
                     testProject,
                     testResourceIri,
                     createValidSparqlTemplateLinkUpdate(linkTargetExists = false),
                     testNewValueUUID,
                     testCreationDate,
                     None,
                   )
        } yield assertTrue(
          query.getQueryString ==
            s"""PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX owl: <http://www.w3.org/2002/07/owl#>
               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?resourceLastModificationDate . } }
               |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThing> <http://rdfh.ch/0001/thing2> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> a knora-base:LinkValue .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:subject <http://rdfh.ch/0001/thing1> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:predicate <http://www.knora.org/ontology/0001/anything#hasOtherThing> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:object <http://rdfh.ch/0001/thing2> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasString "http://rdfh.ch/0001/thing2"^^xsd:string .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasRefCount 1 .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasOrder ?nextOrder .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:isDeleted false .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasUUID "$testBase64UUID" .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueCreationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:attachedToUser <http://rdfh.ch/users/creator1> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:hasPermissions "CR knora-admin:Creator"^^xsd:string .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> <http://rdfh.ch/0001/thing1/values/newLinkValue> . } }
               |WHERE { <http://rdfh.ch/0001/thing1> a ?resourceClass ;
               |    knora-base:isDeleted false .
               |?resourceClass rdfs:subClassOf* knora-base:Resource .
               |OPTIONAL { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?resourceLastModificationDate . }
               |{ SELECT ( MAX( ?order ) AS ?maxOrder ) ( IF( BOUND( ?maxOrder ), ( ?maxOrder + 1 ), 0 ) AS ?nextOrder )
               |WHERE { <http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?otherLinkValue .
               |?otherLinkValue knora-base:valueHasOrder ?order ;
               |    knora-base:isDeleted false . }
               | } }""".stripMargin,
        )
      },
    ),
    suite("validation")(
      test("should fail when insertDirectLink is false") {
        val invalidLinkUpdate = SparqlTemplateLinkUpdate(
          linkPropertyIri = testLinkPropertyIri,
          directLinkExists = false,
          insertDirectLink = false,
          deleteDirectLink = false,
          linkValueExists = false,
          linkTargetExists = true,
          newLinkValueIri = testNewLinkValueIri,
          linkTargetIri = testLinkTargetIri,
          currentReferenceCount = 0,
          newReferenceCount = testNewReferenceCount,
          newLinkValueCreator = testNewLinkValueCreator,
          newLinkValuePermissions = testNewLinkValuePerms,
        )
        val effect = CreateLinkQuery.build(
          testProject,
          testResourceIri,
          invalidLinkUpdate,
          testNewValueUUID,
          testCreationDate,
          None,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("insertDirectLink must be true"))),
        )
      },
      test("should fail when directLinkExists is true") {
        val invalidLinkUpdate = SparqlTemplateLinkUpdate(
          linkPropertyIri = testLinkPropertyIri,
          directLinkExists = true,
          insertDirectLink = true,
          deleteDirectLink = false,
          linkValueExists = false,
          linkTargetExists = true,
          newLinkValueIri = testNewLinkValueIri,
          linkTargetIri = testLinkTargetIri,
          currentReferenceCount = 0,
          newReferenceCount = testNewReferenceCount,
          newLinkValueCreator = testNewLinkValueCreator,
          newLinkValuePermissions = testNewLinkValuePerms,
        )
        val effect = CreateLinkQuery.build(
          testProject,
          testResourceIri,
          invalidLinkUpdate,
          testNewValueUUID,
          testCreationDate,
          None,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("directLinkExists must be false"))),
        )
      },
      test("should fail when linkValueExists is true") {
        val invalidLinkUpdate = SparqlTemplateLinkUpdate(
          linkPropertyIri = testLinkPropertyIri,
          directLinkExists = false,
          insertDirectLink = true,
          deleteDirectLink = false,
          linkValueExists = true,
          linkTargetExists = true,
          newLinkValueIri = testNewLinkValueIri,
          linkTargetIri = testLinkTargetIri,
          currentReferenceCount = 0,
          newReferenceCount = testNewReferenceCount,
          newLinkValueCreator = testNewLinkValueCreator,
          newLinkValuePermissions = testNewLinkValuePerms,
        )
        val effect = CreateLinkQuery.build(
          testProject,
          testResourceIri,
          invalidLinkUpdate,
          testNewValueUUID,
          testCreationDate,
          None,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("linkValueExists must be false"))),
        )
      },
    ),
  )
}
