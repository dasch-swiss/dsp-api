/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

import zio.test.*

object PathSecuritySpec extends ZIOSpecDefault {

  def spec: Spec[Any, Any] = suite("PathSecuritySpec")(
    test("rejects '..' path traversal") {
      assertTrue(PathSecurity.validateEntryName("../etc/passwd").isLeft)
    },
    test("rejects embedded '..' path traversal") {
      assertTrue(PathSecurity.validateEntryName("foo/../bar").isLeft)
    },
    test("rejects leading '/'") {
      assertTrue(PathSecurity.validateEntryName("/etc/passwd").isLeft)
    },
    test("rejects leading '~'") {
      assertTrue(PathSecurity.validateEntryName("~/secret").isLeft)
    },
    test("rejects backslashes") {
      assertTrue(PathSecurity.validateEntryName("foo\\bar").isLeft)
    },
    test("rejects Windows drive letters") {
      assertTrue(PathSecurity.validateEntryName("C:file.txt").isLeft)
    },
    test("rejects null bytes") {
      assertTrue(PathSecurity.validateEntryName("file\u0000.txt").isLeft)
    },
    test("accepts valid relative path") {
      assertTrue(PathSecurity.validateEntryName("data/file.txt") == Right("data/file.txt"))
    },
    test("accepts nested valid path") {
      assertTrue(PathSecurity.validateEntryName("data/sub/dir/file.txt") == Right("data/sub/dir/file.txt"))
    },
  )
}
