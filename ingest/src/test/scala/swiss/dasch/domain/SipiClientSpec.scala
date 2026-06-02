/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.infrastructure.CommandExecutorMock
import swiss.dasch.infrastructure.ProcessOutput
import zio.nio.file.Path
import zio.test.*

object SipiClientSpec extends ZIOSpecDefault {

  private val fileIn  = Path("/tmp/in.jpg")
  private val fileOut = Path("/tmp/out.jp2")

  private val successJson =
    """{"status":"ok","mode":"cli","input_file":"/tmp/in.jpg","output_file":"/tmp/out.jp2",""" +
      """"output_format":"jpx","file_size_bytes":10,""" +
      """"image":{"width":1,"height":1,"channels":3,"bps":8,""" +
      """"colorspace":"RGB","icc_profile_type":"sRGB","orientation":"TOPLEFT"}}"""

  private def errorJson(phase: String, withImage: Boolean = true): String = {
    val img =
      if (withImage)
        ""","image":{"width":2,"height":2,"channels":1,"bps":1,"colorspace":"MINISWHITE","icc_profile_type":"","orientation":"TOPLEFT"}"""
      else ""
    s"""{"status":"error","mode":"cli","phase":"$phase","error_message":"boom","input_file":"/tmp/in.jpg",""" +
      s""""output_file":"/tmp/out.jp2","output_format":"jpx","file_size_bytes":4$img}"""
  }

  private val errorPathSuite = suite("error path (--json)")(
    test("transcode with exit != 0 and a parseable read error fails with SipiCliError(Read)") {
      for {
        _    <- CommandExecutorMock.setOutput(ProcessOutput(errorJson("read"), "stderr log", 1))
        exit <- SipiClient.transcodeImageFile(fileIn, fileOut, SipiImageFormat.Jpx).exit
      } yield assertTrue(exit.causeOption.flatMap(_.failureOption).exists {
        case e: SipiCliError =>
          e.phase == SipiReport.Phase.Read &&
          e.message == "boom" &&
          e.exitCode == 1 &&
          e.imageMeta.exists(_.bps == 1)
        case _ => false
      })
    },
    test("transcode with a cli_args error without image → imageMeta is None") {
      for {
        _    <- CommandExecutorMock.setOutput(ProcessOutput(errorJson("cli_args", withImage = false), "", 1))
        exit <- SipiClient.transcodeImageFile(fileIn, fileOut, SipiImageFormat.Jpx).exit
      } yield assertTrue(exit.causeOption.flatMap(_.failureOption).exists {
        case e: SipiCliError => e.phase == SipiReport.Phase.CliArgs && e.imageMeta.isEmpty
        case _               => false
      })
    },
    test("transcode with exit != 0 and garbage stdout falls back to legacy IOException") {
      for {
        _    <- CommandExecutorMock.setOutput(ProcessOutput("not json at all", "", 1))
        exit <- SipiClient.transcodeImageFile(fileIn, fileOut, SipiImageFormat.Jpx).exit
      } yield assertTrue(exit.causeOption.flatMap(_.failureOption).exists {
        case _: SipiCliError => false
        case e               => e.getMessage.startsWith("Command failed:")
      })
    },
    test("transcode with exit != 0 and empty stdout falls back to legacy IOException") {
      for {
        _    <- CommandExecutorMock.setOutput(ProcessOutput("", "", 1))
        exit <- SipiClient.transcodeImageFile(fileIn, fileOut, SipiImageFormat.Jpx).exit
      } yield assertTrue(exit.causeOption.flatMap(_.failureOption).exists {
        case _: SipiCliError => false
        case e               => e.getMessage.startsWith("Command failed:")
      })
    },
    test("topleft success returns ProcessOutput unchanged") {
      for {
        _   <- CommandExecutorMock.setOutput(ProcessOutput(successJson, "log", 0))
        out <- SipiClient.applyTopLeftCorrection(fileIn, fileOut)
      } yield assertTrue(out.exitCode == 0, out.stdout == successJson)
    },
    test("topleft error surfaces SipiCliError(Convert)") {
      for {
        _    <- CommandExecutorMock.setOutput(ProcessOutput(errorJson("convert"), "log", 2))
        exit <- SipiClient.applyTopLeftCorrection(fileIn, fileOut).exit
      } yield assertTrue(exit.causeOption.flatMap(_.failureOption).exists {
        case e: SipiCliError => e.phase == SipiReport.Phase.Convert && e.exitCode == 2
        case _               => false
      })
    },
    test("query with exit != 0 uses legacy fallback (no JSON parsing attempted)") {
      for {
        _    <- CommandExecutorMock.setOutput(ProcessOutput(errorJson("read"), "", 1))
        exit <- SipiClient.queryImageFile(fileIn).exit
      } yield assertTrue(exit.causeOption.flatMap(_.failureOption).exists {
        case _: SipiCliError => false
        case e               => e.getMessage.startsWith("Command failed:")
      })
    },
  ).provide(
    CommandExecutorMock.layer,
    SipiClientLive.layer,
  )

  val spec = suite("SipiClient")(errorPathSuite)
}
