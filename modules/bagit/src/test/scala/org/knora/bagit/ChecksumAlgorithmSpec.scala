/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit

import zio.*
import zio.test.*

import java.io.ByteArrayInputStream

object ChecksumAlgorithmSpec extends ZIOSpecDefault {

  private val helloWorld = "Hello, World!"

  def spec: Spec[Any, Any] = suite("ChecksumAlgorithmSpec")(
    suite("computeDigest")(
      test("computes MD5 correctly") {
        val input    = new ByteArrayInputStream(helloWorld.getBytes("UTF-8"))
        val expected = "65a8e27d8879283831b664bd8b7f0ad4"
        for {
          result <- ChecksumAlgorithm.MD5.computeDigest(input)
        } yield assertTrue(result == expected)
      },
      test("computes SHA-1 correctly") {
        val input    = new ByteArrayInputStream(helloWorld.getBytes("UTF-8"))
        val expected = "0a0a9f2a6772942557ab5355d76af442f8f65e01"
        for {
          result <- ChecksumAlgorithm.SHA1.computeDigest(input)
        } yield assertTrue(result == expected)
      },
      test("computes SHA-256 correctly") {
        val input    = new ByteArrayInputStream(helloWorld.getBytes("UTF-8"))
        val expected = "dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f"
        for {
          result <- ChecksumAlgorithm.SHA256.computeDigest(input)
        } yield assertTrue(result == expected)
      },
      test("computes SHA-512 correctly") {
        val input    = new ByteArrayInputStream(helloWorld.getBytes("UTF-8"))
        val expected =
          "374d794a95cdcfd8b35993185fef9ba368f160d8daf432d08ba9f1ed1e5abe6cc69291e0fa2fe0006a52570ef18c19def4e617c33ce52ef0a6e5fbe318cb0387"
        for {
          result <- ChecksumAlgorithm.SHA512.computeDigest(input)
        } yield assertTrue(result == expected)
      },
    ),
    suite("fromBagitName")(
      test("parses 'md5'") {
        assertTrue(ChecksumAlgorithm.fromBagitName("md5") == Right(ChecksumAlgorithm.MD5))
      },
      test("parses 'MD5' (case-insensitive)") {
        assertTrue(ChecksumAlgorithm.fromBagitName("MD5") == Right(ChecksumAlgorithm.MD5))
      },
      test("parses 'sha1'") {
        assertTrue(ChecksumAlgorithm.fromBagitName("sha1") == Right(ChecksumAlgorithm.SHA1))
      },
      test("parses 'sha-1'") {
        assertTrue(ChecksumAlgorithm.fromBagitName("sha-1") == Right(ChecksumAlgorithm.SHA1))
      },
      test("parses 'SHA1' (case-insensitive)") {
        assertTrue(ChecksumAlgorithm.fromBagitName("SHA1") == Right(ChecksumAlgorithm.SHA1))
      },
      test("parses 'sha256'") {
        assertTrue(ChecksumAlgorithm.fromBagitName("sha256") == Right(ChecksumAlgorithm.SHA256))
      },
      test("parses 'sha-256'") {
        assertTrue(ChecksumAlgorithm.fromBagitName("sha-256") == Right(ChecksumAlgorithm.SHA256))
      },
      test("parses 'SHA256' (case-insensitive)") {
        assertTrue(ChecksumAlgorithm.fromBagitName("SHA256") == Right(ChecksumAlgorithm.SHA256))
      },
      test("parses 'sha512'") {
        assertTrue(ChecksumAlgorithm.fromBagitName("sha512") == Right(ChecksumAlgorithm.SHA512))
      },
      test("parses 'sha-512'") {
        assertTrue(ChecksumAlgorithm.fromBagitName("sha-512") == Right(ChecksumAlgorithm.SHA512))
      },
      test("rejects invalid algorithm") {
        assertTrue(ChecksumAlgorithm.fromBagitName("crc32") == Left(BagItError.UnsupportedAlgorithm("crc32")))
      },
    ),
  )
}
