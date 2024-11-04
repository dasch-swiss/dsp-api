/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import zio.*
import zio.json.DecoderOps
import zio.json.EncoderOps
import zio.json.ast.Json
import zio.test.*

import java.time.Instant

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.core.MessageRelayLive
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.CalendarNameGregorian
import org.knora.webapi.messages.util.DatePrecisionDay
import org.knora.webapi.messages.v2.responder.valuemessages.ArchiveFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.AudioFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.BooleanValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ColorValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.DateValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.DecimalValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.DocumentFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.GeomValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.GeonameValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.HierarchicalListValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.IntegerValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.IntervalValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.LinkValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.MovingImageFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.StillImageExternalFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.StillImageFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.TimeValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.UriValueContentV2
import org.knora.webapi.routing.v2.AssetIngestState.AssetIngested
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.KnoraIris.*
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resources.IiifImageRequestUrl
import org.knora.webapi.store.iiif.api.FileMetadataSipiResponse
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.impl.SipiServiceMock
import org.knora.webapi.store.iiif.impl.SipiServiceMock.SipiMockMethodName.GetFileMetadataFromDspIngest
import org.knora.webapi.store.iiif.impl.SipiServiceMock.SipiMockMethodName.GetFileMetadataFromSipiTemp

object ApiComplexV2JsonLdRequestParserSpec extends ZIOSpecDefault {
  private val sf = StringFormatter.getInitializedTestInstance

  private val service = ZIO.serviceWithZIO[ApiComplexV2JsonLdRequestParser]

  private val givenFileInfo =
    FileMetadataSipiResponse(
      Some("originalFilename.orig"),
      Some("originalMimeType"),
      "internalMimeType",
      Some(640),
      Some(480),
      Some(666),
      None,
      None,
    )

  private val expectedFileValue = FileValueV2(
    "internalFilename.ext",
    "internalMimeType",
    Some("originalFilename.orig"),
    Some("originalMimeType"),
  )

  private val configureSipiServiceMock = for {
    sipiMock <- ZIO.service[SipiServiceMock]
    _        <- sipiMock.setReturnValue(GetFileMetadataFromSipiTemp, ZIO.fail(Exception("No interaction with sipi expected")))
    _        <- sipiMock.setReturnValue(GetFileMetadataFromDspIngest, ZIO.succeed(givenFileInfo))
  } yield ()

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
          model <- service(_.createValueModelFromJsonLd(json, AssetIngested))
        } yield assertTrue(model.resourceIri.toString == "http://rdfh.ch/0001/a-thing")
      }
    },
    test("rootResourceClassIri should get the rdfs:type") {
      check(Gen.fromIterable(Seq(createIntegerValue, createLinkValue).map(_.toJsonPretty))) { json =>
        for {
          model <- service(_.createValueModelFromJsonLd(json, AssetIngested))
        } yield assertTrue(model.resourceClassIri.toString == "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing")
      }
    },
    test("valueNode properties should be present") {
      for {
        model      <- service(_.createValueModelFromJsonLd(createIntegerValue.toJsonPretty, AssetIngested))
        propertyIri = model.valuePropertyIri
        valueType   = model.valueType
      } yield assertTrue(
        propertyIri == PropertyIri.unsafeFrom(
          sf.toSmartIri("http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger"),
        ),
        valueType == sf.toSmartIri("http://api.knora.org/ontology/knora-api/v2#IntValue"),
        model.shortcode == Shortcode.unsafeFrom("0001"),
      )
    },
    test("should parse integer value") {
      for {
        model <- service(
                   _.createValueModelFromJsonLd(
                     """{
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
                       |}""".stripMargin,
                     AssetIngested,
                   ),
                 )
      } yield assertTrue(model.valueContent == IntegerValueContentV2(ApiV2Complex, 4, None))
    },
    test("should parse DecimalValueContentV2") {
      for {
        model <- service(
                   _.createValueModelFromJsonLd(
                     """{
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
                       |}""".stripMargin,
                     AssetIngested,
                   ),
                 )
      } yield assertTrue(model.valueContent == DecimalValueContentV2(ApiV2Complex, BigDecimal(4), None))
    },
    test("should parse BooleanValueContentV2") {
      for {
        model <- service(
                   _.createValueModelFromJsonLd(
                     """{
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
                       |}""".stripMargin,
                     AssetIngested,
                   ),
                 )
      } yield assertTrue(model.valueContent == BooleanValueContentV2(ApiV2Complex, true, None))
    },
    test("should parse GeomValueContentV2") {
      for {
        model <- service(
                   _.createValueModelFromJsonLd(
                     """{
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
                       |}""".stripMargin,
                     AssetIngested,
                   ),
                 )
      } yield assertTrue(model.valueContent == GeomValueContentV2(ApiV2Complex, "{}", None))
    },
    test("should parse IntervalValueContentV2") {
      for {
        model <- service(
                   _.createValueModelFromJsonLd(
                     """{
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
                       |}""".stripMargin,
                     AssetIngested,
                   ),
                 )
      } yield assertTrue(model.valueContent == IntervalValueContentV2(ApiV2Complex, BigDecimal(4), BigDecimal(2), None))
    },
    test("should parse TimeValueContentV2") {
      for {
        model <- service(
                   _.createValueModelFromJsonLd(
                     """{
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
                       |}""".stripMargin,
                     AssetIngested,
                   ),
                 )
      } yield assertTrue(
        model.valueContent == TimeValueContentV2(ApiV2Complex, Instant.parse("2020-06-04T11:36:54.502951Z"), None),
      )
    },
    test("should parse LinkValueContentV2") {
      for {
        model <-
          service(
            _.createValueModelFromJsonLd(
              s"""
                 |{
                 |  "@id" : "http://rdfh.ch/0001/a-thing",
                 |  "@type" : "ex:Thing",
                 |  "ex:hasOtherThingValue" : {
                 |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                 |    "@type" : "ka:LinkValue",
                 |    "ka:linkValueHasTargetIri" : {
                 |      "@id" : "http://rdfh.ch/0001/CNhWoNGGT7iWOrIwxsEqvA"
                 |    }
                 |  },
                 |  "@context": {
                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                 |    "ex": "https://example.com/test#",
                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                 |  }
                 |}""".stripMargin,
              AssetIngested,
            ),
          )
      } yield assertTrue(
        model.valueContent == LinkValueContentV2(
          ApiV2Complex,
          referredResourceIri = "http://rdfh.ch/0001/CNhWoNGGT7iWOrIwxsEqvA",
          comment = None,
        ),
      )
    },
    test("should parse UriValueContentV2") {
      for {
        model <-
          service(
            _.createValueModelFromJsonLd(
              s"""
                 |{
                 |  "@id" : "http://rdfh.ch/0001/a-thing",
                 |  "@type" : "ex:Thing",
                 |  "ex:hasOtherThingValue" : {
                 |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                 |    "@type" : "ka:UriValue",
                 |    "ka:uriValueAsUri" :  {
                 |      "@type" : "xsd:anyURI",
                 |      "@value" : "http://rdfh.ch/0001/CNhWoNGGT7iWOrIwxsEqvA"
                 |    }
                 |  },
                 |  "@context": {
                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                 |    "ex": "https://example.com/test#",
                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                 |  }
                 |}""".stripMargin,
              AssetIngested,
            ),
          )
      } yield assertTrue(
        model.valueContent == UriValueContentV2(ApiV2Complex, "http://rdfh.ch/0001/CNhWoNGGT7iWOrIwxsEqvA", None),
      )
    },
    test("should parse GeonameValueContentV2") {
      for {
        model <-
          service(
            _.createValueModelFromJsonLd(
              s"""
                 |{
                 |  "@id" : "http://rdfh.ch/0001/a-thing",
                 |  "@type" : "ex:Thing",
                 |  "ex:hasOtherThingValue" : {
                 |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                 |    "@type" : "ka:GeonameValue",
                 |    "ka:geonameValueAsGeonameCode" : "foo"
                 |  },
                 |  "@context": {
                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                 |    "ex": "https://example.com/test#",
                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                 |  }
                 |}""".stripMargin,
              AssetIngested,
            ),
          )
      } yield assertTrue(model.valueContent == GeonameValueContentV2(ApiV2Complex, "foo", None))
    },
    test("should parse ColorValue") {
      for {
        model <-
          service(
            _.createValueModelFromJsonLd(
              s"""
                 |{
                 |  "@id" : "http://rdfh.ch/0001/a-thing",
                 |  "@type" : "ex:Thing",
                 |  "ex:hasOtherThingValue" : {
                 |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                 |    "@type" : "ka:ColorValue",
                 |    "ka:colorValueAsColor" : "red"
                 |  },
                 |  "@context": {
                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                 |    "ex": "https://example.com/test#",
                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                 |  }
                 |}""".stripMargin,
              AssetIngested,
            ),
          )
      } yield assertTrue(model.valueContent == ColorValueContentV2(ApiV2Complex, "red", None))
    },
    test("should parse StillImageFileValue") {
      for {
        _ <- configureSipiServiceMock
        model <-
          service(
            _.createValueModelFromJsonLd(
              s"""
                 |{
                 |  "@id" : "http://rdfh.ch/0001/a-thing",
                 |  "@type" : "ex:Thing",
                 |  "ex:hasOtherThingValue" : {
                 |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                 |    "@type" : "ka:StillImageFileValue",
                 |    "ka:fileValueHasFilename": "internalFilename.ext"
                 |  },
                 |  "@context": {
                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                 |    "ex": "https://example.com/test#",
                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                 |  }
                 |}""".stripMargin,
              AssetIngested,
            ),
          )
      } yield assertTrue(
        model.valueContent == StillImageFileValueContentV2(
          ApiV2Complex,
          expectedFileValue,
          givenFileInfo.width.getOrElse(throw new Exception("width is missing")),
          givenFileInfo.height.getOrElse(throw new Exception("height is missing")),
          None,
        ),
      )
    },
    test("should parse StillImageExternalFileValue") {
      for {
        _ <- ZIO.serviceWithZIO[SipiServiceMock](_.assertNoInteraction)
        model <-
          service(
            _.createValueModelFromJsonLd(
              s"""
                 |{
                 |  "@id" : "http://rdfh.ch/0001/a-thing",
                 |  "@type" : "ex:Thing",
                 |  "ex:hasOtherThingValue" : {
                 |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                 |    "@type" : "ka:StillImageExternalFileValue",
                 |    "ka:stillImageFileValueHasExternalUrl" : "http://www.example.org/prefix1/abcd1234/full/0/native.jpg"
                 |  },
                 |  "@context": {
                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                 |    "ex": "https://example.com/test#",
                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                 |  }
                 |}""".stripMargin,
              AssetIngested,
            ),
          )
      } yield assertTrue(
        model.valueContent == StillImageExternalFileValueContentV2(
          ApiV2Complex,
          FileValueV2(
            "internalFilename",
            "internalMimeType",
            Some("originalFilename"),
            Some("originalMimeType"),
          ),
          IiifImageRequestUrl.unsafeFrom("http://www.example.org/prefix1/abcd1234/full/0/native.jpg"),
          None,
        ),
      )
    },
    test("should parse DocumentFileValue") {
      for {
        _ <- configureSipiServiceMock
        model <- service(
                   _.createValueModelFromJsonLd(
                     s"""
                        |{
                        |  "@id" : "http://rdfh.ch/0001/a-thing",
                        |  "@type" : "ex:Thing",
                        |  "ex:hasOtherThingValue" : {
                        |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                        |    "@type" : "ka:DocumentFileValue",
                        |    "ka:fileValueHasFilename": "internalFilename.ext"
                        |  },
                        |  "@context": {
                        |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                        |    "ex": "https://example.com/test#",
                        |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                        |  }
                        |}""".stripMargin,
                     AssetIngested,
                   ),
                 )
      } yield assertTrue(
        model.valueContent == DocumentFileValueContentV2(
          ApiV2Complex,
          expectedFileValue,
          givenFileInfo.numpages,
          givenFileInfo.width,
          givenFileInfo.height,
          None,
        ),
      )
    },
    test("should parse TextFileValueContentV2") {
      for {
        _ <- configureSipiServiceMock
        model <- service(
                   _.createValueModelFromJsonLd(
                     s"""
                        |{
                        |  "@id" : "http://rdfh.ch/0001/a-thing",
                        |  "@type" : "ex:Thing",
                        |  "ex:hasOtherThingValue" : {
                        |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                        |    "@type" : "ka:TextFileValue",
                        |    "ka:fileValueHasFilename": "internalFilename.ext"
                        |  },
                        |  "@context": {
                        |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                        |    "ex": "https://example.com/test#",
                        |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                        |  }
                        |}""".stripMargin,
                     AssetIngested,
                   ),
                 )
      } yield assertTrue(
        model.valueContent == TextFileValueContentV2(ApiV2Complex, expectedFileValue, None),
      )
    },
    test("should parse AudioFileValueContentV2") {
      for {
        _ <- configureSipiServiceMock
        model <- service(
                   _.createValueModelFromJsonLd(
                     s"""
                        |{
                        |  "@id" : "http://rdfh.ch/0001/a-thing",
                        |  "@type" : "ex:Thing",
                        |  "ex:hasOtherThingValue" : {
                        |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                        |    "@type" : "ka:AudioFileValue",
                        |    "ka:fileValueHasFilename": "internalFilename.ext"
                        |  },
                        |  "@context": {
                        |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                        |    "ex": "https://example.com/test#",
                        |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                        |  }
                        |}""".stripMargin,
                     AssetIngested,
                   ),
                 )
      } yield assertTrue(
        model.valueContent == AudioFileValueContentV2(ApiV2Complex, expectedFileValue, None),
      )
    },
    test("should parse MovingImageFileValueContentV2") {
      for {
        _ <- configureSipiServiceMock
        model <- service(
                   _.createValueModelFromJsonLd(
                     s"""
                        |{
                        |  "@id" : "http://rdfh.ch/0001/a-thing",
                        |  "@type" : "ex:Thing",
                        |  "ex:hasOtherThingValue" : {
                        |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                        |    "@type" : "ka:MovingImageFileValue",
                        |    "ka:fileValueHasFilename": "internalFilename.ext"
                        |  },
                        |  "@context": {
                        |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                        |    "ex": "https://example.com/test#",
                        |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                        |  }
                        |}""".stripMargin,
                     AssetIngested,
                   ),
                 )
      } yield assertTrue(
        model.valueContent == MovingImageFileValueContentV2(ApiV2Complex, expectedFileValue, None),
      )
    },
    test("should parse ArchiveFileValueContentV2") {
      for {
        _ <- configureSipiServiceMock
        model <- service(
                   _.createValueModelFromJsonLd(
                     s"""
                        |{
                        |  "@id" : "http://rdfh.ch/0001/a-thing",
                        |  "@type" : "ex:Thing",
                        |  "ex:hasOtherThingValue" : {
                        |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                        |    "@type" : "ka:ArchiveFileValue",
                        |    "ka:fileValueHasFilename": "internalFilename.ext"
                        |  },
                        |  "@context": {
                        |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                        |    "ex": "https://example.com/test#",
                        |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                        |  }
                        |}""".stripMargin,
                     AssetIngested,
                   ),
                 )
      } yield assertTrue(
        model.valueContent == ArchiveFileValueContentV2(ApiV2Complex, expectedFileValue, None),
      )
    },
    test("should parse HierarchicalListValueContentV2") {
      for {
        model <- service(
                   _.createValueModelFromJsonLd(
                     s"""
                        |{
                        |  "@id" : "http://rdfh.ch/0001/a-thing",
                        |  "@type" : "ex:Thing",
                        |  "ex:hasOtherThingValue" : {
                        |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                        |    "@type" : "ka:ListValue",
                        |    "ka:listValueAsListNode": {
                        |      "@id" : "http://rdfh.ch/0001/CNhWoNGGT7iWOrIwxsEqvA"
                        |    }
                        |  },
                        |  "@context": {
                        |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                        |    "ex": "https://example.com/test#",
                        |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                        |  }
                        |}""".stripMargin,
                     AssetIngested,
                   ),
                 )
      } yield assertTrue(
        model.valueContent == HierarchicalListValueContentV2(
          ApiV2Complex,
          "http://rdfh.ch/0001/CNhWoNGGT7iWOrIwxsEqvA",
          None,
          None,
        ),
      )
    },
    test("should parse DateValueContentV2") {
      for {
        model <- service(
                   _.createValueModelFromJsonLd(
                     s"""
                        |{
                        |  "@id" : "http://rdfh.ch/0001/a-thing",
                        |  "@type" : "ex:Thing",
                        |  "ex:hasOtherThingValue" : {
                        |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                        |    "@type" : "ka:DateValue",
                        |    "ka:dateValueHasCalendar" : "GREGORIAN",
                        |    "ka:dateValueHasEndEra" : "CE",
                        |    "ka:dateValueHasEndYear" : 1489,
                        |    "ka:dateValueHasEndMonth" : 12,
                        |    "ka:dateValueHasEndDay" : 24,
                        |    "ka:dateValueHasStartEra" : "CE",
                        |    "ka:dateValueHasStartMonth" : 1,
                        |    "ka:dateValueHasStartDay" : 28,
                        |    "ka:dateValueHasStartYear" : 1488
                        |  },
                        |  "@context": {
                        |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                        |    "ex": "https://example.com/test#",
                        |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                        |  }
                        |}""".stripMargin,
                     AssetIngested,
                   ),
                 )
      } yield assertTrue(
        model.valueContent == DateValueContentV2(
          ApiV2Complex,
          2264568,
          2265264,
          DatePrecisionDay,
          DatePrecisionDay,
          CalendarNameGregorian,
          None,
        ),
      )
    },
  ).provideSome[Scope](
    IriConverter.layer,
    MessageRelayLive.layer,
    StringFormatter.test,
    ApiComplexV2JsonLdRequestParser.layer,
    SipiServiceMock.layer,
  )
}
