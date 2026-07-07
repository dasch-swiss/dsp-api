/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.management

import sttp.model.StatusCode
import zio.*

import scala.annotation.unused

import org.knora.webapi.core.State
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.store.triplestore.api.TriplestoreService

final class ManagementRestService(auth: AuthorizationRestService, state: State, triplestore: TriplestoreService) {

  def healthCheck: UIO[(HealthResponse, StatusCode)] =
    state.getAppState.map { s =>
      val response = HealthResponse.from(s)
      (response, if (response.status) StatusCode.Ok else StatusCode.ServiceUnavailable)
    }

  def startCompaction(user: User)(@unused ignored: Unit): Task[(String, StatusCode)] =
    auth.ensureSystemAdmin(user) *>
      triplestore.compact().map(success => if (success) ("ok", StatusCode.Ok) else ("forbidden", StatusCode.Forbidden))
}

object ManagementRestService {
  private[management] val layer = ZLayer.derive[ManagementRestService]
}
