/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import java.security.KeyPairGenerator

import org.knora.jwt.Ed25519TestSigner
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.config.ServiceIdentityConfig

@RunWith(classOf[DspZTestJUnitRunner])
class TrustedServiceKeysSpec extends ZIOSpecDefault {

  private val keyA = Ed25519TestSigner.spkiBase64(Ed25519TestSigner.generateKeyPair().getPublic)
  private val keyB = Ed25519TestSigner.spkiBase64(Ed25519TestSigner.generateKeyPair().getPublic)
  private val rsa  = Ed25519TestSigner.spkiBase64(KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic)

  private def entry(name: String, kid: String, publicKey: String) =
    s"""{"name":"$name","kid":"$kid","publicKey":"$publicKey"}"""

  private def array(entries: String*) = entries.mkString("[", ",", "]")

  private def failsWith(raw: String, expected: String, keyMaterial: String*) = {
    val result = TrustedServiceKeys.parse(raw)
    assertTrue(
      result.left.exists(_.contains(expected)),
      keyMaterial.forall(key =>
        result.left.forall(msg => !msg.contains(key.take(12)) && !msg.contains(key.takeRight(12))),
      ),
    )
  }

  def spec: Spec[Any, Any] = suite("TrustedServiceKeys")(
    test("an empty, whitespace-only or [] value means no keys") {
      val results = List("", "   \n", "[]", " [ ] ").map(TrustedServiceKeys.parse)
      assertTrue(results.forall(_.exists(_.isEmpty)))
    },
    test("parses keys by kid, and two keys may share a name") {
      val result = TrustedServiceKeys.parse(array(entry("svc", "svc-1", keyA), entry("svc", "svc-2", keyB)))
      assertTrue(
        result.exists(_.byKid.keySet == Set("svc-1", "svc-2")),
        result.exists(_.byKid.values.forall(_.name.value == "svc")),
        result.exists(_.get("svc-1").exists(_.publicKey.getAlgorithm == "EdDSA")),
      )
    },
    test("accepts a key with PEM armour and line breaks") {
      val pem = s"-----BEGIN PUBLIC KEY-----\\n${keyA.take(20)}\\n${keyA.drop(20)}\\n-----END PUBLIC KEY-----\\n"
      assertTrue(TrustedServiceKeys.parse(array(entry("svc", "svc-1", pem))).exists(_.get("svc-1").isDefined))
    },
    test("rejects malformed JSON and a value that is not an array") {
      failsWith("[{", "is not valid JSON") && failsWith(entry("svc", "svc-1", keyA), "must be a JSON array", keyA)
    },
    test("rejects an entry that is not an object with the three string fields") {
      failsWith(array("""{"name":"svc","kid":"svc-1"}"""), "trusted-keys[0] must be an object")
    },
    test("rejects an unparsable key without quoting it") {
      val garbage = "bm90LWEta2V5LWF0LWFsbC1qdXN0LXNvbWUtdGV4dA=="
      failsWith(array(entry("svc", "svc-1", keyA), entry("svc", "svc-2", garbage)), "[1].publicKey", garbage)
    },
    test("rejects a key that is not an Ed25519 key without quoting it") {
      failsWith(array(entry("svc", "svc-1", rsa)), "[0].publicKey", rsa)
    },
    test("rejects a duplicate kid") {
      failsWith(array(entry("svc", "svc-1", keyA), entry("other", "svc-1", keyB)), "[1].kid is a duplicate", keyA, keyB)
    },
    test("rejects an invalid name") {
      failsWith(array(entry("Bad Name", "svc-1", keyA)), "[0].name", keyA)
    },
    test("rejects a blank kid") {
      failsWith(array(entry("svc", " ", keyA)), "[0].kid must not be blank", keyA)
    },
    test("the layer provides no keys for the default setting") {
      ZIO
        .service[TrustedServiceKeys]
        .map(keys => assertTrue(keys.isEmpty))
        .provide(TrustedServiceKeys.layer, ZLayer.succeed(ServiceIdentityConfig("[]")))
    },
    test("the layer fails startup on an invalid setting") {
      ZIO
        .service[TrustedServiceKeys]
        .provide(TrustedServiceKeys.layer, ZLayer.succeed(ServiceIdentityConfig("{}")))
        .exit
        .map(exit => assertTrue(exit.causeOption.exists(_.isDie)))
    },
  )
}
