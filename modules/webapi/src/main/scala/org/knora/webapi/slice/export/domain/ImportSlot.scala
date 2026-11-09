/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.*

/** Returned by [[ImportSlot.run]] when another import already holds the slot. */
final case class ImportSlotBusyError()

/**
 * Admits one import at a time on this API instance. The slot is in memory and does not span instances.
 *
 * Build it only in `ExportModule`, so that there is one instance; every import kind runs its background work through
 * [[run]].
 */
final class ImportSlot private[domain] (taken: Ref[Boolean]) {

  /**
   * Takes the slot, runs `prepare` on the caller's fiber and hands its result to `work` on a daemon fiber. The slot is
   * released when `prepare` ends abnormally or when `work` ends, whatever the cause. Fails with [[ImportSlotBusyError]]
   * and runs nothing if the slot is taken.
   *
   * The slot is a `Ref` and not a `Semaphore`: a permit cannot pass from the caller's fiber to the daemon fiber.
   * `prepare` and `work` run under `restore`, because the daemon fiber inherits the uninterruptible mask of the
   * surrounding region and would otherwise not be interruptible.
   */
  def run[E, A](prepare: IO[E, A])(work: A => UIO[Unit]): IO[E | ImportSlotBusyError, A] =
    ZIO.uninterruptibleMask { restore =>
      taken.getAndSet(true).flatMap { busy =>
        if (busy) ZIO.fail(ImportSlotBusyError())
        else
          restore(prepare).onError(_ => release).tap(a => restore(work(a)).ensuring(release).forkDaemon)
      }
    }

  private val release: UIO[Unit] = taken.set(false)
}

object ImportSlot {
  private[domain] val layer: ULayer[ImportSlot] = ZLayer.fromZIO(Ref.make(false).map(new ImportSlot(_)))
}
