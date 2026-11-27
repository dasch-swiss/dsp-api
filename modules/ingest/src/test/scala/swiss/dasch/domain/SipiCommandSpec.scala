/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.junit.runner.RunWith
import swiss.dasch.domain.SipiCommand.ApplyTopLeft
import swiss.dasch.domain.SipiCommand.Query
import swiss.dasch.domain.SipiCommand.Transcode
import zio.*
import zio.nio.file.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class SipiCommandSpec extends ZIOSpecDefault {

  val spec: Spec[TestEnvironment & Scope, Nothing] =
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
      test("should render query subcommand with --json") {
        for {
          cmd <- Query(Path("/tmp/example")).render()
        } yield assertTrue(cmd == List("query", "/tmp/example", "--json"))
      },
      test("should render convert subcommand with --json --topleft for ApplyTopLeft") {
        for {
          cmd <- ApplyTopLeft(Path("/tmp/example"), Path("/tmp/example2")).render()
        } yield assertTrue(cmd == List("convert", "/tmp/example", "/tmp/example2", "--json", "--topleft"))
      },
      test("only the convert of the upload targets the upload; topleft and query target stored files") {
        assertTrue(
          Transcode(SipiImageFormat.Jpx, Path("/a"), Path("/b")).target == SipiTarget.Upload,
          ApplyTopLeft(Path("/a"), Path("/b")).target == SipiTarget.Derivative,
          Query(Path("/a")).target == SipiTarget.Derivative,
        )
      },
    )
}
