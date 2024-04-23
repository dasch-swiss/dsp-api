/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service.maintenance

import zio.Task
import zio.ZIO

import org.knora.webapi.store.triplestore.api.TriplestoreService

trait MaintenanceAction {
  val triplestoreService: TriplestoreService

  def shouldExecute: Task[Boolean] = ZIO.succeed(true)

  def execute: Task[Unit]
}
