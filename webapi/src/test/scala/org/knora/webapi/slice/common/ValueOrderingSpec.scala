/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import org.apache.jena.vocabulary.RDF
import zio.*
import zio.json.ast.Json
import zio.test.*

import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelayLive
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.ValueAsString
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateValueInNewResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.IntegerValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueType.UnformattedText
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
import org.knora.webapi.slice.common.jena.StatementOps.*
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoInMemory
import org.knora.webapi.store.iiif.impl.SipiServiceMock
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object ValueOrderingSpec extends ZIOSpecDefault {
  private val sf = StringFormatter.getInitializedTestInstance

  private val parser = ZIO.serviceWithZIO[ApiComplexV2JsonLdRequestParser]

  private def mkTextValue(text: String): CreateValueInNewResourceV2 =
    CreateValueInNewResourceV2(
      valueContent = TextValueContentV2(
        ontologySchema = ApiV2Complex,
        maybeValueHasString = Some(text),
        textValueType = UnformattedText,
        valueHasLanguage = None,
        standoff = Nil,
        mappingIri = None,
        mapping = None,
        xslt = None,
        comment = None,
      ),
    )

  private def mkIntValue(n: Int): CreateValueInNewResourceV2 =
    CreateValueInNewResourceV2(
      valueContent = IntegerValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasInteger = n,
        comment = None,
      ),
    )

  private val hasTextIri  = sf.toSmartIri("http://0.0.0.0:3333/ontology/0001/anything/v2#hasText")
  private val hasIntIri   = sf.toSmartIri("http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger")
  private val hasTextFull = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText"
  private val hasIntFull  = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger"

  val extractJsonArrayOrderSuite = suite("extractJsonArrayOrder")(
    test("extracts text values in JSON array order") {
      parser { p =>
        val json =
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
        val result = p.extractJsonArrayOrder(json)
        ZIO.succeed(
          assertTrue(
            result.get(hasTextFull).contains(Seq("Echo", "Alpha", "Delta")),
          ),
        )
      }
    },
    test("extracts integer values in JSON array order") {
      parser { p =>
        val json =
          """{
            |  "@type" : "anything:Thing",
            |  "anything:hasInteger" : [
            |    { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 5 },
            |    { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 3 },
            |    { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 1 }
            |  ],
            |  "knora-api:attachedToProject" : { "@id" : "http://rdfh.ch/projects/0001" },
            |  "rdfs:label" : "test",
            |  "@context" : {
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
            |  }
            |}""".stripMargin
        val result = p.extractJsonArrayOrder(json)
        ZIO.succeed(
          assertTrue(
            result.get(hasIntFull).contains(Seq("5", "3", "1")),
          ),
        )
      }
    },
    test("extracts nothing for single value (no array)") {
      parser { p =>
        val json =
          """{
            |  "@type" : "anything:Thing",
            |  "anything:hasText" : { "@type" : "knora-api:TextValue", "knora-api:valueAsString" : "single" },
            |  "knora-api:attachedToProject" : { "@id" : "http://rdfh.ch/projects/0001" },
            |  "rdfs:label" : "test",
            |  "@context" : {
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
            |  }
            |}""".stripMargin
        val result = p.extractJsonArrayOrder(json)
        ZIO.succeed(assertTrue(result.get(hasTextFull).isEmpty))
      }
    },
  )

  val extractMatchingStringSuite = suite("extractMatchingString")(
    test("matches valueAsString for TextValue") {
      parser { p =>
        val context = Map("knora-api" -> "http://api.knora.org/ontology/knora-api/v2#")
        val fields  = Chunk(("@type", Json.Str("knora-api:TextValue")), ("knora-api:valueAsString", Json.Str("hello")))
        val result  = p.extractMatchingString(fields, context)
        ZIO.succeed(assertTrue(result == Some("hello")))
      }
    },
    test("matches intValueAsInt for IntValue") {
      parser { p =>
        val context = Map("knora-api" -> "http://api.knora.org/ontology/knora-api/v2#")
        val fields  = Chunk(("@type", Json.Str("knora-api:IntValue")), ("knora-api:intValueAsInt", Json.Num(42)))
        val result  = p.extractMatchingString(fields, context)
        ZIO.succeed(assertTrue(result == Some("42")))
      }
    },
    test("matches colorValueAsColor for ColorValue") {
      parser { p =>
        val context = Map("knora-api" -> "http://api.knora.org/ontology/knora-api/v2#")
        val fields  = Chunk(("knora-api:colorValueAsColor", Json.Str("#ff0000")))
        val result  = p.extractMatchingString(fields, context)
        ZIO.succeed(assertTrue(result == Some("#ff0000")))
      }
    },
    test("matches uriValueAsUri for UriValue (typed literal)") {
      parser { p =>
        val context  = Map("knora-api" -> "http://api.knora.org/ontology/knora-api/v2#")
        val innerObj = Json.Obj(("@type", Json.Str("xsd:anyURI")), ("@value", Json.Str("http://example.org")))
        val fields   = Chunk(("knora-api:uriValueAsUri", innerObj))
        val result   = p.extractMatchingString(fields, context)
        ZIO.succeed(assertTrue(result == Some("http://example.org")))
      }
    },
    test("matches linkValueHasTargetIri for LinkValue (IRI reference)") {
      parser { p =>
        val context  = Map("knora-api" -> "http://api.knora.org/ontology/knora-api/v2#")
        val innerObj = Json.Obj(("@id", Json.Str("http://rdfh.ch/0001/target")))
        val fields   = Chunk(("knora-api:linkValueHasTargetIri", innerObj))
        val result   = p.extractMatchingString(fields, context)
        ZIO.succeed(assertTrue(result == Some("http://rdfh.ch/0001/target")))
      }
    },
    test("matches booleanValueAsBoolean for BooleanValue") {
      parser { p =>
        val context = Map("knora-api" -> "http://api.knora.org/ontology/knora-api/v2#")
        val fields  = Chunk(("knora-api:booleanValueAsBoolean", Json.Bool(true)))
        val result  = p.extractMatchingString(fields, context)
        ZIO.succeed(assertTrue(result == Some("true")))
      }
    },
    test("returns None for unknown fields") {
      parser { p =>
        val context = Map("knora-api" -> "http://api.knora.org/ontology/knora-api/v2#")
        val fields  = Chunk(("@type", Json.Str("something")), ("unknownField", Json.Str("value")))
        val result  = p.extractMatchingString(fields, context)
        ZIO.succeed(assertTrue(result == None))
      }
    },
  )

  val reorderByJsonArrayOrderSuite = suite("reorderByJsonArrayOrder")(
    test("reorders text values to match JSON order and sets orderHint") {
      parser { p =>
        // Simulate Jena's scrambled order: alphabetical
        val grouped = Map(
          hasTextIri -> Seq(mkTextValue("Alpha"), mkTextValue("Bravo"), mkTextValue("Charlie")),
        )
        // JSON had them in reverse order
        val jsonOrder = Map(hasTextFull -> Seq("Charlie", "Bravo", "Alpha"))

        for {
          result <- p.reorderByJsonArrayOrder(grouped, jsonOrder)
        } yield {
          val values = result(hasTextIri)
          assertTrue(
            values.map(_.valueContent.unescape.valueHasString) == Seq("Charlie", "Bravo", "Alpha"),
            values.map(_.orderHint) == Seq(Some(0), Some(1), Some(2)),
          )
        }
      }
    },
    test("reorders integer values to match JSON order and sets orderHint") {
      parser { p =>
        // Simulate Jena's scrambled order: sorted ascending
        val grouped = Map(
          hasIntIri -> Seq(mkIntValue(1), mkIntValue(2), mkIntValue(3), mkIntValue(4), mkIntValue(5)),
        )
        // JSON had them in reverse order
        val jsonOrder = Map(hasIntFull -> Seq("5", "3", "1", "4", "2"))

        for {
          result <- p.reorderByJsonArrayOrder(grouped, jsonOrder)
        } yield {
          val values = result(hasIntIri)
          assertTrue(
            values.map(_.valueContent.unescape.valueHasString) == Seq("5", "3", "1", "4", "2"),
            values.map(_.orderHint) == Seq(Some(0), Some(1), Some(2), Some(3), Some(4)),
          )
        }
      }
    },
    test("handles duplicate text values preserving order") {
      parser { p =>
        val grouped   = Map(hasTextIri -> Seq(mkTextValue("same"), mkTextValue("same"), mkTextValue("different")))
        val jsonOrder = Map(hasTextFull -> Seq("same", "different", "same"))

        for {
          result <- p.reorderByJsonArrayOrder(grouped, jsonOrder)
        } yield {
          val values = result(hasTextIri)
          assertTrue(
            values.map(_.valueContent.unescape.valueHasString) == Seq("same", "different", "same"),
            values.map(_.orderHint) == Seq(Some(0), Some(1), Some(2)),
          )
        }
      }
    },
    test("preserves original order and sets orderHint when property not in jsonOrder") {
      parser { p =>
        val grouped   = Map(hasTextIri -> Seq(mkTextValue("A"), mkTextValue("B")))
        val jsonOrder = Map.empty[String, Seq[String]]

        for {
          result <- p.reorderByJsonArrayOrder(grouped, jsonOrder)
        } yield {
          val values = result(hasTextIri)
          assertTrue(
            values.map(_.valueContent.unescape.valueHasString) == Seq("A", "B"),
            // orderHint is set by position even in fallback case
            values.map(_.orderHint) == Seq(Some(0), Some(1)),
          )
        }
      }
    },
    test("sets orderHint even when all matching fails (values in remaining)") {
      parser { p =>
        val grouped   = Map(hasTextIri -> Seq(mkTextValue("X"), mkTextValue("Y")))
        val jsonOrder = Map(hasTextFull -> Seq("nonexistent1", "nonexistent2"))

        for {
          result <- p.reorderByJsonArrayOrder(grouped, jsonOrder)
        } yield {
          val values = result(hasTextIri)
          assertTrue(
            // originals are preserved in remaining
            values.map(_.valueContent.unescape.valueHasString) == Seq("X", "Y"),
            // orderHint is still set based on position
            values.map(_.orderHint) == Seq(Some(0), Some(1)),
          )
        }
      }
    },
    test("IRI mismatch between jsonOrder key and grouped key causes fallback") {
      parser { p =>
        val grouped = Map(hasTextIri -> Seq(mkTextValue("A"), mkTextValue("B"), mkTextValue("C")))
        // Use a WRONG property IRI key (different path) to simulate total mismatch
        val jsonOrder = Map("http://WRONG/property#hasText" -> Seq("C", "B", "A"))

        for {
          result <- p.reorderByJsonArrayOrder(grouped, jsonOrder)
        } yield {
          val values = result(hasTextIri)
          assertTrue(
            // order is NOT changed because the property IRI path didn't match
            values.map(_.valueContent.unescape.valueHasString) == Seq("A", "B", "C"),
            // orderHint is still set by position in the fallback case
            values.map(_.orderHint) == Seq(Some(0), Some(1), Some(2)),
          )
        }
      }
    },
    test("host-only IRI mismatch resolved by path-based fallback matching") {
      parser { p =>
        val grouped = Map(hasTextIri -> Seq(mkTextValue("Alpha"), mkTextValue("Bravo"), mkTextValue("Charlie")))
        // jsonOrder key has a DIFFERENT HOST but the SAME PATH as hasTextIri
        // hasTextIri.toString = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText"
        // This simulates what happens on a dev server where SmartIri uses one host and the JSON-LD context uses another
        val jsonOrder =
          Map("http://localhost:3333/ontology/0001/anything/v2#hasText" -> Seq("Charlie", "Bravo", "Alpha"))

        for {
          result <- p.reorderByJsonArrayOrder(grouped, jsonOrder)
        } yield {
          val values = result(hasTextIri)
          assertTrue(
            // values ARE reordered because path-based fallback matching succeeds
            values.map(_.valueContent.unescape.valueHasString) == Seq("Charlie", "Bravo", "Alpha"),
            values.map(_.orderHint) == Seq(Some(0), Some(1), Some(2)),
          )
        }
      }
    },
  )

  val expandCompactIriSuite = suite("expandCompactIri")(
    test("expands compact IRI with known prefix") {
      parser { p =>
        val context = Map("knora-api" -> "http://api.knora.org/ontology/knora-api/v2#")
        val result  = p.expandCompactIri("knora-api:intValueAsInt", context)
        ZIO.succeed(assertTrue(result == "http://api.knora.org/ontology/knora-api/v2#intValueAsInt"))
      }
    },
    test("returns compact IRI unchanged when prefix not in context") {
      parser { p =>
        val result = p.expandCompactIri("unknown:something", Map.empty)
        ZIO.succeed(assertTrue(result == "unknown:something"))
      }
    },
    test("returns IRI unchanged when no colon present") {
      parser { p =>
        val result = p.expandCompactIri("noPrefix", Map.empty)
        ZIO.succeed(assertTrue(result == "noPrefix"))
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

  /**
   * These tests verify the integration between Jena's JSON-LD parsing and our ordering logic.
   * They parse real JSON-LD with Jena, create SmartIris from the predicate URIs, and verify
   * that the IRIs match the keys produced by extractJsonArrayOrder. This catches any IRI
   * normalization mismatch between Jena and zio-json parsing.
   */
  val jenaIntegrationSuite = suite("Jena integration - IRI matching and full reordering chain")(
    test("Jena-parsed property IRI matches extractJsonArrayOrder key") {
      parser { p =>
        val jsonOrder = p.extractJsonArrayOrder(integerJsonLd)
        ZIO.scoped {
          for {
            model           <- ModelOps.fromJsonLd(integerJsonLd)
            resource        <- ZIO.fromEither(model.singleRootResource)
            jenaPropertyUris = resource
                                 .listProperties()
                                 .asScala
                                 .map(_.getPredicate.getURI)
                                 .filter(uri =>
                                   !Set(
                                     RDF.`type`.getURI,
                                     "http://www.w3.org/2000/01/rdf-schema#label",
                                     "http://api.knora.org/ontology/knora-api/v2#attachedToProject",
                                   ).contains(uri),
                                 )
                                 .toSet
          } yield {
            // The Jena-parsed property URIs must match the keys from extractJsonArrayOrder
            assertTrue(
              jenaPropertyUris.nonEmpty,
              jenaPropertyUris.forall(uri => jsonOrder.contains(uri)),
              jsonOrder.keys.forall(key => jenaPropertyUris.contains(key)),
            )
          }
        }
      }
    },
    test("SmartIri.toString for Jena-parsed property IRI matches extractJsonArrayOrder key") {
      parser { p =>
        val jsonOrder = p.extractJsonArrayOrder(integerJsonLd)
        ZIO.scoped {
          for {
            model     <- ModelOps.fromJsonLd(integerJsonLd)
            resource  <- ZIO.fromEither(model.singleRootResource)
            converter <- ZIO.service[IriConverter]
            jenaStmts  = resource
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
            smartIris <- ZIO.foreach(jenaStmts)(s => converter.asSmartIri(s.predicateUri))
          } yield {
            val smartIriStrings = smartIris.map(_.toString).toSet
            assertTrue(
              smartIriStrings.nonEmpty,
              // Every SmartIri.toString must exist as a key in jsonOrder
              smartIriStrings.forall(iriStr => jsonOrder.contains(iriStr)),
            )
          }
        }
      }
    },
    test("full Jena parsing chain: parse JSON-LD, extract integers, reorder by JSON order") {
      parser { p =>
        val jsonOrder = p.extractJsonArrayOrder(integerJsonLd)
        ZIO.scoped {
          for {
            model     <- ModelOps.fromJsonLd(integerJsonLd)
            resource  <- ZIO.fromEither(model.singleRootResource)
            converter <- ZIO.service[IriConverter]
            // Get value statements (same filter as extractValues)
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
            // Create SmartIri for property and extract integer value for each statement
            parsed <- ZIO.foreach(valueStmts) { stmt =>
                        for {
                          smartIri     <- converter.asSmartIri(stmt.predicateUri)
                          valueResource = stmt.getObject.asResource()
                          intContent   <- ZIO.fromEither(IntegerValueContentV2.from(valueResource))
                        } yield (smartIri, CreateValueInNewResourceV2(valueContent = intContent))
                      }
            grouped = parsed.groupMap(_._1)(_._2)
            // Reorder using the JSON array order
            result <- p.reorderByJsonArrayOrder(grouped, jsonOrder)
          } yield {
            val values = result.values.head // only one property
            assertTrue(
              // Values must be reordered to match the JSON array order [5, 3, 1, 4, 2]
              values.map(_.valueContent.unescape.valueHasString) == Seq("5", "3", "1", "4", "2"),
              // All values must have orderHint set
              values.map(_.orderHint) == Seq(Some(0), Some(1), Some(2), Some(3), Some(4)),
            )
          }
        }
      }
    },
    test("full Jena parsing chain with text values: parse JSON-LD, extract texts, reorder") {
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
        val jsonOrder = p.extractJsonArrayOrder(textJsonLd)
        ZIO.scoped {
          for {
            model     <- ModelOps.fromJsonLd(textJsonLd)
            resource  <- ZIO.fromEither(model.singleRootResource)
            converter <- ZIO.service[IriConverter]
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
            parsed <- ZIO.foreach(valueStmts) { stmt =>
                        for {
                          smartIri     <- converter.asSmartIri(stmt.predicateUri)
                          valueResource = stmt.getObject.asResource()
                          text         <- ZIO.fromEither(valueResource.objectString(ValueAsString))
                        } yield (
                          smartIri,
                          CreateValueInNewResourceV2(valueContent =
                            TextValueContentV2(
                              ontologySchema = ApiV2Complex,
                              maybeValueHasString = Some(text),
                              textValueType = UnformattedText,
                              valueHasLanguage = None,
                              standoff = Nil,
                              mappingIri = None,
                              mapping = None,
                              xslt = None,
                              comment = None,
                            ),
                          ),
                        )
                      }
            grouped = parsed.groupMap(_._1)(_._2)
            result <- p.reorderByJsonArrayOrder(grouped, jsonOrder)
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
  )

  val extractValuesSuite = suite("extractValues - full pipeline diagnostic")(
    test("integer values: extractValues produces correctly ordered values") {
      parser { p =>
        val jsonOrder = p.extractJsonArrayOrder(integerJsonLd)
        ZIO.scoped {
          for {
            model    <- ModelOps.fromJsonLd(integerJsonLd)
            resource <- ZIO.fromEither(model.singleRootResource)
            shortcode = Shortcode.unsafeFrom("0001")
            result   <- p.extractValues(resource, shortcode, jsonOrder).mapError(msg => new RuntimeException(msg))
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
  )

  val iriPathSuite = suite("iriPath")(
    test("extracts path and fragment from full IRI") {
      parser { p =>
        val result = p.iriPath("http://0.0.0.0:3333/ontology/0001/anything/v2#hasText")
        ZIO.succeed(assertTrue(result == "/ontology/0001/anything/v2#hasText"))
      }
    },
    test("produces same path for different hosts") {
      parser { p =>
        val path1 = p.iriPath("http://0.0.0.0:3333/ontology/0001/anything/v2#hasText")
        val path2 = p.iriPath("http://localhost:3333/ontology/0001/anything/v2#hasText")
        val path3 = p.iriPath("http://api.example.com/ontology/0001/anything/v2#hasText")
        ZIO.succeed(assertTrue(path1 == path2, path2 == path3))
      }
    },
    test("returns original string for non-URI input") {
      parser { p =>
        val result = p.iriPath("not a uri at all")
        ZIO.succeed(assertTrue(result == "not a uri at all"))
      }
    },
  )

  override val spec = suite("ValueOrderingSpec")(
    extractJsonArrayOrderSuite,
    extractMatchingStringSuite,
    reorderByJsonArrayOrderSuite,
    expandCompactIriSuite,
    iriPathSuite,
    jenaIntegrationSuite,
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
