/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.junit.runner.RunWith
import swiss.dasch.infrastructure.CommandExecutorMock
import swiss.dasch.infrastructure.CorrelationId
import swiss.dasch.infrastructure.ProcessOutput
import zio.ZIO
import zio.nio.file.Path
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class SipiClientSpec extends ZIOSpecDefault {

  private val fileIn  = Path("/tmp/in.jpg")
  private val fileOut = Path("/tmp/out.jp2")

  private val successJson =
    """{"status":"ok","mode":"cli","input_file":"/tmp/in.jpg","output_file":"/tmp/out.jp2",""" +
      """"output_format":"jpx","file_size_bytes":10,""" +
      """"image":{"width":1,"height":1,"channels":3,"bps":8,""" +
      """"colorspace":"RGB","icc_profile_type":"sRGB","orientation":"TOPLEFT"}}"""

  private def errorJson(phase: String, withImage: Boolean = true, code: String = "decode_failed"): String = {
    val img =
      if (withImage)
        ""","image":{"width":2,"height":2,"channels":1,"bps":1,"colorspace":"MINISWHITE","icc_profile_type":"","orientation":"TOPLEFT"}"""
      else ""
    s"""{"status":"error","mode":"cli","phase":"$phase","error_code":"$code","correlation_id":"sipi-id",""" +
      s""""error_message":"/tmp/in.jpg: boom","input_file":"/tmp/in.jpg",""" +
      s""""output_file":"/tmp/out.jp2","output_format":"jpx","file_size_bytes":4$img}"""
  }

  private def failure[A](exit: zio.Exit[Throwable, A]): Option[SipiCliError] =
    exit.causeOption.flatMap(_.failureOption).collect { case e: SipiCliError => e }

  private val transcode = SipiClient.transcodeImageFile(fileIn, fileOut, SipiImageFormat.Jpx)

  private val errorPathSuite = suite("error path (--json)")(
    test("transcode with exit != 0 and a parseable read error fails with SipiCliError on the upload") {
      for {
        _    <- CommandExecutorMock.setOutput(ProcessOutput(errorJson("read"), "stderr log", 1))
        exit <- transcode.exit
      } yield assertTrue(failure(exit).exists { e =>
        e.phase == SipiReport.Phase.Read &&
        e.errorCode == "decode_failed" &&
        e.correlationId == "sipi-id" &&
        e.target == SipiTarget.Upload &&
        e.message == "/tmp/in.jpg: boom" &&
        e.exitCode == 1 &&
        e.imageMeta.exists(_.bps == 1)
      })
    },
    test("the client message replaces the reported paths with the upload filename") {
      for {
        _    <- CommandExecutorMock.setOutput(ProcessOutput(errorJson("read"), "", 1))
        exit <- transcode.exit
        error = failure(exit).map(_.copy(uploadFilename = Some("photo.jpg")))
      } yield assertTrue(error.map(_.clientMessage).contains("photo.jpg: boom"))
    },
    test("the client message is fixed when sipi's message names a path the report does not list") {
      for {
        _    <- CommandExecutorMock.setOutput(ProcessOutput(errorJson("read"), "", 1))
        exit <- transcode.exit
        error =
          failure(exit).map(
            _.copy(message = "/tmp/in.jpg: no profile /usr/share/icc/srgb.icc", uploadFilename = Some("photo.jpg")),
          )
      } yield assertTrue(error.map(_.clientMessage).contains("photo.jpg cannot be read: decode_failed"))
    },
    test("transcode with a cli_args error without image → imageMeta is None") {
      for {
        _    <- CommandExecutorMock.setOutput(ProcessOutput(errorJson("cli_args", withImage = false), "", 1))
        exit <- transcode.exit
      } yield assertTrue(failure(exit).exists(e => e.phase == SipiReport.Phase.CliArgs && e.imageMeta.isEmpty))
    },
    test("exit 137 is sipi_killed, even when stdout holds a report") {
      for {
        _    <- CommandExecutorMock.setOutput(ProcessOutput(errorJson("read"), "", 137))
        exit <- transcode.exit
      } yield assertTrue(
        failure(exit).exists(e => e.errorCode == "sipi_killed" && e.phase == SipiReport.Phase.Internal),
      )
    },
    test("exit 139 is sipi_crashed") {
      for {
        _    <- CommandExecutorMock.setOutput(ProcessOutput("", "", 139))
        exit <- transcode.exit
      } yield assertTrue(
        failure(exit).exists(e => e.errorCode == "sipi_crashed" && e.phase == SipiReport.Phase.Internal),
      )
    },
    test("exit != 0 with garbage or empty stdout is sipi_no_report under the request's correlation ID") {
      for {
        _       <- CommandExecutorMock.setOutput(ProcessOutput("not json at all", "", 1))
        garbage <- ZIO.logAnnotate(CorrelationId.annotationKey, "req-1")(transcode.exit)
        _       <- CommandExecutorMock.setOutput(ProcessOutput("", "", 105))
        empty   <- transcode.exit
      } yield assertTrue(
        failure(garbage).exists(e => e.errorCode == "sipi_no_report" && e.correlationId == "req-1"),
        failure(empty).exists(e => e.errorCode == "sipi_no_report" && e.reportedPaths.isEmpty),
      )
    },
    test("a report without correlation_id gets the request's correlation ID") {
      val report = """{"status":"error","phase":"read","error_code":"decode_failed","error_message":"x"}"""
      for {
        _    <- CommandExecutorMock.setOutput(ProcessOutput(report, "", 1))
        exit <- ZIO.logAnnotate(CorrelationId.annotationKey, "req-2")(transcode.exit)
      } yield assertTrue(failure(exit).exists(_.correlationId == "req-2"))
    },
    test("topleft success returns ProcessOutput unchanged") {
      for {
        _   <- CommandExecutorMock.setOutput(ProcessOutput(successJson, "log", 0))
        out <- SipiClient.applyTopLeftCorrection(fileIn, fileOut)
      } yield assertTrue(out.exitCode == 0, out.stdout == successJson)
    },
    test("topleft error surfaces SipiCliError(Convert) on a stored file") {
      for {
        _    <- CommandExecutorMock.setOutput(ProcessOutput(errorJson("convert"), "log", 2))
        exit <- SipiClient.applyTopLeftCorrection(fileIn, fileOut).exit
      } yield assertTrue(failure(exit).exists { e =>
        e.phase == SipiReport.Phase.Convert && e.exitCode == 2 && e.target == SipiTarget.Derivative
      })
    },
    test("query success returns its text output unparsed") {
      val text = "SipiImage with the following parameters:\nnx    = 6\nny    = 6\n"
      for {
        _   <- CommandExecutorMock.setOutput(ProcessOutput(text, "[id] log line", 0))
        out <- SipiClient.queryImageFile(fileIn)
      } yield assertTrue(out.stdout == text)
    },
    test("query with exit != 0 and a report fails with SipiCliError on a stored file") {
      for {
        _    <- CommandExecutorMock.setOutput(ProcessOutput(errorJson("read"), "", 1))
        exit <- SipiClient.queryImageFile(fileIn).exit
      } yield assertTrue(failure(exit).exists { e =>
        e.target == SipiTarget.Derivative && e.errorCode == "decode_failed"
      })
    },
  ).provide(
    CommandExecutorMock.layer,
    SipiClientLive.layer,
  )

  val spec = suite("SipiClient")(errorPathSuite)
}
