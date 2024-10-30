/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import org.apache.jena.rdf.model.RDFNode
import zio.*
import zio.json.DecoderOps
import zio.json.EncoderOps
import zio.json.ast.Json
import zio.test.*
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.valuemessages.BooleanValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.DecimalValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.GeomValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.IntegerValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.IntervalValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.TimeValueContentV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.KnoraIris.*
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

import java.time.Instant

object KnoraApiValueModelSpec extends ZIOSpecDefault {
  private val sf = StringFormatter.getInitializedTestInstance

  private val createIntegerValue = """
    {
      "@id": "http://rdfh.ch/0001/a-thing",
      "@type": "anything:Thing",
      "anything:hasInteger": {
        "@type": "knora-api:IntValue",
        "knora-api:intValueAsInt": 4
      },
      "@context": {
        "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
        "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
      }
    }
  """.fromJson[Json].getOrElse(throw new Exception("Invalid JSON"))

  private val createLinkValue =
    s"""{
         "@id" : "http://rdfh.ch/0001/a-thing",
         "@type" : "anything:Thing",
         "anything:hasOtherThingValue" : {
           "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
           "@type" : "knora-api:LinkValue",
           "knora-api:valueHasUUID": "mr9i2aUUJolv64V_9hYdTw",
           "knora-api:linkValueHasTargetIri" : {
             "@id" : "http://rdfh.ch/0001/CNhWoNGGT7iWOrIwxsEqvA"
           },
           "knora-api:valueCreationDate" : {
               "@type" : "xsd:dateTimeStamp",
               "@value" : "2020-06-04T11:36:54.502951Z"
           }
         },
         "@context" : {
           "xsd" : "http://www.w3.org/2001/XMLSchema#",
           "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
         }
       }""".fromJson[Json].getOrElse(throw new Exception("Invalid JSON"))

  val spec = suite("KnoraApiValueModel")(
    test("getResourceIri should get the id") {
      check(Gen.fromIterable(Seq(createIntegerValue, createLinkValue).map(_.toJsonPretty))) { json =>
        for {
          model <- KnoraApiValueModel.fromJsonLd(json)
        } yield assertTrue(model.resourceIri.toString == "http://rdfh.ch/0001/a-thing")
      }
    },
    test("rootResourceClassIri should get the rdfs:type") {
      check(Gen.fromIterable(Seq(createIntegerValue, createLinkValue).map(_.toJsonPretty))) { json =>
        for {
          model <- KnoraApiValueModel.fromJsonLd(json)
        } yield assertTrue(model.resourceClassIri.toString == "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing")
      }
    },
    test("rootResourceProperties should get the props") {
      check(Gen.fromIterable(Seq(createIntegerValue, createLinkValue).map(_.toJsonPretty))) { json =>
        for {
          model <- KnoraApiValueModel.fromJsonLd(json)
          node   = model.valueNode
        } yield assertTrue(node != null)
      }
    },
    test("valueNode properties should be present") {
      for {
        model       <- KnoraApiValueModel.fromJsonLd(createIntegerValue.toJsonPretty)
        propertyIri  = model.valueNode.propertyIri
        valueType    = model.valueNode.valueType
        foo: RDFNode = model.valueNode.node

      } yield assertTrue(
        propertyIri == PropertyIri.unsafeFrom(
          sf.toSmartIri("http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger"),
        ),
        valueType == sf.toSmartIri("http://api.knora.org/ontology/knora-api/v2#IntValue"),
        model.valueNode.shortcode == Shortcode.unsafeFrom("0001"),
      )
    },
    test("should parse integer value") {
      for {
        model <- KnoraApiValueModel.fromJsonLd("""{
                                                 |  "@id": "http://rdfh.ch/0001/a-thing",
                                                 |  "@type": "ex:Thing",
                                                 |  "ex:someInt": {
                                                 |    "@type": "ka:IntValue",
                                                 |    "ka:intValueAsInt": {
                                                 |       "@type": "xsd:integer",
                                                 |       "@value": 4
                                                 |    }
                                                 |  },
                                                 |  "@context": {
                                                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                                                 |    "ex": "https://example.com/test#",
                                                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                                                 |  }
                                                 |}""".stripMargin)
        content = model.valueNode.getValueContent
      } yield assertTrue(content == Right(IntegerValueContentV2(ApiV2Complex, 4, None)))
    },
    test("should parse DecimalValueContentV2") {
      for {
        model <- KnoraApiValueModel.fromJsonLd("""{
                                                 |  "@id": "http://rdfh.ch/0001/a-thing",
                                                 |  "@type": "ex:Thing",
                                                 |  "ex:someDec": {
                                                 |    "@type": "ka:DecimalValue",
                                                 |    "ka:decimalValueAsDecimal": {
                                                 |       "@type": "xsd:decimal",
                                                 |       "@value": "4"
                                                 |    }
                                                 |  },
                                                 |  "@context": {
                                                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                                                 |    "ex": "https://example.com/test#",
                                                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                                                 |  }
                                                 |}""".stripMargin)
        content = model.valueNode.getValueContent
      } yield assertTrue(content == Right(DecimalValueContentV2(ApiV2Complex, BigDecimal(4), None)))
    },
    test("should parse BooleanValueContentV2") {
      for {
        model <- KnoraApiValueModel.fromJsonLd("""{
                                                 |  "@id": "http://rdfh.ch/0001/a-thing",
                                                 |  "@type": "ex:Thing",
                                                 |  "ex:someBool": {
                                                 |    "@type": "ka:BooleanValue",
                                                 |    "ka:booleanValueAsBoolean": {
                                                 |       "@type": "xsd:boolean",
                                                 |       "@value": "true"
                                                 |    }
                                                 |  },
                                                 |  "@context": {
                                                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                                                 |    "ex": "https://example.com/test#",
                                                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                                                 |  }
                                                 |}""".stripMargin)
        content = model.valueNode.getValueContent
      } yield assertTrue(content == Right(BooleanValueContentV2(ApiV2Complex, true, None)))
    },
    test("should parse GeomValueContentV2") {
      for {
        model <- KnoraApiValueModel.fromJsonLd("""{
                                                 |  "@id": "http://rdfh.ch/0001/a-thing",
                                                 |  "@type": "ex:Thing",
                                                 |  "ex:someGeom": {
                                                 |    "@type": "ka:GeomValue",
                                                 |    "ka:geometryValueAsGeometry": "{}"
                                                 |  },
                                                 |  "@context": {
                                                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                                                 |    "ex": "https://example.com/test#",
                                                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                                                 |  }
                                                 |}""".stripMargin)
        content = model.valueNode.getValueContent
      } yield assertTrue(content == Right(GeomValueContentV2(ApiV2Complex, "{}", None)))
    },
    test("should parse IntervalValueContentV2") {
      for {
        model <- KnoraApiValueModel.fromJsonLd("""{
                                                 |  "@id": "http://rdfh.ch/0001/a-thing",
                                                 |  "@type": "ex:Thing",
                                                 |  "ex:someInterval": {
                                                 |    "@type": "ka:IntervalValue",
                                                 |    "ka:intervalValueHasStart": {
                                                 |       "@type": "xsd:decimal",
                                                 |       "@value": 4
                                                 |    },
                                                 |    "ka:intervalValueHasEnd": {
                                                 |       "@type": "xsd:decimal",
                                                 |       "@value": 2
                                                 |    }
                                                 |  },
                                                 |  "@context": {
                                                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                                                 |    "ex": "https://example.com/test#",
                                                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                                                 |  }
                                                 |}""".stripMargin)
        content = model.valueNode.getValueContent
      } yield assertTrue(content == Right(IntervalValueContentV2(ApiV2Complex, BigDecimal(4), BigDecimal(2), None)))
    },
    test("should parse TimeValueContentV2") {
      for {
        model <- KnoraApiValueModel.fromJsonLd("""{
                                                 |  "@id": "http://rdfh.ch/0001/a-thing",
                                                 |  "@type": "ex:Thing",
                                                 |  "ex:someTimeValue": {
                                                 |    "@type": "ka:TimeValue",
                                                 |    "ka:timeValueAsTimeStamp": {
                                                 |       "@type": "xsd:dateTimeStamp",
                                                 |       "@value": "2020-06-04T11:36:54.502951Z"
                                                 |    }
                                                 |  },
                                                 |  "@context": {
                                                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                                                 |    "ex": "https://example.com/test#",
                                                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                                                 |  }
                                                 |}""".stripMargin)
        content = model.valueNode.getValueContent
      } yield assertTrue(
        content == Right(TimeValueContentV2(ApiV2Complex, Instant.parse("2020-06-04T11:36:54.502951Z"), None)),
      )
    },
  ).provideSome[Scope](IriConverter.layer, StringFormatter.test)
}
