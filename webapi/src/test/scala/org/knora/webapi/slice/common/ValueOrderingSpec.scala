/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import org.apache.jena.vocabulary.RDF
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.test.*

import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelayLive
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionRepoInMemory
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.repo.*
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.admin.repo.LicenseRepo
import org.knora.webapi.slice.admin.repo.service.*
import org.knora.webapi.slice.common.jena.JenaConversions.given
import org.knora.webapi.slice.common.jena.ModelOps
import org.knora.webapi.slice.common.jena.ModelOps.*
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoInMemory
import org.knora.webapi.store.iiif.impl.SipiServiceMock
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object ValueOrderingSpec extends ZIOSpecDefault {

  private val parser = ZIO.serviceWithZIO[ApiComplexV2JsonLdRequestParser]

  private val OrderIndexProperty = "http://knora.org/internal/orderIndex"

  val injectOrderIndicesSuite = suite("injectOrderIndices")(
    test("injects indices into text value array") {
      parser { p =>
        val json =
          """{
            |  "@type" : "anything:Thing",
            |  "anything:hasText" : [
            |    { "@type" : "knora-api:TextValue", "knora-api:valueAsString" : "Echo" },
            |    { "@type" : "knora-api:TextValue", "knora-api:valueAsString" : "Alpha" },
            |    { "@type" : "knora-api:TextValue", "knora-api:valueAsString" : "Delta" }
            |  ],
            |  "@context" : {
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
            |  }
            |}""".stripMargin
        val result    = p.injectOrderIndices(json)
        val resultObj = result.fromJson[Json.Obj].fold(_ => throw new AssertionError("JSON parse failed"), identity)
        val values    =
          resultObj
            .get("anything:hasText")
            .fold(throw new AssertionError("missing key"))(_.asInstanceOf[Json.Arr].elements)
        ZIO.succeed(
          assertTrue(
            values.size == 3,
            values(0).asInstanceOf[Json.Obj].get(OrderIndexProperty).contains(Json.Num(0)),
            values(1).asInstanceOf[Json.Obj].get(OrderIndexProperty).contains(Json.Num(1)),
            values(2).asInstanceOf[Json.Obj].get(OrderIndexProperty).contains(Json.Num(2)),
          ),
        )
      }
    },
    test("injects indices into integer value array") {
      parser { p =>
        val json =
          """{
            |  "@type" : "anything:Thing",
            |  "anything:hasInteger" : [
            |    { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 5 },
            |    { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 3 },
            |    { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 1 }
            |  ],
            |  "@context" : {
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin
        val result    = p.injectOrderIndices(json)
        val resultObj = result.fromJson[Json.Obj].fold(_ => throw new AssertionError("JSON parse failed"), identity)
        val values    =
          resultObj
            .get("anything:hasInteger")
            .fold(throw new AssertionError("missing key"))(_.asInstanceOf[Json.Arr].elements)
        ZIO.succeed(
          assertTrue(
            values(0).asInstanceOf[Json.Obj].get(OrderIndexProperty).contains(Json.Num(0)),
            values(1).asInstanceOf[Json.Obj].get(OrderIndexProperty).contains(Json.Num(1)),
            values(2).asInstanceOf[Json.Obj].get(OrderIndexProperty).contains(Json.Num(2)),
          ),
        )
      }
    },
    test("preserves single values (not arrays) unchanged") {
      parser { p =>
        val json =
          """{
            |  "@type" : "anything:Thing",
            |  "anything:hasText" : { "@type" : "knora-api:TextValue", "knora-api:valueAsString" : "single" },
            |  "@context" : {
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin
        val result    = p.injectOrderIndices(json)
        val resultObj = result.fromJson[Json.Obj].fold(_ => throw new AssertionError("JSON parse failed"), identity)
        val value     =
          resultObj.get("anything:hasText").fold(throw new AssertionError("missing key"))(_.asInstanceOf[Json.Obj])
        ZIO.succeed(
          assertTrue(
            value.get(OrderIndexProperty).isEmpty,
          ),
        )
      }
    },
    test("preserves @context and @type fields unchanged") {
      parser { p =>
        val json =
          """{
            |  "@type" : "anything:Thing",
            |  "@context" : {
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
            |  }
            |}""".stripMargin
        val result    = p.injectOrderIndices(json)
        val resultObj = result.fromJson[Json.Obj].fold(_ => throw new AssertionError("JSON parse failed"), identity)
        ZIO.succeed(
          assertTrue(
            resultObj.get("@type").contains(Json.Str("anything:Thing")),
            resultObj.get("@context").isDefined,
          ),
        )
      }
    },
    test("handles empty arrays") {
      parser { p =>
        val json =
          """{
            |  "@type" : "anything:Thing",
            |  "anything:hasText" : [],
            |  "@context" : {
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin
        val result    = p.injectOrderIndices(json)
        val resultObj = result.fromJson[Json.Obj].fold(_ => throw new AssertionError("JSON parse failed"), identity)
        val values    =
          resultObj
            .get("anything:hasText")
            .fold(throw new AssertionError("missing key"))(_.asInstanceOf[Json.Arr].elements)
        ZIO.succeed(assertTrue(values.isEmpty))
      }
    },
    test("handles mixed properties with multiple arrays") {
      parser { p =>
        val json =
          """{
            |  "@type" : "anything:Thing",
            |  "anything:hasText" : [
            |    { "@type" : "knora-api:TextValue", "knora-api:valueAsString" : "Echo" },
            |    { "@type" : "knora-api:TextValue", "knora-api:valueAsString" : "Alpha" }
            |  ],
            |  "anything:hasInteger" : [
            |    { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 5 },
            |    { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 3 }
            |  ],
            |  "@context" : {
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin
        val result    = p.injectOrderIndices(json)
        val resultObj = result.fromJson[Json.Obj].fold(_ => throw new AssertionError("JSON parse failed"), identity)
        val texts     =
          resultObj
            .get("anything:hasText")
            .fold(throw new AssertionError("missing key"))(_.asInstanceOf[Json.Arr].elements)
        val ints =
          resultObj
            .get("anything:hasInteger")
            .fold(throw new AssertionError("missing key"))(_.asInstanceOf[Json.Arr].elements)
        ZIO.succeed(
          assertTrue(
            texts(0).asInstanceOf[Json.Obj].get(OrderIndexProperty).contains(Json.Num(0)),
            texts(1).asInstanceOf[Json.Obj].get(OrderIndexProperty).contains(Json.Num(1)),
            ints(0).asInstanceOf[Json.Obj].get(OrderIndexProperty).contains(Json.Num(0)),
            ints(1).asInstanceOf[Json.Obj].get(OrderIndexProperty).contains(Json.Num(1)),
          ),
        )
      }
    },
    test("returns raw JSON unchanged on parse failure") {
      parser { p =>
        val invalid = "not valid json {"
        val result  = p.injectOrderIndices(invalid)
        ZIO.succeed(assertTrue(result == invalid))
      }
    },
  )

  private val integerJsonLd =
    """{
      |  "@type" : "anything:Thing",
      |  "anything:hasInteger" : [
      |    { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 5 },
      |    { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 3 },
      |    { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 1 },
      |    { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 4 },
      |    { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 2 }
      |  ],
      |  "knora-api:attachedToProject" : { "@id" : "http://rdfh.ch/projects/0001" },
      |  "rdfs:label" : "test",
      |  "@context" : {
      |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
      |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
      |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
      |  }
      |}""".stripMargin

  val orderIndexRoundTripSuite = suite("orderIndex round-trip through Jena")(
    test("inject -> Jena parse -> readOrderIndex gives correct indices for 3 text values") {
      parser { p =>
        val textJsonLd =
          """{
            |  "@type" : "anything:Thing",
            |  "anything:hasText" : [
            |    { "@type" : "knora-api:TextValue", "knora-api:valueAsString" : "Echo" },
            |    { "@type" : "knora-api:TextValue", "knora-api:valueAsString" : "Alpha" },
            |    { "@type" : "knora-api:TextValue", "knora-api:valueAsString" : "Delta" }
            |  ],
            |  "knora-api:attachedToProject" : { "@id" : "http://rdfh.ch/projects/0001" },
            |  "rdfs:label" : "test",
            |  "@context" : {
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
            |  }
            |}""".stripMargin
        val injected = p.injectOrderIndices(textJsonLd)
        ZIO.scoped {
          for {
            model     <- ModelOps.fromJsonLd(injected)
            resource  <- ZIO.fromEither(model.singleRootResource)
            valueStmts = resource
                           .listProperties()
                           .asScala
                           .filter(s =>
                             !Set(
                               RDF.`type`.getURI,
                               "http://www.w3.org/2000/01/rdf-schema#label",
                               "http://api.knora.org/ontology/knora-api/v2#attachedToProject",
                             ).contains(s.getPredicate.getURI),
                           )
                           .toSeq
            valuesWithIndex = valueStmts.map { stmt =>
                                val r     = stmt.getObject.asResource()
                                val text  = r.objectString("http://api.knora.org/ontology/knora-api/v2#valueAsString")
                                val index = r.objectIntOption(OrderIndexProperty).toOption.flatten
                                (text.toOption.getOrElse("???"), index)
                              }
            sorted = valuesWithIndex.sortBy { case (_, idx) => idx.getOrElse(Int.MaxValue) }
          } yield assertTrue(
            sorted.map { case (value, _) => value } == Seq("Echo", "Alpha", "Delta"),
            sorted.map { case (_, idx) => idx } == Seq(Some(0), Some(1), Some(2)),
          )
        }
      }
    },
    test("inject -> Jena parse -> readOrderIndex gives correct indices for 5 integer values") {
      parser { p =>
        val injected = p.injectOrderIndices(integerJsonLd)
        ZIO.scoped {
          for {
            model     <- ModelOps.fromJsonLd(injected)
            resource  <- ZIO.fromEither(model.singleRootResource)
            valueStmts = resource
                           .listProperties()
                           .asScala
                           .filter(s =>
                             !Set(
                               RDF.`type`.getURI,
                               "http://www.w3.org/2000/01/rdf-schema#label",
                               "http://api.knora.org/ontology/knora-api/v2#attachedToProject",
                             ).contains(s.getPredicate.getURI),
                           )
                           .toSeq
            valuesWithIndex = valueStmts.map { stmt =>
                                val r     = stmt.getObject.asResource()
                                val int   = r.objectInt("http://api.knora.org/ontology/knora-api/v2#intValueAsInt")
                                val index = r.objectIntOption(OrderIndexProperty).toOption.flatten
                                (int.toOption.getOrElse(0), index)
                              }
            sorted = valuesWithIndex.sortBy { case (_, idx) => idx.getOrElse(Int.MaxValue) }
          } yield assertTrue(
            sorted.map { case (value, _) => value } == Seq(5, 3, 1, 4, 2),
            sorted.map { case (_, idx) => idx } == Seq(Some(0), Some(1), Some(2), Some(3), Some(4)),
          )
        }
      }
    },
    test("inject -> Jena parse -> readOrderIndex preserves rich text XML content") {
      parser { p =>
        val richTextJsonLd =
          """{
            |  "@type" : "anything:Thing",
            |  "anything:hasRichtext" : [
            |    {
            |      "@type" : "knora-api:TextValue",
            |      "knora-api:textValueAsXml" : "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text><p>First <strong>bold</strong></p></text>",
            |      "knora-api:textValueHasMapping" : { "@id" : "http://rdfh.ch/standoff/mappings/StandardMapping" }
            |    },
            |    {
            |      "@type" : "knora-api:TextValue",
            |      "knora-api:textValueAsXml" : "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text><p>Second <em>italic</em></p></text>",
            |      "knora-api:textValueHasMapping" : { "@id" : "http://rdfh.ch/standoff/mappings/StandardMapping" }
            |    }
            |  ],
            |  "knora-api:attachedToProject" : { "@id" : "http://rdfh.ch/projects/0001" },
            |  "rdfs:label" : "test",
            |  "@context" : {
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
            |  }
            |}""".stripMargin
        val injected = p.injectOrderIndices(richTextJsonLd)
        ZIO.scoped {
          for {
            model     <- ModelOps.fromJsonLd(injected)
            resource  <- ZIO.fromEither(model.singleRootResource)
            valueStmts = resource
                           .listProperties()
                           .asScala
                           .filter(s =>
                             !Set(
                               RDF.`type`.getURI,
                               "http://www.w3.org/2000/01/rdf-schema#label",
                               "http://api.knora.org/ontology/knora-api/v2#attachedToProject",
                             ).contains(s.getPredicate.getURI),
                           )
                           .toSeq
            valuesWithIndex = valueStmts.map { stmt =>
                                val r     = stmt.getObject.asResource()
                                val xml   = r.objectString("http://api.knora.org/ontology/knora-api/v2#textValueAsXml")
                                val index = r.objectIntOption(OrderIndexProperty).toOption.flatten
                                (xml.toOption.getOrElse("???"), index)
                              }
            sorted = valuesWithIndex.sortBy { case (_, idx) => idx.getOrElse(Int.MaxValue) }
          } yield assertTrue(
            sorted.map { case (_, idx) => idx } == Seq(Some(0), Some(1)),
            sorted.head match { case (xml, _) => xml.contains("<strong>bold</strong>") },
            sorted(1) match { case (xml, _) => xml.contains("<em>italic</em>") },
          )
        }
      }
    },
    test("inject -> Jena parse -> readOrderIndex for 10 values (stress test)") {
      parser { p =>
        val values = (0 until 10)
          .map(i => s"""{ "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : ${9 - i} }""")
          .mkString(",\n    ")
        val json =
          s"""{
             |  "@type" : "anything:Thing",
             |  "anything:hasInteger" : [
             |    $values
             |  ],
             |  "knora-api:attachedToProject" : { "@id" : "http://rdfh.ch/projects/0001" },
             |  "rdfs:label" : "test",
             |  "@context" : {
             |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
             |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
             |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
             |  }
             |}""".stripMargin
        val injected = p.injectOrderIndices(json)
        ZIO.scoped {
          for {
            model     <- ModelOps.fromJsonLd(injected)
            resource  <- ZIO.fromEither(model.singleRootResource)
            valueStmts = resource
                           .listProperties()
                           .asScala
                           .filter(s =>
                             !Set(
                               RDF.`type`.getURI,
                               "http://www.w3.org/2000/01/rdf-schema#label",
                               "http://api.knora.org/ontology/knora-api/v2#attachedToProject",
                             ).contains(s.getPredicate.getURI),
                           )
                           .toSeq
            valuesWithIndex = valueStmts.map { stmt =>
                                val r     = stmt.getObject.asResource()
                                val int   = r.objectInt("http://api.knora.org/ontology/knora-api/v2#intValueAsInt")
                                val index = r.objectIntOption(OrderIndexProperty).toOption.flatten
                                (int.toOption.getOrElse(0), index)
                              }
            sorted = valuesWithIndex.sortBy { case (_, idx) => idx.getOrElse(Int.MaxValue) }
          } yield assertTrue(
            sorted.map { case (value, _) => value } == (0 until 10).map(i => 9 - i).toSeq,
            sorted.map { case (_, idx) => idx } == (0 until 10).map(i => Some(i)).toSeq,
          )
        }
      }
    },
  )

  val extractValuesSuite = suite("extractValues - full pipeline")(
    test("integer values preserve JSON array order") {
      parser { p =>
        ZIO.scoped {
          val injected = p.injectOrderIndices(integerJsonLd)
          for {
            model    <- ModelOps.fromJsonLd(injected)
            resource <- ZIO.fromEither(model.singleRootResource)
            shortcode = Shortcode.unsafeFrom("0001")
            result   <- p.extractValues(resource, shortcode).mapError(msg => new RuntimeException(msg))
          } yield {
            val values = result.values.head
            assertTrue(
              values.map(_.valueContent.unescape.valueHasString) == Seq("5", "3", "1", "4", "2"),
              values.map(_.orderHint) == Seq(Some(0), Some(1), Some(2), Some(3), Some(4)),
            )
          }
        }
      }
    },
    test("text values preserve JSON array order") {
      parser { p =>
        val textJsonLd =
          """{
            |  "@type" : "anything:Thing",
            |  "anything:hasText" : [
            |    { "@type" : "knora-api:TextValue", "knora-api:valueAsString" : "Echo" },
            |    { "@type" : "knora-api:TextValue", "knora-api:valueAsString" : "Alpha" },
            |    { "@type" : "knora-api:TextValue", "knora-api:valueAsString" : "Delta" }
            |  ],
            |  "knora-api:attachedToProject" : { "@id" : "http://rdfh.ch/projects/0001" },
            |  "rdfs:label" : "test",
            |  "@context" : {
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
            |  }
            |}""".stripMargin
        ZIO.scoped {
          val injected = p.injectOrderIndices(textJsonLd)
          for {
            model    <- ModelOps.fromJsonLd(injected)
            resource <- ZIO.fromEither(model.singleRootResource)
            shortcode = Shortcode.unsafeFrom("0001")
            result   <- p.extractValues(resource, shortcode).mapError(msg => new RuntimeException(msg))
          } yield {
            val values = result.values.head
            assertTrue(
              values.map(_.valueContent.unescape.valueHasString) == Seq("Echo", "Alpha", "Delta"),
              values.map(_.orderHint) == Seq(Some(0), Some(1), Some(2)),
            )
          }
        }
      }
    },
    test("multiple properties each preserve their own order") {
      parser { p =>
        val multiPropJsonLd =
          """{
            |  "@type" : "anything:Thing",
            |  "anything:hasText" : [
            |    { "@type" : "knora-api:TextValue", "knora-api:valueAsString" : "Zulu" },
            |    { "@type" : "knora-api:TextValue", "knora-api:valueAsString" : "Alpha" }
            |  ],
            |  "anything:hasInteger" : [
            |    { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 9 },
            |    { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 1 },
            |    { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 5 }
            |  ],
            |  "knora-api:attachedToProject" : { "@id" : "http://rdfh.ch/projects/0001" },
            |  "rdfs:label" : "test",
            |  "@context" : {
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
            |  }
            |}""".stripMargin
        ZIO.scoped {
          val injected = p.injectOrderIndices(multiPropJsonLd)
          for {
            model    <- ModelOps.fromJsonLd(injected)
            resource <- ZIO.fromEither(model.singleRootResource)
            shortcode = Shortcode.unsafeFrom("0001")
            result   <- p.extractValues(resource, shortcode).mapError(msg => new RuntimeException(msg))
          } yield {
            val textValues =
              result.find { case (iri, _) => iri.toString.contains("hasText") }.map { case (_, vs) => vs }
                .getOrElse(Seq.empty)
            val intValues =
              result.find { case (iri, _) => iri.toString.contains("hasInteger") }.map { case (_, vs) => vs }
                .getOrElse(Seq.empty)
            assertTrue(
              textValues.map(_.valueContent.unescape.valueHasString) == Seq("Zulu", "Alpha"),
              intValues.map(_.valueContent.unescape.valueHasString) == Seq("9", "1", "5"),
            )
          }
        }
      }
    },
    test("boolean values preserve JSON array order") {
      parser { p =>
        val booleanJsonLd =
          """{
            |  "@type" : "anything:Thing",
            |  "anything:hasBoolean" : [
            |    { "@type" : "knora-api:BooleanValue", "knora-api:booleanValueAsBoolean" : true },
            |    { "@type" : "knora-api:BooleanValue", "knora-api:booleanValueAsBoolean" : false },
            |    { "@type" : "knora-api:BooleanValue", "knora-api:booleanValueAsBoolean" : true }
            |  ],
            |  "knora-api:attachedToProject" : { "@id" : "http://rdfh.ch/projects/0001" },
            |  "rdfs:label" : "test",
            |  "@context" : {
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
            |  }
            |}""".stripMargin
        ZIO.scoped {
          val injected = p.injectOrderIndices(booleanJsonLd)
          for {
            model    <- ModelOps.fromJsonLd(injected)
            resource <- ZIO.fromEither(model.singleRootResource)
            shortcode = Shortcode.unsafeFrom("0001")
            result   <- p.extractValues(resource, shortcode).mapError(msg => new RuntimeException(msg))
          } yield {
            val values = result.values.head
            assertTrue(
              values.map(_.valueContent.unescape.valueHasString) == Seq("true", "false", "true"),
              values.map(_.orderHint) == Seq(Some(0), Some(1), Some(2)),
            )
          }
        }
      }
    },
    test("color values preserve JSON array order") {
      parser { p =>
        val colorJsonLd =
          """{
            |  "@type" : "anything:Thing",
            |  "anything:hasColor" : [
            |    { "@type" : "knora-api:ColorValue", "knora-api:colorValueAsColor" : "#ff0000" },
            |    { "@type" : "knora-api:ColorValue", "knora-api:colorValueAsColor" : "#00ff00" },
            |    { "@type" : "knora-api:ColorValue", "knora-api:colorValueAsColor" : "#0000ff" }
            |  ],
            |  "knora-api:attachedToProject" : { "@id" : "http://rdfh.ch/projects/0001" },
            |  "rdfs:label" : "test",
            |  "@context" : {
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
            |  }
            |}""".stripMargin
        ZIO.scoped {
          val injected = p.injectOrderIndices(colorJsonLd)
          for {
            model    <- ModelOps.fromJsonLd(injected)
            resource <- ZIO.fromEither(model.singleRootResource)
            shortcode = Shortcode.unsafeFrom("0001")
            result   <- p.extractValues(resource, shortcode).mapError(msg => new RuntimeException(msg))
          } yield {
            val values = result.values.head
            assertTrue(
              values.map(_.valueContent.unescape.valueHasString) == Seq("#ff0000", "#00ff00", "#0000ff"),
              values.map(_.orderHint) == Seq(Some(0), Some(1), Some(2)),
            )
          }
        }
      }
    },
    test("decimal values preserve JSON array order") {
      parser { p =>
        val decimalJsonLd =
          """{
            |  "@type" : "anything:Thing",
            |  "anything:hasDecimal" : [
            |    { "@type" : "knora-api:DecimalValue", "knora-api:decimalValueAsDecimal" : { "@type" : "xsd:decimal", "@value" : "3.14" } },
            |    { "@type" : "knora-api:DecimalValue", "knora-api:decimalValueAsDecimal" : { "@type" : "xsd:decimal", "@value" : "1.41" } },
            |    { "@type" : "knora-api:DecimalValue", "knora-api:decimalValueAsDecimal" : { "@type" : "xsd:decimal", "@value" : "2.72" } }
            |  ],
            |  "knora-api:attachedToProject" : { "@id" : "http://rdfh.ch/projects/0001" },
            |  "rdfs:label" : "test",
            |  "@context" : {
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
            |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
            |  }
            |}""".stripMargin
        ZIO.scoped {
          val injected = p.injectOrderIndices(decimalJsonLd)
          for {
            model    <- ModelOps.fromJsonLd(injected)
            resource <- ZIO.fromEither(model.singleRootResource)
            shortcode = Shortcode.unsafeFrom("0001")
            result   <- p.extractValues(resource, shortcode).mapError(msg => new RuntimeException(msg))
          } yield {
            val values = result.values.head
            assertTrue(
              values.size == 3,
              values.map(_.orderHint) == Seq(Some(0), Some(1), Some(2)),
            )
          }
        }
      }
    },
    test("URI values preserve JSON array order") {
      parser { p =>
        val uriJsonLd =
          """{
            |  "@type" : "anything:Thing",
            |  "anything:hasUri" : [
            |    { "@type" : "knora-api:UriValue", "knora-api:uriValueAsUri" : { "@type" : "xsd:anyURI", "@value" : "https://example.com/a" } },
            |    { "@type" : "knora-api:UriValue", "knora-api:uriValueAsUri" : { "@type" : "xsd:anyURI", "@value" : "https://example.com/b" } },
            |    { "@type" : "knora-api:UriValue", "knora-api:uriValueAsUri" : { "@type" : "xsd:anyURI", "@value" : "https://example.com/c" } }
            |  ],
            |  "knora-api:attachedToProject" : { "@id" : "http://rdfh.ch/projects/0001" },
            |  "rdfs:label" : "test",
            |  "@context" : {
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
            |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
            |  }
            |}""".stripMargin
        ZIO.scoped {
          val injected = p.injectOrderIndices(uriJsonLd)
          for {
            model    <- ModelOps.fromJsonLd(injected)
            resource <- ZIO.fromEither(model.singleRootResource)
            shortcode = Shortcode.unsafeFrom("0001")
            result   <- p.extractValues(resource, shortcode).mapError(msg => new RuntimeException(msg))
          } yield {
            val values = result.values.head
            assertTrue(
              values.map(_.valueContent.unescape.valueHasString) == Seq(
                "https://example.com/a",
                "https://example.com/b",
                "https://example.com/c",
              ),
              values.map(_.orderHint) == Seq(Some(0), Some(1), Some(2)),
            )
          }
        }
      }
    },
    test("link values preserve JSON array order") {
      parser { p =>
        val linkJsonLd =
          """{
            |  "@type" : "anything:Thing",
            |  "anything:hasOtherThingValue" : [
            |    { "@type" : "knora-api:LinkValue", "knora-api:linkValueHasTargetIri" : { "@id" : "http://rdfh.ch/0001/thing-1" } },
            |    { "@type" : "knora-api:LinkValue", "knora-api:linkValueHasTargetIri" : { "@id" : "http://rdfh.ch/0001/thing-2" } },
            |    { "@type" : "knora-api:LinkValue", "knora-api:linkValueHasTargetIri" : { "@id" : "http://rdfh.ch/0001/thing-3" } }
            |  ],
            |  "knora-api:attachedToProject" : { "@id" : "http://rdfh.ch/projects/0001" },
            |  "rdfs:label" : "test",
            |  "@context" : {
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
            |  }
            |}""".stripMargin
        ZIO.scoped {
          val injected = p.injectOrderIndices(linkJsonLd)
          for {
            model    <- ModelOps.fromJsonLd(injected)
            resource <- ZIO.fromEither(model.singleRootResource)
            shortcode = Shortcode.unsafeFrom("0001")
            result   <- p.extractValues(resource, shortcode).mapError(msg => new RuntimeException(msg))
          } yield {
            val values = result.values.head
            assertTrue(
              values.map(_.valueContent.unescape.valueHasString) == Seq(
                "http://rdfh.ch/0001/thing-1",
                "http://rdfh.ch/0001/thing-2",
                "http://rdfh.ch/0001/thing-3",
              ),
              values.map(_.orderHint) == Seq(Some(0), Some(1), Some(2)),
            )
          }
        }
      }
    },
    test("single value (not in array) gets None orderHint") {
      parser { p =>
        val singleValueJsonLd =
          """{
            |  "@type" : "anything:Thing",
            |  "anything:hasInteger" : { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 42 },
            |  "knora-api:attachedToProject" : { "@id" : "http://rdfh.ch/projects/0001" },
            |  "rdfs:label" : "test",
            |  "@context" : {
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
            |  }
            |}""".stripMargin
        ZIO.scoped {
          val injected = p.injectOrderIndices(singleValueJsonLd)
          for {
            model    <- ModelOps.fromJsonLd(injected)
            resource <- ZIO.fromEither(model.singleRootResource)
            shortcode = Shortcode.unsafeFrom("0001")
            result   <- p.extractValues(resource, shortcode).mapError(msg => new RuntimeException(msg))
          } yield {
            val values = result.values.head
            assertTrue(
              values.size == 1,
              values.head.orderHint.isEmpty,
              values.head.valueContent.unescape.valueHasString == "42",
            )
          }
        }
      }
    },
  )

  override val spec = suite("ValueOrderingSpec")(
    injectOrderIndicesSuite,
    orderIndexRoundTripSuite,
    extractValuesSuite,
  ).provide(
    AdministrativePermissionRepoInMemory.layer,
    AdministrativePermissionService.layer,
    ApiComplexV2JsonLdRequestParser.layer,
    GroupService.layer,
    IriConverter.layer,
    IriService.layer,
    KnoraGroupRepoInMemory.layer,
    KnoraGroupService.layer,
    KnoraProjectRepoInMemory.layer,
    KnoraProjectService.layer,
    KnoraUserRepoInMemory.layer,
    KnoraUserService.layer,
    KnoraUserToUserConverter.layer,
    LicenseRepo.layer,
    MessageRelayLive.layer,
    OntologyRepoInMemory.emptyLayer,
    PasswordService.layer,
    ProjectService.layer,
    SipiServiceMock.layer,
    StringFormatter.test,
    TriplestoreServiceInMemory.emptyLayer,
    UserService.layer,
    AppConfig.layer,
  )
}
