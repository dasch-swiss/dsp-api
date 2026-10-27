/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import org.knora.webapi.slice.ingest.api.ApiProblem
import org.knora.webapi.slice.ingest.api.ApiProblem.InternalServerError
import org.knora.webapi.slice.ingest.api.ApiProblem.NotFound
import org.knora.webapi.slice.ingest.domain.AssetRef
import org.knora.webapi.slice.ingest.domain.ProjectShortcode

trait HandlerFunctions {
  def projectNotFoundOrServerError(mayBeError: Option[Throwable], shortcode: ProjectShortcode): ApiProblem =
    mayBeError.map(InternalServerError(_)).getOrElse(NotFound(shortcode))

  def assetRefNotFoundOrServerError(mayBeError: Option[Throwable], assetRef: AssetRef): ApiProblem =
    mayBeError
      .map(InternalServerError(_))
      .getOrElse(NotFound(s"${assetRef.belongsToProject}/${assetRef.id}", "AssetRef"))
}
