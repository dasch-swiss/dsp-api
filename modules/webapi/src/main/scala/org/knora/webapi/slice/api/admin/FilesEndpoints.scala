/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.tapir.*
import sttp.tapir.EndpointInput.PathCapture
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.zio.jsonBody
import zio.ZLayer
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.slice.admin.domain.model.AssetAccess
import org.knora.webapi.slice.admin.domain.model.DerivativeAccess
import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.slice.admin.domain.model.OriginalAccess
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.api.admin.AdminPathVariables.projectShortcode
import org.knora.webapi.slice.api.admin.FilesPathVar.filename
import org.knora.webapi.slice.common.api.BaseEndpoints

object FilesPathVar {
  val filename: PathCapture[InternalFilename] = path[InternalFilename]("filename")
}

/**
 * The access decision for one asset, as two channels that never merge: `original` is read by dsp-ingest and
 * `derivative` by Sipi's preflight hook.
 *
 * The literals are the VRE's own vocabulary, not Sipi's permission types; the hook translates. `size` and
 * `watermark` accompany `derivative: "clamped"` and nothing else, and exactly one of them is present.
 */
final case class AssetAccessResponse(
  derivative: String,
  original: String,
  size: Option[String] = None,
  watermark: Option[Boolean] = None,
)

object AssetAccessResponse {
  given JsonCodec[AssetAccessResponse] = DeriveJsonCodec.gen[AssetAccessResponse]

  def from(access: AssetAccess): AssetAccessResponse = {
    val original = access.original match {
      case OriginalAccess.Withhold => "withhold"
      case OriginalAccess.Grant    => "grant"
    }
    access.derivative match {
      case DerivativeAccess.Denied                             => AssetAccessResponse("denied", original)
      case DerivativeAccess.Stream                             => AssetAccessResponse("stream", original)
      case DerivativeAccess.Full                               => AssetAccessResponse("full", original)
      case DerivativeAccess.Clamped(RestrictedView.Size(size)) =>
        AssetAccessResponse("clamped", original, size = Some(size))
      // A watermark clamp is always on: `Watermark(false)` means "nothing configured" and never becomes a
      // stored setting, so it cannot reach a clamped decision.
      case DerivativeAccess.Clamped(_: RestrictedView.Watermark) =>
        AssetAccessResponse("clamped", original, watermark = Some(true))
    }
  }
}

final class FilesEndpoints(base: BaseEndpoints) {
  val getAdminFilesShortcodeFileIri = base.withUserEndpoint.get
    .in("admin" / "files" / projectShortcode / filename)
    .out(jsonBody[AssetAccessResponse])
    .description(
      "Returns the access decision for a given filename: what the caller may receive for the original and " +
        "what it may receive for the derivative. Publicly accessible. The `{shortcode}` path segment is not " +
        "authoritative and does not affect the result - the file is identified by its filename alone (internal " +
        "filenames are globally-unique asset IDs); the segment is retained only for URL compatibility.",
    )
}

object FilesEndpoints {
  val layer = ZLayer.derive[FilesEndpoints]
}
