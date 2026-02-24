/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit

final case class ExtractionLimits(
  maxTotalBytes: Long = 200L * 1024 * 1024 * 1024,
  maxEntryCount: Int = 100_000,
  maxSingleEntryBytes: Long = 20L * 1024 * 1024 * 1024,
)

object ExtractionLimits {
  val default: ExtractionLimits = ExtractionLimits()
}
