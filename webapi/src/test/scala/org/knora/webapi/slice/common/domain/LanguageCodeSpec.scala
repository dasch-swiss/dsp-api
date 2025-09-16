/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.domain

import zio.*
import zio.json.*
import zio.test.*

object LanguageCodeSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Any] = suite("LanguageCodeSpec")(
    test("from should create LanguageCode from valid string") {
      for {
        de <- ZIO.from(LanguageCode.from("de"))
        en <- ZIO.from(LanguageCode.from("en"))
        fr <- ZIO.from(LanguageCode.from("fr"))
        it <- ZIO.from(LanguageCode.from("it"))
        rm <- ZIO.from(LanguageCode.from("rm"))
      } yield assertTrue(
        de == LanguageCode.DE,
        en == LanguageCode.EN,
        fr == LanguageCode.FR,
        it == LanguageCode.IT,
        rm == LanguageCode.RM,
      )
    },
    test("from should return Left for invalid string") {
      val result = LanguageCode.from("invalid")
      assertTrue(
        result.isLeft,
        result.left.getOrElse("") == "Unsupported language code: invalid, supported codes are: DE, EN, FR, IT, RM",
      )
    },
    test("isSupported should return true for valid codes") {
      assertTrue(
        LanguageCode.isSupported("de"),
        LanguageCode.isSupported("en"),
        LanguageCode.isSupported("fr"),
        LanguageCode.isSupported("it"),
        LanguageCode.isSupported("rm"),
      )
    },
    test("isSupported should return false for invalid codes") {
      assertTrue(
        !LanguageCode.isSupported("invalid"),
        !LanguageCode.isSupported("es"),
        !LanguageCode.isSupported(""),
        !LanguageCode.isSupported("DE"), // case-sensitive
      )
    },
    test("isNotSupported should return false for valid codes") {
      assertTrue(
        !LanguageCode.isNotSupported("de"),
        !LanguageCode.isNotSupported("en"),
        !LanguageCode.isNotSupported("fr"),
        !LanguageCode.isNotSupported("it"),
        !LanguageCode.isNotSupported("rm"),
      )
    },
    test("isNotSupported should return true for invalid codes") {
      assertTrue(
        LanguageCode.isNotSupported("invalid"),
        LanguageCode.isNotSupported("es"),
        LanguageCode.isNotSupported(""),
      )
    },
    test("value should return correct string representation") {
      assertTrue(
        LanguageCode.DE.value == "de",
        LanguageCode.EN.value == "en",
        LanguageCode.FR.value == "fr",
        LanguageCode.IT.value == "it",
        LanguageCode.RM.value == "rm",
      )
    },
    test("JSON encoding should work correctly") {
      val json = LanguageCode.DE.toJson
      assertTrue(json == "\"de\"")
    },
    test("JSON decoding should work correctly") {
      for {
        decoded <- ZIO.from("\"de\"".fromJson[LanguageCode])
      } yield assertTrue(decoded == LanguageCode.DE)
    },
    test("JSON decoding should fail for invalid codes") {
      val result = "\"invalid\"".fromJson[LanguageCode]
      assertTrue(result.isLeft)
    },
    test("all enum values should have correct string representations") {
      assertTrue(
        LanguageCode.values.forall(lc => LanguageCode.from(lc.value).contains(lc)),
      )
    },
  )
}
