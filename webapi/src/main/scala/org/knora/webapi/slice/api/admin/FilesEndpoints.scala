/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.tapir.*
import sttp.tapir.EndpointInput.PathCapture
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.zio.jsonBody
import zio.ZLayer

import org.knora.webapi.slice.api.admin.AdminPathVariables.projectShortcode
import org.knora.webapi.slice.api.admin.Codecs.TapirCodec.sparqlEncodedString
import org.knora.webapi.slice.api.admin.FilesPathVar.filename
import org.knora.webapi.slice.api.admin.model.PermissionCodeAndProjectRestrictedViewSettings
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.domain.SparqlEncodedString

object FilesPathVar {
  val filename: PathCapture[SparqlEncodedString] = path[SparqlEncodedString]("filename")
}

final class FilesEndpoints(base: BaseEndpoints) {
  val getAdminFilesShortcodeFileIri = base.withUserEndpoint.get
    .in("admin" / "files" / projectShortcode / filename)
    .out(jsonBody[PermissionCodeAndProjectRestrictedViewSettings])
    .description(
      "Returns the permission code and the project's restricted view settings for a given shortcode and filename. " +
        "Publicly accessible.",
    )
}

object FilesEndpoints {
  val layer = ZLayer.derive[FilesEndpoints]
}
