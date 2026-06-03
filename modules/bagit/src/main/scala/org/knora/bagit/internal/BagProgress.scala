/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

/**
 * Pure helpers for the BagIt payload-packing progress heartbeat.
 *
 * The decision of *when* to emit a progress line is percentage-driven (one line per `stepPercent`
 * of bytes packed, so the number of lines is bounded regardless of file count) with a wall-clock
 * floor (never stay silent longer than `maxGap`, so a single huge file or a slow disk still shows
 * signs of life). Keeping these functions free of effects and mutable state makes them
 * deterministically testable; [[BagCreator]] supplies the clock, counters and logging.
 */
object BagProgress {

  /** Reporter bookkeeping carried between heartbeat ticks. */
  final case class ReporterState(lastEmitNanos: Long, lastStep: Int)

  /**
   * Emit a progress line when a new percentage step has been reached since the last line, or when
   * the maximum silent gap has elapsed (whichever comes first).
   */
  def shouldEmit(state: ReporterState, currentStep: Int, nowNanos: Long, maxGapNanos: Long): Boolean =
    currentStep > state.lastStep || (nowNanos - state.lastEmitNanos) >= maxGapNanos

  /**
   * Percentage `[0, 99]` of total bytes packed (`0` when nothing is known yet). Capped below 100 so
   * the heartbeat never claims completion before the final summary line is logged.
   */
  def percentDone(bytesDone: Long, totalBytes: Long): Int =
    if (totalBytes <= 0L) 0 else math.min(99, ((bytesDone.toDouble / totalBytes) * 100).toInt)

  /** Which `stepPercent`-sized progress bucket the current byte count falls into (e.g. 0, 1, 2, ... for 10% steps). */
  def stepIndex(bytesDone: Long, totalBytes: Long, stepPercent: Int): Int =
    percentDone(bytesDone, totalBytes) / stepPercent

  /** Estimated nanoseconds remaining assuming throughput stays constant; `None` until progress starts. */
  def etaNanos(elapsedNanos: Long, bytesDone: Long, totalBytes: Long): Option[Long] =
    if (bytesDone <= 0L || totalBytes <= bytesDone) None
    else Some((elapsedNanos.toDouble * (totalBytes - bytesDone) / bytesDone).toLong)

  /** Human-readable byte count using decimal (1000-based) units, e.g. `15.4 GB`. */
  def formatBytes(bytes: Long): String = {
    val units = Vector("B", "KB", "MB", "GB", "TB", "PB")
    @annotation.tailrec
    def reduce(value: Double, idx: Int): (Double, Int) =
      if (value >= 1000.0 && idx < units.size - 1) reduce(value / 1000.0, idx + 1) else (value, idx)
    if (bytes < 1000L) s"$bytes B"
    else {
      val (value, idx) = reduce(bytes.toDouble, 0)
      f"$value%.1f ${units(idx)}"
    }
  }

  /** Human-readable duration, `m:ss` or `h:mm:ss`. Negative inputs are clamped to zero. */
  def formatDuration(nanos: Long): String = {
    val totalSeconds = math.max(0L, nanos) / 1000000000L
    val hours        = totalSeconds / 3600
    val minutes      = (totalSeconds % 3600) / 60
    val seconds      = totalSeconds  % 60
    if (hours > 0) f"$hours%d:$minutes%02d:$seconds%02d" else f"$minutes%d:$seconds%02d"
  }

  /** The line logged once before packing begins, advertising the totals so operators can estimate. */
  def startLine(totalFiles: Long, totalBytes: Long): String =
    s"BagIt: packing $totalFiles files, ${formatBytes(totalBytes)}"

  /** The periodic heartbeat line logged while packing. */
  def progressLine(
    filesDone: Long,
    totalFiles: Long,
    bytesDone: Long,
    totalBytes: Long,
    elapsedNanos: Long,
  ): String = {
    val pct = percentDone(bytesDone, totalBytes)
    val eta = etaNanos(elapsedNanos, bytesDone, totalBytes).map(formatDuration).getOrElse("?")
    s"BagIt progress: $filesDone/$totalFiles files, ${formatBytes(bytesDone)} / ${formatBytes(totalBytes)} " +
      s"($pct%), elapsed ${formatDuration(elapsedNanos)}, ETA ~$eta"
  }

  /** The final summary line logged once packing completes. */
  def doneLine(totalFiles: Long, totalBytes: Long, elapsedNanos: Long): String =
    s"BagIt: packed $totalFiles files, ${formatBytes(totalBytes)} in ${formatDuration(elapsedNanos)}"
}
