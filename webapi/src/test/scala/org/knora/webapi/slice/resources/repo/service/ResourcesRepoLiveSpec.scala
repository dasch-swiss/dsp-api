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
import org.knora.webapi.messages.util.DatePrecisionDay
import org.knora.webapi.messages.util.CalendarNameGregorian

object TestData {

  val graphIri          = InternalIri("foo:Graph")
  val projectIri        = "foo:ProjectIri"
  val userIri           = "foo:UserIri"
  val resourceIri       = "foo:ResourceInstanceIri"
  val resourceClassIri  = "foo:ClassIri"
  val label             = "foo Label"
  val creationDate      = Instant.parse("2024-01-01T10:00:00.673298Z")
  val permissions       = "fooPermissionsString"
  val valueIri          = "foo:ValueIri"
  val valueCreator      = "foo:ValueCreatorIri"
  val valuePermissions  = "fooValuePermissions"
  val valueCreationDate = Instant.parse("2024-01-01T12:00:00.673298Z")
  val propertyIri       = "foo:propertyIri"

  val resourceDefinition = ResourceReadyToCreate(
    resourceIri = resourceIri,
    resourceClassIri = resourceClassIri,
    resourceLabel = label,
    creationDate = creationDate,
    permissions = permissions,
    newValueInfos = Seq.empty,
    linkUpdates = Seq.empty,
  )

  def intValueDefinition(uuid: UUID) =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = propertyIri,
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
    )

  def boolValueDefinition(uuid: UUID) =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = propertyIri,
      valueIri = valueIri,
      valueTypeIri = OntologyConstants.KnoraBase.BooleanValue,
      valueUUID = uuid,
      value = TypeSpecificValueInfo.BooleanValueInfo(true),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "true",
      comment = None,
    )

  def decimalValueDefinition(uuid: UUID) =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = propertyIri,
      valueIri = valueIri,
      valueTypeIri = OntologyConstants.KnoraBase.DecimalValue,
      valueUUID = uuid,
      value = TypeSpecificValueInfo.DecimalValueInfo(BigDecimal(42.42)),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "42.42",
      comment = None,
    )

  def uriValueDefinition(uuid: UUID) =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = propertyIri,
      valueIri = valueIri,
      valueTypeIri = OntologyConstants.KnoraBase.UriValue,
      valueUUID = uuid,
      value = TypeSpecificValueInfo.UriValueInfo("http://example.com"),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "http://example.com",
      comment = None,
    )

  def dateValueDefinition(uuid: UUID) =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = propertyIri,
      valueIri = valueIri,
      valueTypeIri = OntologyConstants.KnoraBase.DateValue,
      valueUUID = uuid,
      value = TypeSpecificValueInfo.DateValueInfo(
        0,
        0,
        DatePrecisionDay,
        DatePrecisionDay,
        CalendarNameGregorian,
      ),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "2024-01-01",
      comment = None,
    )

  def colorValueDefinition(uuid: UUID) =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = propertyIri,
      valueIri = valueIri,
      valueTypeIri = OntologyConstants.KnoraBase.ColorValue,
      valueUUID = uuid,
      value = TypeSpecificValueInfo.ColorValueInfo("#ff0000"),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "#ff0000",
      comment = None,
    )

  def geometryValueDefinition(uuid: UUID) =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = propertyIri,
      valueIri = valueIri,
      valueTypeIri = OntologyConstants.KnoraBase.GeomValue,
      valueUUID = uuid,
      value = TypeSpecificValueInfo.GeomValueInfo(
        """{"status":"active","lineColor":"#33ff33","lineWidth":2,"points":[{"x":0.20226843100189035,"y":0.3090909090909091},{"x":0.6389413988657845,"y":0.3594405594405594}],"type":"rectangle","original_index":0}""",
      ),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString =
        """{"status":"active","lineColor":"#33ff33","lineWidth":2,"points":[{"x":0.20226843100189035,"y":0.3090909090909091},{"x":0.6389413988657845,"y":0.3594405594405594}],"type":"rectangle","original_index":0}""",
      comment = None,
    )

  def stillImageFileValueDefinition(uuid: UUID) =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = propertyIri,
      valueIri = valueIri,
      valueTypeIri = OntologyConstants.KnoraBase.StillImageFileValue,
      valueUUID = uuid,
      value = TypeSpecificValueInfo.StillImageFileValueInfo(
        internalFilename = "24159oO1pNg-ByLN1NLlMSJ.jp2",
        internalMimeType = "image/jp2",
        originalFilename = Some("foo.png"),
        originalMimeType = Some("image/png"),
        dimX = 100,
        dimY = 60,
      ),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "foo.jpg",
      comment = None,
    )

  def stillImageExternalFileValueDefinition(uuid: UUID) =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = propertyIri,
      valueIri = valueIri,
      valueTypeIri = OntologyConstants.KnoraBase.StillImageFileValue,
      valueUUID = uuid,
      value = TypeSpecificValueInfo.StillImageExternalFileValueInfo(
        internalFilename = "24159oO1pNg-ByLN1NLlMSJ.jp2",
        internalMimeType = "image/jp2",
        originalFilename = None,
        originalMimeType = None,
        externalUrl = "http://example.com/foo.jpg",
      ),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "foo.jpg",
      comment = None,
    )

  def documentFileValueDefinition(uuid: UUID) =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = propertyIri,
      valueIri = valueIri,
      valueTypeIri = OntologyConstants.KnoraBase.DocumentFileValue,
      valueUUID = uuid,
      value = TypeSpecificValueInfo.DocumentFileValueInfo(
        internalFilename = "24159oO1pNg-ByLN1NLlMSJ.pdf",
        internalMimeType = "application/pdf",
        originalFilename = Some("foo.pdf"),
        originalMimeType = Some("application/pdf"),
        dimX = Some(100),
        dimY = Some(60),
        pageCount = Some(10),
      ),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "foo.pdf",
      comment = None,
    )

  def otherFileValueDefinition(uuid: UUID) =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = propertyIri,
      valueIri = valueIri,
      valueTypeIri = OntologyConstants.KnoraBase.ArchiveFileValue,
      valueUUID = uuid,
      value = TypeSpecificValueInfo.OtherFileValueInfo(
        internalFilename = "24159oO1pNg-ByLN1NLlMSJ.zip",
        internalMimeType = "application/zip",
        originalFilename = Some("foo.zip"),
        originalMimeType = Some("application/zip"),
      ),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "foo.zip",
      comment = None,
    )

  def hierarchicalListValueDefinition(uuid: UUID) =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = propertyIri,
      valueIri = valueIri,
      valueTypeIri = OntologyConstants.KnoraBase.ListValue,
      valueUUID = uuid,
      value = TypeSpecificValueInfo.HierarchicalListValueInfo("foo:ListNodeIri"),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "foo list",
      comment = None,
    )

  def intervalValueDefinition(uuid: UUID) =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = propertyIri,
      valueIri = valueIri,
      valueTypeIri = OntologyConstants.KnoraBase.IntervalValue,
      valueUUID = uuid,
      value = TypeSpecificValueInfo.IntervalValueInfo(
        valueHasIntervalStart = BigDecimal(0.0),
        valueHasIntervalEnd = BigDecimal(100.0),
      ),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "0.0 - 100.0",
      comment = None,
    )

  def timeValueDefinition(uuid: UUID) =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = propertyIri,
      valueIri = valueIri,
      valueTypeIri = OntologyConstants.KnoraBase.TimeValue,
      valueUUID = uuid,
      value = TypeSpecificValueInfo.TimeValueInfo(Instant.parse("1024-01-01T10:00:00.673298Z")),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "1024-01-01T10:00:00.673298Z",
      comment = None,
    )

  def geonameValueDefinition(uuid: UUID) =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = propertyIri,
      valueIri = valueIri,
      valueTypeIri = OntologyConstants.KnoraBase.GeonameValue,
      valueUUID = uuid,
      value = TypeSpecificValueInfo.GeonameValueInfo("geoname_code"),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "geoname_code",
      comment = None,
    )

}

object ResourcesRepoLiveSpec extends ZIOSpecDefault {
  import TestData.*

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
    val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resourceDefinition, projectIri, userIri)
    val reference = ResourcesRepoLive.createNewResourceQueryTwirl(
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
      val resource    = resourceDefinition.copy(newValueInfos = List(intValueDefinition(uuid)))

      val expected = Update(
        s"""|
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
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      val reference = ResourcesRepoLive.createNewResourceQueryTwirl(
        dataGraphIri = graphIri,
        resourceToCreate = resource,
        projectIri = projectIri,
        creatorIri = userIri,
      )

      assertUpdateQueriesEqual(expected, result) && assertUpdateQueriesEqual(reference, result)
    },
    test("Create a new resource with a boolean value") {
      val uuid        = UUID.randomUUID()
      val uuidEncoded = UuidUtil.base64Encode(uuid)
      val resource    = resourceDefinition.copy(newValueInfos = List(boolValueDefinition(uuid)))

      val expected = Update(
        s"""|
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
            |        <foo:ValueIri> rdf:type <http://www.knora.org/ontology/knora-base#BooleanValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "true" ;
            |            knora-base:valueHasUUID "$uuidEncoded" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasBoolean true .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      val reference = ResourcesRepoLive.createNewResourceQueryTwirl(
        dataGraphIri = graphIri,
        resourceToCreate = resource,
        projectIri = projectIri,
        creatorIri = userIri,
      )

      assertUpdateQueriesEqual(expected, result) && assertUpdateQueriesEqual(reference, result)
    },
    test("Create a new resource with a decimal value") {
      val uuid        = UUID.randomUUID()
      val uuidEncoded = UuidUtil.base64Encode(uuid)
      val resource    = resourceDefinition.copy(newValueInfos = List(decimalValueDefinition(uuid)))

      val expected = Update(
        s"""|
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
            |        <foo:ValueIri> rdf:type <http://www.knora.org/ontology/knora-base#DecimalValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "42.42" ;
            |            knora-base:valueHasUUID "$uuidEncoded" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasDecimal 42.42 .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      val reference = ResourcesRepoLive.createNewResourceQueryTwirl(
        dataGraphIri = graphIri,
        resourceToCreate = resource,
        projectIri = projectIri,
        creatorIri = userIri,
      )
      assertUpdateQueriesEqual(expected, result) && assertUpdateQueriesEqual(reference, result)
    },
    test("Create a new resource with a URI value") {
      val uuid        = UUID.randomUUID()
      val uuidEncoded = UuidUtil.base64Encode(uuid)
      val resource    = resourceDefinition.copy(newValueInfos = List(uriValueDefinition(uuid)))

      val expected = Update(
        s"""|
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
            |        <foo:ValueIri> rdf:type <http://www.knora.org/ontology/knora-base#UriValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "http://example.com" ;
            |            knora-base:valueHasUUID "$uuidEncoded" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasUri "http://example.com"^^xsd:anyURI .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      val reference = ResourcesRepoLive.createNewResourceQueryTwirl(
        dataGraphIri = graphIri,
        resourceToCreate = resource,
        projectIri = projectIri,
        creatorIri = userIri,
      )
      assertUpdateQueriesEqual(expected, result) && assertUpdateQueriesEqual(reference, result)
    },
    test("Create a new resource with a date value") {
      val uuid        = UUID.randomUUID()
      val uuidEncoded = UuidUtil.base64Encode(uuid)
      val resource    = resourceDefinition.copy(newValueInfos = List(dateValueDefinition(uuid)))

      val expected = Update(
        s"""|
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
            |        <foo:ValueIri> rdf:type <http://www.knora.org/ontology/knora-base#DateValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "2024-01-01" ;
            |            knora-base:valueHasUUID "$uuidEncoded" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasStartJDN 0 ;
            |            knora-base:valueHasEndJDN 0 ;
            |            knora-base:valueHasStartPrecision "DAY" ;
            |            knora-base:valueHasEndPrecision "DAY" ;
            |            knora-base:valueHasCalendar "GREGORIAN" .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      val reference = ResourcesRepoLive.createNewResourceQueryTwirl(
        dataGraphIri = graphIri,
        resourceToCreate = resource,
        projectIri = projectIri,
        creatorIri = userIri,
      )
      assertUpdateQueriesEqual(expected, result) && assertUpdateQueriesEqual(reference, result)
    },
    test("Create a new resource with a color value") {
      val uuid        = UUID.randomUUID()
      val uuidEncoded = UuidUtil.base64Encode(uuid)
      val resource    = resourceDefinition.copy(newValueInfos = List(colorValueDefinition(uuid)))

      val expected = Update(
        s"""|
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
            |        <foo:ValueIri> rdf:type <http://www.knora.org/ontology/knora-base#ColorValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "#ff0000" ;
            |            knora-base:valueHasUUID "$uuidEncoded" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasColor "#ff0000" .
            |    }
            |}
            |""".stripMargin,
      )

      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      val reference = ResourcesRepoLive.createNewResourceQueryTwirl(
        dataGraphIri = graphIri,
        resourceToCreate = resource,
        projectIri = projectIri,
        creatorIri = userIri,
      )
      assertUpdateQueriesEqual(expected, result) && assertUpdateQueriesEqual(reference, result)
    },
    test("Create a new resource with a geometry value") {
      val uuid        = UUID.randomUUID()
      val uuidEncoded = UuidUtil.base64Encode(uuid)
      val resource    = resourceDefinition.copy(newValueInfos = List(geometryValueDefinition(uuid)))

      val expected = Update(
        s"""|
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
            |        <foo:ValueIri> rdf:type <http://www.knora.org/ontology/knora-base#GeomValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "{\\"status\\":\\"active\\",\\"lineColor\\":\\"#33ff33\\",\\"lineWidth\\":2,\\"points\\":[{\\"x\\":0.20226843100189035,\\"y\\":0.3090909090909091},{\\"x\\":0.6389413988657845,\\"y\\":0.3594405594405594}],\\"type\\":\\"rectangle\\",\\"original_index\\":0}" ;
            |            knora-base:valueHasUUID "$uuidEncoded" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasGeometry "{\\"status\\":\\"active\\",\\"lineColor\\":\\"#33ff33\\",\\"lineWidth\\":2,\\"points\\":[{\\"x\\":0.20226843100189035,\\"y\\":0.3090909090909091},{\\"x\\":0.6389413988657845,\\"y\\":0.3594405594405594}],\\"type\\":\\"rectangle\\",\\"original_index\\":0}" .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      val reference = ResourcesRepoLive.createNewResourceQueryTwirl(
        dataGraphIri = graphIri,
        resourceToCreate = resource,
        projectIri = projectIri,
        creatorIri = userIri,
      )
      assertUpdateQueriesEqual(expected, result) && assertUpdateQueriesEqual(reference, result)
    },
    test("Create a new resource with a still image file value") {
      val uuid        = UUID.randomUUID()
      val uuidEncoded = UuidUtil.base64Encode(uuid)
      val resource    = resourceDefinition.copy(newValueInfos = List(stillImageFileValueDefinition(uuid)))

      val expected = Update(
        s"""|
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
            |        <foo:ValueIri> rdf:type <http://www.knora.org/ontology/knora-base#StillImageFileValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "foo.jpg" ;
            |            knora-base:valueHasUUID "$uuidEncoded" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:internalFilename "24159oO1pNg-ByLN1NLlMSJ.jp2" ;
            |            knora-base:internalMimeType "image/jp2" ;
            |            knora-base:dimX 100 ;
            |            knora-base:dimY 60 ;
            |            knora-base:originalFilename "foo.png" ;
            |            knora-base:originalMimeType "image/png" .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      val reference = ResourcesRepoLive.createNewResourceQueryTwirl(
        dataGraphIri = graphIri,
        resourceToCreate = resource,
        projectIri = projectIri,
        creatorIri = userIri,
      )
      assertUpdateQueriesEqual(expected, result) && assertUpdateQueriesEqual(reference, result)
    },
    test("Create a new resource with a still image external file value") {
      val uuid        = UUID.randomUUID()
      val uuidEncoded = UuidUtil.base64Encode(uuid)
      val resource    = resourceDefinition.copy(newValueInfos = List(stillImageExternalFileValueDefinition(uuid)))

      val expected = Update(
        s"""|
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
            |        <foo:ValueIri> rdf:type <http://www.knora.org/ontology/knora-base#StillImageFileValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "foo.jpg" ;
            |            knora-base:valueHasUUID "$uuidEncoded" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:internalFilename "24159oO1pNg-ByLN1NLlMSJ.jp2" ;
            |            knora-base:internalMimeType "image/jp2" ;
            |            knora-base:externalUrl "http://example.com/foo.jpg" .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      val reference = ResourcesRepoLive.createNewResourceQueryTwirl(
        dataGraphIri = graphIri,
        resourceToCreate = resource,
        projectIri = projectIri,
        creatorIri = userIri,
      )
      assertUpdateQueriesEqual(expected, result) && assertUpdateQueriesEqual(reference, result)
    },
    test("Create a new resource with a document file value") {
      val uuid        = UUID.randomUUID()
      val uuidEncoded = UuidUtil.base64Encode(uuid)
      val resource    = resourceDefinition.copy(newValueInfos = List(documentFileValueDefinition(uuid)))

      val expected = Update(
        s"""|
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
            |        <foo:ValueIri> rdf:type <http://www.knora.org/ontology/knora-base#DocumentFileValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "foo.pdf" ;
            |            knora-base:valueHasUUID "$uuidEncoded" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:internalFilename "24159oO1pNg-ByLN1NLlMSJ.pdf" ;
            |            knora-base:internalMimeType "application/pdf" ;
            |            knora-base:originalFilename "foo.pdf" ;
            |            knora-base:originalMimeType "application/pdf" ;
            |            knora-base:dimX 100 ;
            |            knora-base:dimY 60 ;
            |            knora-base:pageCount 10 .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      val reference = ResourcesRepoLive.createNewResourceQueryTwirl(
        dataGraphIri = graphIri,
        resourceToCreate = resource,
        projectIri = projectIri,
        creatorIri = userIri,
      )
      assertUpdateQueriesEqual(expected, result) && assertUpdateQueriesEqual(reference, result)
    },
    test("Create a new resource with another file value") {
      val uuid        = UUID.randomUUID()
      val uuidEncoded = UuidUtil.base64Encode(uuid)
      val resource    = resourceDefinition.copy(newValueInfos = List(otherFileValueDefinition(uuid)))

      val expected = Update(
        s"""|
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
            |        <foo:ValueIri> rdf:type <http://www.knora.org/ontology/knora-base#ArchiveFileValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "foo.zip" ;
            |            knora-base:valueHasUUID "$uuidEncoded" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:internalFilename "24159oO1pNg-ByLN1NLlMSJ.zip" ;
            |            knora-base:internalMimeType "application/zip" ;
            |            knora-base:originalFilename "foo.zip" ;
            |            knora-base:originalMimeType "application/zip" .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      val reference = ResourcesRepoLive.createNewResourceQueryTwirl(
        dataGraphIri = graphIri,
        resourceToCreate = resource,
        projectIri = projectIri,
        creatorIri = userIri,
      )
      assertUpdateQueriesEqual(expected, result) && assertUpdateQueriesEqual(reference, result)
    },
    test("Create a new resource with a list value") {
      val uuid        = UUID.randomUUID()
      val uuidEncoded = UuidUtil.base64Encode(uuid)
      val resource    = resourceDefinition.copy(newValueInfos = List(hierarchicalListValueDefinition(uuid)))

      val expected = Update(
        s"""|
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
            |        <foo:ValueIri> rdf:type <http://www.knora.org/ontology/knora-base#ListValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "foo list" ;
            |            knora-base:valueHasUUID "$uuidEncoded" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasListNode <foo:ListNodeIri> .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      val reference = ResourcesRepoLive.createNewResourceQueryTwirl(
        dataGraphIri = graphIri,
        resourceToCreate = resource,
        projectIri = projectIri,
        creatorIri = userIri,
      )
      assertUpdateQueriesEqual(expected, result) && assertUpdateQueriesEqual(reference, result)
    },
    test("Create a new resource with an interval value") {
      val uuid        = UUID.randomUUID()
      val uuidEncoded = UuidUtil.base64Encode(uuid)
      val resource    = resourceDefinition.copy(newValueInfos = List(intervalValueDefinition(uuid)))

      val expected = Update(
        s"""|
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
            |        <foo:ValueIri> rdf:type <http://www.knora.org/ontology/knora-base#IntervalValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "0.0 - 100.0" ;
            |            knora-base:valueHasUUID "$uuidEncoded" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasIntervalStart 0.0 ;
            |            knora-base:valueHasIntervalEnd 100.0 ;
            |    }
            |}
            |""".stripMargin,
      )

      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      val reference = ResourcesRepoLive.createNewResourceQueryTwirl(
        dataGraphIri = graphIri,
        resourceToCreate = resource,
        projectIri = projectIri,
        creatorIri = userIri,
      )
      assertUpdateQueriesEqual(expected, result) && assertUpdateQueriesEqual(reference, result)
    },
    test("Create a new resource with a time value") {
      val uuid        = UUID.randomUUID()
      val uuidEncoded = UuidUtil.base64Encode(uuid)
      val resource    = resourceDefinition.copy(newValueInfos = List(timeValueDefinition(uuid)))

      val expected = Update(
        s"""|
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
            |        <foo:ValueIri> rdf:type <http://www.knora.org/ontology/knora-base#TimeValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "1024-01-01T10:00:00.673298Z" ;
            |            knora-base:valueHasUUID "$uuidEncoded" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasTimeStamp "1024-01-01T10:00:00.673298Z"^^xsd:dateTime .
            |    }
            |}
            |""".stripMargin,
      )

      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      val reference = ResourcesRepoLive.createNewResourceQueryTwirl(
        dataGraphIri = graphIri,
        resourceToCreate = resource,
        projectIri = projectIri,
        creatorIri = userIri,
      )
      assertUpdateQueriesEqual(expected, result) && assertUpdateQueriesEqual(reference, result)
    },
    test("Create a new resource with a geoname value") {
      val uuid        = UUID.randomUUID()
      val uuidEncoded = UuidUtil.base64Encode(uuid)
      val resource    = resourceDefinition.copy(newValueInfos = List(geonameValueDefinition(uuid)))

      val expected = Update(
        s"""|
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
            |        <foo:ValueIri> rdf:type <http://www.knora.org/ontology/knora-base#GeonameValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "geoname_code" ;
            |            knora-base:valueHasUUID "$uuidEncoded" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasGeonameCode "geoname_code" .
            |    }
            |}
            |""".stripMargin,
      )

      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      val reference = ResourcesRepoLive.createNewResourceQueryTwirl(
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
  // TODO:
  // - add test for other value types
  //   - link value
  //   - text value (unformatted)
  //   - text value (formatted)
  // - bring back the link stuff (and figure out what's the deal)
  // - add test for creating a resource with multiple values
  // - add test for creating a resource with multiple links
  // - add test for creating a resource with comment (and other optionals)

}
