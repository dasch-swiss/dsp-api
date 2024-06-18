/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import org.apache.jena.update.UpdateFactory
import zio.*
import zio.test.*

import java.time.Instant
import java.util.UUID

import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.twirl.NewValueInfo
import org.knora.webapi.messages.twirl.TypeSpecificValueInfo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object ResourcesRepoLiveSpec extends ZIOSpecDefault {

  private def assertUpdateQueriesEqual(expected: Update, actual: Update) = {
    val parsedExpected = UpdateFactory.create(expected.sparql)
    val parsedActual   = UpdateFactory.create(actual.sparql)
    if (parsedExpected.equalTo(parsedActual)) {
      assertTrue(true)
    } else {
      val expectedStr = parsedExpected.toString()
      val actualStr   = parsedActual.toString()
      assertTrue(expectedStr == actualStr)
    }
  }

  def spec: Spec[Environment & (TestEnvironment & Scope), Any] = tests.provide(StringFormatter.test)

  val graphIri          = InternalIri("foo:Graph")
  val projectIri        = "foo:ProjectIri"
  val userIri           = "foo:UserIri"
  val resourceIri       = "foo:ResourceInstanceIri"
  val resourceClassIri  = "foo:ClassIri"
  val label             = "foo Label"
  val creationDate      = Instant.parse("2024-01-01T10:00:00.673298Z")
  val permissions       = "fooPermissionsString"
  val valueIri          = "foo:ValueIri"
  val valueCreator      = "foo:valueCreatorIri"
  val valuePermissions  = "fooValuePermissions"
  val valueCreationDate = Instant.parse("2024-01-01T12:00:00.673298Z")

  val resourceDefinition = ResourceReadyToCreate(
    resourceIri = resourceIri,
    resourceClassIri = resourceClassIri,
    resourceLabel = label,
    creationDate = creationDate,
    permissions = permissions,
    newValueInfos = Seq.empty,
    linkUpdates = Seq.empty,
  )

  val createResourceWithoutValuesTest = test("Create a new resource query without values") {
    val expected =
      Update(s"""|
                 |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                 |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |
                 |INSERT DATA {
                 |    GRAPH <${graphIri.value}> {
                 |        <$resourceIri> rdf:type <$resourceClassIri> ;
                 |            rdfs:label "$label" ;
                 |            knora-base:isDeleted false ;
                 |            knora-base:attachedToUser <$userIri> ;
                 |            knora-base:attachedToProject <$projectIri> ;
                 |            knora-base:hasPermissions "$permissions" ;
                 |            knora-base:creationDate "$creationDate"^^xsd:dateTime .
                 |    }
                 |}
                 |""".stripMargin)
    val result = ResourcesRepoLive
      .createNewResourceQueryWithBuilder(
        dataGraphIri = graphIri,
        resourceToCreate = resourceDefinition,
        projectIri = projectIri,
        creatorIri = userIri,
      )

    val reference = ResourcesRepoLive.createNewResourceQuery(
      dataGraphIri = graphIri,
      resourceToCreate = resourceDefinition,
      projectIri = projectIri,
      creatorIri = userIri,
    )

    assertUpdateQueriesEqual(expected, result) && assertUpdateQueriesEqual(reference, result)
  }

  val createResourceWithValueSuite = suite("Create new resource with values")(
    test("Create a new resource with an integer value") {
      val uuid        = UUID.randomUUID()
      val uuidEncoded = UuidUtil.base64Encode(uuid)
      val propertyIri = "foo:Property"
      val values = List(
        NewValueInfo(
          resourceIri = resourceIri,
          propertyIri = "foo:Property",
          valueIri = valueIri,
          valueTypeIri = OntologyConstants.KnoraBase.IntValue,
          valueUUID = uuid,
          value = TypeSpecificValueInfo.IntegerValueInfo(42),
          valuePermissions = valuePermissions,
          valueCreator = valueCreator,
          creationDate = valueCreationDate,
          valueHasOrder = 1,
          valueHasString = "42",
          comment = None,
        ),
      )

      val resource = resourceDefinition.copy(newValueInfos = values)

      val expected =
        Update(s"""|
                   |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                   |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                   |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                   |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                   |
                   |INSERT DATA {
                   |    GRAPH <${graphIri.value}> {
                   |        <$resourceIri> rdf:type <$resourceClassIri> ;
                   |            rdfs:label "$label" ;
                   |            knora-base:isDeleted false ;
                   |            knora-base:attachedToUser <$userIri> ;
                   |            knora-base:attachedToProject <$projectIri> ;
                   |            knora-base:hasPermissions "$permissions" ;
                   |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
                   |            <$propertyIri> <$valueIri> .
                   |        <foo:ValueIri> rdf:type <http://www.knora.org/ontology/knora-base#IntValue> ;
                   |            knora-base:isDeleted false  ;
                   |            knora-base:valueHasString "42" ;
                   |            knora-base:valueHasUUID "$uuidEncoded" ;
                   |            knora-base:attachedToUser <$valueCreator> ;
                   |            knora-base:hasPermissions "$valuePermissions" ;
                   |            knora-base:valueHasOrder 1 ;
                   |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
                   |            knora-base:valueHasInteger 42 .
                   |    }
                   |}
                   |""".stripMargin)

      val result = ResourcesRepoLive.createNewResourceQueryWithBuilder(
        dataGraphIri = graphIri,
        resourceToCreate = resource,
        projectIri = projectIri,
        creatorIri = userIri,
      )
      val reference = ResourcesRepoLive.createNewResourceQuery(
        dataGraphIri = graphIri,
        resourceToCreate = resource,
        projectIri = projectIri,
        creatorIri = userIri,
      )

      assertUpdateQueriesEqual(expected, result) && assertUpdateQueriesEqual(reference, result)
    },
  )

  val tests: Spec[StringFormatter, Nothing] =
    suite("ResourcesRepoLiveSpec")(
      createResourceWithoutValuesTest,
      createResourceWithValueSuite,
      // test("Create new resource query with links") {
      //   val graphIri         = InternalIri("fooGraph")
      //   val projectIri       = "fooProject"
      //   val userIri          = "fooUser"
      //   val resourceIri      = "fooResource"
      //   val resourceClassIri = "fooClass"
      //   val label            = "fooLabel"
      //   val creationDate     = Instant.parse("2024-01-01T10:00:00.673298Z")
      //   val permissions      = "fooPermissions"

      //   val linkPropertyIri = "fooLinkProperty"
      //   val linkTargetIri   = "fooLinkTarget"
      //   val linkValueIri    = "fooLinkValue"
      //   val linkCreator     = "fooLinkCreator"
      //   val linkPermissions = "fooLinkPermissions"
      //   val valueUuid       = UuidUtil.makeRandomBase64EncodedUuid

      //   val resourceDefinition = ResourceReadyToCreate(
      //     resourceIri = resourceIri,
      //     resourceClassIri = resourceClassIri,
      //     resourceLabel = label,
      //     creationDate = creationDate,
      //     permissions = permissions,
      //     newValueInfos = Seq.empty,
      //     linkUpdates = Seq(
      //       NewLinkValueInfo(
      //         linkPropertyIri = linkPropertyIri,
      //         newLinkValueIri = linkValueIri,
      //         linkTargetIri = linkTargetIri,
      //         newReferenceCount = 1,
      //         newLinkValueCreator = linkCreator,
      //         newLinkValuePermissions = linkPermissions,
      //         valueUuid = valueUuid,
      //       ),
      //     ),
      //   )

      //   val expected =
      //     Update(s"""|
      //                |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
      //                |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      //                |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
      //                |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
      //                |
      //                |INSERT DATA {
      //                |    GRAPH <fooGraph> {
      //                |        <fooResource> rdf:type <fooClass> ;
      //                |            rdfs:label \"\"\"fooLabel\"\"\" ;
      //                |            knora-base:isDeleted false ;
      //                |            knora-base:attachedToUser <fooUser> ;
      //                |            knora-base:attachedToProject <fooProject> ;
      //                |            knora-base:hasPermissions "fooPermissions" ;
      //                |            knora-base:creationDate "2024-01-01T10:00:00.673298Z"^^xsd:dateTime .
      //                |
      //                |
      //                |
      //                |
      //                |
      //                |
      //                |            <fooResource> <fooLinkProperty> <fooLinkTarget> .
      //                |
      //                |
      //                |            <fooLinkValue> rdf:type knora-base:LinkValue ;
      //                |                rdf:subject <fooResource> ;
      //                |                rdf:predicate <fooLinkProperty> ;
      //                |                rdf:object <fooLinkTarget> ;
      //                |                knora-base:valueHasString "fooLinkTarget" ;
      //                |                knora-base:valueHasRefCount 1 ;
      //                |                knora-base:isDeleted false ;
      //                |                knora-base:valueCreationDate "2024-01-01T10:00:00.673298Z"^^xsd:dateTime ;
      //                |                knora-base:attachedToUser <fooLinkCreator> ;
      //                |                knora-base:hasPermissions "fooLinkPermissions" ;
      //                |                knora-base:valueHasUUID "$valueUuid" .
      //                |
      //                |
      //                |            <fooResource> <fooLinkPropertyValue> <fooLinkValue> .
      //                |
      //                |    }
      //                |}
      //                |""".stripMargin)
      //   val result = ResourcesRepoLive.createNewResourceQuery(
      //     dataGraphIri = graphIri,
      //     resourceToCreate = resourceDefinition,
      //     projectIri = projectIri,
      //     creatorIri = userIri,
      //   )

      //   assertTrue(expected.sparql == result.sparql)
      // },
    )

}
