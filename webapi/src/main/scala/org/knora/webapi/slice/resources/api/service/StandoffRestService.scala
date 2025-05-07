/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api.service

import zio.*

import org.knora.webapi.slice.admin.domain.model.User

case class StandoffRestService() {
  def createMapping(user: User)(u: Unit): Task[Unit] =
    ZIO.unit
}

object StandoffRestService {
  val layer = ZLayer.derive[StandoffRestService]
}
