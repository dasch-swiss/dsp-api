/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

import zio.test.*

import org.knora.bagit.internal.BagProgress.ReporterState

object BagProgressSpec extends ZIOSpecDefault {

  private val oneSecond = 1000000000L

  def spec: Spec[Any, Any] = suite("BagProgressSpec")(
    suite("formatBytes")(
      test("uses bytes below 1000") {
        assertTrue(BagProgress.formatBytes(0L) == "0 B", BagProgress.formatBytes(999L) == "999 B")
      },
      test("scales up through decimal units") {
        assertTrue(
          BagProgress.formatBytes(1000L) == "1.0 KB",
          BagProgress.formatBytes(1500000L) == "1.5 MB",
          BagProgress.formatBytes(15400000000L) == "15.4 GB",
        )
      },
    ),
    suite("formatDuration")(
      test("renders m:ss below an hour") {
        assertTrue(
          BagProgress.formatDuration(0L) == "0:00",
          BagProgress.formatDuration(5 * oneSecond) == "0:05",
          BagProgress.formatDuration(65 * oneSecond) == "1:05",
        )
      },
      test("renders h:mm:ss at or above an hour") {
        assertTrue(BagProgress.formatDuration(3661 * oneSecond) == "1:01:01")
      },
      test("clamps negative input to zero") {
        assertTrue(BagProgress.formatDuration(-1L) == "0:00")
      },
    ),
    suite("percentDone")(
      test("is zero when nothing is known") {
        assertTrue(BagProgress.percentDone(0L, 0L) == 0, BagProgress.percentDone(0L, 100L) == 0)
      },
      test("reports the fraction packed") {
        assertTrue(BagProgress.percentDone(50L, 100L) == 50)
      },
      test("never reaches 100 so the summary line owns completion") {
        assertTrue(BagProgress.percentDone(100L, 100L) == 99, BagProgress.percentDone(200L, 100L) == 99)
      },
    ),
    suite("stepIndex")(
      test("buckets progress into stepPercent-sized buckets") {
        assertTrue(BagProgress.stepIndex(550L, 1000L, 10) == 5, BagProgress.stepIndex(90L, 1000L, 10) == 0)
      },
      test("is zero before any progress") {
        assertTrue(BagProgress.stepIndex(0L, 1000L, 10) == 0)
      },
    ),
    suite("etaNanos")(
      test("is empty until progress starts and once finished") {
        assertTrue(
          BagProgress.etaNanos(10 * oneSecond, 0L, 100L).isEmpty,
          BagProgress.etaNanos(10 * oneSecond, 100L, 100L).isEmpty,
        )
      },
      test("extrapolates the remaining time from current throughput") {
        assertTrue(BagProgress.etaNanos(10 * oneSecond, 50L, 100L).contains(10 * oneSecond))
      },
    ),
    suite("shouldEmit")(
      test("emits when a new step is reached") {
        assertTrue(
          BagProgress.shouldEmit(ReporterState(0L, 2), currentStep = 3, nowNanos = 1L, maxGapNanos = Long.MaxValue),
        )
      },
      test("emits when the silent gap is exceeded even without a new step") {
        assertTrue(BagProgress.shouldEmit(ReporterState(0L, 5), currentStep = 5, nowNanos = 10L, maxGapNanos = 5L))
      },
      test("stays quiet within the same step and gap") {
        assertTrue(!BagProgress.shouldEmit(ReporterState(0L, 5), currentStep = 5, nowNanos = 3L, maxGapNanos = 5L))
      },
    ),
    suite("log lines")(
      test("start, progress and done lines carry the key figures") {
        val progress = BagProgress.progressLine(8L, 16L, 1500000L, 15400000000L, 65 * oneSecond)
        assertTrue(
          BagProgress.startLine(16L, 15400000000L) == "BagIt: packing 16 files, 15.4 GB",
          progress.contains("8/16 files"),
          progress.contains("1.5 MB / 15.4 GB"),
          progress.contains("elapsed 1:05"),
          progress.contains("ETA ~"),
          BagProgress.doneLine(16L, 15400000000L, 65 * oneSecond) == "BagIt: packed 16 files, 15.4 GB in 1:05",
        )
      },
      test("progress line renders the percentage packed") {
        assertTrue(BagProgress.progressLine(5L, 10L, 50L, 100L, 10 * oneSecond).contains("(50%)"))
      },
    ),
  )
}
