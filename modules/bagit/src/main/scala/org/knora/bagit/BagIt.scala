/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit

import zio.*
import zio.nio.file.Path

import java.io.IOException

import org.knora.bagit.domain.*
import org.knora.bagit.internal.*

object BagIt {

  def create(
    payloadEntries: List[PayloadEntry],
    outputZipPath: Path,
    algorithms: List[ChecksumAlgorithm] = List(ChecksumAlgorithm.SHA512),
    bagInfo: Option[BagInfo] = None,
  ): IO[IOException, Path] =
    BagCreator.createBag(payloadEntries, algorithms, bagInfo, outputZipPath)

  def readAndValidateZip(zipPath: Path): ZIO[Scope, IOException | BagItError, (Bag, Path)] =
    for {
      result        <- BagReader.readFromZip(zipPath)
      (bag, bagRoot) = result
      _             <- BagValidator.validate(bag, bagRoot)
    } yield (bag, bagRoot)
}
