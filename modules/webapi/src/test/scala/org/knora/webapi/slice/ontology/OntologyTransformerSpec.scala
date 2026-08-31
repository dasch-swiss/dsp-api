/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.update.UpdateAction
import org.junit.runner.RunWith
import zio.Exit
import zio.NonEmptyChunk
import zio.ULayer
import zio.ZIO
import zio.ZLayer
import zio.test.*

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.*

import dsp.valueobjects.UuidUtil
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.InternalSchema
import org.knora.webapi.core.TestAppConfig
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.OntologyConstants.Rdf
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.StandoffEntityInfoGetResponseV2
import org.knora.webapi.messages.v2.responder.standoffmessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueType
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.common.jena.DatasetOps
import org.knora.webapi.slice.common.jena.ModelOps
import org.knora.webapi.slice.resources.repo.service.value.queries.InsertValueQueryBuilder
import org.knora.webapi.slice.standoff.service.StandoffMappingService
import org.knora.webapi.slice.standoff.service.StandoffMappingServiceInMemory

@RunWith(classOf[DspZTestJUnitRunner])
class OntologyTransformerSpec extends ZIOSpecDefault {

  private val transformer = ZIO.serviceWithZIO[OntologyTransformer]

  private val shortcode    = Shortcode.unsafeFrom("9999")
  private val resourceIri  = ResourceIri.makeNew(shortcode)
  private val resourceIri2 = ResourceIri.makeNew(shortcode)
  private val valueIri     = ValueIri.makeNew(resourceIri)

  private val onto         = "http://0.0.0.0:3333/ontology/9999/onto/v2#"
  private val internalOnto = "http://www.knora.org/ontology/9999/onto#"
  private val knoraApi     = "http://api.knora.org/ontology/knora-api/v2#"
  private val xsd          = "http://www.w3.org/2001/XMLSchema#"

  private def writeTempFile(suffix: String, content: String): ZIO[Any, Throwable, Path] =
    ZIO.attemptBlocking {
      val p = Files.createTempFile("onto-transformer-test-", suffix)
      Files.write(p, content.getBytes(StandardCharsets.UTF_8))
      p
    }

  private def deleteIfExists(p: Path): ZIO[Any, Nothing, Unit] =
    ZIO.attempt(Files.deleteIfExists(p)).ignore.unit

  /** Wraps a single value object as a one-resource JSON-LD payload on a fixed resource IRI. */
  private def resourceWithValueJsonLd(
    valueProp: String,
    valueClass: String,
    inner: String,
  ): String =
    s"""
       |[{
       |    "@id": "$resourceIri",
       |    "@type": "${onto}Example",
       |    "rdfs:label": "test",
       |    "$valueProp": {
       |      "@id": "$valueIri",
       |      "@type": "$valueClass",
       |      $inner
       |    },
       |    "@context": {
       |       "rdfs": "http://www.w3.org/2000/01/rdf-schema#"
       |    }
       |}]""".stripMargin

  /** Expected Turtle for a resource carrying a single simple-scalar value. */
  private def expectedResourceWithSimpleValue(
    propLocalName: String,
    valueClass: String,
    valueHasProp: String,
    valueLiteral: String,
  ): String =
    s"""
       | PREFIX rdf:        <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
       | PREFIX rdfs:       <http://www.w3.org/2000/01/rdf-schema#>
       | PREFIX xsd:        <http://www.w3.org/2001/XMLSchema#>
       | PREFIX onto:       <http://www.knora.org/ontology/9999/onto#>
       | PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
       |
       | <$resourceIri>
       |     a           onto:Example ;
       |     rdfs:label  "test" ;
       |     onto:$propLocalName <$valueIri> .
       |
       | <$valueIri>
       |     a                        knora-base:$valueClass ;
       |     knora-base:$valueHasProp $valueLiteral .
       |""".stripMargin

  /** Stage-1 contract: drives the package-private `toInternalSchema` (IRI rewrite only). */
  private def runTransform(jsonLd: String, expectedTurtle: String) =
    ZIO.scoped {
      for {
        inputPath  <- ZIO.acquireRelease(writeTempFile(".jsonld", jsonLd).orDie)(deleteIfExists)
        outputPath <- transformer(_.toInternalSchema(inputPath))
        actualNQ   <- ZIO.attempt(new String(Files.readAllBytes(outputPath), StandardCharsets.UTF_8))
        actual     <- ModelOps.from(actualNQ, Lang.NTRIPLES)
        expected   <- ModelOps.fromTurtle(expectedTurtle)
        iso         = actual.isIsomorphicWith(expected)
      } yield assertTrue(iso)
    }

  // ---- Stage 2 ----

  private val valueIri2    = ValueIri.makeNew(resourceIri)
  private val knownInstant = Instant.parse("2026-01-01T00:00:00Z")

  private val project = KnoraProject(
    ProjectIri.unsafeFrom("http://rdfh.ch/projects/9999"),
    Shortname.unsafeFrom("onto"),
    shortcode,
    None,
    NonEmptyChunk(Description.unsafeFrom(StringLiteralV2.from("Some description"))),
    List.empty,
    None,
    Status.Active,
    SelfJoin.CannotJoin,
    RestrictedView.default,
    Set.empty,
    Set.empty,
  )

  private val ctx = ConversionContext(
    attachedToUser = UserIri.unsafeFrom("http://rdfh.ch/users/exampleUser"),
    attachedToProject = project,
    permissions = "CR knora-admin:Creator|V knora-admin:KnownUser",
  )

  private val dataNamedGraph = ProjectService.projectDataNamedGraphV2(project).value

  /**
   * Stage-2: drives the full `toKnoraBase` with a fixed clock so synthesised dates are deterministic. The output is
   * NQuads with every quad in the project's data named graph; the assertion extracts that named model and checks
   * nothing landed in any other graph.
   */
  private def runTransformStage2(jsonLd: String, expectedTurtle: String) =
    ZIO.scoped {
      for {
        inputPath   <- ZIO.acquireRelease(writeTempFile(".jsonld", jsonLd).orDie)(deleteIfExists)
        _           <- TestClock.setTime(knownInstant)
        outputPath  <- transformer(_.toKnoraBase(inputPath, ctx))
        actualNQ    <- ZIO.attempt(new String(Files.readAllBytes(outputPath), StandardCharsets.UTF_8))
        dataset     <- DatasetOps.from(actualNQ, Lang.NQUADS).mapError(new RuntimeException(_))
        graphNames   = dataset.listModelNames().asScala.map(_.getURI).toList
        actual       = dataset.getNamedModel(dataNamedGraph)
        defaultEmpty = dataset.getDefaultModel.isEmpty
        expected    <- ModelOps.fromTurtle(expectedTurtle)
        iso          = actual.isIsomorphicWith(expected)
      } yield assertTrue(iso, defaultEmpty, graphNames == List(dataNamedGraph))
    }

  private val simpleScalarValues = suite("Simple Scalar Values")(
    test("BooleanValue") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testBoolean",
          valueClass = s"${knoraApi}BooleanValue",
          inner = s""""${knoraApi}booleanValueAsBoolean": { "@type": "${xsd}boolean", "@value": true }""",
        ),
        expectedTurtle = expectedResourceWithSimpleValue(
          propLocalName = "testBoolean",
          valueClass = "BooleanValue",
          valueHasProp = "valueHasBoolean",
          valueLiteral = "\"true\"^^xsd:boolean",
        ),
      )
    },
    test("ColorValue") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testColor",
          valueClass = s"${knoraApi}ColorValue",
          inner = s""""${knoraApi}colorValueAsColor": { "@type": "${xsd}string", "@value": "#00ff00" }""",
        ),
        expectedTurtle = expectedResourceWithSimpleValue(
          propLocalName = "testColor",
          valueClass = "ColorValue",
          valueHasProp = "valueHasColor",
          valueLiteral = "\"#00ff00\"",
        ),
      )
    },
    test("DecimalValue") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testDecimalSimpleText",
          valueClass = s"${knoraApi}DecimalValue",
          inner = s""""${knoraApi}decimalValueAsDecimal": { "@type": "${xsd}decimal", "@value": "2.71" }""",
        ),
        expectedTurtle = expectedResourceWithSimpleValue(
          propLocalName = "testDecimalSimpleText",
          valueClass = "DecimalValue",
          valueHasProp = "valueHasDecimal",
          valueLiteral = "\"2.71\"^^xsd:decimal",
        ),
      )
    },
    test("GeonameValue") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testGeoname",
          valueClass = s"${knoraApi}GeonameValue",
          inner = s""""${knoraApi}geonameValueAsGeonameCode": { "@type": "${xsd}string", "@value": "1111111" }""",
        ),
        expectedTurtle = expectedResourceWithSimpleValue(
          propLocalName = "testGeoname",
          valueClass = "GeonameValue",
          valueHasProp = "valueHasGeonameCode",
          valueLiteral = "\"1111111\"",
        ),
      )
    },
    test("IntValue") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testIntegerSimpleText",
          valueClass = s"${knoraApi}IntValue",
          inner = s""""${knoraApi}intValueAsInt": { "@type": "${xsd}int", "@value": "1" }""",
        ),
        expectedTurtle = expectedResourceWithSimpleValue(
          propLocalName = "testIntegerSimpleText",
          valueClass = "IntValue",
          valueHasProp = "valueHasInteger",
          valueLiteral = "\"1\"^^xsd:int",
        ),
      )
    },
    test("TimeValue") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testTimeValue",
          valueClass = s"${knoraApi}TimeValue",
          inner =
            s""""${knoraApi}timeValueAsTimeStamp": { "@type": "${xsd}dateTimeStamp", "@value": "2019-10-23T13:45:12.01-14:00" }""",
        ),
        expectedTurtle = expectedResourceWithSimpleValue(
          propLocalName = "testTimeValue",
          valueClass = "TimeValue",
          valueHasProp = "valueHasTimeStamp",
          valueLiteral = "\"2019-10-23T13:45:12.01-14:00\"^^xsd:dateTimeStamp",
        ),
      )
    },
    test("UriValue") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testUriValue",
          valueClass = s"${knoraApi}UriValue",
          inner = s""""${knoraApi}uriValueAsUri": { "@type": "${xsd}anyURI", "@value": "https://dasch.swiss" }""",
        ),
        expectedTurtle = expectedResourceWithSimpleValue(
          propLocalName = "testUriValue",
          valueClass = "UriValue",
          valueHasProp = "valueHasUri",
          valueLiteral = "\"https://dasch.swiss\"^^xsd:anyURI",
        ),
      )
    },
  )

  private val iriRefValues = suite("IRI-Ref Values")(
    test("ListValue") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testListProp",
          valueClass = s"${knoraApi}ListValue",
          inner = s""""${knoraApi}listValueAsListNode": { "@id": "http://rdfh.ch/lists/9999/WF8qwFbGQg228GJUlqOLzw" }""",
        ),
        expectedTurtle = expectedResourceWithSimpleValue(
          propLocalName = "testListProp",
          valueClass = "ListValue",
          valueHasProp = "valueHasListNode",
          valueLiteral = "<http://rdfh.ch/lists/9999/WF8qwFbGQg228GJUlqOLzw>",
        ),
      )
    },
    test("LinkValue (stage 1: IRI rewrite only, no reification yet)") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testHasLinkToValue",
          valueClass = s"${knoraApi}LinkValue",
          inner = s""""${knoraApi}linkValueHasTargetIri": { "@id": "http://rdfh.ch/9999/CV9Lea7hSESPWPuILr8dyw" }""",
        ),
        expectedTurtle = expectedResourceWithSimpleValue(
          propLocalName = "testHasLinkToValue",
          valueClass = "LinkValue",
          valueHasProp = "linkValueHasTargetIri",
          valueLiteral = "<http://rdfh.ch/9999/CV9Lea7hSESPWPuILr8dyw>",
        ),
      )
    },
  )

  private val textValues = suite("Text Values")(
    test("simple text") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testSimpleText",
          valueClass = s"${knoraApi}TextValue",
          inner = s""""${knoraApi}valueAsString": { "@type": "${xsd}string", "@value": "Text" }""",
        ),
        expectedTurtle = expectedResourceWithSimpleValue(
          propLocalName = "testSimpleText",
          valueClass = "TextValue",
          valueHasProp = "valueHasString",
          valueLiteral = "\"Text\"",
        ),
      )
    },
    test("simple text with comment") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testSimpleText",
          valueClass = s"${knoraApi}TextValue",
          inner = s""""${knoraApi}valueAsString": { "@type": "${xsd}string", "@value": "Text 1" },
                     |    "${knoraApi}valueHasComment": { "@type": "${xsd}string", "@value": "comment" }""".stripMargin,
        ),
        expectedTurtle = s"""
                            | PREFIX rdfs:       <http://www.w3.org/2000/01/rdf-schema#>
                            | PREFIX onto:       <http://www.knora.org/ontology/9999/onto#>
                            | PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                            |
                            | <$resourceIri>
                            |     a          onto:Example ;
                            |     rdfs:label "test" ;
                            |     onto:testSimpleText <$valueIri> .
                            |
                            | <$valueIri>
                            |     a                          knora-base:TextValue ;
                            |     knora-base:valueHasString  "Text 1" ;
                            |     knora-base:valueHasComment "comment" .
                            |""".stripMargin,
      )
    },
    test("rich text") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testRichtext",
          valueClass = s"${knoraApi}TextValue",
          inner =
            s""""${knoraApi}textValueAsXml": { "@type": "${xsd}string", "@value": "<?xml version=\\"1.0\\" encoding=\\"UTF-8\\"?>\\n<text>Text</text>" },
               |    "${knoraApi}textValueHasMapping": { "@id": "http://rdfh.ch/standoff/mappings/StandardMapping" }""".stripMargin,
        ),
        expectedTurtle =
          s"""
             | PREFIX rdfs:       <http://www.w3.org/2000/01/rdf-schema#>
             | PREFIX onto:       <http://www.knora.org/ontology/9999/onto#>
             | PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
             |
             | <$resourceIri>
             |     a          onto:Example ;
             |     rdfs:label "test" ;
             |     onto:testRichtext <$valueIri> .
             |
             | <$valueIri>
             |     a                              knora-base:TextValue ;
             |     knora-base:textValueAsXml      "<?xml version=\\"1.0\\" encoding=\\"UTF-8\\"?>\\n<text>Text</text>" ;
             |     knora-base:textValueHasMapping <http://rdfh.ch/standoff/mappings/StandardMapping> .
             |""".stripMargin,
      )
    },
  )

  private val dateValues = suite("Date Values")(
    test("DateValue GREGORIAN with eras") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testSubDate1",
          valueClass = s"${knoraApi}DateValue",
          inner =
            s""""${knoraApi}dateValueHasCalendar":  { "@type": "${xsd}string",  "@value": "GREGORIAN" },
               |    "${knoraApi}dateValueHasStartYear":  { "@type": "${xsd}integer", "@value": 1800 },
               |    "${knoraApi}dateValueHasStartMonth": { "@type": "${xsd}integer", "@value": 1 },
               |    "${knoraApi}dateValueHasStartDay":   { "@type": "${xsd}integer", "@value": 1 },
               |    "${knoraApi}dateValueHasStartEra":   { "@type": "${xsd}string",  "@value": "CE" },
               |    "${knoraApi}dateValueHasEndYear":    { "@type": "${xsd}integer", "@value": 1900 },
               |    "${knoraApi}dateValueHasEndMonth":   { "@type": "${xsd}integer", "@value": 1 },
               |    "${knoraApi}dateValueHasEndDay":     { "@type": "${xsd}integer", "@value": 1 },
               |    "${knoraApi}dateValueHasEndEra":     { "@type": "${xsd}string",  "@value": "CE" }""".stripMargin,
        ),
        expectedTurtle = s"""
                            | PREFIX xsd:        <http://www.w3.org/2001/XMLSchema#>
                            | PREFIX rdfs:       <http://www.w3.org/2000/01/rdf-schema#>
                            | PREFIX onto:       <http://www.knora.org/ontology/9999/onto#>
                            | PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                            |
                            | <$resourceIri>
                            |     a          onto:Example ;
                            |     rdfs:label "test" ;
                            |     onto:testSubDate1 <$valueIri> .
                            |
                            | <$valueIri>
                            |     a                                 knora-base:DateValue ;
                            |     knora-base:dateValueHasCalendar   "GREGORIAN" ;
                            |     knora-base:dateValueHasStartYear  "1800"^^xsd:integer ;
                            |     knora-base:dateValueHasStartMonth "1"^^xsd:integer ;
                            |     knora-base:dateValueHasStartDay   "1"^^xsd:integer ;
                            |     knora-base:dateValueHasStartEra   "CE" ;
                            |     knora-base:dateValueHasEndYear    "1900"^^xsd:integer ;
                            |     knora-base:dateValueHasEndMonth   "1"^^xsd:integer ;
                            |     knora-base:dateValueHasEndDay     "1"^^xsd:integer ;
                            |     knora-base:dateValueHasEndEra     "CE" .
                            |""".stripMargin,
      )
    },
  )

  private val resourceMetadata = suite("Stage 2 — Resource metadata synthesis")(
    test("synthesises attachedToUser, attachedToProject, hasPermissions, creationDate and isDeleted on the resource") {
      runTransformStage2(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testBoolean",
          valueClass = s"${knoraApi}BooleanValue",
          inner = s""""${knoraApi}booleanValueAsBoolean": { "@type": "${xsd}boolean", "@value": true }""",
        ),
        expectedTurtle = s"""
                            | PREFIX rdf:        <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                            | PREFIX rdfs:       <http://www.w3.org/2000/01/rdf-schema#>
                            | PREFIX xsd:        <http://www.w3.org/2001/XMLSchema#>
                            | PREFIX onto:       <http://www.knora.org/ontology/9999/onto#>
                            | PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                            |
                            | <$resourceIri>
                            |     a                            onto:Example ;
                            |     rdfs:label                   "test" ;
                            |     onto:testBoolean             <$valueIri> ;
                            |     knora-base:attachedToUser    <${ctx.attachedToUser}> ;
                            |     knora-base:attachedToProject <${ctx.attachedToProject.id.value}> ;
                            |     knora-base:hasPermissions    "${ctx.permissions}" ;
                            |     knora-base:creationDate      "$knownInstant"^^xsd:dateTime ;
                            |     knora-base:isDeleted         false .
                            |
                            | <$valueIri>
                            |     a                            knora-base:BooleanValue ;
                            |     knora-base:valueHasBoolean   "true"^^xsd:boolean ;
                            |     knora-base:attachedToUser    <${ctx.attachedToUser}> ;
                            |     knora-base:hasPermissions    "${ctx.permissions}" ;
                            |     knora-base:valueCreationDate "$knownInstant"^^xsd:dateTime ;
                            |     knora-base:valueHasUUID      "${valueIri.valueId}" ;
                            |     knora-base:valueHasString    "true" ;
                            |     knora-base:isDeleted         false .
                            |""".stripMargin,
      )
    },
    test("synthesises value metadata independently on each sibling value of the same property") {
      runTransformStage2(
        jsonLd = s"""
                    |[{
                    |    "@id": "$resourceIri",
                    |    "@type": "${onto}Example",
                    |    "rdfs:label": "test",
                    |    "${onto}testInt": [
                    |      { "@id": "$valueIri",  "@type": "${knoraApi}IntValue",
                    |        "${knoraApi}intValueAsInt": { "@type": "${xsd}int", "@value": "1" } },
                    |      { "@id": "$valueIri2", "@type": "${knoraApi}IntValue",
                    |        "${knoraApi}intValueAsInt": { "@type": "${xsd}int", "@value": "2" } }
                    |    ],
                    |    "@context": { "rdfs": "http://www.w3.org/2000/01/rdf-schema#" }
                    |}]""".stripMargin,
        expectedTurtle = s"""
                            | PREFIX rdf:        <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                            | PREFIX rdfs:       <http://www.w3.org/2000/01/rdf-schema#>
                            | PREFIX xsd:        <http://www.w3.org/2001/XMLSchema#>
                            | PREFIX onto:       <http://www.knora.org/ontology/9999/onto#>
                            | PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                            |
                            | <$resourceIri>
                            |     a                            onto:Example ;
                            |     rdfs:label                   "test" ;
                            |     onto:testInt                 <$valueIri>, <$valueIri2> ;
                            |     knora-base:attachedToUser    <${ctx.attachedToUser}> ;
                            |     knora-base:attachedToProject <${ctx.attachedToProject.id.value}> ;
                            |     knora-base:hasPermissions    "${ctx.permissions}" ;
                            |     knora-base:creationDate      "$knownInstant"^^xsd:dateTime ;
                            |     knora-base:isDeleted         false .
                            |
                            | <$valueIri>
                            |     a                            knora-base:IntValue ;
                            |     knora-base:valueHasInteger   "1"^^xsd:int ;
                            |     knora-base:attachedToUser    <${ctx.attachedToUser}> ;
                            |     knora-base:hasPermissions    "${ctx.permissions}" ;
                            |     knora-base:valueCreationDate "$knownInstant"^^xsd:dateTime ;
                            |     knora-base:valueHasUUID      "${valueIri.valueId}" ;
                            |     knora-base:valueHasString    "1" ;
                            |     knora-base:isDeleted         false .
                            |
                            | <$valueIri2>
                            |     a                            knora-base:IntValue ;
                            |     knora-base:valueHasInteger   "2"^^xsd:int ;
                            |     knora-base:attachedToUser    <${ctx.attachedToUser}> ;
                            |     knora-base:hasPermissions    "${ctx.permissions}" ;
                            |     knora-base:valueCreationDate "$knownInstant"^^xsd:dateTime ;
                            |     knora-base:valueHasUUID      "${valueIri2.valueId.value}" ;
                            |     knora-base:valueHasString    "2" ;
                            |     knora-base:isDeleted         false .
                            |""".stripMargin,
      )
    },
  )

  /** Full expected `knora-base` graph for a single-value resource, including synthesised resource + value metadata. */
  private def expectedStage2SingleValue(
    propLocalName: String,
    valueClass: String,
    valueContent: String,
    valueHasString: String,
  ): String =
    s"""
       | PREFIX rdf:        <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
       | PREFIX rdfs:       <http://www.w3.org/2000/01/rdf-schema#>
       | PREFIX xsd:        <http://www.w3.org/2001/XMLSchema#>
       | PREFIX onto:       <http://www.knora.org/ontology/9999/onto#>
       | PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
       |
       | <$resourceIri>
       |     a                            onto:Example ;
       |     rdfs:label                   "test" ;
       |     onto:$propLocalName          <$valueIri> ;
       |     knora-base:attachedToUser    <${ctx.attachedToUser}> ;
       |     knora-base:attachedToProject <${ctx.attachedToProject.id.value}> ;
       |     knora-base:hasPermissions    "${ctx.permissions}" ;
       |     knora-base:creationDate      "$knownInstant"^^xsd:dateTime ;
       |     knora-base:isDeleted         false .
       |
       | <$valueIri>
       |     a                            knora-base:$valueClass ;
       |     $valueContent ;
       |     knora-base:attachedToUser    <${ctx.attachedToUser}> ;
       |     knora-base:hasPermissions    "${ctx.permissions}" ;
       |     knora-base:valueCreationDate "$knownInstant"^^xsd:dateTime ;
       |     knora-base:valueHasUUID      "${valueIri.valueId}" ;
       |     knora-base:valueHasString    "$valueHasString" ;
       |     knora-base:isDeleted         false .
       |""".stripMargin

  private val valueHasString = suite("Stage 2 — valueHasString")(
    test("ColorValue uses the hex string") {
      runTransformStage2(
        resourceWithValueJsonLd(
          s"${onto}testColor",
          s"${knoraApi}ColorValue",
          s""""${knoraApi}colorValueAsColor": { "@type": "${xsd}string", "@value": "#00ff00" }""",
        ),
        expectedStage2SingleValue("testColor", "ColorValue", """knora-base:valueHasColor "#00ff00"""", "#00ff00"),
      )
    },
    test("DecimalValue uses the decimal lexical form") {
      runTransformStage2(
        resourceWithValueJsonLd(
          s"${onto}testDecimal",
          s"${knoraApi}DecimalValue",
          s""""${knoraApi}decimalValueAsDecimal": { "@type": "${xsd}decimal", "@value": "2.71" }""",
        ),
        expectedStage2SingleValue(
          "testDecimal",
          "DecimalValue",
          """knora-base:valueHasDecimal "2.71"^^xsd:decimal""",
          "2.71",
        ),
      )
    },
    test("GeonameValue uses the geoname code") {
      runTransformStage2(
        resourceWithValueJsonLd(
          s"${onto}testGeoname",
          s"${knoraApi}GeonameValue",
          s""""${knoraApi}geonameValueAsGeonameCode": { "@type": "${xsd}string", "@value": "1111111" }""",
        ),
        expectedStage2SingleValue(
          "testGeoname",
          "GeonameValue",
          """knora-base:valueHasGeonameCode "1111111"""",
          "1111111",
        ),
      )
    },
    test("TimeValue uses the timestamp lexical form") {
      runTransformStage2(
        resourceWithValueJsonLd(
          s"${onto}testTimeValue",
          s"${knoraApi}TimeValue",
          s""""${knoraApi}timeValueAsTimeStamp": { "@type": "${xsd}dateTimeStamp", "@value": "2019-10-23T13:45:12.01-14:00" }""",
        ),
        expectedStage2SingleValue(
          "testTimeValue",
          "TimeValue",
          """knora-base:valueHasTimeStamp "2019-10-23T13:45:12.01-14:00"^^xsd:dateTimeStamp""",
          "2019-10-23T13:45:12.01-14:00",
        ),
      )
    },
    test("UriValue uses the URI") {
      runTransformStage2(
        resourceWithValueJsonLd(
          s"${onto}testUriValue",
          s"${knoraApi}UriValue",
          s""""${knoraApi}uriValueAsUri": { "@type": "${xsd}anyURI", "@value": "https://dasch.swiss" }""",
        ),
        expectedStage2SingleValue(
          "testUriValue",
          "UriValue",
          """knora-base:valueHasUri "https://dasch.swiss"^^xsd:anyURI""",
          "https://dasch.swiss",
        ),
      )
    },
    test("ListValue falls back to the list-node IRI") {
      val node = "http://rdfh.ch/lists/9999/WF8qwFbGQg228GJUlqOLzw"
      runTransformStage2(
        resourceWithValueJsonLd(
          s"${onto}testListProp",
          s"${knoraApi}ListValue",
          s""""${knoraApi}listValueAsListNode": { "@id": "$node" }""",
        ),
        expectedStage2SingleValue("testListProp", "ListValue", s"knora-base:valueHasListNode <$node>", node),
      )
    },
  )

  /** Drives a GREGORIAN `DateValue` through stage 2 and asserts the collapsed JDN form. */
  private def runDateStage2(
    inner: String,
    startJDN: Int,
    endJDN: Int,
    startPrecision: String,
    endPrecision: String,
    dateString: String,
  ) =
    runTransformStage2(
      jsonLd = resourceWithValueJsonLd(s"${onto}testSubDate1", s"${knoraApi}DateValue", inner),
      expectedTurtle = s"""
                          | PREFIX rdf:        <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                          | PREFIX rdfs:       <http://www.w3.org/2000/01/rdf-schema#>
                          | PREFIX xsd:        <http://www.w3.org/2001/XMLSchema#>
                          | PREFIX onto:       <http://www.knora.org/ontology/9999/onto#>
                          | PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                          |
                          | <$resourceIri>
                          |     a                            onto:Example ;
                          |     rdfs:label                   "test" ;
                          |     onto:testSubDate1            <$valueIri> ;
                          |     knora-base:attachedToUser    <${ctx.attachedToUser}> ;
                          |     knora-base:attachedToProject <${ctx.attachedToProject.id.value}> ;
                          |     knora-base:hasPermissions    "${ctx.permissions}" ;
                          |     knora-base:creationDate      "$knownInstant"^^xsd:dateTime ;
                          |     knora-base:isDeleted         false .
                          |
                          | <$valueIri>
                          |     a                                 knora-base:DateValue ;
                          |     knora-base:valueHasCalendar       "GREGORIAN" ;
                          |     knora-base:valueHasStartJDN       $startJDN ;
                          |     knora-base:valueHasEndJDN         $endJDN ;
                          |     knora-base:valueHasStartPrecision "$startPrecision" ;
                          |     knora-base:valueHasEndPrecision   "$endPrecision" ;
                          |     knora-base:valueHasString         "$dateString" ;
                          |     knora-base:attachedToUser         <${ctx.attachedToUser}> ;
                          |     knora-base:hasPermissions         "${ctx.permissions}" ;
                          |     knora-base:valueCreationDate      "$knownInstant"^^xsd:dateTime ;
                          |     knora-base:valueHasUUID           "${valueIri.valueId}" ;
                          |     knora-base:isDeleted              false .
                          |""".stripMargin,
    )

  private val dateValuesStage2 = suite("Stage 2 — DateValue → JDN")(
    test("day-precision range") {
      runDateStage2(
        inner = s""""${knoraApi}dateValueHasCalendar":  { "@type": "${xsd}string",  "@value": "GREGORIAN" },
                   |    "${knoraApi}dateValueHasStartYear":  { "@type": "${xsd}integer", "@value": 1800 },
                   |    "${knoraApi}dateValueHasStartMonth": { "@type": "${xsd}integer", "@value": 1 },
                   |    "${knoraApi}dateValueHasStartDay":   { "@type": "${xsd}integer", "@value": 2 },
                   |    "${knoraApi}dateValueHasStartEra":   { "@type": "${xsd}string",  "@value": "CE" },
                   |    "${knoraApi}dateValueHasEndYear":    { "@type": "${xsd}integer", "@value": 1900 },
                   |    "${knoraApi}dateValueHasEndMonth":   { "@type": "${xsd}integer", "@value": 3 },
                   |    "${knoraApi}dateValueHasEndDay":     { "@type": "${xsd}integer", "@value": 4 },
                   |    "${knoraApi}dateValueHasEndEra":     { "@type": "${xsd}string",  "@value": "CE" }""".stripMargin,
        startJDN = 2378498,
        endJDN = 2415083,
        startPrecision = "DAY",
        endPrecision = "DAY",
        dateString = "GREGORIAN:1800-01-02 CE:1900-03-04 CE",
      )
    },
    test("month-precision range (no day fields)") {
      runDateStage2(
        inner = s""""${knoraApi}dateValueHasCalendar":  { "@type": "${xsd}string",  "@value": "GREGORIAN" },
                   |    "${knoraApi}dateValueHasStartYear":  { "@type": "${xsd}integer", "@value": 1800 },
                   |    "${knoraApi}dateValueHasStartMonth": { "@type": "${xsd}integer", "@value": 3 },
                   |    "${knoraApi}dateValueHasStartEra":   { "@type": "${xsd}string",  "@value": "CE" },
                   |    "${knoraApi}dateValueHasEndYear":    { "@type": "${xsd}integer", "@value": 1900 },
                   |    "${knoraApi}dateValueHasEndMonth":   { "@type": "${xsd}integer", "@value": 5 },
                   |    "${knoraApi}dateValueHasEndEra":     { "@type": "${xsd}string",  "@value": "CE" }""".stripMargin,
        startJDN = 2378556,
        endJDN = 2415171,
        startPrecision = "MONTH",
        endPrecision = "MONTH",
        dateString = "GREGORIAN:1800-03 CE:1900-05 CE",
      )
    },
    test("year-precision range (no month or day fields)") {
      runDateStage2(
        inner = s""""${knoraApi}dateValueHasCalendar":  { "@type": "${xsd}string",  "@value": "GREGORIAN" },
                   |    "${knoraApi}dateValueHasStartYear":  { "@type": "${xsd}integer", "@value": 1800 },
                   |    "${knoraApi}dateValueHasStartEra":   { "@type": "${xsd}string",  "@value": "CE" },
                   |    "${knoraApi}dateValueHasEndYear":    { "@type": "${xsd}integer", "@value": 1900 },
                   |    "${knoraApi}dateValueHasEndEra":     { "@type": "${xsd}string",  "@value": "CE" }""".stripMargin,
        startJDN = 2378497,
        endJDN = 2415385,
        startPrecision = "YEAR",
        endPrecision = "YEAR",
        dateString = "GREGORIAN:1800 CE:1900 CE",
      )
    },
    test("single date (equal start and end) collapses to one date") {
      runDateStage2(
        inner = s""""${knoraApi}dateValueHasCalendar":  { "@type": "${xsd}string",  "@value": "GREGORIAN" },
                   |    "${knoraApi}dateValueHasStartYear":  { "@type": "${xsd}integer", "@value": 1800 },
                   |    "${knoraApi}dateValueHasStartMonth": { "@type": "${xsd}integer", "@value": 1 },
                   |    "${knoraApi}dateValueHasStartDay":   { "@type": "${xsd}integer", "@value": 2 },
                   |    "${knoraApi}dateValueHasStartEra":   { "@type": "${xsd}string",  "@value": "CE" },
                   |    "${knoraApi}dateValueHasEndYear":    { "@type": "${xsd}integer", "@value": 1800 },
                   |    "${knoraApi}dateValueHasEndMonth":   { "@type": "${xsd}integer", "@value": 1 },
                   |    "${knoraApi}dateValueHasEndDay":     { "@type": "${xsd}integer", "@value": 2 },
                   |    "${knoraApi}dateValueHasEndEra":     { "@type": "${xsd}string",  "@value": "CE" }""".stripMargin,
        startJDN = 2378498,
        endJDN = 2378498,
        startPrecision = "DAY",
        endPrecision = "DAY",
        dateString = "GREGORIAN:1800-01-02 CE",
      )
    },
  )

  private val linkValuesStage2 = suite("Stage 2 — LinkValue reification")(
    test("reifies the LinkValue, adds the direct-link triple, refCount and valueHasString") {
      val target = "http://rdfh.ch/9999/CV9Lea7hSESPWPuILr8dyw"
      runTransformStage2(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testHasLinkToValue",
          valueClass = s"${knoraApi}LinkValue",
          inner = s""""${knoraApi}linkValueHasTargetIri": { "@id": "$target" }""",
        ),
        expectedTurtle = s"""
                            | PREFIX rdf:        <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                            | PREFIX rdfs:       <http://www.w3.org/2000/01/rdf-schema#>
                            | PREFIX xsd:        <http://www.w3.org/2001/XMLSchema#>
                            | PREFIX onto:       <http://www.knora.org/ontology/9999/onto#>
                            | PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                            |
                            | <$resourceIri>
                            |     a                            onto:Example ;
                            |     rdfs:label                   "test" ;
                            |     onto:testHasLinkToValue      <$valueIri> ;
                            |     onto:testHasLinkTo           <$target> ;
                            |     knora-base:attachedToUser    <${ctx.attachedToUser}> ;
                            |     knora-base:attachedToProject <${ctx.attachedToProject.id.value}> ;
                            |     knora-base:hasPermissions    "${ctx.permissions}" ;
                            |     knora-base:creationDate      "$knownInstant"^^xsd:dateTime ;
                            |     knora-base:isDeleted         false .
                            |
                            | <$valueIri>
                            |     a                            knora-base:LinkValue ;
                            |     rdf:subject                  <$resourceIri> ;
                            |     rdf:predicate                onto:testHasLinkTo ;
                            |     rdf:object                   <$target> ;
                            |     knora-base:valueHasRefCount  1 ;
                            |     knora-base:valueHasString    "$target" ;
                            |     knora-base:attachedToUser    <${ctx.attachedToUser}> ;
                            |     knora-base:hasPermissions    "${ctx.permissions}" ;
                            |     knora-base:valueCreationDate "$knownInstant"^^xsd:dateTime ;
                            |     knora-base:valueHasUUID      "${valueIri.valueId}" ;
                            |     knora-base:isDeleted         false .
                            |""".stripMargin,
      )
    },
  )

  private val textValueTypeStage2 = suite("Stage 2 — hasTextValueType")(
    test("tags a simple (non-XML) TextValue as UnformattedText") {
      runTransformStage2(
        resourceWithValueJsonLd(
          s"${onto}testSimpleText",
          s"${knoraApi}TextValue",
          s""""${knoraApi}valueAsString": { "@type": "${xsd}string", "@value": "Text" }""",
        ),
        expectedStage2SingleValue(
          "testSimpleText",
          "TextValue",
          "knora-base:hasTextValueType knora-base:UnformattedText",
          "Text",
        ),
      )
    },
  )

  // ---- Standoff mapping stub (D1): a hermetic standard mapping covering the tags the standoff fixtures use ----

  private val standoffPrefix  = "http://www.knora.org/ontology/standoff#"
  private val standoffRootTag = standoffPrefix + "StandoffRootTag"
  private val standoffParaTag = standoffPrefix + "StandoffParagraphTag"
  private val standoffBoldTag = standoffPrefix + "StandoffBoldTag"
  private val standoffLinkTag = KnoraBase.StandoffLinkTag

  private def xmlTag(name: String, cls: String, sep: Boolean, dataType: Option[XMLStandoffDataTypeClass] = None) =
    XMLTag(name, XMLTagToStandoffClass(cls, Map.empty, dataType), separatorRequired = sep)

  private val standardMapping: MappingXMLtoStandoff =
    MappingXMLtoStandoff(
      namespace = Map(
        "noNamespace" -> Map(
          "text"   -> Map("noClass" -> xmlTag("text", standoffRootTag, sep = false)),
          "p"      -> Map("noClass" -> xmlTag("p", standoffParaTag, sep = true)),
          "strong" -> Map("noClass" -> xmlTag("strong", standoffBoldTag, sep = false)),
          "a"      -> Map(
            "salsah-link" -> xmlTag(
              "a",
              standoffLinkTag,
              sep = false,
              Some(XMLStandoffDataTypeClass(StandoffDataTypeClasses.StandoffLinkTag, "href")),
            ),
          ),
        ),
      ),
      defaultXSLTransformation = None,
    )

  private def standoffClassInfo(iri: String, dataType: Option[StandoffDataTypeClasses.Value]) = {
    val smartIri = StringFormatter.getInitializedTestInstance.toSmartIri(iri)
    smartIri -> ReadClassInfoV2(
      entityInfoContent = ClassInfoContentV2(classIri = smartIri, ontologySchema = InternalSchema),
      allBaseClasses = Seq.empty,
      isStandoffClass = true,
      standoffDataType = dataType,
    )
  }

  private val standardMappingEntities: StandoffEntityInfoGetResponseV2 =
    StandoffEntityInfoGetResponseV2(
      standoffClassInfoMap = Map(
        standoffClassInfo(standoffRootTag, None),
        standoffClassInfo(standoffParaTag, None),
        standoffClassInfo(standoffBoldTag, None),
        standoffClassInfo(standoffLinkTag, Some(StandoffDataTypeClasses.StandoffLinkTag)),
      ),
      standoffPropertyInfoMap = Map.empty,
    )

  private val standardMappingResponse =
    GetMappingResponseV2(StandoffMappingIri.StandardMapping, standardMapping, standardMappingEntities)

  private val standoffMappingServiceStub: ULayer[StandoffMappingService] =
    StandoffMappingServiceInMemory.layer(StandoffMappingIri.StandardMapping -> standardMappingResponse)

  private val standardMappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping"

  /** Wraps a rich-text value: `xmlEscaped` is the XML with its double quotes already escaped for the JSON string. */
  private def richtextJsonLd(xmlEscaped: String, mappingIri: String = standardMappingIri) =
    resourceWithValueJsonLd(
      s"${onto}testRichtext",
      s"${knoraApi}TextValue",
      s""""${knoraApi}textValueAsXml": { "@type": "${xsd}string", "@value": "$xmlEscaped" },
         |    "${knoraApi}textValueHasMapping": { "@id": "$mappingIri" }""".stripMargin,
    )

  private val simpleRichtextXml = "<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?>\\n<text>Text</text>"

  private def transformStage2Exit(jsonLd: String) =
    ZIO.scoped {
      for {
        inputPath <- ZIO.acquireRelease(writeTempFile(".jsonld", jsonLd).orDie)(deleteIfExists)
        _         <- TestClock.setTime(knownInstant)
        exit      <- transformer(_.toKnoraBase(inputPath, ctx)).exit
      } yield exit
    }

  private def assertRejected(jsonLd: String, substring: String) =
    transformStage2Exit(jsonLd).map {
      case Exit.Failure(cause) => assertTrue(cause.failureOption.exists(_.message.contains(substring)))
      case Exit.Success(_)     => assertTrue(false)
    }

  private val standoffStage2 = suite("Stage 2 — rich text to standoff")(
    test("converts a rich-text value to standoff (tag nodes, valueHasStandoff, mapping, plain-text projection)") {
      runTransformStage2(
        richtextJsonLd(simpleRichtextXml),
        expectedTurtle =
          s"""
             | PREFIX rdf:        <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
             | PREFIX rdfs:       <http://www.w3.org/2000/01/rdf-schema#>
             | PREFIX xsd:        <http://www.w3.org/2001/XMLSchema#>
             | PREFIX onto:       <http://www.knora.org/ontology/9999/onto#>
             | PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
             | PREFIX standoff:   <http://www.knora.org/ontology/standoff#>
             |
             | <$resourceIri>
             |     a                            onto:Example ;
             |     rdfs:label                   "test" ;
             |     onto:testRichtext            <$valueIri> ;
             |     knora-base:attachedToUser    <${ctx.attachedToUser}> ;
             |     knora-base:attachedToProject <${ctx.attachedToProject.id.value}> ;
             |     knora-base:hasPermissions    "${ctx.permissions}" ;
             |     knora-base:creationDate      "$knownInstant"^^xsd:dateTime ;
             |     knora-base:isDeleted         false .
             |
             | <$valueIri>
             |     a                                        knora-base:TextValue ;
             |     knora-base:valueHasString                "Text" ;
             |     knora-base:valueHasMapping               <http://rdfh.ch/standoff/mappings/StandardMapping> ;
             |     knora-base:hasTextValueType              knora-base:FormattedText ;
             |     knora-base:valueHasMaxStandoffStartIndex 0 ;
             |     knora-base:valueHasStandoff              <$valueIri/standoff/0> ;
             |     knora-base:attachedToUser                <${ctx.attachedToUser}> ;
             |     knora-base:hasPermissions                "${ctx.permissions}" ;
             |     knora-base:valueCreationDate             "$knownInstant"^^xsd:dateTime ;
             |     knora-base:valueHasUUID                  "${valueIri.valueId}" ;
             |     knora-base:isDeleted                     false .
             |
             | <$valueIri/standoff/0>
             |     a                                  standoff:StandoffRootTag ;
             |     knora-base:standoffTagHasStart      0 ;
             |     knora-base:standoffTagHasEnd        4 ;
             |     knora-base:standoffTagHasStartIndex 0 ;
             |     knora-base:standoffTagHasUUID       "${UuidUtil.base64Encode(new UUID(0L, 1L))}" .
             |""".stripMargin,
      )
    },
    test("converts nested formatting to a multi-level standoff tree with resolved parent IRIs") {
      runTransformStage2(
        richtextJsonLd(richtextXml("<p>text <strong>bold</strong></p>")),
        expectedTurtle =
          s"""
             | PREFIX rdf:        <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
             | PREFIX rdfs:       <http://www.w3.org/2000/01/rdf-schema#>
             | PREFIX xsd:        <http://www.w3.org/2001/XMLSchema#>
             | PREFIX onto:       <http://www.knora.org/ontology/9999/onto#>
             | PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
             | PREFIX standoff:   <http://www.knora.org/ontology/standoff#>
             |
             | <$resourceIri>
             |     a                            onto:Example ;
             |     rdfs:label                   "test" ;
             |     onto:testRichtext            <$valueIri> ;
             |     knora-base:attachedToUser    <${ctx.attachedToUser}> ;
             |     knora-base:attachedToProject <${ctx.attachedToProject.id.value}> ;
             |     knora-base:hasPermissions    "${ctx.permissions}" ;
             |     knora-base:creationDate      "$knownInstant"^^xsd:dateTime ;
             |     knora-base:isDeleted         false .
             |
             | <$valueIri>
             |     a                                        knora-base:TextValue ;
             |     knora-base:valueHasString                "text bold${StringFormatter.INFORMATION_SEPARATOR_TWO}" ;
             |     knora-base:valueHasMapping               <http://rdfh.ch/standoff/mappings/StandardMapping> ;
             |     knora-base:hasTextValueType              knora-base:FormattedText ;
             |     knora-base:valueHasMaxStandoffStartIndex 2 ;
             |     knora-base:valueHasStandoff              <$valueIri/standoff/0>, <$valueIri/standoff/1>, <$valueIri/standoff/2> ;
             |     knora-base:attachedToUser                <${ctx.attachedToUser}> ;
             |     knora-base:hasPermissions                "${ctx.permissions}" ;
             |     knora-base:valueCreationDate             "$knownInstant"^^xsd:dateTime ;
             |     knora-base:valueHasUUID                  "${valueIri.valueId}" ;
             |     knora-base:isDeleted                     false .
             |
             | <$valueIri/standoff/0>
             |     a                                  standoff:StandoffRootTag ;
             |     knora-base:standoffTagHasStart      0 ;
             |     knora-base:standoffTagHasEnd        10 ;
             |     knora-base:standoffTagHasStartIndex 0 ;
             |     knora-base:standoffTagHasUUID       "${UuidUtil.base64Encode(new UUID(0L, 1L))}" .
             |
             | <$valueIri/standoff/1>
             |     a                                    standoff:StandoffParagraphTag ;
             |     knora-base:standoffTagHasStart       0 ;
             |     knora-base:standoffTagHasEnd         9 ;
             |     knora-base:standoffTagHasStartIndex  1 ;
             |     knora-base:standoffTagHasStartParent <$valueIri/standoff/0> ;
             |     knora-base:standoffTagHasUUID        "${UuidUtil.base64Encode(new UUID(0L, 2L))}" .
             |
             | <$valueIri/standoff/2>
             |     a                                    standoff:StandoffBoldTag ;
             |     knora-base:standoffTagHasStart       5 ;
             |     knora-base:standoffTagHasEnd         9 ;
             |     knora-base:standoffTagHasStartIndex  2 ;
             |     knora-base:standoffTagHasStartParent <$valueIri/standoff/1> ;
             |     knora-base:standoffTagHasUUID        "${UuidUtil.base64Encode(new UUID(0L, 3L))}" .
             |""".stripMargin,
      )
    },
    test("rejects a text value referencing a non-standard mapping") {
      assertRejected(
        richtextJsonLd(simpleRichtextXml, mappingIri = "http://rdfh.ch/standoff/mappings/CustomMapping"),
        "custom mapping",
      )
    },
    test("rejects malformed XML") {
      assertRejected(richtextJsonLd("not valid xml <<<"), "Failed to restructure")
    },
    test("rejects a salsah-link whose href is not a valid resource IRI") {
      assertRejected(
        richtextJsonLd(richtextXml(salsahLink("not-a-valid-iri", "link"))),
        "Invalid standoff resource reference",
      )
    },
    test("rejects a rich-text TextValue that has more than one incoming edge") {
      assertRejected(twoEdgeTextValueJsonLd, "must have exactly one incoming edge")
    },
    test("a minted standoff-tag IRI is not classified as a ValueIri (D4 collision guard)") {
      assertTrue(ValueIri.from(s"$valueIri/standoff/0").isLeft)
    },
    test("rejects a rich-text TextValue that has no incoming edge") {
      assertRejected(orphanTextValueJsonLd, "must have exactly one incoming edge")
    },
    test("leaves a TextValue with neither valueHasString nor textValueAsXml untouched by the standoff pass") {
      for {
        model <- transformStage2Model(bareTextValueJsonLd)
      } yield assertTrue(
        objectsOf(model, valueIri.value, KnoraBase.ValueHasStandoff).isEmpty,
        objectsOf(model, valueIri.value, KnoraBase.ValueHasMapping).isEmpty,
        objectsOf(model, valueIri.value, KnoraBase.HasTextValueType) == List(KnoraBase.UnformattedText),
      )
    },
  )

  // ---- Standoff-link (salsah-link) fixtures + model-query helpers ----

  private val linkTarget  = "http://rdfh.ch/9999/CV9Lea7hSESPWPuILr8dyw"
  private val linkTargetA = "http://rdfh.ch/9999/AAAAAAAAAAAAAAAAAAAAAA"
  private val linkTargetB = "http://rdfh.ch/9999/BBBBBBBBBBBBBBBBBBBBBB"

  private def salsahLink(target: String, label: String) =
    s"<a class=\\\"salsah-link\\\" href=\\\"$target\\\">$label</a>"
  private def richtextXml(body: String) = s"<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?><text>$body</text>"

  /** A one-resource payload with two rich-text values on `onto:testRichtext`. */
  private def twoRichtextJsonLd(xml1: String, xml2: String) =
    s"""
       |[{
       |  "@id": "$resourceIri",
       |  "@type": "${onto}Example",
       |  "rdfs:label": "test",
       |  "${onto}testRichtext": [
       |    { "@id": "$valueIri",  "@type": "${knoraApi}TextValue",
       |      "${knoraApi}textValueAsXml": { "@type": "${xsd}string", "@value": "$xml1" },
       |      "${knoraApi}textValueHasMapping": { "@id": "$standardMappingIri" } },
       |    { "@id": "$valueIri2", "@type": "${knoraApi}TextValue",
       |      "${knoraApi}textValueAsXml": { "@type": "${xsd}string", "@value": "$xml2" },
       |      "${knoraApi}textValueHasMapping": { "@id": "$standardMappingIri" } }
       |  ],
       |  "@context": { "rdfs": "http://www.w3.org/2000/01/rdf-schema#" }
       |}]""".stripMargin

  /** A one-resource payload with a user LinkValue and a rich-text value carrying a salsah-link, both to `target`. */
  private def userLinkAndRichtextJsonLd(target: String) =
    s"""
       |[{
       |  "@id": "$resourceIri",
       |  "@type": "${onto}Example",
       |  "rdfs:label": "test",
       |  "${onto}testHasLinkToValue": { "@id": "$valueIri2", "@type": "${knoraApi}LinkValue",
       |    "${knoraApi}linkValueHasTargetIri": { "@id": "$target" } },
       |  "${onto}testRichtext": { "@id": "$valueIri", "@type": "${knoraApi}TextValue",
       |    "${knoraApi}textValueAsXml": { "@type": "${xsd}string", "@value": "${richtextXml(
        salsahLink(target, "link"),
      )}" },
       |    "${knoraApi}textValueHasMapping": { "@id": "$standardMappingIri" } },
       |  "@context": { "rdfs": "http://www.w3.org/2000/01/rdf-schema#" }
       |}]""".stripMargin

  /** Two resources whose `testRichtext` both point at the same value IRI, giving that TextValue two incoming edges. */
  private def twoEdgeTextValueJsonLd =
    s"""
       |[
       |  { "@id": "$resourceIri", "@type": "${onto}Example", "rdfs:label": "test",
       |    "${onto}testRichtext": { "@id": "$valueIri", "@type": "${knoraApi}TextValue",
       |      "${knoraApi}textValueAsXml": { "@type": "${xsd}string", "@value": "$simpleRichtextXml" },
       |      "${knoraApi}textValueHasMapping": { "@id": "$standardMappingIri" } },
       |    "@context": { "rdfs": "http://www.w3.org/2000/01/rdf-schema#" } },
       |  { "@id": "$resourceIri2", "@type": "${onto}Example", "rdfs:label": "test2",
       |    "${onto}testRichtext": { "@id": "$valueIri" },
       |    "@context": { "rdfs": "http://www.w3.org/2000/01/rdf-schema#" } }
       |]""".stripMargin

  /** A payload whose sole node is the value itself — a rich-text TextValue with no resource referencing it. */
  private def orphanTextValueJsonLd =
    s"""
       |[{
       |  "@id": "$valueIri",
       |  "@type": "${knoraApi}TextValue",
       |  "${knoraApi}textValueAsXml": { "@type": "${xsd}string", "@value": "$simpleRichtextXml" },
       |  "${knoraApi}textValueHasMapping": { "@id": "$standardMappingIri" }
       |}]""".stripMargin

  /** A resource carrying a TextValue with neither `valueHasString` nor `textValueAsXml` (structurally incomplete). */
  private def bareTextValueJsonLd =
    s"""
       |[{
       |  "@id": "$resourceIri",
       |  "@type": "${onto}Example",
       |  "rdfs:label": "test",
       |  "${onto}testRichtext": { "@id": "$valueIri", "@type": "${knoraApi}TextValue" },
       |  "@context": { "rdfs": "http://www.w3.org/2000/01/rdf-schema#" }
       |}]""".stripMargin

  private def transformStage2Model(jsonLd: String) =
    ZIO.scoped {
      for {
        inputPath  <- ZIO.acquireRelease(writeTempFile(".jsonld", jsonLd).orDie)(deleteIfExists)
        _          <- TestClock.setTime(knownInstant)
        outputPath <- transformer(_.toKnoraBase(inputPath, ctx))
        actualNQ   <- ZIO.attempt(new String(Files.readAllBytes(outputPath), StandardCharsets.UTF_8))
        dataset    <- DatasetOps.from(actualNQ, Lang.NQUADS).mapError(new RuntimeException(_))
      } yield dataset.getNamedModel(dataNamedGraph)
    }

  private def objectsOf(m: Model, subject: String, prop: String): List[String] =
    m.listObjectsOfProperty(m.getResource(subject), m.getProperty(prop)).asScala.map(_.toString).toList

  private def intProp(m: Model, subject: String, prop: String): Int =
    m.getResource(subject).getRequiredProperty(m.getProperty(prop)).getInt

  private def standoffLinkValues(m: Model): List[String] =
    objectsOf(m, resourceIri.value, KnoraBase.HasStandoffLinkToValue)

  private def linkValueForTarget(m: Model, target: String): String =
    standoffLinkValues(m).find(lv => objectsOf(m, lv, Rdf.Object).contains(target)).get

  private val standoffLinkStage2 = suite("Stage 2 — standoff-link LinkValues")(
    test("emits a system hasStandoffLinkTo LinkValue for a salsah-link (refCount 1)") {
      runTransformStage2(
        richtextJsonLd(richtextXml(salsahLink(linkTarget, "link"))),
        expectedTurtle =
          s"""
             | PREFIX rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
             | PREFIX rdfs:        <http://www.w3.org/2000/01/rdf-schema#>
             | PREFIX xsd:         <http://www.w3.org/2001/XMLSchema#>
             | PREFIX onto:        <http://www.knora.org/ontology/9999/onto#>
             | PREFIX knora-base:  <http://www.knora.org/ontology/knora-base#>
             | PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
             | PREFIX standoff:    <http://www.knora.org/ontology/standoff#>
             |
             | <$resourceIri>
             |     a                                 onto:Example ;
             |     rdfs:label                        "test" ;
             |     onto:testRichtext                 <$valueIri> ;
             |     knora-base:hasStandoffLinkTo      <$linkTarget> ;
             |     knora-base:hasStandoffLinkToValue <$resourceIri/values/1> ;
             |     knora-base:attachedToUser         <${ctx.attachedToUser}> ;
             |     knora-base:attachedToProject      <${ctx.attachedToProject.id.value}> ;
             |     knora-base:hasPermissions         "${ctx.permissions}" ;
             |     knora-base:creationDate           "$knownInstant"^^xsd:dateTime ;
             |     knora-base:isDeleted              false .
             |
             | <$valueIri>
             |     a                                        knora-base:TextValue ;
             |     knora-base:valueHasString                "link" ;
             |     knora-base:valueHasMapping               <http://rdfh.ch/standoff/mappings/StandardMapping> ;
             |     knora-base:hasTextValueType              knora-base:FormattedText ;
             |     knora-base:valueHasMaxStandoffStartIndex 1 ;
             |     knora-base:valueHasStandoff              <$valueIri/standoff/0>, <$valueIri/standoff/1> ;
             |     knora-base:attachedToUser                <${ctx.attachedToUser}> ;
             |     knora-base:hasPermissions                "${ctx.permissions}" ;
             |     knora-base:valueCreationDate             "$knownInstant"^^xsd:dateTime ;
             |     knora-base:valueHasUUID                  "${valueIri.valueId}" ;
             |     knora-base:isDeleted                     false .
             |
             | <$valueIri/standoff/0>
             |     a                                  standoff:StandoffRootTag ;
             |     knora-base:standoffTagHasStart      0 ;
             |     knora-base:standoffTagHasEnd        4 ;
             |     knora-base:standoffTagHasStartIndex 0 ;
             |     knora-base:standoffTagHasUUID       "${UuidUtil.base64Encode(new UUID(0L, 1L))}" .
             |
             | <$valueIri/standoff/1>
             |     a                                    knora-base:StandoffLinkTag ;
             |     knora-base:standoffTagHasStart       0 ;
             |     knora-base:standoffTagHasEnd         4 ;
             |     knora-base:standoffTagHasStartIndex  1 ;
             |     knora-base:standoffTagHasStartParent <$valueIri/standoff/0> ;
             |     knora-base:standoffTagHasUUID        "${UuidUtil.base64Encode(new UUID(0L, 2L))}" ;
             |     knora-base:standoffTagHasLink        <$linkTarget> .
             |
             | <$resourceIri/values/1>
             |     a                            knora-base:LinkValue ;
             |     rdf:subject                  <$resourceIri> ;
             |     rdf:predicate                knora-base:hasStandoffLinkTo ;
             |     rdf:object                   <$linkTarget> ;
             |     knora-base:valueHasString    "$linkTarget" ;
             |     knora-base:valueHasRefCount  1 ;
             |     knora-base:isDeleted         false ;
             |     knora-base:valueCreationDate "$knownInstant"^^xsd:dateTime ;
             |     knora-base:attachedToUser    knora-admin:SystemUser ;
             |     knora-base:hasPermissions    "CR knora-admin:SystemUser|V knora-admin:UnknownUser" ;
             |     knora-base:valueHasUUID      "1" .
             |""".stripMargin,
      )
    },
    test("counts a target's refCount across the resource's text values") {
      for {
        m <- transformStage2Model(
               twoRichtextJsonLd(richtextXml(salsahLink(linkTarget, "a")), richtextXml(salsahLink(linkTarget, "b"))),
             )
      } yield assertTrue(
        standoffLinkValues(m).size == 1,
        objectsOf(m, resourceIri.value, KnoraBase.HasStandoffLinkTo) == List(linkTarget),
        intProp(m, standoffLinkValues(m).head, KnoraBase.ValueHasRefCount) == 2,
      )
    },
    test("collapses two links to one target within a single value to a refCount of 1") {
      for {
        m <- transformStage2Model(
               richtextJsonLd(richtextXml(salsahLink(linkTarget, "a") + " " + salsahLink(linkTarget, "b"))),
             )
      } yield assertTrue(
        standoffLinkValues(m).size == 1,
        intProp(m, standoffLinkValues(m).head, KnoraBase.ValueHasRefCount) == 1,
      )
    },
    test("mints one LinkValue per distinct target, in sorted-by-IRI order") {
      for {
        m <- transformStage2Model(
               richtextJsonLd(richtextXml(salsahLink(linkTargetB, "b") + " " + salsahLink(linkTargetA, "a"))),
             )
      } yield assertTrue(
        objectsOf(m, resourceIri.value, KnoraBase.HasStandoffLinkTo).toSet == Set(linkTargetA, linkTargetB),
        linkValueForTarget(m, linkTargetA) == s"$resourceIri/values/1",
        linkValueForTarget(m, linkTargetB) == s"$resourceIri/values/2",
        intProp(m, linkValueForTarget(m, linkTargetA), KnoraBase.ValueHasRefCount) == 1,
        intProp(m, linkValueForTarget(m, linkTargetB), KnoraBase.ValueHasRefCount) == 1,
      )
    },
    test("keeps a user link and a standoff link to the same target as separate LinkValues") {
      for {
        m <- transformStage2Model(userLinkAndRichtextJsonLd(linkTarget))
      } yield assertTrue(
        objectsOf(m, resourceIri.value, s"${internalOnto}testHasLinkTo") == List(linkTarget),
        objectsOf(m, resourceIri.value, KnoraBase.HasStandoffLinkTo) == List(linkTarget),
        standoffLinkValues(m).size == 1,
        intProp(m, standoffLinkValues(m).head, KnoraBase.ValueHasRefCount) == 1,
        intProp(m, valueIri2.value, KnoraBase.ValueHasRefCount) == 1,
      )
    },
    test("handles a self-referential salsah-link") {
      for {
        m <- transformStage2Model(richtextJsonLd(richtextXml(salsahLink(resourceIri.value, "self"))))
      } yield assertTrue(
        objectsOf(m, resourceIri.value, KnoraBase.HasStandoffLinkTo) == List(resourceIri.value),
        standoffLinkValues(m).size == 1,
        intProp(m, standoffLinkValues(m).head, KnoraBase.ValueHasRefCount) == 1,
      )
    },
  )

  private val graphHandling = suite("Stage 1 — @graph handling")(
    test("flattens @graph declarations from the payload (the target graph comes from the project)") {
      runTransform(
        jsonLd = s"""
                    |{
                    |  "@id": "http://example.org/ignored-graph",
                    |  "@graph": [{
                    |      "@id": "$resourceIri",
                    |      "@type": "${onto}Example",
                    |      "rdfs:label": "test"
                    |  }],
                    |  "@context": { "rdfs": "http://www.w3.org/2000/01/rdf-schema#" }
                    |}""".stripMargin,
        expectedTurtle = s"""
                            | PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                            | PREFIX onto: <http://www.knora.org/ontology/9999/onto#>
                            |
                            | <$resourceIri>
                            |     a          onto:Example ;
                            |     rdfs:label "test" .
                            |""".stripMargin,
      )
    },
  )

  // ---- Cross-emitter standoff equivalence ----
  // Checks that the import transformer's standoff RDF matches the canonical create-path emission
  // (InsertValueQueryBuilder.buildStandoffPatterns) for the same tags. standoffTagHasUUID is projected out: the
  // transformer mints a fresh UUID, while the create path keeps the parse-time UUID.

  /** Builds the canonical standoff RDF for `rawXml` through the create-path emitter, into a fresh Jena model. */
  private def canonicalStandoffModel(rawXml: String): Model = {
    val tws =
      StandoffTagUtilV2.convertXMLtoStandoffTagV2(
        rawXml,
        standardMappingResponse,
        acceptStandoffLinksToClientIDs = false,
      )
    val textValue = TextValueContentV2(
      ontologySchema = InternalSchema,
      maybeValueHasString = Some(tws.text),
      textValueType = TextValueType.FormattedText,
      standoff = tws.standoffTagV2,
      mappingIri = Some(StandoffMappingIri.StandardMapping),
    )
    val patterns =
      InsertValueQueryBuilder.buildStandoffPatterns(
        org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.iri(valueIri.value),
        textValue,
      )
    val insertData = patterns.map(_.getQueryString).mkString("INSERT DATA {\n", "\n", "\n}")
    val model      = ModelFactory.createDefaultModel()
    UpdateAction.parseExecute(insertData, model)
    model
  }

  /** Keeps only the standoff triples: the value's `valueHasStandoff` edges and its standoff-node subjects. */
  private def standoffSubgraph(model: Model): Model = {
    val out              = ModelFactory.createDefaultModel()
    val valueHasStandoff = model.createProperty(KnoraBase.ValueHasStandoff)
    val standoffPrefix   = s"${valueIri.value}/standoff/"
    model.listStatements().asScala.foreach { st =>
      val subject = st.getSubject
      val keep    = st.getPredicate == valueHasStandoff ||
        (subject.isURIResource && subject.getURI.startsWith(standoffPrefix))
      if (keep) { val _ = out.add(st) }
    }
    out
  }

  private def dropStandoffUuid(model: Model): Model = {
    val _ = model.removeAll(null, model.createProperty(KnoraBase.StandoffTagHasUUID), null)
    model
  }

  private val standoffEmissionEquivalence = suite("Stage 2 — standoff RDF equivalence with the create path")(
    test("transformer standoff RDF is isomorphic to InsertValueQueryBuilder for nested formatting") {
      val innerXml = "<p>text <strong>bold</strong></p>"
      val rawXml   = s"""<?xml version="1.0" encoding="UTF-8"?><text>$innerXml</text>"""
      for {
        model   <- transformStage2Model(richtextJsonLd(richtextXml(innerXml)))
        actual   = dropStandoffUuid(standoffSubgraph(model))
        expected = dropStandoffUuid(canonicalStandoffModel(rawXml))
      } yield assertTrue(actual.isIsomorphicWith(expected))
    },
  )

  override def spec = suite("OntologyTransformerSpec")(
    simpleScalarValues,
    iriRefValues,
    textValues,
    dateValues,
    graphHandling,
    resourceMetadata,
    valueHasString,
    dateValuesStage2,
    linkValuesStage2,
    textValueTypeStage2,
    standoffStage2,
    standoffLinkStage2,
    standoffEmissionEquivalence,
  ).provide(
    OntologyTransformer.layer,
    StringFormatter.test,
    TestAppConfig.layer(),
    IdSourceInMemory.layer,
    standoffMappingServiceStub,
  )
}
