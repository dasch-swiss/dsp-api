/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.Scope
import zio.test._

import java.util.UUID

object UuidUtilSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment & Scope, Any] = suite("UuidUtil")(base64EncodeAndBase64Decode + hasSupportedVersion)

  private val base64EncodeAndBase64Decode = test("encode UUID to Base64 and decode again") {
    val uuid              = UUID.randomUUID
    val base64EncodedUuid = UuidUtil.base64Encode(uuid)
    val base64DecodedUuid = UuidUtil.base64Decode(base64EncodedUuid)

    assertTrue(base64DecodedUuid.toOption.contains(uuid))
  }

  private val hasSupportedVersion =
    test("return TRUE for BEOL and project IRIs that contain UUID version 4 or 5, otherwise return FALSE") {
      val iriV3             = "http://rdfh.ch/0000/rKAU0FNjPUKWqOT8MEW_UQ"
      val iriV4             = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA"
      val iriV5             = "http://rdfh.ch/080C/Ef9heHjPWDS7dMR_gGax2Q"
      val beolExceptionIri  = "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF"
      val uuidV3            = UuidUtil.hasSupportedVersion(iriV3)
      val uuidV4            = UuidUtil.hasSupportedVersion(iriV4)
      val uuidV5            = UuidUtil.hasSupportedVersion(iriV5)
      val beolExceptionUuid = UuidUtil.hasSupportedVersion(beolExceptionIri)

      assertTrue(!uuidV3, uuidV4, uuidV5, beolExceptionUuid)
    }
}
