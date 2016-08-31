package org.knora.webapi.util

import java.util.UUID

import org.scalatest.{Matchers, WordSpec}

/**
  * Tests [[KnoraIdUtil]].
  */
class KnoraIdUtilSpec extends WordSpec with Matchers {
    val knoraIdUtil = new KnoraIdUtil

    "The Knora ID utility" should {
        "convert a UUID to Base-64 encoding and back again" in {
            val uuid = UUID.randomUUID
            val base64EncodedUuid = knoraIdUtil.base64EncodeUuid(uuid)
            val base4DecodedUuid = knoraIdUtil.base64DecodeUuid(base64EncodedUuid)
            uuid should be(base4DecodedUuid)
        }
    }
}
