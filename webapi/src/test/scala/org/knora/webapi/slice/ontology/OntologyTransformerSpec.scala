/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology

import org.apache.jena.riot.Lang
import zio.ZIO
import zio.test.*

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

import org.knora.webapi.core.TestAppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.common.jena.ModelOps

object OntologyTransformerSpec extends ZIOSpecDefault {

  private val transformer = ZIO.serviceWithZIO[OntologyTransformer]

  private val shortcode   = Shortcode.unsafeFrom("9999")
  private val resourceIri = ResourceIri.makeNew(shortcode)
  private val valueIri    = ValueIri.makeNew(resourceIri)

  private val onto     = "http://0.0.0.0:3333/ontology/9999/onto/v2#"
  private val knoraApi = "http://api.knora.org/ontology/knora-api/v2#"
  private val xsd      = "http://www.w3.org/2001/XMLSchema#"

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

  private val ctx = ConversionContext(
    attachedToUser = UserIri.unsafeFrom("http://rdfh.ch/users/exampleUser"),
    attachedToProject = ProjectIri.unsafeFrom("http://rdfh.ch/projects/9999"),
    permissions = "CR knora-admin:Creator|V knora-admin:KnownUser",
  )

  /** Stage-2: drives the full `toKnoraBase` with a fixed clock so synthesised dates are deterministic. */
  private def runTransformStage2(jsonLd: String, expectedTurtle: String) =
    ZIO.scoped {
      for {
        inputPath  <- ZIO.acquireRelease(writeTempFile(".jsonld", jsonLd).orDie)(deleteIfExists)
        _          <- TestClock.setTime(knownInstant)
        outputPath <- transformer(_.toKnoraBase(inputPath, ctx))
        actualNQ   <- ZIO.attempt(new String(Files.readAllBytes(outputPath), StandardCharsets.UTF_8))
        actual     <- ModelOps.from(actualNQ, Lang.NTRIPLES)
        expected   <- ModelOps.fromTurtle(expectedTurtle)
        iso         = actual.isIsomorphicWith(expected)
      } yield assertTrue(iso)
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
                            |     knora-base:attachedToProject <${ctx.attachedToProject.value}> ;
                            |     knora-base:hasPermissions    "${ctx.permissions}" ;
                            |     knora-base:creationDate      "$knownInstant"^^xsd:dateTimeStamp ;
                            |     knora-base:isDeleted         false .
                            |
                            | <$valueIri>
                            |     a                            knora-base:BooleanValue ;
                            |     knora-base:valueHasBoolean   "true"^^xsd:boolean ;
                            |     knora-base:attachedToUser    <${ctx.attachedToUser}> ;
                            |     knora-base:hasPermissions    "${ctx.permissions}" ;
                            |     knora-base:valueCreationDate "$knownInstant"^^xsd:dateTimeStamp ;
                            |     knora-base:valueHasUUID      "${valueIri.valueId.value}" ;
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
                            |     knora-base:attachedToProject <${ctx.attachedToProject.value}> ;
                            |     knora-base:hasPermissions    "${ctx.permissions}" ;
                            |     knora-base:creationDate      "$knownInstant"^^xsd:dateTimeStamp ;
                            |     knora-base:isDeleted         false .
                            |
                            | <$valueIri>
                            |     a                            knora-base:IntValue ;
                            |     knora-base:valueHasInteger   "1"^^xsd:int ;
                            |     knora-base:attachedToUser    <${ctx.attachedToUser}> ;
                            |     knora-base:hasPermissions    "${ctx.permissions}" ;
                            |     knora-base:valueCreationDate "$knownInstant"^^xsd:dateTimeStamp ;
                            |     knora-base:valueHasUUID      "${valueIri.valueId.value}" ;
                            |     knora-base:valueHasString    "1" ;
                            |     knora-base:isDeleted         false .
                            |
                            | <$valueIri2>
                            |     a                            knora-base:IntValue ;
                            |     knora-base:valueHasInteger   "2"^^xsd:int ;
                            |     knora-base:attachedToUser    <${ctx.attachedToUser}> ;
                            |     knora-base:hasPermissions    "${ctx.permissions}" ;
                            |     knora-base:valueCreationDate "$knownInstant"^^xsd:dateTimeStamp ;
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
       |     knora-base:attachedToProject <${ctx.attachedToProject.value}> ;
       |     knora-base:hasPermissions    "${ctx.permissions}" ;
       |     knora-base:creationDate      "$knownInstant"^^xsd:dateTimeStamp ;
       |     knora-base:isDeleted         false .
       |
       | <$valueIri>
       |     a                            knora-base:$valueClass ;
       |     $valueContent ;
       |     knora-base:attachedToUser    <${ctx.attachedToUser}> ;
       |     knora-base:hasPermissions    "${ctx.permissions}" ;
       |     knora-base:valueCreationDate "$knownInstant"^^xsd:dateTimeStamp ;
       |     knora-base:valueHasUUID      "${valueIri.valueId.value}" ;
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
                          |     knora-base:attachedToProject <${ctx.attachedToProject.value}> ;
                          |     knora-base:hasPermissions    "${ctx.permissions}" ;
                          |     knora-base:creationDate      "$knownInstant"^^xsd:dateTimeStamp ;
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
                          |     knora-base:valueCreationDate      "$knownInstant"^^xsd:dateTimeStamp ;
                          |     knora-base:valueHasUUID           "${valueIri.valueId.value}" ;
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

  override def spec = suite("OntologyTransformerSpec")(
    simpleScalarValues,
    iriRefValues,
    textValues,
    dateValues,
    resourceMetadata,
    valueHasString,
    dateValuesStage2,
  ).provide(OntologyTransformer.layer, StringFormatter.test, TestAppConfig.layer())

}
