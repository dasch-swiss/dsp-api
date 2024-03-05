/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder
import zio.URIO
import zio.ZIO
import zio.ZLayer
import zio.test.Spec
import zio.test.TestSuccess
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.security.MessageDigest
import java.security.SecureRandom

import org.knora.webapi.slice.admin.domain.model.Password
import org.knora.webapi.slice.admin.domain.model.PasswordHash
import org.knora.webapi.slice.admin.domain.model.PasswordStrength

object PasswordServiceSpec extends ZIOSpecDefault {

  private val strength: PasswordStrength = PasswordStrength.unsafeFrom(10)
  private val bCryptEncoder              = new BCryptPasswordEncoder(strength.value, new SecureRandom())
  // legacy encoders
  private val sCryptEncoder = new SCryptPasswordEncoder(16384, 8, 1, 32, 64)
  private def sha1Encode(rawPassword: String) = PasswordHash.unsafeFrom(
    MessageDigest.getInstance("SHA-1").digest(rawPassword.getBytes("UTF-8")).map("%02x".format(_)).mkString,
  )

  private def hashPassword(pw: String): URIO[PasswordService, PasswordHash] =
    ZIO.serviceWith[PasswordService](_.hashPassword(Password.unsafeFrom(pw)))

  private def matches(raw: String, hash: PasswordHash): URIO[PasswordService, Boolean] =
    ZIO.serviceWith[PasswordService](_.matches(Password.unsafeFrom(raw), hash))

  val spec: Spec[PasswordService, Nothing]#ZSpec[Any, Nothing, TestSuccess] =
    suite("PasswordService")(
      test("hashPassword - should hash new passwords with BCrypt") {
        val somePassword = "password"
        for {
          encoded      <- hashPassword(somePassword)
          bCryptMatches = bCryptEncoder.matches(somePassword, encoded.value)
        } yield assertTrue(bCryptMatches)
      },
      suite("matches")(
        test("given hash and pw match it should match pw and hash (bcrypt)") {
          val somePassword = "password"
          for {
            encoded <- hashPassword(somePassword)
            matched <- matches(somePassword, encoded)
          } yield assertTrue(matched)
        },
        test("given hash and pw match dont match it should now match (bcrypt)") {
          val someOtherPassword = "otherPassword"
          val somePassword      = "password"
          for {
            encoded <- hashPassword(somePassword)
            matched <- matches(someOtherPassword, encoded)
          } yield assertTrue(!matched)
        },
        test("given hash and pw match it should match pw and hash (scrypt)") {
          val somePassword = "password"
          val scryptHash   = PasswordHash.unsafeFrom(sCryptEncoder.encode(somePassword))
          for {
            matched <- matches(somePassword, scryptHash)
          } yield assertTrue(matched)
        },
        test("given hash and pw match dont match it should now match (scrypt)") {
          val someOtherPassword = "otherPassword"
          val somePassword      = "password"
          val scryptHash        = PasswordHash.unsafeFrom(sCryptEncoder.encode(somePassword))
          for {
            matched <- matches(someOtherPassword, scryptHash)
          } yield assertTrue(!matched)
        },
        test("given hash and pw match it should match pw and hash (sha1)") {
          val somePassword = "password"
          val sha1Hash     = sha1Encode(somePassword)
          for {
            matched <- matches(somePassword, sha1Hash)
          } yield assertTrue(matched)
        },
        test("given hash and pw match dont match it should now match (sh1)") {
          val someOtherPassword = "otherPassword"
          val somePassword      = "password"
          val sha1Hash          = sha1Encode(somePassword)
          for {
            matched <- matches(someOtherPassword, sha1Hash)
          } yield assertTrue(!matched)
        },
      ),
    ).provide(ZLayer.succeed(strength) >>> ZLayer.derive[PasswordService])
}
