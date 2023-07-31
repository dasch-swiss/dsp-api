/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.SipiCommand.{ FormatArgument, QueryArgument, TopLeftArgument }
import zio.*
import zio.nio.file.*
import zio.test.*

object SipiCommandSpec extends ZIOSpecDefault {

  val spec: Spec[TestEnvironment with Scope, Nothing] =
    suite("SipiCommand")(
      test("should render format command") {
        check(Gen.fromIterable(SipiImageFormat.all)) { format =>
          for {
            cmd <- FormatArgument(format, Path("/tmp/example"), Path("/tmp/example2")).render()
          } yield assertTrue(cmd == s"--format ${format.toCliString} /tmp/example /tmp/example2")
        }
      },
      test("should assemble query command") {
        for {
          cmd <- QueryArgument(Path("/tmp/example")).render()
        } yield assertTrue(cmd == s"--query /tmp/example")
      },
      test("should assemble topleft command") {
        for {
          cmd <- TopLeftArgument(Path("/tmp/example"), Path("/tmp/example2")).render()
        } yield assertTrue(cmd == s"--topleft /tmp/example /tmp/example2")
      },
    )
}
