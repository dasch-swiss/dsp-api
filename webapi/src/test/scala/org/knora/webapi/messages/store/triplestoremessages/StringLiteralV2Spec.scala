/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.store.triplestoremessages

import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.test.*

import org.knora.webapi.slice.common.domain.LanguageCode

/**
 * Tests for [[StringLiteralV2]] JSON serialization/deserialization and schema.
 */
object StringLiteralV2Spec extends ZIOSpecDefault {

  def spec: Spec[Any, Any] = suite("StringLiteralV2Spec")(
    jsonEncodingTests,
    jsonDecodingTests,
    roundTripTests,
    invalidJsonTests,
    factoryMethodTests,
    orderingTests,
  )

  private val jsonEncodingTests = suite("JSON encoding")(
    test("PlainStringLiteralV2 should encode to JSON without language tag") {
      val literal: StringLiteralV2 = PlainStringLiteralV2("Hello World")
      val actualJson               = literal.toJson
      val expectedJson             = """{"value":"Hello World"}"""
      for {
        actual   <- ZIO.from(actualJson.fromJson[Json])
        expected <- ZIO.from(expectedJson.fromJson[Json])
      } yield assertTrue(actual == expected)
    },
    test("LanguageTaggedStringLiteralV2 should encode to JSON with language tag") {
      val literal: StringLiteralV2 = LanguageTaggedStringLiteralV2("Bonjour", LanguageCode.FR)
      val actualJson               = literal.toJson
      val expectedJson             = """{"value":"Bonjour","language":"fr"}"""
      for {
        actual   <- ZIO.from(actualJson.fromJson[Json])
        expected <- ZIO.from(expectedJson.fromJson[Json])
      } yield assertTrue(actual == expected)
    },
    test("PlainStringLiteralV2 should encode with correct structure") {
      val literal: StringLiteralV2 = PlainStringLiteralV2("Test")
      val actualJson               = literal.toJson
      val expectedJson             = """{"value":"Test"}"""
      for {
        actual   <- ZIO.from(actualJson.fromJson[Json])
        expected <- ZIO.from(expectedJson.fromJson[Json])
      } yield assertTrue(actual == expected)
    },
    test("LanguageTaggedStringLiteralV2 should encode with correct structure") {
      val literal: StringLiteralV2 = LanguageTaggedStringLiteralV2("Test", LanguageCode.EN)
      val actualJson               = literal.toJson
      val expectedJson             = """{"value":"Test","language":"en"}"""
      for {
        actual   <- ZIO.from(actualJson.fromJson[Json])
        expected <- ZIO.from(expectedJson.fromJson[Json])
      } yield assertTrue(actual == expected)
    },
  )

  private val jsonDecodingTests = suite("JSON decoding")(
    test("should decode PlainStringLiteralV2 from JSON") {
      val json = """{"value":"Hello World"}"""
      for {
        decoded <- ZIO.from(json.fromJson[StringLiteralV2])
      } yield assertTrue(
        decoded == PlainStringLiteralV2("Hello World"),
        decoded.value == "Hello World",
        decoded.languageOption.isEmpty,
      )
    },
    test("should decode LanguageTaggedStringLiteralV2 from JSON") {
      val json = """{"value":"Bonjour","language":"fr"}"""
      for {
        decoded <- ZIO.from(json.fromJson[StringLiteralV2])
      } yield assertTrue(
        decoded == LanguageTaggedStringLiteralV2("Bonjour", LanguageCode.FR),
        decoded.value == "Bonjour",
        decoded.languageOption.contains(LanguageCode.FR),
      )
    },
    test("should decode multiple language codes correctly") {
      val testCases = List(
        ("""{"value":"Hello","language":"en"}""", LanguageCode.EN),
        ("""{"value":"Hallo","language":"de"}""", LanguageCode.DE),
        ("""{"value":"Bonjour","language":"fr"}""", LanguageCode.FR),
        ("""{"value":"Ciao","language":"it"}""", LanguageCode.IT),
        ("""{"value":"Allegra","language":"rm"}""", LanguageCode.RM),
      )
      ZIO
        .foreach(testCases) { case (json, expectedLang) =>
          ZIO.from(json.fromJson[StringLiteralV2]).map { decoded =>
            assertTrue(
              decoded.languageOption.contains(expectedLang),
              decoded.isInstanceOf[LanguageTaggedStringLiteralV2],
            )
          }
        }
        .map(assertions => assertions.reduce(_ && _))
    },
  )

  private val roundTripTests = suite("round-trip serialization")(
    test("PlainStringLiteralV2 should survive round-trip") {
      val original: StringLiteralV2 = PlainStringLiteralV2("Test string")
      for {
        json    <- ZIO.succeed(original.toJson)
        decoded <- ZIO.from(json.fromJson[StringLiteralV2])
      } yield assertTrue(decoded == original)
    },
    test("LanguageTaggedStringLiteralV2 should survive round-trip") {
      val original: StringLiteralV2 = LanguageTaggedStringLiteralV2("Test string", LanguageCode.DE)
      for {
        json    <- ZIO.succeed(original.toJson)
        decoded <- ZIO.from(json.fromJson[StringLiteralV2])
      } yield assertTrue(decoded == original)
    },
    test("should handle strings with special characters") {
      val testStrings = List(
        "String with \"quotes\"",
        "String with\nnewlines",
        "String with\ttabs",
        "String with unicode: \u00E9\u00F1\u00FC",
        "String with backslash: \\",
      )
      ZIO
        .foreach(testStrings) { str =>
          val original: StringLiteralV2 = PlainStringLiteralV2(str)
          for {
            json    <- ZIO.succeed(original.toJson)
            decoded <- ZIO.from(json.fromJson[StringLiteralV2])
          } yield assertTrue(decoded.value == str)
        }
        .map(assertions => assertions.reduce(_ && _))
    },
    test("should handle empty string") {
      val original: StringLiteralV2 = PlainStringLiteralV2("")
      for {
        json    <- ZIO.succeed(original.toJson)
        decoded <- ZIO.from(json.fromJson[StringLiteralV2])
      } yield assertTrue(decoded == original, decoded.value == "")
    },
  )

  private val invalidJsonTests = suite("invalid JSON handling")(
    test("should fail to decode malformed JSON") {
      val invalidJson = """{"invalid": "structure"}"""
      val result      = invalidJson.fromJson[StringLiteralV2]
      assertTrue(result.isLeft)
    },
    test("should fail to decode JSON with missing value field") {
      val invalidJson = """{}"""
      val result      = invalidJson.fromJson[StringLiteralV2]
      assertTrue(result.isLeft)
    },
    test("should fail to decode with invalid language code") {
      val invalidJson = """{"value":"test","language":"invalid"}"""
      val result      = invalidJson.fromJson[StringLiteralV2]
      assertTrue(result.isLeft)
    },
  )

  private val factoryMethodTests = suite("factory methods")(
    test("from(value) should create PlainStringLiteralV2") {
      val literal = StringLiteralV2.from("test")
      assertTrue(
        literal.isInstanceOf[PlainStringLiteralV2],
        literal.value == "test",
        literal.languageOption.isEmpty,
      )
    },
    test("from(value, language) should create LanguageTaggedStringLiteralV2") {
      val literal = StringLiteralV2.from("test", LanguageCode.EN)
      assertTrue(
        literal.isInstanceOf[LanguageTaggedStringLiteralV2],
        literal.value == "test",
        literal.languageOption.contains(LanguageCode.EN),
      )
    },
    test("from(value, Some(language)) should create LanguageTaggedStringLiteralV2") {
      for {
        literal <- ZIO.from(StringLiteralV2.from("test", Some("en")))
      } yield assertTrue(
        literal.isInstanceOf[LanguageTaggedStringLiteralV2],
        literal.value == "test",
        literal.languageOption.contains(LanguageCode.EN),
      )
    },
    test("from(value, None) should create PlainStringLiteralV2") {
      for {
        literal <- ZIO.from(StringLiteralV2.from("test", None))
      } yield assertTrue(
        literal.isInstanceOf[PlainStringLiteralV2],
        literal.value == "test",
        literal.languageOption.isEmpty,
      )
    },
    test("from(value, Some(invalid)) should return Left") {
      val result = StringLiteralV2.from("test", Some("invalid"))
      assertTrue(result.isLeft)
    },
    test("unsafeFrom should create literals successfully") {
      val plain  = StringLiteralV2.unsafeFrom("test", None)
      val tagged = StringLiteralV2.unsafeFrom("test", Some("de"))
      assertTrue(
        plain.isInstanceOf[PlainStringLiteralV2],
        tagged.isInstanceOf[LanguageTaggedStringLiteralV2],
      )
    },
  )

  private val orderingTests = suite("ordering and comparison")(
    test("should compare StringLiteralV2 by value") {
      val a = PlainStringLiteralV2("apple")
      val b = PlainStringLiteralV2("banana")
      assertTrue(a.compare(b) < 0, b.compare(a) > 0, a.compare(a) == 0)
    },
    test("should order by value using implicit ordering") {
      val literals = List(
        PlainStringLiteralV2("zebra"),
        LanguageTaggedStringLiteralV2("apple", LanguageCode.EN),
        PlainStringLiteralV2("mango"),
      )
      val sorted = literals.sorted(StringLiteralV2.orderByValue)
      assertTrue(
        sorted(0).value == "apple",
        sorted(1).value == "mango",
        sorted(2).value == "zebra",
      )
    },
    test("should order by language code") {
      val literals = List(
        LanguageTaggedStringLiteralV2("test", LanguageCode.FR),
        PlainStringLiteralV2("test"),
        LanguageTaggedStringLiteralV2("test", LanguageCode.DE),
        LanguageTaggedStringLiteralV2("test", LanguageCode.EN),
      )
      val sorted = literals.sorted(StringLiteralV2.orderByLanguage)
      assertTrue(
        sorted(0).languageOption.isEmpty, // PlainStringLiteralV2 (empty string) comes first
        sorted(1).languageOption.contains(LanguageCode.DE),
        sorted(2).languageOption.contains(LanguageCode.EN),
        sorted(3).languageOption.contains(LanguageCode.FR),
      )
    },
    test("toString should return value") {
      val plain  = PlainStringLiteralV2("test")
      val tagged = LanguageTaggedStringLiteralV2("test", LanguageCode.EN)
      assertTrue(
        plain.toString == "test",
        tagged.toString == "test",
      )
    },
  )
}
