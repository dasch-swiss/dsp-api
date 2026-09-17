/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
 * To regenerate golden files without editing spec source, run the rewrite via the GOLDEN_REWRITE
 * environment variable, e.g.:
 *   bazel test <target> --test_filter='.*<Spec>.*' --test_env=GOLDEN_REWRITE=1
 * This run fails by design (see assertNever below). Follow it with a clean rerun without the env
 * variable, which must pass.
 *
 * GOLDEN_REWRITE only affects specs in modules that depend on //modules/testkit (test-it, test-e2e).
 * modules/webapi and modules/sparql-builder each have their own same-named GoldenTest without it.
 *
 * Placeholder rule for NEW golden cases: Bazel runfiles entries are symlinks to the source files, so
 * a rewrite of an already-existing golden file lands in the source tree. A rewrite of a file that does
 * not exist yet instead creates a plain file in the runfiles tree, which is discarded after the test
 * run. Therefore, before running the rewrite for a new golden case, create an empty placeholder file
 * at the expected path under src/test/resources/... first. After the rewrite run, use git status to
 * confirm the source files actually changed.
 *
 * Use git diff or the test output to inspect the differences and either update the standard or update the code.
 *
 * Beware: a watch-mode run with "rewrite = true" will loop, if the output keeps changing.
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

    if (rewrite || rewriteAll || sys.env.get("GOLDEN_REWRITE").exists(_.nonEmpty)) {
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
