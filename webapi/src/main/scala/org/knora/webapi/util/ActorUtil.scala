/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import org.apache.pekko
import zio.*

import dsp.errors.*

import pekko.actor.ActorRef

object ActorUtil {

  /**
   * An actor that expects to receive messages sent using the `ask` pattern can use this method to handle
   * unexpected request messages in a consistent way.
   *
   * @param sender  the actor that made the request in the `ask` pattern.
   * @param message the message that was received.
   */
  def handleUnexpectedMessage(sender: ActorRef, message: Any, who: String): Unit = {
    val unexpectedMessageException = UnexpectedMessageException(
      s"$who received an unexpected message $message of type ${message.getClass.getCanonicalName}",
    )
    sender ! pekko.actor.Status.Failure(unexpectedMessageException)
  }
}

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
