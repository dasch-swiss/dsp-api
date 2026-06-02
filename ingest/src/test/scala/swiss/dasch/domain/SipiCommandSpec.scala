/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.SipiCommand.ApplyTopLeft
import swiss.dasch.domain.SipiCommand.Query
import swiss.dasch.domain.SipiCommand.Transcode
import zio.*
import zio.nio.file.*
import zio.test.*

object SipiCommandSpec extends ZIOSpecDefault {

  val spec: Spec[TestEnvironment with Scope, Nothing] =
    suite("SipiCommand")(
      test("should render convert subcommand with --json for Transcode") {
        check(Gen.fromIterable(SipiImageFormat.all)) { format =>
          for {
            cmd <- Transcode(format, Path("/tmp/example"), Path("/tmp/example2")).render()
          } yield assertTrue(
            cmd == List(
              "convert",
              "/tmp/example",
              "/tmp/example2",
              "--json",
              "--format",
              format.toCliString,
              "--topleft",
            ),
          )
        }
      },
      test("should render query subcommand without --json") {
        for {
          cmd <- Query(Path("/tmp/example")).render()
        } yield assertTrue(cmd == List("query", "/tmp/example"))
      },
      test("should render convert subcommand with --json --topleft for ApplyTopLeft") {
        for {
          cmd <- ApplyTopLeft(Path("/tmp/example"), Path("/tmp/example2")).render()
        } yield assertTrue(cmd == List("convert", "/tmp/example", "/tmp/example2", "--json", "--topleft"))
      },
      test("emitsJson matches the commands that include --json in their argv") {
        assertTrue(
          Transcode(SipiImageFormat.Jpx, Path("/a"), Path("/b")).emitsJson,
          ApplyTopLeft(Path("/a"), Path("/b")).emitsJson,
          !Query(Path("/a")).emitsJson,
        )
      },
    )
}
