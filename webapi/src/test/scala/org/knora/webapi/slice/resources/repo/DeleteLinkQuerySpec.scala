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
import org.knora.webapi.messages.twirl.SparqlTemplateLinkUpdate
import org.knora.webapi.slice.admin.domain.model.UserIri

object DeleteLinkQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testDataNamedGraph        = "http://www.knora.org/data/0001/anything"
  private val testLinkSourceIri         = "http://rdfh.ch/0001/thing1"
  private val testLinkPropertyIri       = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri
  private val testLinkTargetIri         = "http://rdfh.ch/0001/thing2"
  private val testNewLinkValueIri       = "http://rdfh.ch/0001/thing1/values/newLinkValue"
  private val testCurrentReferenceCount = 1
  private val testNewLinkValueCreator   = "http://rdfh.ch/users/creator1"
  private val testNewLinkValuePerms     = "CR knora-admin:Creator"
  private val testCurrentTime           = Instant.parse("2024-01-15T10:30:00Z")
  private val testRequestingUser        = UserIri.unsafeFrom("http://rdfh.ch/users/root")

  private def createValidLinkUpdate(
    linkPropertyIri: SmartIri = testLinkPropertyIri,
    linkTargetIri: String = testLinkTargetIri,
    newLinkValueIri: String = testNewLinkValueIri,
    currentReferenceCount: Int = testCurrentReferenceCount,
    newLinkValueCreator: String = testNewLinkValueCreator,
    newLinkValuePermissions: String = testNewLinkValuePerms,
  ): SparqlTemplateLinkUpdate = SparqlTemplateLinkUpdate(
    linkPropertyIri = linkPropertyIri,
    directLinkExists = true,
    insertDirectLink = false,
    deleteDirectLink = true,
    linkValueExists = true,
    linkTargetExists = true,
    newLinkValueIri = newLinkValueIri,
    linkTargetIri = linkTargetIri,
    currentReferenceCount = currentReferenceCount,
    newReferenceCount = 0,
    newLinkValueCreator = newLinkValueCreator,
    newLinkValuePermissions = newLinkValuePermissions,
  )

  override def spec: Spec[TestEnvironment, Any] = suite("DeleteLinkQuerySpec")(
    suite("build")(
      test("should produce correct query for deleting a link without comment") {
        for {
          query <- DeleteLinkQuery.build(
                     testDataNamedGraph,
                     testLinkSourceIri,
                     createValidLinkUpdate(),
                     None,
                     testCurrentTime,
                     testRequestingUser,
                   )
        } yield assertTrue(
          query.getQueryString ==
            """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
              |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?linkSourceLastModificationDate .
              |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThing> <http://rdfh.ch/0001/thing2> .
              |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?currentLinkValue .
              |?currentLinkValue knora-base:valueHasUUID ?currentLinkUUID . } }
              |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1/values/newLinkValue> a knora-base:LinkValue .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:subject <http://rdfh.ch/0001/thing1> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:predicate <http://www.knora.org/ontology/0001/anything#hasOtherThing> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:object <http://rdfh.ch/0001/thing2> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasString "http://rdfh.ch/0001/thing2"^^xsd:string .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasRefCount 0 .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueCreationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:deleteDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:deletedBy <http://rdfh.ch/users/root> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:previousValue ?currentLinkValue .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasUUID ?currentLinkUUID .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:isDeleted true .
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
              |OPTIONAL { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?linkSourceLastModificationDate . } }""".stripMargin,
        )
      },
      test("should produce correct query for deleting a link with a delete comment") {
        for {
          query <- DeleteLinkQuery.build(
                     testDataNamedGraph,
                     testLinkSourceIri,
                     createValidLinkUpdate(),
                     Some("This link is no longer needed"),
                     testCurrentTime,
                     testRequestingUser,
                   )
        } yield assertTrue(
          query.getQueryString ==
            """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
              |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?linkSourceLastModificationDate .
              |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThing> <http://rdfh.ch/0001/thing2> .
              |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?currentLinkValue .
              |?currentLinkValue knora-base:valueHasUUID ?currentLinkUUID . } }
              |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1/values/newLinkValue> a knora-base:LinkValue .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:subject <http://rdfh.ch/0001/thing1> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:predicate <http://www.knora.org/ontology/0001/anything#hasOtherThing> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:object <http://rdfh.ch/0001/thing2> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasString "http://rdfh.ch/0001/thing2"^^xsd:string .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasRefCount 0 .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueCreationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:deleteDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:deletedBy <http://rdfh.ch/users/root> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:previousValue ?currentLinkValue .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasUUID ?currentLinkUUID .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:isDeleted true .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:attachedToUser <http://rdfh.ch/users/creator1> .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:hasPermissions "CR knora-admin:Creator"^^xsd:string .
              |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> <http://rdfh.ch/0001/thing1/values/newLinkValue> .
              |<http://rdfh.ch/0001/thing1> knora-base:lastModificationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
              |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:deleteComment "This link is no longer needed" . } }
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
              |OPTIONAL { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?linkSourceLastModificationDate . } }""".stripMargin,
        )
      },
      test("should produce correct query for different link property") {
        val differentLinkProperty = "http://www.knora.org/ontology/0803/incunabula#partOf".toSmartIri
        for {
          query <- DeleteLinkQuery.build(
                     "http://www.knora.org/data/0803/incunabula",
                     "http://rdfh.ch/0803/page123",
                     createValidLinkUpdate(
                       linkPropertyIri = differentLinkProperty,
                       linkTargetIri = "http://rdfh.ch/0803/book456",
                       newLinkValueIri = "http://rdfh.ch/0803/page123/values/deletedLink",
                       currentReferenceCount = 1,
                       newLinkValueCreator = "http://rdfh.ch/users/incunabulaUser",
                       newLinkValuePermissions = "CR knora-admin:Creator|V knora-admin:KnownUser",
                     ),
                     None,
                     Instant.parse("2024-02-01T08:00:00Z"),
                     UserIri.unsafeFrom("http://rdfh.ch/users/incunabulaUser"),
                   )
        } yield assertTrue(
          query.getQueryString ==
            """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
              |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |DELETE { GRAPH <http://www.knora.org/data/0803/incunabula> { <http://rdfh.ch/0803/page123> knora-base:lastModificationDate ?linkSourceLastModificationDate .
              |<http://rdfh.ch/0803/page123> <http://www.knora.org/ontology/0803/incunabula#partOf> <http://rdfh.ch/0803/book456> .
              |<http://rdfh.ch/0803/page123> <http://www.knora.org/ontology/0803/incunabula#partOfValue> ?currentLinkValue .
              |?currentLinkValue knora-base:valueHasUUID ?currentLinkUUID . } }
              |INSERT { GRAPH <http://www.knora.org/data/0803/incunabula> { <http://rdfh.ch/0803/page123/values/deletedLink> a knora-base:LinkValue .
              |<http://rdfh.ch/0803/page123/values/deletedLink> rdf:subject <http://rdfh.ch/0803/page123> .
              |<http://rdfh.ch/0803/page123/values/deletedLink> rdf:predicate <http://www.knora.org/ontology/0803/incunabula#partOf> .
              |<http://rdfh.ch/0803/page123/values/deletedLink> rdf:object <http://rdfh.ch/0803/book456> .
              |<http://rdfh.ch/0803/page123/values/deletedLink> knora-base:valueHasString "http://rdfh.ch/0803/book456"^^xsd:string .
              |<http://rdfh.ch/0803/page123/values/deletedLink> knora-base:valueHasRefCount 0 .
              |<http://rdfh.ch/0803/page123/values/deletedLink> knora-base:valueCreationDate "2024-02-01T08:00:00Z"^^xsd:dateTime .
              |<http://rdfh.ch/0803/page123/values/deletedLink> knora-base:deleteDate "2024-02-01T08:00:00Z"^^xsd:dateTime .
              |<http://rdfh.ch/0803/page123/values/deletedLink> knora-base:deletedBy <http://rdfh.ch/users/incunabulaUser> .
              |<http://rdfh.ch/0803/page123/values/deletedLink> knora-base:previousValue ?currentLinkValue .
              |<http://rdfh.ch/0803/page123/values/deletedLink> knora-base:valueHasUUID ?currentLinkUUID .
              |<http://rdfh.ch/0803/page123/values/deletedLink> knora-base:isDeleted true .
              |<http://rdfh.ch/0803/page123/values/deletedLink> knora-base:attachedToUser <http://rdfh.ch/users/incunabulaUser> .
              |<http://rdfh.ch/0803/page123/values/deletedLink> knora-base:hasPermissions "CR knora-admin:Creator|V knora-admin:KnownUser"^^xsd:string .
              |<http://rdfh.ch/0803/page123> <http://www.knora.org/ontology/0803/incunabula#partOfValue> <http://rdfh.ch/0803/page123/values/deletedLink> .
              |<http://rdfh.ch/0803/page123> knora-base:lastModificationDate "2024-02-01T08:00:00Z"^^xsd:dateTime . } }
              |WHERE { <http://rdfh.ch/0803/page123> a ?linkSourceClass ;
              |    knora-base:isDeleted false .
              |?linkSourceClass rdfs:subClassOf* knora-base:Resource .
              |<http://rdfh.ch/0803/page123> <http://www.knora.org/ontology/0803/incunabula#partOf> <http://rdfh.ch/0803/book456> .
              |<http://rdfh.ch/0803/page123> <http://www.knora.org/ontology/0803/incunabula#partOfValue> ?currentLinkValue .
              |?currentLinkValue a knora-base:LinkValue ;
              |    rdf:subject <http://rdfh.ch/0803/page123> ;
              |    rdf:predicate <http://www.knora.org/ontology/0803/incunabula#partOf> ;
              |    rdf:object <http://rdfh.ch/0803/book456> ;
              |    knora-base:valueHasRefCount 1 ;
              |    knora-base:isDeleted false ;
              |    knora-base:valueHasUUID ?currentLinkUUID .
              |OPTIONAL { <http://rdfh.ch/0803/page123> knora-base:lastModificationDate ?linkSourceLastModificationDate . } }""".stripMargin,
        )
      },
    ),
    suite("validation")(
      test("should fail with SparqlGenerationException when deleteDirectLink is false") {
        val invalidLinkUpdate = SparqlTemplateLinkUpdate(
          linkPropertyIri = testLinkPropertyIri,
          directLinkExists = true,
          insertDirectLink = false,
          deleteDirectLink = false, // Invalid: must be true
          linkValueExists = true,
          linkTargetExists = true,
          newLinkValueIri = testNewLinkValueIri,
          linkTargetIri = testLinkTargetIri,
          currentReferenceCount = testCurrentReferenceCount,
          newReferenceCount = 0,
          newLinkValueCreator = testNewLinkValueCreator,
          newLinkValuePermissions = testNewLinkValuePerms,
        )
        val effect = DeleteLinkQuery.build(
          testDataNamedGraph,
          testLinkSourceIri,
          invalidLinkUpdate,
          None,
          testCurrentTime,
          testRequestingUser,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("deleteDirectLink must be true"))),
        )
      },
      test("should fail with SparqlGenerationException when linkValueExists is false") {
        val invalidLinkUpdate = SparqlTemplateLinkUpdate(
          linkPropertyIri = testLinkPropertyIri,
          directLinkExists = true,
          insertDirectLink = false,
          deleteDirectLink = true,
          linkValueExists = false, // Invalid: must be true
          linkTargetExists = true,
          newLinkValueIri = testNewLinkValueIri,
          linkTargetIri = testLinkTargetIri,
          currentReferenceCount = testCurrentReferenceCount,
          newReferenceCount = 0,
          newLinkValueCreator = testNewLinkValueCreator,
          newLinkValuePermissions = testNewLinkValuePerms,
        )
        val effect = DeleteLinkQuery.build(
          testDataNamedGraph,
          testLinkSourceIri,
          invalidLinkUpdate,
          None,
          testCurrentTime,
          testRequestingUser,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("linkValueExists must be true"))),
        )
      },
      test("should fail with SparqlGenerationException when directLinkExists is false") {
        val invalidLinkUpdate = SparqlTemplateLinkUpdate(
          linkPropertyIri = testLinkPropertyIri,
          directLinkExists = false, // Invalid: must be true
          insertDirectLink = false,
          deleteDirectLink = true,
          linkValueExists = true,
          linkTargetExists = true,
          newLinkValueIri = testNewLinkValueIri,
          linkTargetIri = testLinkTargetIri,
          currentReferenceCount = testCurrentReferenceCount,
          newReferenceCount = 0,
          newLinkValueCreator = testNewLinkValueCreator,
          newLinkValuePermissions = testNewLinkValuePerms,
        )
        val effect = DeleteLinkQuery.build(
          testDataNamedGraph,
          testLinkSourceIri,
          invalidLinkUpdate,
          None,
          testCurrentTime,
          testRequestingUser,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("directLinkExists must be true"))),
        )
      },
      test("should fail with SparqlGenerationException when newReferenceCount is not 0") {
        val invalidLinkUpdate = SparqlTemplateLinkUpdate(
          linkPropertyIri = testLinkPropertyIri,
          directLinkExists = true,
          insertDirectLink = false,
          deleteDirectLink = true,
          linkValueExists = true,
          linkTargetExists = true,
          newLinkValueIri = testNewLinkValueIri,
          linkTargetIri = testLinkTargetIri,
          currentReferenceCount = testCurrentReferenceCount,
          newReferenceCount = 1, // Invalid: must be 0
          newLinkValueCreator = testNewLinkValueCreator,
          newLinkValuePermissions = testNewLinkValuePerms,
        )
        val effect = DeleteLinkQuery.build(
          testDataNamedGraph,
          testLinkSourceIri,
          invalidLinkUpdate,
          None,
          testCurrentTime,
          testRequestingUser,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("newReferenceCount must be 0"))),
        )
      },
    ),
  )
}
