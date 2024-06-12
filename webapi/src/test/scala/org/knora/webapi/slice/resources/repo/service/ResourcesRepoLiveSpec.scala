/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import zio.*
import zio.test.*

import java.time.Instant
import java.util.UUID

import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.twirl.NewLinkValueInfo
import org.knora.webapi.messages.twirl.NewValueInfo
import org.knora.webapi.messages.twirl.TypeSpecificValueInfo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object ResourcesRepoLiveSpec extends ZIOSpecDefault {
  def spec: Spec[Environment & (TestEnvironment & Scope), Any] = tests.provide(StringFormatter.test)

  val tests: Spec[StringFormatter, Nothing] =
    suite("ResourcesRepoLiveSpec")(
      test("Create new resource query without values") {
        val tripleQuotes = "\"\"\""

        val graphIri         = InternalIri("fooGraph")
        val projectIri       = "fooProject"
        val userIri          = "fooUser"
        val resourceIri      = "fooResource"
        val resourceClassIri = "fooClass"
        val label            = "fooLabel"
        val creationDate     = Instant.parse("2024-01-01T10:00:00.673298Z")
        val permissions      = "fooPermissions"

        val resourceDefinition = ResourceReadyToCreate(
          resourceIri = resourceIri,
          resourceClassIri = resourceClassIri,
          resourceLabel = label,
          creationDate = creationDate,
          permissions = permissions,
          newValueInfos = Seq.empty,
          linkUpdates = Seq.empty,
        )

        val expected =
          Update(s"""|
                     |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                     |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                     |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                     |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                     |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                     |
                     |INSERT DATA {
                     |    GRAPH <${graphIri.value}> {
                     |        <$resourceIri> rdf:type <$resourceClassIri> ;
                     |            knora-base:isDeleted false ;
                     |            knora-base:attachedToUser <$userIri> ;
                     |            knora-base:attachedToProject <$projectIri> ;
                     |            rdfs:label $tripleQuotes$label$tripleQuotes ;
                     |            knora-base:hasPermissions "$permissions" ;
                     |            knora-base:creationDate "$creationDate"^^xsd:dateTime .
                     |
                     |        
                     |
                     |
                     |        
                     |    }
                     |}
                     |""".stripMargin)
        val result = ResourcesRepoLive.createNewResourceQuery(
          dataGraphIri = graphIri,
          resourceToCreate = resourceDefinition,
          projectIri = projectIri,
          creatorIri = userIri,
        )

        assertTrue(expected.sparql == result.sparql)
      },
      test("Create new resource query with values") {
        val graphIri         = InternalIri("fooGraph")
        val projectIri       = "fooProject"
        val userIri          = "fooUser"
        val resourceIri      = "fooResource"
        val resourceClassIri = "fooClass"
        val label            = "fooLabel"
        val creationDate     = Instant.parse("2024-01-01T10:00:00.673298Z")
        val permissions      = "fooPermissions"

        val valueIri          = "fooValue"
        val valueCreator      = "fooCreator"
        val valuePermissions  = "fooValuePermissions"
        val valueCreationDate = Instant.parse("2024-01-01T10:00:00.673298Z")

        val uuid        = UUID.randomUUID()
        val uuidEncoded = UuidUtil.base64Encode(uuid)

        val resourceDefinition = ResourceReadyToCreate(
          resourceIri = resourceIri,
          resourceClassIri = resourceClassIri,
          resourceLabel = label,
          creationDate = creationDate,
          permissions = permissions,
          newValueInfos = Seq(
            NewValueInfo(
              resourceIri = resourceIri,
              propertyIri = "fooProperty",
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
          ),
          linkUpdates = Seq.empty,
        )

        val expected =
          Update(s"""|
                     |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                     |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                     |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                     |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                     |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                     |
                     |INSERT DATA {
                     |    GRAPH <fooGraph> {
                     |        <fooResource> rdf:type <fooClass> ;
                     |            knora-base:isDeleted false ;
                     |            knora-base:attachedToUser <fooUser> ;
                     |            knora-base:attachedToProject <fooProject> ;
                     |            rdfs:label \"\"\"fooLabel\"\"\" ;
                     |            knora-base:hasPermissions \"fooPermissions\" ;
                     |            knora-base:creationDate \"2024-01-01T10:00:00.673298Z\"^^xsd:dateTime .
                     |
                     |        
                     |            # Value: fooValue
                     |            # Property: fooProperty
                     |
                     |            
                     |            <fooValue> rdf:type <http://www.knora.org/ontology/knora-base#IntValue> ;
                     |                knora-base:isDeleted false  ;
                     |                knora-base:valueHasString \"\"\"42\"\"\" ;
                     |                knora-base:valueHasUUID \"$uuidEncoded\" .
                     |
                     |            
                     |                    <fooValue> knora-base:valueHasInteger 42 .
                     |                
                     |
                     |            
                     |            
                     |
                     |            <fooValue> knora-base:attachedToUser <fooCreator> ;
                     |                knora-base:hasPermissions \"fooValuePermissions\"^^xsd:string ;
                     |                knora-base:valueHasOrder 1 ;
                     |                knora-base:valueCreationDate \"2024-01-01T10:00:00.673298Z\"^^xsd:dateTime .
                     |
                     |            
                     |            <fooResource> <fooProperty> <fooValue> .
                     |        
                     |
                     |
                     |        
                     |    }
                     |}
                     |""".stripMargin)
        val result = ResourcesRepoLive.createNewResourceQuery(
          dataGraphIri = graphIri,
          resourceToCreate = resourceDefinition,
          projectIri = projectIri,
          creatorIri = userIri,
        )

        assertTrue(expected.sparql == result.sparql)
      },
      test("Create new resource query with links") {
        val graphIri         = InternalIri("fooGraph")
        val projectIri       = "fooProject"
        val userIri          = "fooUser"
        val resourceIri      = "fooResource"
        val resourceClassIri = "fooClass"
        val label            = "fooLabel"
        val creationDate     = Instant.parse("2024-01-01T10:00:00.673298Z")
        val permissions      = "fooPermissions"

        val linkPropertyIri = "fooLinkProperty"
        val linkTargetIri   = "fooLinkTarget"
        val linkValueIri    = "fooLinkValue"
        val linkCreator     = "fooLinkCreator"
        val linkPermissions = "fooLinkPermissions"
        val valueUuid       = UuidUtil.makeRandomBase64EncodedUuid

        val resourceDefinition = ResourceReadyToCreate(
          resourceIri = resourceIri,
          resourceClassIri = resourceClassIri,
          resourceLabel = label,
          creationDate = creationDate,
          permissions = permissions,
          newValueInfos = Seq.empty,
          linkUpdates = Seq(
            NewLinkValueInfo(
              linkPropertyIri = linkPropertyIri,
              newLinkValueIri = linkValueIri,
              linkTargetIri = linkTargetIri,
              newReferenceCount = 1,
              newLinkValueCreator = linkCreator,
              newLinkValuePermissions = linkPermissions,
              valueUuid = valueUuid,
            ),
          ),
        )

        val expected =
          Update(s"""|
                     |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                     |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                     |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                     |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                     |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                     |
                     |INSERT DATA {
                     |    GRAPH <fooGraph> {
                     |        <fooResource> rdf:type <fooClass> ;
                     |            knora-base:isDeleted false ;
                     |            knora-base:attachedToUser <fooUser> ;
                     |            knora-base:attachedToProject <fooProject> ;
                     |            rdfs:label \"\"\"fooLabel\"\"\" ;
                     |            knora-base:hasPermissions "fooPermissions" ;
                     |            knora-base:creationDate "2024-01-01T10:00:00.673298Z"^^xsd:dateTime .
                     |
                     |        
                     |
                     |
                     |        
                     |
                     |            <fooResource> <fooLinkProperty> <fooLinkTarget> .
                     |
                     |            
                     |            <fooLinkValue> rdf:type knora-base:LinkValue ;
                     |                rdf:subject <fooResource> ;
                     |                rdf:predicate <fooLinkProperty> ;
                     |                rdf:object <fooLinkTarget> ;
                     |                knora-base:valueHasString "fooLinkTarget" ;
                     |                knora-base:valueHasRefCount 1 ;
                     |                knora-base:isDeleted false ;
                     |                knora-base:valueCreationDate "2024-01-01T10:00:00.673298Z"^^xsd:dateTime ;
                     |                knora-base:attachedToUser <fooLinkCreator> ;
                     |                knora-base:hasPermissions "fooLinkPermissions" ;
                     |                knora-base:valueHasUUID "$valueUuid" .
                     |
                     |            
                     |            <fooResource> <fooLinkPropertyValue> <fooLinkValue> .
                     |        
                     |    }
                     |}
                     |""".stripMargin)
        val result = ResourcesRepoLive.createNewResourceQuery(
          dataGraphIri = graphIri,
          resourceToCreate = resourceDefinition,
          projectIri = projectIri,
          creatorIri = userIri,
        )

        assertTrue(expected.sparql == result.sparql)
      },
    )

}
