/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit

import zio.*

import java.io.InputStream
import java.security.MessageDigest

enum ChecksumAlgorithm(val bagitName: String, val javaName: String) {
  case MD5    extends ChecksumAlgorithm("md5", "MD5")
  case SHA1   extends ChecksumAlgorithm("sha1", "SHA-1")
  case SHA256 extends ChecksumAlgorithm("sha256", "SHA-256")
  case SHA512 extends ChecksumAlgorithm("sha512", "SHA-512")
}

object ChecksumAlgorithm {

  def fromBagitName(name: String): Either[BagItError.UnsupportedAlgorithm, ChecksumAlgorithm] =
    name.toLowerCase match {
      case "md5"                => Right(ChecksumAlgorithm.MD5)
      case "sha1" | "sha-1"     => Right(ChecksumAlgorithm.SHA1)
      case "sha256" | "sha-256" => Right(ChecksumAlgorithm.SHA256)
      case "sha512" | "sha-512" => Right(ChecksumAlgorithm.SHA512)
      case other                => Left(BagItError.UnsupportedAlgorithm(other))
    }

  extension (algorithm: ChecksumAlgorithm) {
    def computeDigest(input: InputStream): Task[String] =
      ZIO.attemptBlocking {
        val digest = MessageDigest.getInstance(algorithm.javaName)
        val buffer = new Array[Byte](8192)
        var read   = input.read(buffer)
        while (read != -1) {
          digest.update(buffer, 0, read)
          read = input.read(buffer)
        }
        digest.digest().map(b => String.format("%02x", b)).mkString
      }
  }
}
