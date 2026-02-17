/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.domain

import org.knora.bagit.ChecksumAlgorithm

final case class ManifestEntry(checksum: String, path: PayloadPath)

final case class Manifest(algorithm: ChecksumAlgorithm, entries: List[ManifestEntry]) {
  def filename: String = s"manifest-${algorithm.bagitName}.txt"
}
