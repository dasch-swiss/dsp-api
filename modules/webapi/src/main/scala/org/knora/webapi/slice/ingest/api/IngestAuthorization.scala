/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.infrastructure.Scope
import org.knora.webapi.slice.ingest.api.ApiProblem.Unauthorized
import org.knora.webapi.slice.ingest.domain.ProjectShortcode

/** The scope checks of the ingest routes. A refusal is a `401` with ingest's body, as in the standalone service. */
object IngestAuthorization {

  def ensureAdminScope(principal: IngestPrincipal): IO[ApiProblem, Unit] =
    ZIO.unless(principal.scope.hasAdmin)(ZIO.fail(Unauthorized("Admin permissions required."))).unit

  def ensureProjectReadable(principal: IngestPrincipal, shortcode: ProjectShortcode): IO[ApiProblem, Unit] =
    ensureProject(principal.scope, shortcode)(_.projectReadable(_))

  def ensureProjectWritable(principal: IngestPrincipal, shortcode: ProjectShortcode): IO[ApiProblem, Unit] =
    ensureProject(principal.scope, shortcode)(_.projectWritable(_))

  private def ensureProject(scope: Scope, shortcode: ProjectShortcode)(
    allows: (Scope, Shortcode) => Boolean,
  ): IO[ApiProblem, Unit] =
    ZIO
      .unless(Shortcode.from(shortcode.value).exists(allows(scope, _)))(ZIO.fail(Unauthorized("No project access.")))
      .unit
}
