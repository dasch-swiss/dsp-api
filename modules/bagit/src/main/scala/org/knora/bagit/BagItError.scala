/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit

sealed trait BagItError {
  def message: String
}

object BagItError {

  // Structural errors
  case object MissingBagitTxt extends BagItError {
    def message: String = "Missing required file: bagit.txt"
  }

  final case class InvalidBagitTxt(detail: String) extends BagItError {
    def message: String = s"Invalid bagit.txt: $detail"
  }

  case object MissingPayloadDirectory extends BagItError {
    def message: String = "Missing required directory: data/"
  }

  case object MissingPayloadManifest extends BagItError {
    def message: String = "Missing required payload manifest (manifest-*.txt)"
  }

  final case class InvalidManifestEntry(line: String, detail: String) extends BagItError {
    def message: String = s"Invalid manifest entry '$line': $detail"
  }

  final case class UnsupportedAlgorithm(algorithm: String) extends BagItError {
    def message: String = s"Unsupported checksum algorithm: $algorithm"
  }

  // Completeness errors
  final case class FileNotInManifest(path: String) extends BagItError {
    def message: String = s"Payload file not listed in manifest: $path"
  }

  final case class ManifestEntryMissing(path: String) extends BagItError {
    def message: String = s"Manifest entry references missing file: $path"
  }

  // Validity errors
  final case class ChecksumMismatch(path: String, algorithm: String, expected: String, actual: String)
      extends BagItError {
    def message: String =
      s"Checksum mismatch for $path ($algorithm): expected $expected, got $actual"
  }

  // Security errors
  final case class PathTraversalDetected(path: String) extends BagItError {
    def message: String = s"Path traversal detected: $path"
  }

  final case class ExtractionLimitExceeded(detail: String) extends BagItError {
    def message: String = s"Extraction limit exceeded: $detail"
  }

  final case class FileTooLarge(fileName: String, size: Long) extends BagItError {
    def message: String = s"File too large: $fileName ($size bytes)"
  }
}
