/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.*

trait DataTaskPersistence {
  def onChanged(task: CurrentDataTask): UIO[Unit]
  def onDeleted(taskId: DataTaskId): UIO[Unit]
}

object DataTaskPersistence {
  val noop: ULayer[DataTaskPersistence] = ZLayer.succeed(new DataTaskPersistence {
    def onChanged(task: CurrentDataTask): UIO[Unit] = ZIO.unit
    def onDeleted(taskId: DataTaskId): UIO[Unit]    = ZIO.unit
  })
}
