/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.json.*
import zio.test.*

object SipiReportSpec extends ZIOSpecDefault {

  private val successPayload =
    """{
      |  "status": "ok",
      |  "mode": "cli",
      |  "input_file": "/path/to/input.jpg",
      |  "output_file": "/tmp/out.jp2",
      |  "output_format": "jpx",
      |  "file_size_bytes": 26688,
      |  "image": {
      |    "width": 404,
      |    "height": 201,
      |    "channels": 3,
      |    "bps": 8,
      |    "colorspace": "RGB",
      |    "icc_profile_type": "sRGB",
      |    "orientation": "TOPLEFT"
      |  }
      |}""".stripMargin

  private val errorImageBlock =
    """,
      |  "image": {
      |    "width": 8040,
      |    "height": 9624,
      |    "channels": 1,
      |    "bps": 1,
      |    "colorspace": "MINISWHITE",
      |    "icc_profile_type": "",
      |    "orientation": "TOPLEFT"
      |  }""".stripMargin

  private def errorPayload(phase: String, includeImage: Boolean = true): String = {
    val imageBlock = if (includeImage) errorImageBlock else ""
    s"""{
       |  "status": "error",
       |  "mode": "cli",
       |  "phase": "$phase",
       |  "error_message": "boom",
       |  "input_file": "/tmp/in",
       |  "output_file": "/tmp/out",
       |  "output_format": "jpx",
       |  "file_size_bytes": 4768164$imageBlock
       |}""".stripMargin
  }

  val spec = suite("SipiReport decoder")(
    test("decodes the success payload with every field") {
      val decoded = successPayload.fromJson[SipiReport]
      assertTrue(
        decoded == Right(
          SipiReport.Ok(
            inputFile = "/path/to/input.jpg",
            outputFile = "/tmp/out.jp2",
            outputFormat = "jpx",
            fileSizeBytes = 26688L,
            image = Some(
              SipiReport.ImageMeta(
                width = 404,
                height = 201,
                channels = 3,
                bps = 8,
                colorspace = "RGB",
                iccProfileType = "sRGB",
                orientation = "TOPLEFT",
              ),
            ),
          ),
        ),
      )
    },
    test("decodes an error payload for every phase") {
      val phases = List(
        "cli_args" -> SipiReport.Phase.CliArgs,
        "read"     -> SipiReport.Phase.Read,
        "convert"  -> SipiReport.Phase.Convert,
        "write"    -> SipiReport.Phase.Write,
      )
      val results = phases.map { case (raw, expected) =>
        errorPayload(raw).fromJson[SipiReport] match {
          case Right(err: SipiReport.Err) => err.phase == expected
          case _                          => false
        }
      }
      assertTrue(results.forall(identity))
    },
    test("accepts an error payload without the image field (cli_args)") {
      val decoded = errorPayload("cli_args", includeImage = false).fromJson[SipiReport]
      assertTrue(decoded.exists {
        case err: SipiReport.Err => err.image.isEmpty && err.phase == SipiReport.Phase.CliArgs
        case _                   => false
      })
    },
    test("populates ImageMeta on the 1-bit bilevel TIFF error example") {
      val decoded = errorPayload("read").fromJson[SipiReport]
      assertTrue(decoded.exists {
        case err: SipiReport.Err =>
          err.image.exists(im => im.bps == 1 && im.colorspace == "MINISWHITE" && im.channels == 1)
        case _ => false
      })
    },
    test("ignores unknown fields at the top level and inside image (forward compat)") {
      val payload =
        """{
          |  "status": "ok",
          |  "future_flag": true,
          |  "input_file": "/a",
          |  "output_file": "/b",
          |  "output_format": "jpx",
          |  "file_size_bytes": 1,
          |  "image": {
          |    "width": 1, "height": 1, "channels": 1, "bps": 8,
          |    "colorspace": "RGB", "icc_profile_type": "sRGB", "orientation": "TOPLEFT",
          |    "future_tag": "xyz"
          |  }
          |}""".stripMargin
      assertTrue(payload.fromJson[SipiReport].isRight)
    },
    test("rejects a payload with an unknown phase value") {
      val payload =
        """{"status": "error", "phase": "brand_new_phase", "error_message": "?",
          |"input_file": "", "output_file": "", "output_format": "", "file_size_bytes": 0}""".stripMargin
      assertTrue(payload.fromJson[SipiReport].isLeft)
    },
    test("rejects malformed JSON") {
      assertTrue("not json at all".fromJson[SipiReport].isLeft) &&
      assertTrue("".fromJson[SipiReport].isLeft) &&
      assertTrue("""{"status": "ok""".fromJson[SipiReport].isLeft)
    },
    test("rejects a payload with a missing status field") {
      val payload = """{"input_file": "/a", "output_file": "/b"}"""
      assertTrue(payload.fromJson[SipiReport].isLeft)
    },
  )
}
