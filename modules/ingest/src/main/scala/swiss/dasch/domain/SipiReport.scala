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
    case CliArgs, Read, Convert, Write, Unknown, Internal
  }
  object Phase {
    given JsonDecoder[Phase] = JsonDecoder[String].mapOrFail {
      case "cli_args" => Right(Phase.CliArgs)
      case "read"     => Right(Phase.Read)
      case "convert"  => Right(Phase.Convert)
      case "write"    => Right(Phase.Write)
      case "unknown"  => Right(Phase.Unknown)
      case "internal" => Right(Phase.Internal)
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
    @jsonField("correlation_id") correlationId: Option[String] = None,
  ) extends SipiReport
  object Ok {
    given JsonDecoder[Ok] = DeriveJsonDecoder.gen[Ok]
  }

  /** The four file fields are optional: sipi's fallback document, for a message that is not valid UTF-8, omits them. */
  final case class Err(
    phase: Phase,
    @jsonField("error_code") errorCode: String,
    @jsonField("error_message") errorMessage: String,
    @jsonField("correlation_id") correlationId: Option[String],
    @jsonField("input_file") inputFile: Option[String],
    @jsonField("output_file") outputFile: Option[String],
    @jsonField("output_format") outputFormat: Option[String],
    @jsonField("file_size_bytes") fileSizeBytes: Option[Long],
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

/** The file a sipi command reads: the user's upload, or a file ingest already stored or created. */
enum SipiTarget {
  case Upload, Derivative
}

/**
 * A failed sipi run, from its `--json` error report or, without a report, from its exit code.
 *
 * `message` is sipi's diagnostic and holds server paths; only [[clientMessage]] may reach a client.
 */
final case class SipiCliError(
  phase: SipiReport.Phase,
  errorCode: String,
  correlationId: String,
  target: SipiTarget,
  message: String,
  reportedPaths: List[String],
  imageMeta: Option[SipiReport.ImageMeta],
  exitCode: Int,
  uploadFilename: Option[String] = None,
) extends IOException(s"Sipi [$phase/$errorCode] $message") {

  /**
   * `message` with every path from the report replaced by the upload filename. A report without paths (sipi's
   * fallback document), or a message that still holds an absolute path after the replacement, gives a fixed
   * message, since nothing says which other part of `message` is a server path.
   */
  def clientMessage: String = {
    val replacement = uploadFilename.getOrElse("the file")
    val fixed       = s"$replacement cannot be read: $errorCode"
    val paths       = reportedPaths.filter(_.nonEmpty)
    if (paths.isEmpty) fixed
    else {
      val redacted = paths.sortBy(-_.length).foldLeft(message)(_.replace(_, replacement))
      if (SipiCliError.absolutePath.findFirstIn(redacted).isDefined) fixed else redacted
    }
  }
}

object SipiCliError {
  val killed: String   = "sipi_killed"
  val crashed: String  = "sipi_crashed"
  val noReport: String = "sipi_no_report"

  private val absolutePath = """(?:^|[\s'"(=:])/[^\s'"]""".r
}
