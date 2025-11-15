/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio.test.*

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.quoted.*
import scala.util.chaining.scalaUtilChainingOps

/* Extend your testing Spec object with GoldenTest and use assertGolden("content", "testCaseNr1") to
 * validate its content while enabling automatic updates to the standard with
 * rewrite = true through "assertGolden(..., rewrite = true)" or "rewriteAll = true" in the trait.
 *
 * Use git diff or the test output to inspect the differences and either update the standard or update the code.
 *
 * Beware: running sbt tests with the "~" prefix and rewrite = true will introduce an indefinite loop.
 */
trait GoldenTest {
  val rewriteAll: Boolean = false

  // NOTE: adding an implicit en-/decoding interface is advisable

  inline def assertGolden(
    actual: String,
    suffix: String, // NOTE: as of right now, adding a default breaks the macros, so no default
    rewrite: Boolean = false,
  ): TestResult = {
    val (name, store) = GoldenTest.goldenPath(suffix)
    val path          = Paths.get(store)

    if (rewrite || rewriteAll) {
      Files.write(path, actual.getBytes("UTF-8"))
      assertNever(s"[GoldenTest] Rewritten: $path")
    } else {
      if (!Files.exists(path)) {
        assertNever(s"[GoldenTest] File not found: $path (to create, set rewrite = true)")
      } else {
        val expected = new String(Files.readAllBytes(path), "UTF-8")
        assertTrue(actual == expected).label(s"[GoldenTest] Failed for $name (to override, set rewrite = true)")
      }
    }
  }
}

object GoldenTest {
  inline def goldenPath(suffix: String): (String, String) = ${ goldenPathImpl('suffix) }

  private def goldenPathImpl(suffixExpr: Expr[String])(using q: Quotes) =
    import q.reflect.*

    val absPath: Path = Position.ofMacroExpansion.sourceFile.getJPath.get

    val suffix          = suffixExpr.valueOrAbort
    val suffixDefaulted = if (suffix == "") "" else s"__$suffix"

    val baseName = absPath.getFileName.toString.stripSuffix(".scala") // Demo
    val name     = s"${baseName}${suffixDefaulted}"
    val outPath  =
      s"${absPath.getParent}/$name.txt"
        .pipe(_.replace("/src/test/scala/", "/src/test/resources/"))

    Files.createDirectories(Paths.get(outPath).getParent)

    Expr((name, outPath))
}
