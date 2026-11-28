/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import org.junit.runner.RunWith
import swiss.dasch.api.ApiProblem.InternalServerError
import swiss.dasch.api.ApiProblem.UnprocessableContent
import swiss.dasch.domain.SipiCliError
import swiss.dasch.domain.SipiReport.Phase
import swiss.dasch.domain.SipiTarget
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class IngestFailureSpec extends ZIOSpecDefault {

  private val uploadCodes = List("decode_failed", "unsupported_format", "malformed_input", "metadata_parse_failed")

  private def sipiError(target: SipiTarget, phase: Phase, code: String) =
    SipiCliError(
      phase = phase,
      errorCode = code,
      correlationId = "sipi-id",
      target = target,
      message = "/data/x/1234.jpg.orig: IPTC block does not parse",
      reportedPaths = List("/data/x/1234.jpg.orig", "/data/x/1234.jpx"),
      imageMeta = None,
      exitCode = 1,
      uploadFilename = Some("photo.jpg"),
    )

  private def is422(problem: ApiProblem, code: String) = problem match {
    case UnprocessableContent("sipi-id", `code`, _) => true
    case _                                          => false
  }

  private def is500(problem: ApiProblem, code: String) = problem match {
    case InternalServerError("sipi-id", `code`, message) => !message.contains("/data/")
    case _                                               => false
  }

  val spec = suite("IngestFailure for sipi failures")(
    test("convert of the upload in phase read with a content code is 422 with that code") {
      ZIO
        .foreach(uploadCodes)(code =>
          IngestFailure.toApiProblem(sipiError(SipiTarget.Upload, Phase.Read, code)).map(is422(_, code)),
        )
        .map(results => assertTrue(results.forall(identity)))
    },
    test("the same codes on a stored file are 500 with that code") {
      ZIO
        .foreach(uploadCodes)(code =>
          IngestFailure.toApiProblem(sipiError(SipiTarget.Derivative, Phase.Read, code)).map(is500(_, code)),
        )
        .map(results => assertTrue(results.forall(identity)))
    },
    test("another phase or another code on the upload is 500 with that code") {
      for {
        otherPhase <- IngestFailure.toApiProblem(sipiError(SipiTarget.Upload, Phase.Convert, "decode_failed"))
        otherCode  <- IngestFailure.toApiProblem(sipiError(SipiTarget.Upload, Phase.Read, "write_failed"))
      } yield assertTrue(is500(otherPhase, "decode_failed"), is500(otherCode, "write_failed"))
    },
    test("sipi_killed, sipi_crashed and sipi_no_report are 500 with that code") {
      val codes = List(SipiCliError.killed, SipiCliError.crashed, SipiCliError.noReport)
      ZIO
        .foreach(codes)(code =>
          IngestFailure.toApiProblem(sipiError(SipiTarget.Upload, Phase.Internal, code)).map(is500(_, code)),
        )
        .map(results => assertTrue(results.forall(identity)))
    },
    test("the 422 message holds the upload filename and no server path") {
      IngestFailure
        .toApiProblem(sipiError(SipiTarget.Upload, Phase.Read, "metadata_parse_failed"))
        .map {
          case UnprocessableContent(_, _, message) =>
            assertTrue(message == "photo.jpg: IPTC block does not parse")
          case other => assertNever(s"expected 422, got $other")
        }
    },
    test("a 422 from a report without file fields has a fixed message without sipi's text") {
      val fallback = sipiError(SipiTarget.Upload, Phase.Read, "decode_failed").copy(reportedPaths = Nil)
      IngestFailure.toApiProblem(fallback).map {
        case UnprocessableContent(_, _, message) => assertTrue(message == "photo.jpg cannot be read: decode_failed")
        case other                               => assertNever(s"expected 422, got $other")
      }
    },
    test("errorCode of a sipi failure is sipi's code") {
      assertTrue(IngestFailure.errorCode(sipiError(SipiTarget.Upload, Phase.Read, "decode_failed")) == "decode_failed")
    },
  )
}
