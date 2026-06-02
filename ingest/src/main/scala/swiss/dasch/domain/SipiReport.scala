/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.json.*
import zio.json.ast.Json

import java.io.IOException

/**
 * Structured report emitted by `sipi --json` on stdout.
 *
 * Schema reference: sipi's `docs/src/guide/json-output.md` (unversioned, additive).
 * Decoders tolerate unknown fields so future additive changes on the sipi side
 * do not break us.
 */
sealed trait SipiReport
object SipiReport {

  enum Phase {
    case CliArgs, Read, Convert, Write
  }
  object Phase {
    given JsonDecoder[Phase] = JsonDecoder[String].mapOrFail {
      case "cli_args" => Right(Phase.CliArgs)
      case "read"     => Right(Phase.Read)
      case "convert"  => Right(Phase.Convert)
      case "write"    => Right(Phase.Write)
      case other      => Left(s"Unknown sipi phase: $other")
    }
  }

  final case class ImageMeta(
    width: Int,
    height: Int,
    channels: Int,
    bps: Int,
    colorspace: String,
    @jsonField("icc_profile_type") iccProfileType: String,
    orientation: String,
  )
  object ImageMeta {
    given JsonDecoder[ImageMeta] = DeriveJsonDecoder.gen[ImageMeta]
  }

  final case class Ok(
    @jsonField("input_file") inputFile: String,
    @jsonField("output_file") outputFile: String,
    @jsonField("output_format") outputFormat: String,
    @jsonField("file_size_bytes") fileSizeBytes: Long,
    image: Option[ImageMeta],
  ) extends SipiReport
  object Ok {
    given JsonDecoder[Ok] = DeriveJsonDecoder.gen[Ok]
  }

  final case class Err(
    phase: Phase,
    @jsonField("error_message") errorMessage: String,
    @jsonField("input_file") inputFile: String,
    @jsonField("output_file") outputFile: String,
    @jsonField("output_format") outputFormat: String,
    @jsonField("file_size_bytes") fileSizeBytes: Long,
    image: Option[ImageMeta],
  ) extends SipiReport
  object Err {
    given JsonDecoder[Err] = DeriveJsonDecoder.gen[Err]
  }

  given JsonDecoder[SipiReport] = JsonDecoder[Json.Obj].mapOrFail { obj =>
    obj.get("status").flatMap(_.asString) match {
      case Some("ok")    => obj.toJson.fromJson[Ok]
      case Some("error") => obj.toJson.fromJson[Err]
      case Some(other)   => Left(s"Unknown sipi report status: $other")
      case None          => Left("Missing `status` field in sipi report")
    }
  }
}

/**
 * Typed error raised when a sipi CLI invocation fails with a structured
 * `--json` report on stdout. Extends `IOException` so it slots into the
 * existing `IO[IOException, _]` signatures without a wider refactor.
 */
final case class SipiCliError(
  phase: SipiReport.Phase,
  message: String,
  inputFile: Option[String],
  imageMeta: Option[SipiReport.ImageMeta],
  exitCode: Int,
) extends IOException(s"Sipi [$phase] $message")
