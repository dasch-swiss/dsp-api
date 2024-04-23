/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.Task
import zio.ZIO

import org.knora.webapi.store.triplestore.api.TriplestoreService

trait MaintenanceAction[A] {
  val triplestoreService: TriplestoreService

  /**
   * Check if the action should be executed.
   *
   * This method can be implemented to define a condition under which the action should be executed or not.
   * This mechanism may be used to prevent the execution of an action if it is not necessary,
   * if this would be a more expensive operation.
   * This would typically be an ASK query to the triplestore.
   *
   * @return true if the action should be executed, false otherwise
   */
  def shouldExecute: Task[Boolean] = ZIO.succeed(true)

  /**
   * Executes the maintenance action.
   *
   * @param params optional parameters as provided by the caller
   */
  def execute(params: Option[A]): Task[Unit]
}
