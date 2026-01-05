/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import zio.*

/**
 * Represents the result of a task in a sequence of tasks.
 */
trait ResultAndNext[T] {

  /**
   * Returns the underlying result of this task.
   */
  def result: T

  /**
   * Returns the next task, or `None` if this was the last task.
   */
  def next: Option[NextExecutionStep[T]]
}

/**
 * Represents a task in a sequence of tasks.
 */
trait NextExecutionStep[T] {

  /**
   * Runs the task.
   *
   * @param params the result of the previous task, or `None` if this is the first task in the sequence.
   * @return the result of this task.
   */
  def run(params: Option[ResultAndNext[T]]): Task[ResultAndNext[T]]
}
