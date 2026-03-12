/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3
import zio.IO
import zio.ZLayer

import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.AuthorizationRestService

final class V3Authorizer(auth: AuthorizationRestService) {

  def ensureSystemAdmin(user: User): IO[Forbidden, Unit] =
    auth.ensureSystemAdmin(user).mapError(e => Forbidden(e.message))

  def ensureSystemAdminOrProjectAdmin(user: User, project: KnoraProject): IO[Forbidden, Unit] =
    auth.ensureSystemAdminOrProjectAdmin(user, project).mapError(e => Forbidden(e.message))

  def ensureSystemAdminOrProjectAdminById(user: User, projectIri: ProjectIri): IO[Forbidden, Unit] =
    auth.ensureSystemAdminOrProjectAdminById(user, projectIri).mapError(e => Forbidden(e.message)).unit
}

object V3Authorizer {
  val layer = ZLayer.derive[V3Authorizer]
}
