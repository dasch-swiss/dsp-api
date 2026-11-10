/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.*

object ImportSlotTestSupport {

  /**
   * Waits, bounded to 10 seconds of real time, until the slot is free. The daemon fiber of an import releases the slot
   * after the task reached its terminal state, so a single check right after that is racy.
   *
   * The probe runs only a failing `prepare`, which takes and releases the slot synchronously, so it never holds the
   * slot while the caller continues.
   */
  def awaitFree(slot: ImportSlot): UIO[Unit] =
    slot
      .run(ZIO.fail(ProbeDone))(_ => ZIO.unit)
      .flip
      .flatMap {
        case ProbeDone => ZIO.unit
        case busy      => ZIO.fail(busy)
      }
      .retry(Schedule.spaced(20.millis) && Schedule.recurs(500))
      .orElse(ZIO.die(new IllegalStateException("The import slot was not released within 10 seconds.")))
      .withClock(Clock.ClockLive)

  /** Re-runs `effect` every 10 milliseconds until its result satisfies `done`; needs a live clock. */
  def awaitUntil[A](effect: UIO[A])(done: A => Boolean): UIO[A] =
    effect.repeat(Schedule.spaced(10.millis) *> Schedule.recurUntil[A](done))

  private case object ProbeDone
}
