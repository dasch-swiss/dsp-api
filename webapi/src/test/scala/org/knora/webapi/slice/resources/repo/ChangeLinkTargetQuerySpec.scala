/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*
import zio.test.Assertion.*

import java.time.Instant

import dsp.errors.SparqlGenerationException
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.slice.resources.repo.model.SparqlTemplateLinkUpdate

object ChangeLinkTargetQuerySpec extends ZIOSpecDefault {

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
  private val testLinkSourceIri             = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing1".toSmartIri)
  private val testLinkPropertyIri           = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri
  private val testCurrentLinkTargetIri      = "http://rdfh.ch/0001/thing2"
  private val testNewLinkTargetIri          = "http://rdfh.ch/0001/thing3"
  private val testNewLinkValueIriForCurrent = "http://rdfh.ch/0001/thing1/values/currentLinkValueNew"
  private val testNewLinkValueIriForNew     = "http://rdfh.ch/0001/thing1/values/newLinkValue"
  private val testCurrentReferenceCount     = 1
  private val testNewLinkValueCreator       = "http://rdfh.ch/users/creator1"
  private val testNewLinkValuePerms         = "CR knora-admin:Creator"
  private val testCurrentTime               = Instant.parse("2024-01-15T10:30:00Z")
  private val testRequestingUser            = UserIri.unsafeFrom("http://rdfh.ch/users/root")

  private def createCurrentLinkUpdate(
    linkPropertyIri: SmartIri = testLinkPropertyIri,
    linkTargetIri: String = testCurrentLinkTargetIri,
    newLinkValueIri: String = testNewLinkValueIriForCurrent,
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

  private def createNewLinkUpdate(
    linkPropertyIri: SmartIri = testLinkPropertyIri,
    linkTargetIri: String = testNewLinkTargetIri,
    newLinkValueIri: String = testNewLinkValueIriForNew,
    newReferenceCount: Int = 1,
    newLinkValueCreator: String = testNewLinkValueCreator,
    newLinkValuePermissions: String = testNewLinkValuePerms,
  ): SparqlTemplateLinkUpdate = SparqlTemplateLinkUpdate(
    linkPropertyIri = linkPropertyIri,
    directLinkExists = false,
    insertDirectLink = true,
    deleteDirectLink = false,
    linkValueExists = false,
    linkTargetExists = true,
    newLinkValueIri = newLinkValueIri,
    linkTargetIri = linkTargetIri,
    currentReferenceCount = 0,
    newReferenceCount = newReferenceCount,
    newLinkValueCreator = newLinkValueCreator,
    newLinkValuePermissions = newLinkValuePermissions,
  )

  override def spec: Spec[TestEnvironment, Any] = suite("ChangeLinkTargetQuerySpec")(
    suite("build")(
      test("should produce correct query for changing link target without comment") {
        for {
          result <- ChangeLinkTargetQuery.build(
                      testProject,
                      testLinkSourceIri,
                      createCurrentLinkUpdate(),
                      createNewLinkUpdate(),
                      None,
                      testCurrentTime,
                      testRequestingUser,
                    )
          (uuid, query) = result
        } yield {
          val uuidEncoded = UuidUtil.base64Encode(uuid)
          val expected    =
            s"""PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?linkSourceLastModificationDate .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThing> <http://rdfh.ch/0001/thing2> .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?currentLinkValueForCurrentLink .
               |?currentLinkValueForCurrentLink knora-base:valueHasUUID ?currentLinkUUID .
               |?currentLinkValueForCurrentLink knora-base:hasPermissions ?currentLinkPermissions . } }
               |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1/values/currentLinkValueNew> a knora-base:LinkValue .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> rdf:subject <http://rdfh.ch/0001/thing1> .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> rdf:predicate <http://www.knora.org/ontology/0001/anything#hasOtherThing> .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> rdf:object <http://rdfh.ch/0001/thing2> .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:valueHasString "http://rdfh.ch/0001/thing2"^^xsd:string .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:valueHasRefCount 0 .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:valueCreationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:previousValue ?currentLinkValueForCurrentLink .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:valueHasUUID ?currentLinkUUID .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:deleteDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:deletedBy <http://rdfh.ch/users/root> .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:isDeleted true .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:attachedToUser <http://rdfh.ch/users/creator1> .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:hasPermissions "CR knora-admin:Creator"^^xsd:string .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> <http://rdfh.ch/0001/thing1/values/currentLinkValueNew> .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThing> <http://rdfh.ch/0001/thing3> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> a knora-base:LinkValue .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:subject <http://rdfh.ch/0001/thing1> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:predicate <http://www.knora.org/ontology/0001/anything#hasOtherThing> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:object <http://rdfh.ch/0001/thing3> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasString "http://rdfh.ch/0001/thing3"^^xsd:string .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasRefCount 1 .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasOrder ?order .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:isDeleted false .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasUUID "$uuidEncoded" .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueCreationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:attachedToUser <http://rdfh.ch/users/creator1> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:hasPermissions "CR knora-admin:Creator"^^xsd:string .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> <http://rdfh.ch/0001/thing1/values/newLinkValue> .
               |<http://rdfh.ch/0001/thing1> knora-base:lastModificationDate "2024-01-15T10:30:00Z"^^xsd:dateTime . } }
               |WHERE { <http://rdfh.ch/0001/thing1> a ?linkSourceClass .
               |?linkSourceClass rdfs:subClassOf* knora-base:Resource .
               |<http://rdfh.ch/0001/thing1> knora-base:isDeleted false .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThing> <http://rdfh.ch/0001/thing2> .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?currentLinkValueForCurrentLink .
               |?currentLinkValueForCurrentLink a knora-base:LinkValue ;
               |    rdf:subject <http://rdfh.ch/0001/thing1> ;
               |    rdf:predicate <http://www.knora.org/ontology/0001/anything#hasOtherThing> ;
               |    rdf:object <http://rdfh.ch/0001/thing2> ;
               |    knora-base:valueHasRefCount 1 ;
               |    knora-base:isDeleted false ;
               |    knora-base:valueHasUUID ?currentLinkUUID ;
               |    knora-base:hasPermissions ?currentLinkPermissions .
               |OPTIONAL { ?currentLinkValueForCurrentLink knora-base:valueHasOrder ?order . }
               |FILTER NOT EXISTS { <http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThing> <http://rdfh.ch/0001/thing3> . }
               |FILTER NOT EXISTS { <http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?currentLinkValueForNewLink .
               |?currentLinkValueForNewLink a knora-base:LinkValue ;
               |    rdf:subject <http://rdfh.ch/0001/thing1> ;
               |    rdf:predicate <http://www.knora.org/ontology/0001/anything#hasOtherThing> ;
               |    rdf:object <http://rdfh.ch/0001/thing3> ;
               |    knora-base:isDeleted false . }
               |<http://rdfh.ch/0001/thing3> a ?linkTargetClass ;
               |    knora-base:isDeleted false .
               |?linkTargetClass rdfs:subClassOf* knora-base:Resource .
               |<http://www.knora.org/ontology/0001/anything#hasOtherThing> knora-base:objectClassConstraint ?expectedTargetClass .
               |?linkTargetClass rdfs:subClassOf* ?expectedTargetClass .
               |OPTIONAL { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?linkSourceLastModificationDate . } }""".stripMargin
          assertTrue(query.sparql == expected)
        }
      },
      test("should produce correct query for changing link target with comment") {
        for {
          result <- ChangeLinkTargetQuery.build(
                      testProject,
                      testLinkSourceIri,
                      createCurrentLinkUpdate(),
                      createNewLinkUpdate(),
                      Some("Updated link target"),
                      testCurrentTime,
                      testRequestingUser,
                    )
          (uuid, query) = result
        } yield {
          val uuidEncoded = UuidUtil.base64Encode(uuid)
          val expected    =
            s"""PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?linkSourceLastModificationDate .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThing> <http://rdfh.ch/0001/thing2> .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?currentLinkValueForCurrentLink .
               |?currentLinkValueForCurrentLink knora-base:valueHasUUID ?currentLinkUUID .
               |?currentLinkValueForCurrentLink knora-base:hasPermissions ?currentLinkPermissions . } }
               |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/thing1/values/currentLinkValueNew> a knora-base:LinkValue .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> rdf:subject <http://rdfh.ch/0001/thing1> .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> rdf:predicate <http://www.knora.org/ontology/0001/anything#hasOtherThing> .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> rdf:object <http://rdfh.ch/0001/thing2> .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:valueHasString "http://rdfh.ch/0001/thing2"^^xsd:string .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:valueHasRefCount 0 .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:valueCreationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:previousValue ?currentLinkValueForCurrentLink .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:valueHasUUID ?currentLinkUUID .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:deleteDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:deletedBy <http://rdfh.ch/users/root> .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:isDeleted true .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:attachedToUser <http://rdfh.ch/users/creator1> .
               |<http://rdfh.ch/0001/thing1/values/currentLinkValueNew> knora-base:hasPermissions "CR knora-admin:Creator"^^xsd:string .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> <http://rdfh.ch/0001/thing1/values/currentLinkValueNew> .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThing> <http://rdfh.ch/0001/thing3> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> a knora-base:LinkValue .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:subject <http://rdfh.ch/0001/thing1> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:predicate <http://www.knora.org/ontology/0001/anything#hasOtherThing> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> rdf:object <http://rdfh.ch/0001/thing3> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasString "http://rdfh.ch/0001/thing3"^^xsd:string .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasComment "Updated link target" .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasRefCount 1 .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasOrder ?order .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:isDeleted false .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueHasUUID "$uuidEncoded" .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:valueCreationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:attachedToUser <http://rdfh.ch/users/creator1> .
               |<http://rdfh.ch/0001/thing1/values/newLinkValue> knora-base:hasPermissions "CR knora-admin:Creator"^^xsd:string .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> <http://rdfh.ch/0001/thing1/values/newLinkValue> .
               |<http://rdfh.ch/0001/thing1> knora-base:lastModificationDate "2024-01-15T10:30:00Z"^^xsd:dateTime . } }
               |WHERE { <http://rdfh.ch/0001/thing1> a ?linkSourceClass .
               |?linkSourceClass rdfs:subClassOf* knora-base:Resource .
               |<http://rdfh.ch/0001/thing1> knora-base:isDeleted false .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThing> <http://rdfh.ch/0001/thing2> .
               |<http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?currentLinkValueForCurrentLink .
               |?currentLinkValueForCurrentLink a knora-base:LinkValue ;
               |    rdf:subject <http://rdfh.ch/0001/thing1> ;
               |    rdf:predicate <http://www.knora.org/ontology/0001/anything#hasOtherThing> ;
               |    rdf:object <http://rdfh.ch/0001/thing2> ;
               |    knora-base:valueHasRefCount 1 ;
               |    knora-base:isDeleted false ;
               |    knora-base:valueHasUUID ?currentLinkUUID ;
               |    knora-base:hasPermissions ?currentLinkPermissions .
               |OPTIONAL { ?currentLinkValueForCurrentLink knora-base:valueHasOrder ?order . }
               |FILTER NOT EXISTS { <http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThing> <http://rdfh.ch/0001/thing3> . }
               |FILTER NOT EXISTS { <http://rdfh.ch/0001/thing1> <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?currentLinkValueForNewLink .
               |?currentLinkValueForNewLink a knora-base:LinkValue ;
               |    rdf:subject <http://rdfh.ch/0001/thing1> ;
               |    rdf:predicate <http://www.knora.org/ontology/0001/anything#hasOtherThing> ;
               |    rdf:object <http://rdfh.ch/0001/thing3> ;
               |    knora-base:isDeleted false . }
               |<http://rdfh.ch/0001/thing3> a ?linkTargetClass ;
               |    knora-base:isDeleted false .
               |?linkTargetClass rdfs:subClassOf* knora-base:Resource .
               |<http://www.knora.org/ontology/0001/anything#hasOtherThing> knora-base:objectClassConstraint ?expectedTargetClass .
               |?linkTargetClass rdfs:subClassOf* ?expectedTargetClass .
               |OPTIONAL { <http://rdfh.ch/0001/thing1> knora-base:lastModificationDate ?linkSourceLastModificationDate . } }""".stripMargin
          assertTrue(query.sparql == expected)
        }
      },
    ),
    suite("validation")(
      test("should fail when linkUpdateForCurrentLink.deleteDirectLink is false") {
        val invalidUpdate = SparqlTemplateLinkUpdate(
          linkPropertyIri = testLinkPropertyIri,
          directLinkExists = true,
          insertDirectLink = false,
          deleteDirectLink = false,
          linkValueExists = true,
          linkTargetExists = true,
          newLinkValueIri = testNewLinkValueIriForCurrent,
          linkTargetIri = testCurrentLinkTargetIri,
          currentReferenceCount = testCurrentReferenceCount,
          newReferenceCount = 0,
          newLinkValueCreator = testNewLinkValueCreator,
          newLinkValuePermissions = testNewLinkValuePerms,
        )
        val effect = ChangeLinkTargetQuery.build(
          testProject,
          testLinkSourceIri,
          invalidUpdate,
          createNewLinkUpdate(),
          None,
          testCurrentTime,
          testRequestingUser,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("deleteDirectLink must be true"))),
        )
      },
      test("should fail when linkUpdateForCurrentLink.linkValueExists is false") {
        val invalidUpdate = SparqlTemplateLinkUpdate(
          linkPropertyIri = testLinkPropertyIri,
          directLinkExists = true,
          insertDirectLink = false,
          deleteDirectLink = true,
          linkValueExists = false,
          linkTargetExists = true,
          newLinkValueIri = testNewLinkValueIriForCurrent,
          linkTargetIri = testCurrentLinkTargetIri,
          currentReferenceCount = testCurrentReferenceCount,
          newReferenceCount = 0,
          newLinkValueCreator = testNewLinkValueCreator,
          newLinkValuePermissions = testNewLinkValuePerms,
        )
        val effect = ChangeLinkTargetQuery.build(
          testProject,
          testLinkSourceIri,
          invalidUpdate,
          createNewLinkUpdate(),
          None,
          testCurrentTime,
          testRequestingUser,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("linkValueExists must be true"))),
        )
      },
      test("should fail when linkUpdateForCurrentLink.newReferenceCount is not 0") {
        val invalidUpdate = SparqlTemplateLinkUpdate(
          linkPropertyIri = testLinkPropertyIri,
          directLinkExists = true,
          insertDirectLink = false,
          deleteDirectLink = true,
          linkValueExists = true,
          linkTargetExists = true,
          newLinkValueIri = testNewLinkValueIriForCurrent,
          linkTargetIri = testCurrentLinkTargetIri,
          currentReferenceCount = testCurrentReferenceCount,
          newReferenceCount = 1,
          newLinkValueCreator = testNewLinkValueCreator,
          newLinkValuePermissions = testNewLinkValuePerms,
        )
        val effect = ChangeLinkTargetQuery.build(
          testProject,
          testLinkSourceIri,
          invalidUpdate,
          createNewLinkUpdate(),
          None,
          testCurrentTime,
          testRequestingUser,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("newReferenceCount must be 0"))),
        )
      },
      test("should fail when linkPropertyIris don't match") {
        val differentProperty = "http://www.knora.org/ontology/0803/incunabula#partOf".toSmartIri
        val effect            = ChangeLinkTargetQuery.build(
          testProject,
          testLinkSourceIri,
          createCurrentLinkUpdate(),
          createNewLinkUpdate(linkPropertyIri = differentProperty),
          None,
          testCurrentTime,
          testRequestingUser,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("must be equal to"))),
        )
      },
      test("should fail when linkUpdateForNewLink.directLinkExists is true") {
        val invalidUpdate = SparqlTemplateLinkUpdate(
          linkPropertyIri = testLinkPropertyIri,
          directLinkExists = true,
          insertDirectLink = true,
          deleteDirectLink = false,
          linkValueExists = false,
          linkTargetExists = true,
          newLinkValueIri = testNewLinkValueIriForNew,
          linkTargetIri = testNewLinkTargetIri,
          currentReferenceCount = 0,
          newReferenceCount = 1,
          newLinkValueCreator = testNewLinkValueCreator,
          newLinkValuePermissions = testNewLinkValuePerms,
        )
        val effect = ChangeLinkTargetQuery.build(
          testProject,
          testLinkSourceIri,
          createCurrentLinkUpdate(),
          invalidUpdate,
          None,
          testCurrentTime,
          testRequestingUser,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("directLinkExists must be false"))),
        )
      },
      test("should fail when linkUpdateForNewLink.linkValueExists is true") {
        val invalidUpdate = SparqlTemplateLinkUpdate(
          linkPropertyIri = testLinkPropertyIri,
          directLinkExists = false,
          insertDirectLink = true,
          deleteDirectLink = false,
          linkValueExists = true,
          linkTargetExists = true,
          newLinkValueIri = testNewLinkValueIriForNew,
          linkTargetIri = testNewLinkTargetIri,
          currentReferenceCount = 0,
          newReferenceCount = 1,
          newLinkValueCreator = testNewLinkValueCreator,
          newLinkValuePermissions = testNewLinkValuePerms,
        )
        val effect = ChangeLinkTargetQuery.build(
          testProject,
          testLinkSourceIri,
          createCurrentLinkUpdate(),
          invalidUpdate,
          None,
          testCurrentTime,
          testRequestingUser,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("linkValueExists must be false"))),
        )
      },
      test("should fail when linkUpdateForNewLink.insertDirectLink is false") {
        val invalidUpdate = SparqlTemplateLinkUpdate(
          linkPropertyIri = testLinkPropertyIri,
          directLinkExists = false,
          insertDirectLink = false,
          deleteDirectLink = false,
          linkValueExists = false,
          linkTargetExists = true,
          newLinkValueIri = testNewLinkValueIriForNew,
          linkTargetIri = testNewLinkTargetIri,
          currentReferenceCount = 0,
          newReferenceCount = 1,
          newLinkValueCreator = testNewLinkValueCreator,
          newLinkValuePermissions = testNewLinkValuePerms,
        )
        val effect = ChangeLinkTargetQuery.build(
          testProject,
          testLinkSourceIri,
          createCurrentLinkUpdate(),
          invalidUpdate,
          None,
          testCurrentTime,
          testRequestingUser,
        )
        assertZIO(effect.exit)(
          failsWithA[SparqlGenerationException] &&
            fails(hasMessage(containsString("insertDirectLink must be true"))),
        )
      },
    ),
  )
}
