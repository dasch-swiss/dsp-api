/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

import zio.test.*

object ManifestPathEncodingSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Any] = suite("ManifestPathEncodingSpec")(
    suite("encode")(
      test("encodes percent sign as %25") {
        assertTrue(ManifestPathEncoding.encode("100%done.txt") == "100%25done.txt")
      },
      test("encodes LF as %0A") {
        assertTrue(ManifestPathEncoding.encode("line\nbreak.txt") == "line%0Abreak.txt")
      },
      test("encodes CR as %0D") {
        assertTrue(ManifestPathEncoding.encode("carriage\rreturn.txt") == "carriage%0Dreturn.txt")
      },
      test("path with no special characters is unchanged") {
        assertTrue(ManifestPathEncoding.encode("data/normal-file.txt") == "data/normal-file.txt")
      },
      test("encodes multiple special characters") {
        assertTrue(ManifestPathEncoding.encode("50%\ndata.txt") == "50%25%0Adata.txt")
      },
    ),
    suite("decode")(
      test("decodes %25 to percent sign") {
        assertTrue(ManifestPathEncoding.decode("100%25done.txt") == "100%done.txt")
      },
      test("decodes %0A to LF") {
        assertTrue(ManifestPathEncoding.decode("line%0Abreak.txt") == "line\nbreak.txt")
      },
      test("decodes %0D to CR") {
        assertTrue(ManifestPathEncoding.decode("carriage%0Dreturn.txt") == "carriage\rreturn.txt")
      },
      test("path with no encoding is unchanged") {
        assertTrue(ManifestPathEncoding.decode("data/normal-file.txt") == "data/normal-file.txt")
      },
      test("%2F is preserved literally, not decoded to '/'") {
        assertTrue(ManifestPathEncoding.decode("data%2Ffile.txt") == "data%2Ffile.txt")
      },
      test("%20 is preserved literally, not decoded to space") {
        assertTrue(ManifestPathEncoding.decode("my%20file.txt") == "my%20file.txt")
      },
      test("%2E%2E is preserved literally, not decoded to '..'") {
        assertTrue(ManifestPathEncoding.decode("%2E%2E/etc/passwd") == "%2E%2E/etc/passwd")
      },
      test("%00 is preserved literally, not decoded to null byte") {
        assertTrue(ManifestPathEncoding.decode("file%00.txt") == "file%00.txt")
      },
    ),
    suite("round-trip")(
      test("encode then decode preserves original for strings with LF, CR, and percent") {
        val original = "50% off\nspecial\rfile.txt"
        val encoded  = ManifestPathEncoding.encode(original)
        val decoded  = ManifestPathEncoding.decode(encoded)
        assertTrue(decoded == original)
      },
    ),
  )
}
