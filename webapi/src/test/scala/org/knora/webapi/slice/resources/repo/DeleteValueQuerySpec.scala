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
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.twirl.SparqlTemplateLinkUpdate
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ValueIri
import org.knora.webapi.slice.common.ResourceIri

object DeleteValueQuerySpec extends ZIOSpecDefault {

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
  private val testResourceIri    = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing1")
  private val testPropertyIri    = PropertyIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#hasText".toSmartIri)
  private val testValueIri       = ValueIri.unsafeFrom("http://rdfh.ch/0001/thing1/values/value1".toSmartIri)
  private val testCurrentTime    = Instant.parse("2024-01-15T10:30:00Z")
  private val testRequestingUser = UserIri.unsafeFrom("http://rdfh.ch/users/root")

  private val testLinkPropertyIri     = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo".toSmartIri
  private val testLinkTargetIri       = "http://rdfh.ch/0001/thing2"
  private val testNewLinkValueIri     = "http://rdfh.ch/0001/thing1/values/newLinkValue"
  private val testNewLinkValueCreator = "http://rdfh.ch/users/systemUser"
  private val testNewLinkValuePerms   = "CR knora-admin:Creator"

  private def createValidLinkUpdate(
    linkPropertyIri: SmartIri = testLinkPropertyIri,
    linkTargetIri: String = testLinkTargetIri,
    newLinkValueIri: String = testNewLinkValueIri,
    currentReferenceCount: Int = 2,
    newReferenceCount: Int = 1,
    newLinkValueCreator: String = testNewLinkValueCreator,
    newLinkValuePermissions: String = testNewLinkValuePerms,
  ): SparqlTemplateLinkUpdate = SparqlTemplateLinkUpdate(
    linkPropertyIri = linkPropertyIri,
    directLinkExists = true,
    insertDirectLink = false,
    deleteDirectLink = false,
    linkValueExists = true,
    linkTargetExists = true,
    newLinkValueIri = newLinkValueIri,
    linkTargetIri = linkTargetIri,
    currentReferenceCount = currentReferenceCount,
    newReferenceCount = newReferenceCount,
    newLinkValueCreator = newLinkValueCreator,
    newLinkValuePermissions = newLinkValuePermissions,
  )

  override def spec: Spec[TestEnvironment, Any] = suite("DeleteValueQuerySpec")(
    suite("build")(
      test("should produce correct query for deleting a value without link updates or comment") {
        for {
          query <- DeleteValueQuery.build(
                     testProject,
                     testResourceIri,
                     testPropertyIri,
                     testValueIri,
                     None,
                     Seq.empty,
                     testCurrentTime,
                     testRequestingUser,
                   )
        } yield assertTrue(
          query.getQueryString ==
            """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
              |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?resourceLastModificationDate .
              |<http://rdfh.ch/0001/thing1/values/value1> knora-base:isDeleted false . } }
              |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1/values/value1> knora-base:isDeleted true ;
              |    knora-base:deletedBy <http://rdfh.ch/users/root> ;
              |    knora-base:deleteDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
              |<http://rdfh.ch/0001/thing1> knora-base:lastModificationDate "2024-01-15T10:30:00Z"^^xsd:dateTime . } }
              |WHERE { <http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasText> <http://rdfh.ch/0001/thing1/values/value1> .
              |<http://rdfh.ch/0001/thing1/values/value1> a ?valueClass ;
              |    knora-base:isDeleted false .
              |?valueClass rdfs:subClassOf* knora-base:Value .
              |OPTIONAL { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?resourceLastModificationDate . } }""".stripMargin,
        )
      },
      test("should produce correct query with a delete comment") {
        for {
          query <- DeleteValueQuery.build(
                     testProject,
                     testResourceIri,
                     testPropertyIri,
                     testValueIri,
                     Some("This value is no longer needed"),
                     Seq.empty,
                     testCurrentTime,
                     testRequestingUser,
                   )
        } yield assertTrue(
          query.getQueryString ==
            """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
              |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?resourceLastModificationDate .
              |<http://rdfh.ch/0001/thing1/values/value1> knora-base:isDeleted false . } }
              |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1/values/value1> knora-base:isDeleted true ;
              |    knora-base:deletedBy <http://rdfh.ch/users/root> ;
              |    knora-base:deleteDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
              |<http://rdfh.ch/0001/thing1/values/value1> knora-base:deleteComment "This value is no longer needed" .
              |<http://rdfh.ch/0001/thing1> knora-base:lastModificationDate "2024-01-15T10:30:00Z"^^xsd:dateTime . } }
              |WHERE { <http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasText> <http://rdfh.ch/0001/thing1/values/value1> .
              |<http://rdfh.ch/0001/thing1/values/value1> a ?valueClass ;
              |    knora-base:isDeleted false .
              |?valueClass rdfs:subClassOf* knora-base:Value .
              |OPTIONAL { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?resourceLastModificationDate . } }""".stripMargin,
        )
      },
      test("should produce correct query with one link update (newReferenceCount > 0)") {
        val linkUpdate = createValidLinkUpdate(currentReferenceCount = 2, newReferenceCount = 1)
        for {
          query <- DeleteValueQuery.build(
                     testProject,
                     testResourceIri,
                     testPropertyIri,
                     testValueIri,
                     None,
                     Seq(linkUpdate),
                     testCurrentTime,
                     testRequestingUser,
                   )
        } yield assertTrue(
          query.getQueryString ==
            """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
              |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?resourceLastModificationDate .
              |<http://rdfh.ch/0001/thing1/values/value1> knora-base:isDeleted false .
              |<http://rdfh.ch/0001/thing1> knora-base:hasStandoffLinkToValue ?linkValue0 .
              |?linkValue0 knora-base:valueHasUUID ?linkValueUUID0 . } }
              |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1/values/value1> knora-base:isDeleted true ;
              |    knora-base:deletedBy <http://rdfh.ch/users/root> ;
              |    knora-base:deleteDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> a knora-base:LinkValue .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:subject <http://rdfh.ch/0001/thing1> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:predicate knora-base:hasStandoffLinkTo .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:object <http://rdfh.ch/0001/thing2> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasString "http://rdfh.ch/0001/thing2"^^xsd:string .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasRefCount 1 .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:isDeleted false .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueCreationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:attachedToUser <http://rdfh.ch/users/systemUser> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:hasPermissions "CR knora-admin:Creator"^^xsd:string .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:previousValue ?linkValue0 .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasUUID ?linkValueUUID0 .
              |<http://rdfh.ch/0001/thing1> knora-base:hasStandoffLinkToValue <http://rdfh.ch/0001/thing1/values/newLinkValue> .
              |<http://rdfh.ch/0001/thing1> knora-base:lastModificationDate "2024-01-15T10:30:00Z"^^xsd:dateTime . } }
              |WHERE { <http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasText> <http://rdfh.ch/0001/thing1/values/value1> .
              |<http://rdfh.ch/0001/thing1/values/value1> a ?valueClass ;
              |    knora-base:isDeleted false .
              |?valueClass rdfs:subClassOf* knora-base:Value .
              |<http://rdfh.ch/0001/thing1> knora-base:hasStandoffLinkTo <http://rdfh.ch/0001/thing2> .
              |<http://rdfh.ch/0001/thing1> knora-base:hasStandoffLinkToValue ?linkValue0 .
              |?linkValue0 a knora-base:LinkValue ;
              |    rdf:subject <http://rdfh.ch/0001/thing1> ;
              |    rdf:predicate knora-base:hasStandoffLinkTo ;
              |    rdf:object <http://rdfh.ch/0001/thing2> ;
              |    knora-base:valueHasRefCount 2 ;
              |    knora-base:isDeleted false ;
              |    knora-base:valueHasUUID ?linkValueUUID0 .
              |OPTIONAL { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?resourceLastModificationDate . } }""".stripMargin,
        )
      },
      test("should produce correct query with one link update (newReferenceCount == 0, with deleteDirectLink)") {
        val linkUpdate = createValidLinkUpdate(
          currentReferenceCount = 1,
          newReferenceCount = 0,
        ).copy(deleteDirectLink = true)
        for {
          query <- DeleteValueQuery.build(
                     testProject,
                     testResourceIri,
                     testPropertyIri,
                     testValueIri,
                     None,
                     Seq(linkUpdate),
                     testCurrentTime,
                     testRequestingUser,
                   )
        } yield assertTrue(
          query.getQueryString ==
            """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
              |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?resourceLastModificationDate .
              |<http://rdfh.ch/0001/thing1/values/value1> knora-base:isDeleted false .
              |<http://rdfh.ch/0001/thing1> knora-base:hasStandoffLinkTo <http://rdfh.ch/0001/thing2> .
              |<http://rdfh.ch/0001/thing1> knora-base:hasStandoffLinkToValue ?linkValue0 .
              |?linkValue0 knora-base:valueHasUUID ?linkValueUUID0 . } }
              |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1/values/value1> knora-base:isDeleted true ;
              |    knora-base:deletedBy <http://rdfh.ch/users/root> ;
              |    knora-base:deleteDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> a knora-base:LinkValue .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:subject <http://rdfh.ch/0001/thing1> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:predicate knora-base:hasStandoffLinkTo .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:object <http://rdfh.ch/0001/thing2> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasString "http://rdfh.ch/0001/thing2"^^xsd:string .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasRefCount 0 .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:isDeleted true .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:deletedBy <http://rdfh.ch/users/systemUser> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:deleteDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueCreationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:attachedToUser <http://rdfh.ch/users/systemUser> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:hasPermissions "CR knora-admin:Creator"^^xsd:string .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:previousValue ?linkValue0 .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasUUID ?linkValueUUID0 .
              |<http://rdfh.ch/0001/thing1> knora-base:hasStandoffLinkToValue <http://rdfh.ch/0001/thing1/values/newLinkValue> .
              |<http://rdfh.ch/0001/thing1> knora-base:lastModificationDate "2024-01-15T10:30:00Z"^^xsd:dateTime . } }
              |WHERE { <http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasText> <http://rdfh.ch/0001/thing1/values/value1> .
              |<http://rdfh.ch/0001/thing1/values/value1> a ?valueClass ;
              |    knora-base:isDeleted false .
              |?valueClass rdfs:subClassOf* knora-base:Value .
              |<http://rdfh.ch/0001/thing1> knora-base:hasStandoffLinkTo <http://rdfh.ch/0001/thing2> .
              |<http://rdfh.ch/0001/thing1> knora-base:hasStandoffLinkToValue ?linkValue0 .
              |?linkValue0 a knora-base:LinkValue ;
              |    rdf:subject <http://rdfh.ch/0001/thing1> ;
              |    rdf:predicate knora-base:hasStandoffLinkTo ;
              |    rdf:object <http://rdfh.ch/0001/thing2> ;
              |    knora-base:valueHasRefCount 1 ;
              |    knora-base:isDeleted false ;
              |    knora-base:valueHasUUID ?linkValueUUID0 .
              |OPTIONAL { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?resourceLastModificationDate . } }""".stripMargin,
        )
      },
    ),
    suite("validation")(
      test("should fail when insertDirectLink is true") {
        val invalidLinkUpdate = createValidLinkUpdate().copy(insertDirectLink = true)
        val effect            = DeleteValueQuery.build(
          testProject,
          testResourceIri,
          testPropertyIri,
          testValueIri,
          None,
          Seq(invalidLinkUpdate),
          testCurrentTime,
          testRequestingUser,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("insertDirectLink must be false"))),
        )
      },
      test("should fail when directLinkExists is false") {
        val invalidLinkUpdate = createValidLinkUpdate().copy(directLinkExists = false)
        val effect            = DeleteValueQuery.build(
          testProject,
          testResourceIri,
          testPropertyIri,
          testValueIri,
          None,
          Seq(invalidLinkUpdate),
          testCurrentTime,
          testRequestingUser,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("directLinkExists must be true"))),
        )
      },
      test("should fail when linkValueExists is false") {
        val invalidLinkUpdate = createValidLinkUpdate().copy(linkValueExists = false)
        val effect            = DeleteValueQuery.build(
          testProject,
          testResourceIri,
          testPropertyIri,
          testValueIri,
          None,
          Seq(invalidLinkUpdate),
          testCurrentTime,
          testRequestingUser,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("linkValueExists must be true"))),
        )
      },
    ),
  )
}
