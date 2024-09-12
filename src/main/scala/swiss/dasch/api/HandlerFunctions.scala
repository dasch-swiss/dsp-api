/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiProblem.{InternalServerError, NotFound}
import swiss.dasch.domain.{AssetRef, ProjectShortcode}

trait HandlerFunctions {
  def projectNotFoundOrServerError(mayBeError: Option[Throwable], shortcode: ProjectShortcode): ApiProblem =
    mayBeError.map(InternalServerError(_)).getOrElse(NotFound(shortcode))

  def assetRefNotFoundOrServerError(mayBeError: Option[Throwable], assetRef: AssetRef): ApiProblem =
    mayBeError
      .map(InternalServerError(_))
      .getOrElse(NotFound(s"${assetRef.belongsToProject}/${assetRef.id}", "AssetRef"))
}
