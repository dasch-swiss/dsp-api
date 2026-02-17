/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

import zio.test.*

import org.knora.bagit.domain.PayloadPath

object ManifestParserSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Any] = suite("ManifestParserSpec")(
    test("parses valid line with two-space separator") {
      val result = ManifestParser.parseLine("abcdef1234567890  data/file.txt")
      assertTrue(
        result.toOption.map(_.checksum).contains("abcdef1234567890"),
        PayloadPath.value(result.toOption.get.path) == "data/file.txt",
      )
    },
    test("parses valid line with tab separator") {
      val result = ManifestParser.parseLine("abcdef1234567890\tdata/file.txt")
      assertTrue(result.toOption.map(_.checksum).contains("abcdef1234567890"))
    },
    test("rejects invalid hex checksum") {
      val result = ManifestParser.parseLine("not-hex  data/file.txt")
      assertTrue(result.isLeft)
    },
    test("rejects empty path") {
      val result = ManifestParser.parseLine("abcdef1234567890  ")
      assertTrue(result.isLeft)
    },
    test("parseAll skips empty lines") {
      val lines  = List("abcdef1234567890  data/file.txt", "", "  ", "1234567890abcdef  data/other.txt")
      val result = ManifestParser.parseAll(lines)
      assertTrue(result.toOption.map(_.length).contains(2))
    },
    test("lowercase normalizes checksums") {
      val result = ManifestParser.parseLine("ABCDEF1234567890  data/file.txt")
      assertTrue(result.toOption.get.checksum == "abcdef1234567890")
    },
    test("decodes %25 in path to percent sign") {
      val result = ManifestParser.parseLine("abcdef1234567890  data/100%25done.txt")
      assertTrue(result.isRight, PayloadPath.value(result.toOption.get.path) == "data/100%done.txt")
    },
    test("decodes %0A in path to LF character") {
      val result = ManifestParser.parseLine("abcdef1234567890  data/line%0Abreak.txt")
      assertTrue(result.isRight, PayloadPath.value(result.toOption.get.path) == "data/line\nbreak.txt")
    },
    test("decodes %0D in path to CR character") {
      val result = ManifestParser.parseLine("abcdef1234567890  data/carriage%0Dreturn.txt")
      assertTrue(result.isRight, PayloadPath.value(result.toOption.get.path) == "data/carriage\rreturn.txt")
    },
  )
}
