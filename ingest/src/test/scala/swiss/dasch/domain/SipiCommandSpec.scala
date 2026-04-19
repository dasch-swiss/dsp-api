/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.SipiCommand.FormatArgument
import swiss.dasch.domain.SipiCommand.QueryArgument
import swiss.dasch.domain.SipiCommand.TopLeftArgument
import zio.*
import zio.nio.file.*
import zio.test.*

object SipiCommandSpec extends ZIOSpecDefault {

  val spec: Spec[TestEnvironment with Scope, Nothing] =
    suite("SipiCommand")(
      test("should render format command with --json") {
        check(Gen.fromIterable(SipiImageFormat.all)) { format =>
          for {
            cmd <- FormatArgument(format, Path("/tmp/example"), Path("/tmp/example2")).render()
          } yield assertTrue(
            cmd == List("--json", "--format", format.toCliString, "--topleft", "/tmp/example", "/tmp/example2"),
          )
        }
      },
      test("should assemble query command without --json (mutex with --query)") {
        for {
          cmd <- QueryArgument(Path("/tmp/example")).render()
        } yield assertTrue(cmd == List("--query", "/tmp/example"))
      },
      test("should assemble topleft command with --json") {
        for {
          cmd <- TopLeftArgument(Path("/tmp/example"), Path("/tmp/example2")).render()
        } yield assertTrue(cmd == List("--json", "--topleft", "/tmp/example", "/tmp/example2"))
      },
      test("emitsJson matches the commands that include --json in their argv") {
        assertTrue(
          FormatArgument(SipiImageFormat.Jpx, Path("/a"), Path("/b")).emitsJson,
          TopLeftArgument(Path("/a"), Path("/b")).emitsJson,
          !QueryArgument(Path("/a")).emitsJson,
        )
      },
    )
}
