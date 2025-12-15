/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

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
 * Beware: a test run with the "~" prefix in sbt and "rewrite = true" will loop, if the output keeps changing.
 */
trait GoldenTest {
  val rewriteAll: Boolean = false

  // NOTE: adding an implicit en-/decoding interface is advisable

  inline def assertGolden(
    actual: String,
    suffix: String, // NOTE: as of right now, adding a default breaks the macros, so no default
    rewrite: Boolean = false,
  ): TestResult = {
    val (name, store)            = GoldenTest.goldenPath(suffix)
    val path                     = Paths.get(store)
    val expected: Option[String] = Option.when(Files.exists(path)) {
      new String(Files.readAllBytes(path), "UTF-8")
    }

    if (rewrite || rewriteAll) {
      // NOTE: this should prevent infinite loops, if the output is stable
      if (expected != Some(actual)) Files.write(path, actual.getBytes("UTF-8")): Unit

      assertNever(s"[GoldenTest] Rewritten: $path")
    } else {
      if (expected.isEmpty) {
        assertNever(s"[GoldenTest] File not found: $path (to create, set rewrite = true)")
      } else {
        assertTrue(expected == Some(actual)).label(s"[GoldenTest] Failed for $name (to override, set rewrite = true)")
      }
    }
  }
}

object GoldenTest {
  inline def goldenPath(suffix: String): (String, String) = ${ goldenPathImpl('suffix) }

  // NOTE: the suffixExpr.valueOrAbort was failing in the IDE, so ALL `.get`s were eliminated in the macro code

  private def goldenPathImpl(suffixExpr: Expr[String])(using q: Quotes) =
    import q.reflect.*

    val absPath: Option[Path] = Position.ofMacroExpansion.sourceFile.getJPath

    val suffix          = suffixExpr.value.getOrElse("")
    val suffixDefaulted = if (suffix == "") "" else s"__$suffix"

    val baseName = absPath.map(_.getFileName.toString).getOrElse("").stripSuffix(".scala") // Demo
    val name     = s"${baseName}${suffixDefaulted}"
    val outPath  =
      s"${absPath.map(_.getParent).getOrElse("")}/$name.txt"
        .pipe(_.replace("/src/test/scala/", "/src/test/resources/"))

    Files.createDirectories(Paths.get(outPath).getParent)

    Expr((name, outPath))
}
