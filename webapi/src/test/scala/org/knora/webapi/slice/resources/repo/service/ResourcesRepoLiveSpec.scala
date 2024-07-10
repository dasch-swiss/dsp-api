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
import org.knora.webapi.messages.twirl.StandoffAttribute
import org.knora.webapi.messages.twirl.StandoffAttributeValue
import org.knora.webapi.messages.twirl.StandoffLinkValueInfo
import org.knora.webapi.messages.twirl.StandoffTagInfo
import org.knora.webapi.messages.twirl.TypeSpecificValueInfo
import org.knora.webapi.messages.util.CalendarNameGregorian
import org.knora.webapi.messages.util.DatePrecisionDay
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object TestData {

  val graphIri          = InternalIri("foo:Graph")
  val projectIri        = "foo:ProjectIri"
  val userIri           = "foo:UserIri"
  val resourceIri       = "foo:ResourceInstanceIri"
  val resourceClassIri  = "foo:ClassIri"
  val label             = "foo Label"
  val creationDate      = Instant.parse("2024-01-01T10:00:00.673298Z")
  val permissions       = "fooPermissionsString"
  val valueCreator      = "foo:ValueCreatorIri"
  val valuePermissions  = "fooValuePermissions"
  val valueCreationDate = Instant.parse("2024-01-01T12:00:00.673298Z")

  val resourceDefinition = ResourceReadyToCreate(
    resourceIri = resourceIri,
    resourceClassIri = resourceClassIri,
    resourceLabel = label,
    creationDate = creationDate,
    permissions = permissions,
    newValueInfos = Seq.empty,
    standoffLinks = Seq.empty,
  )

  val linkValueDefinition = NewValueInfo(
    resourceIri = resourceIri,
    propertyIri = "foo:hasLinkToValue",
    valueIri = "foo:LinkValueIri",
    valueTypeIri = OntologyConstants.KnoraBase.LinkValue,
    valueUUID = UUID.randomUUID(),
    value = TypeSpecificValueInfo.LinkValueInfo("foo:LinkTargetIri"),
    valuePermissions = valuePermissions,
    valueCreator = valueCreator,
    creationDate = valueCreationDate,
    valueHasOrder = 1,
    valueHasString = "foo:LinkValueIri",
    comment = None,
  )

  val unformattedTextValueDefinition = NewValueInfo(
    resourceIri = resourceIri,
    propertyIri = "foo:hasUnformattedTextValue",
    valueIri = "foo:UnformattedTextValueIri",
    valueTypeIri = OntologyConstants.KnoraBase.TextValue,
    valueUUID = UUID.randomUUID(),
    value = TypeSpecificValueInfo.UnformattedTextValueInfo(Some("en")),
    valuePermissions = valuePermissions,
    valueCreator = valueCreator,
    creationDate = valueCreationDate,
    valueHasOrder = 1,
    valueHasString = "this is a text without formatting",
    comment = None,
  )

  val standoffTagUuid = UUID.randomUUID()
  val formattedTextValueDefinition = NewValueInfo(
    resourceIri = resourceIri,
    propertyIri = "foo:hasFormattedTextValue",
    valueIri = "foo:FormattedTextValueIri",
    valueTypeIri = OntologyConstants.KnoraBase.TextValue,
    valueUUID = UUID.randomUUID(),
    value = TypeSpecificValueInfo.FormattedTextValueInfo(
      valueHasLanguage = Some("en"),
      mappingIri = "foo:MappingIri",
      maxStandoffStartIndex = 0,
      standoff = List(
        StandoffTagInfo(
          standoffTagClassIri = "foo:StandoffTagClassIri",
          standoffTagInstanceIri = "foo:StandoffTagInstanceIri",
          startParentIri = Some("foo:StartParentIri"),
          endParentIri = Some("foo:EndParentIri"),
          uuid = standoffTagUuid,
          originalXMLID = Some("xml-id"),
          startIndex = 0,
          endIndex = Some(3),
          startPosition = 0,
          endPosition = 3,
          attributes = List(
            StandoffAttribute(
              "foo:attributePropertyIri",
              StandoffAttributeValue.IriAttribute("foo:standoffAttributeIri"),
            ),
            StandoffAttribute("foo:attributePropertyIri", StandoffAttributeValue.UriAttribute("http://example.com")),
            StandoffAttribute(
              "foo:attributePropertyIri",
              StandoffAttributeValue.InternalReferenceAttribute("foo:internalRef"),
            ),
            StandoffAttribute("foo:attributePropertyIri", StandoffAttributeValue.StringAttribute("attribute value")),
            StandoffAttribute("foo:attributePropertyIri", StandoffAttributeValue.IntegerAttribute(42)),
            StandoffAttribute("foo:attributePropertyIri", StandoffAttributeValue.DecimalAttribute(BigDecimal(42.42))),
            StandoffAttribute("foo:attributePropertyIri", StandoffAttributeValue.BooleanAttribute(true)),
            StandoffAttribute(
              "foo:attributePropertyIri",
              StandoffAttributeValue.TimeAttribute(Instant.parse("1024-01-01T10:00:00.673298Z")),
            ),
          ),
        ),
      ),
    ),
    valuePermissions = valuePermissions,
    valueCreator = valueCreator,
    creationDate = valueCreationDate,
    valueHasOrder = 1,
    valueHasString = "this is a text with formatting",
    comment = None,
  )

  val intValueDefinition =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "foo:hasInt",
      valueIri = "foo:IntValueIri",
      valueTypeIri = OntologyConstants.KnoraBase.IntValue,
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.IntegerValueInfo(42),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "42",
      comment = None,
    )

  val boolValueDefinition =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "foo:hasBoolean",
      valueIri = "foo:BooleanValueIri",
      valueTypeIri = OntologyConstants.KnoraBase.BooleanValue,
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.BooleanValueInfo(true),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "true",
      comment = None,
    )

  val decimalValueDefinition =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "foo:hasDecimal",
      valueIri = "foo:DecimalValueIri",
      valueTypeIri = OntologyConstants.KnoraBase.DecimalValue,
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.DecimalValueInfo(BigDecimal(42.42)),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "42.42",
      comment = None,
    )

  val uriValueDefinition =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "foo:hasUri",
      valueIri = "foo:UriValueIri",
      valueTypeIri = OntologyConstants.KnoraBase.UriValue,
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.UriValueInfo("http://example.com"),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "http://example.com",
      comment = None,
    )

  val dateValueDefinition =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "foo:hasDate",
      valueIri = "foo:DateValueIri",
      valueTypeIri = OntologyConstants.KnoraBase.DateValue,
      valueUUID = UUID.randomUUID(),
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

  val colorValueDefinition =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "foo:hasColor",
      valueIri = "foo:ColorValueIri",
      valueTypeIri = OntologyConstants.KnoraBase.ColorValue,
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.ColorValueInfo("#ff0000"),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "#ff0000",
      comment = None,
    )

  val geometryValueDefinition =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "foo:hasGeom",
      valueIri = "foo:GeomValueIri",
      valueTypeIri = OntologyConstants.KnoraBase.GeomValue,
      valueUUID = UUID.randomUUID(),
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

  val stillImageFileValueDefinition =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "foo:hasStillImage",
      valueIri = "foo:StillImageFileValueIri",
      valueTypeIri = OntologyConstants.KnoraBase.StillImageFileValue,
      valueUUID = UUID.randomUUID(),
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

  val stillImageExternalFileValueDefinition =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "foo:hasStillImageExternal",
      valueIri = "foo:StillImageExternalFileValueIri",
      valueTypeIri = OntologyConstants.KnoraBase.StillImageFileValue,
      valueUUID = UUID.randomUUID(),
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

  val documentFileValueDefinition =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "foo:hasDocument",
      valueIri = "foo:DocumentFileValueIri",
      valueTypeIri = OntologyConstants.KnoraBase.DocumentFileValue,
      valueUUID = UUID.randomUUID(),
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

  val otherFileValueDefinition =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "foo:hasOtherFile",
      valueIri = "foo:OtherFileValueIri",
      valueTypeIri = OntologyConstants.KnoraBase.ArchiveFileValue,
      valueUUID = UUID.randomUUID(),
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

  val hierarchicalListValueDefinition =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "foo:hasList",
      valueIri = "foo:ListNodeIri",
      valueTypeIri = OntologyConstants.KnoraBase.ListValue,
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.HierarchicalListValueInfo("foo:ListNodeIri"),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "foo list",
      comment = None,
    )

  val intervalValueDefinition =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "foo:hasInterval",
      valueIri = "foo:IntervalValueIri",
      valueTypeIri = OntologyConstants.KnoraBase.IntervalValue,
      valueUUID = UUID.randomUUID(),
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

  val timeValueDefinition =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "foo:hasTime",
      valueIri = "foo:TimeValueIri",
      valueTypeIri = OntologyConstants.KnoraBase.TimeValue,
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.TimeValueInfo(Instant.parse("1024-01-01T10:00:00.673298Z")),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "1024-01-01T10:00:00.673298Z",
      comment = None,
    )

  val geonameValueDefinition =
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "foo:hasGeoname",
      valueIri = "foo:GeonameValueIri",
      valueTypeIri = OntologyConstants.KnoraBase.GeonameValue,
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.GeonameValueInfo("geoname_code"),
      valuePermissions = valuePermissions,
      valueCreator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "geoname_code",
      comment = None,
    )

  val standoffLinkValue =
    StandoffLinkValueInfo(
      linkPropertyIri = "foo:hasStandoffLinkTo",
      newLinkValueIri = "foo:StandoffLinkValueIri",
      linkTargetIri = "foo:StandoffLinkTargetIri",
      newReferenceCount = 2,
      newLinkValueCreator = valueCreator,
      newLinkValuePermissions = valuePermissions,
      valueUuid = UuidUtil.base64Encode(UUID.randomUUID()),
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
      assertTrue(actualStr == expectedStr)
    }
  }

  def spec: Spec[Environment & (TestEnvironment & Scope), Any] = tests.provide(StringFormatter.test)

  private val createResourceWithoutValuesTest = test("Create a new resource query without values") {
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
    assertUpdateQueriesEqual(expected, result)
  }

  private val createResourceWithValueSuite = suite("Create new resource with any type of value")(
    test("Create a new resource with a link value") {
      val resource = resourceDefinition.copy(newValueInfos = List(linkValueDefinition))

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
            |            <foo:hasLinkToValue> <foo:LinkValueIri> ;
            |            <foo:hasLinkTo> <foo:LinkTargetIri> .
            |        <foo:LinkValueIri> rdf:type <http://www.knora.org/ontology/knora-base#LinkValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "foo:LinkValueIri" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(linkValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            rdf:subject <$resourceIri> ;
            |            rdf:predicate <foo:hasLinkTo> ;
            |            rdf:object <foo:LinkTargetIri> ;
            |            knora-base:valueHasRefCount 1 .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with an unformatted text value") {
      val resource = resourceDefinition.copy(newValueInfos = List(unformattedTextValueDefinition))

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
            |            <foo:hasUnformattedTextValue> <foo:UnformattedTextValueIri> .
            |        <foo:UnformattedTextValueIri> rdf:type <http://www.knora.org/ontology/knora-base#TextValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "this is a text without formatting" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(unformattedTextValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasLanguage "en" .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with an formatted text value") {
      val resource = resourceDefinition.copy(newValueInfos = List(formattedTextValueDefinition))

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
            |            <foo:hasFormattedTextValue> <foo:FormattedTextValueIri> .
            |        <foo:FormattedTextValueIri> rdf:type <http://www.knora.org/ontology/knora-base#TextValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "this is a text with formatting" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(formattedTextValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasLanguage "en" ;
            |            knora-base:valueHasMapping <foo:MappingIri> ;
            |            knora-base:valueHasMaxStandoffStartIndex 0 ;
            |            knora-base:valueHasStandoff <foo:StandoffTagInstanceIri> .
            |        <foo:StandoffTagInstanceIri> rdf:type <foo:StandoffTagClassIri> ;
            |            knora-base:standoffTagHasEndIndex 3 ;
            |            knora-base:standoffTagHasStartParent <foo:StartParentIri> ;
            |            knora-base:standoffTagHasEndParent <foo:EndParentIri> ;
            |            knora-base:standoffTagHasOriginalXMLID "xml-id" ;
            |            <foo:attributePropertyIri> <foo:standoffAttributeIri> ;
            |            <foo:attributePropertyIri> "http://example.com"^^xsd:anyURI ;
            |            <foo:attributePropertyIri> <foo:internalRef> ;
            |            <foo:attributePropertyIri> "attribute value" ;
            |            <foo:attributePropertyIri> 42 ;
            |            <foo:attributePropertyIri> 42.42 ;
            |            <foo:attributePropertyIri> true ;
            |            <foo:attributePropertyIri> "1024-01-01T10:00:00.673298Z"^^xsd:dateTime ;
            |            knora-base:standoffTagHasStartIndex 0 ;
            |            knora-base:standoffTagHasUUID "${UuidUtil.base64Encode(standoffTagUuid)}" ;
            |            knora-base:standoffTagHasStart 0 ;
            |            knora-base:standoffTagHasEnd 3 .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with an integer value") {
      val resource = resourceDefinition.copy(newValueInfos = List(intValueDefinition))

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
            |            <foo:hasInt> <foo:IntValueIri> .
            |        <foo:IntValueIri> rdf:type <http://www.knora.org/ontology/knora-base#IntValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "42" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(intValueDefinition.valueUUID)}" ;
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
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a boolean value") {
      val resource = resourceDefinition.copy(newValueInfos = List(boolValueDefinition))

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
            |            <foo:hasBoolean> <foo:BooleanValueIri> .
            |        <foo:BooleanValueIri> rdf:type <http://www.knora.org/ontology/knora-base#BooleanValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "true" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(boolValueDefinition.valueUUID)}" ;
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
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a decimal value") {
      val resource = resourceDefinition.copy(newValueInfos = List(decimalValueDefinition))

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
            |            <foo:hasDecimal> <foo:DecimalValueIri> .
            |        <foo:DecimalValueIri> rdf:type <http://www.knora.org/ontology/knora-base#DecimalValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "42.42" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(decimalValueDefinition.valueUUID)}" ;
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
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a URI value") {
      val resource = resourceDefinition.copy(newValueInfos = List(uriValueDefinition))

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
            |            <foo:hasUri> <foo:UriValueIri> .
            |        <foo:UriValueIri> rdf:type <http://www.knora.org/ontology/knora-base#UriValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "http://example.com" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(uriValueDefinition.valueUUID)}" ;
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
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a date value") {
      val resource = resourceDefinition.copy(newValueInfos = List(dateValueDefinition))

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
            |            <foo:hasDate> <foo:DateValueIri> .
            |        <foo:DateValueIri> rdf:type <http://www.knora.org/ontology/knora-base#DateValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "2024-01-01" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(dateValueDefinition.valueUUID)}" ;
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
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a color value") {
      val resource = resourceDefinition.copy(newValueInfos = List(colorValueDefinition))

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
            |            <foo:hasColor> <foo:ColorValueIri> .
            |        <foo:ColorValueIri> rdf:type <http://www.knora.org/ontology/knora-base#ColorValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "#ff0000" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(colorValueDefinition.valueUUID)}" ;
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
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a geometry value") {
      val resource = resourceDefinition.copy(newValueInfos = List(geometryValueDefinition))

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
            |            <foo:hasGeom> <foo:GeomValueIri> .
            |        <foo:GeomValueIri> rdf:type <http://www.knora.org/ontology/knora-base#GeomValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "{\\"status\\":\\"active\\",\\"lineColor\\":\\"#33ff33\\",\\"lineWidth\\":2,\\"points\\":[{\\"x\\":0.20226843100189035,\\"y\\":0.3090909090909091},{\\"x\\":0.6389413988657845,\\"y\\":0.3594405594405594}],\\"type\\":\\"rectangle\\",\\"original_index\\":0}" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(geometryValueDefinition.valueUUID)}" ;
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
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a still image file value") {
      val resource = resourceDefinition.copy(newValueInfos = List(stillImageFileValueDefinition))

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
            |            <foo:hasStillImage> <foo:StillImageFileValueIri> .
            |        <foo:StillImageFileValueIri> rdf:type <http://www.knora.org/ontology/knora-base#StillImageFileValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "foo.jpg" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(stillImageFileValueDefinition.valueUUID)}" ;
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
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a still image external file value") {
      val resource    = resourceDefinition.copy(newValueInfos = List(stillImageExternalFileValueDefinition))
      val uuidEncoded = UuidUtil.base64Encode(stillImageExternalFileValueDefinition.valueUUID)

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
            |            <foo:hasStillImageExternal> <foo:StillImageExternalFileValueIri> .
            |        <foo:StillImageExternalFileValueIri> rdf:type <http://www.knora.org/ontology/knora-base#StillImageFileValue> ;
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
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a document file value") {
      val resource = resourceDefinition.copy(newValueInfos = List(documentFileValueDefinition))

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
            |            <foo:hasDocument> <foo:DocumentFileValueIri> .
            |        <foo:DocumentFileValueIri> rdf:type <http://www.knora.org/ontology/knora-base#DocumentFileValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "foo.pdf" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(documentFileValueDefinition.valueUUID)}" ;
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
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with another file value") {
      val resource = resourceDefinition.copy(newValueInfos = List(otherFileValueDefinition))

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
            |            <foo:hasOtherFile> <foo:OtherFileValueIri> .
            |        <foo:OtherFileValueIri> rdf:type <http://www.knora.org/ontology/knora-base#ArchiveFileValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "foo.zip" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(otherFileValueDefinition.valueUUID)}" ;
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
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a list value") {
      val resource = resourceDefinition.copy(newValueInfos = List(hierarchicalListValueDefinition))

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
            |            <foo:hasList> <foo:ListNodeIri> .
            |        <foo:ListNodeIri> rdf:type <http://www.knora.org/ontology/knora-base#ListValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "foo list" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(hierarchicalListValueDefinition.valueUUID)}" ;
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
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with an interval value") {
      val resource = resourceDefinition.copy(newValueInfos = List(intervalValueDefinition))

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
            |            <foo:hasInterval> <foo:IntervalValueIri> .
            |        <foo:IntervalValueIri> rdf:type <http://www.knora.org/ontology/knora-base#IntervalValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "0.0 - 100.0" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(intervalValueDefinition.valueUUID)}" ;
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
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a time value") {
      val resource = resourceDefinition.copy(newValueInfos = List(timeValueDefinition))

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
            |            <foo:hasTime> <foo:TimeValueIri> .
            |        <foo:TimeValueIri> rdf:type <http://www.knora.org/ontology/knora-base#TimeValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "1024-01-01T10:00:00.673298Z" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(timeValueDefinition.valueUUID)}" ;
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
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a geoname value") {
      val resource = resourceDefinition.copy(newValueInfos = List(geonameValueDefinition))

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
            |            <foo:hasGeoname> <foo:GeonameValueIri> .
            |        <foo:GeonameValueIri> rdf:type <http://www.knora.org/ontology/knora-base#GeonameValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "geoname_code" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(geonameValueDefinition.valueUUID)}" ;
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
      assertUpdateQueriesEqual(expected, result)
    },
  )

  private val createResourceWithMultipleValuesTest = test("Create a resource with multiple values") {
    val resource = resourceDefinition.copy(newValueInfos = List(intValueDefinition, boolValueDefinition))
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
          |            <foo:hasInt> <foo:IntValueIri> ;
          |            <foo:hasBoolean> <foo:BooleanValueIri> .
          |        <foo:IntValueIri> rdf:type <http://www.knora.org/ontology/knora-base#IntValue> ;
          |            knora-base:isDeleted false  ;
          |            knora-base:valueHasString "42" ;
          |            knora-base:valueHasUUID "${UuidUtil.base64Encode(intValueDefinition.valueUUID)}" ;
          |            knora-base:attachedToUser <$valueCreator> ;
          |            knora-base:hasPermissions "$valuePermissions" ;
          |            knora-base:valueHasOrder 1 ;
          |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
          |            knora-base:valueHasInteger 42 .
          |        <foo:BooleanValueIri> rdf:type <http://www.knora.org/ontology/knora-base#BooleanValue> ;
          |            knora-base:isDeleted false  ;
          |            knora-base:valueHasString "true" ;
          |            knora-base:valueHasUUID "${UuidUtil.base64Encode(boolValueDefinition.valueUUID)}" ;
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
    assertUpdateQueriesEqual(expected, result)
  }

  private val createResourceWithStzandoffLinkTest = test("Create a resource with a standoff link value") {
    val resource = resourceDefinition.copy(standoffLinks = List(standoffLinkValue))

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
          |            <foo:hasStandoffLinkTo> <foo:StandoffLinkTargetIri> ;
          |            <foo:hasStandoffLinkToValue> <foo:StandoffLinkValueIri> .
          |        <foo:StandoffLinkValueIri> rdf:type <http://www.knora.org/ontology/knora-base#LinkValue> ;
          |            rdf:subject <$resourceIri> ;
          |            rdf:predicate <foo:hasStandoffLinkTo> ;
          |            rdf:object <foo:StandoffLinkTargetIri> ;
          |            knora-base:valueHasString "foo:StandoffLinkTargetIri" ;
          |            knora-base:valueHasRefCount 2;
          |            knora-base:isDeleted false  ;
          |            knora-base:valueCreationDate "$creationDate"^^xsd:dateTime ;
          |            knora-base:attachedToUser <$valueCreator> ;
          |            knora-base:hasPermissions "$valuePermissions" ;
          |            knora-base:valueHasUUID "${standoffLinkValue.valueUuid}" .
          |    }
          |}
          |""".stripMargin,
    )
    val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
    assertUpdateQueriesEqual(expected, result)
  }

  private val createResourceWithValueAndStandoffLinkTest =
    test("Create a resource with a value and a standoff link value") {
      val resource =
        resourceDefinition.copy(standoffLinks = List(standoffLinkValue), newValueInfos = List(intValueDefinition))

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
            |            <foo:hasInt> <foo:IntValueIri> ;
            |            <foo:hasStandoffLinkTo> <foo:StandoffLinkTargetIri> ;
            |            <foo:hasStandoffLinkToValue> <foo:StandoffLinkValueIri> .
            |        <foo:IntValueIri> rdf:type <http://www.knora.org/ontology/knora-base#IntValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "42" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(intValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasInteger 42 .
            |        <foo:StandoffLinkValueIri> rdf:type <http://www.knora.org/ontology/knora-base#LinkValue> ;
            |            rdf:subject <$resourceIri> ;
            |            rdf:predicate <foo:hasStandoffLinkTo> ;
            |            rdf:object <foo:StandoffLinkTargetIri> ;
            |            knora-base:valueHasString "foo:StandoffLinkTargetIri" ;
            |            knora-base:valueHasRefCount 2;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueCreationDate "$creationDate"^^xsd:dateTime ;
            |            knora-base:attachedToUser <$valueCreator> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasUUID "${standoffLinkValue.valueUuid}" .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    }

  val tests: Spec[StringFormatter, Nothing] =
    suite("ResourcesRepoLiveSpec")(
      createResourceWithoutValuesTest,
      createResourceWithValueSuite,
      createResourceWithMultipleValuesTest,
      createResourceWithStzandoffLinkTest,
      createResourceWithValueAndStandoffLinkTest,
    )
  // TODO:
  // - reuse vocabulary

}
