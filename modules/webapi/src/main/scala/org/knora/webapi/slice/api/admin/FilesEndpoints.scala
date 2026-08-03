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

import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.slice.api.admin.AdminPathVariables.projectShortcode
import org.knora.webapi.slice.api.admin.FilesPathVar.filename
import org.knora.webapi.slice.api.admin.model.PermissionCodeAndProjectRestrictedViewSettings
import org.knora.webapi.slice.common.api.BaseEndpoints

object FilesPathVar {
  val filename: PathCapture[InternalFilename] = path[InternalFilename]("filename")
}

final class FilesEndpoints(base: BaseEndpoints) {
  val getAdminFilesShortcodeFileIri = base.withUserEndpoint.get
    .in("admin" / "files" / projectShortcode / filename)
    .out(jsonBody[PermissionCodeAndProjectRestrictedViewSettings])
    .description(
      "Returns the permission code and the project's restricted view settings for a given filename. " +
        "Publicly accessible. The `{shortcode}` path segment is not authoritative and does not affect the result " +
        "— the file is identified by its filename alone (internal filenames are globally-unique asset IDs); the " +
        "segment is retained only for URL compatibility.",
    )
}

object FilesEndpoints {
  val layer = ZLayer.derive[FilesEndpoints]
}
