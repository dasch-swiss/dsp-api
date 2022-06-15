/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.domain

import dsp.user.domain.User
import dsp.user.domain._
import dsp.valueobjects.User._
import zio.ZLayer
import zio._
import zio.test._

/**
 * This spec is used to test [[dsp.user.domain.UserDomain]].
 */
object UserDomainSpec extends ZIOSpecDefault {

  def spec = (userDomainTests)

  private val givenName  = GivenName.make("GivenName1").fold(e => throw e.head, v => v)
  private val familyName = FamilyName.make("familyName1").fold(e => throw e.head, v => v)
  private val username   = Username.make("username1").fold(e => throw e.head, v => v)
  private val email      = Email.make("email1@email.com").fold(e => throw e.head, v => v)
  private val password   = PasswordHash.make("password1").fold(e => throw e.head, v => v)
  private val language   = LanguageCode.make("en").fold(e => throw e.head, v => v)
  private val status     = UserStatus.make(true).fold(e => throw e.head, v => v)
  private val user = User.make(
    givenName = givenName,
    familyName = familyName,
    username = username,
    email = email,
    password = password,
    language = language,
    status = status
  )

  val userDomainTests = suite("UserDomain")(
    test("update the username") {
      val newValue = Username.make("newUsername").fold(e => throw e.head, v => v)
      for {
        updatedUser <- ZIO.succeed(user.updateUsername(newValue))
        _           <- ZIO.debug(updatedUser)
      } yield assertTrue(updatedUser.username == newValue) &&
        assertTrue(updatedUser.username != username) &&
        assertTrue(updatedUser.email == email) &&
        assertTrue(updatedUser.givenName == givenName) &&
        assertTrue(updatedUser.familyName == familyName) &&
        assertTrue(updatedUser.password == password) &&
        assertTrue(updatedUser.language == language) &&
        assertTrue(updatedUser.status == status)
    },
    test("update the email") {
      val newValue = Email.make("new.mail1@email.com").fold(e => throw e.head, v => v)
      for {
        retrievedUser <- ZIO.succeed(user.updateEmail(newValue))
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser.email == newValue) &&
        assertTrue(retrievedUser.email != email) &&
        assertTrue(retrievedUser.givenName == givenName) &&
        assertTrue(retrievedUser.familyName == familyName) &&
        assertTrue(retrievedUser.password == password) &&
        assertTrue(retrievedUser.language == language) &&
        assertTrue(retrievedUser.status == status)
    },
    test("update the givenName") {
      val newValue = GivenName.make("newGivenName").fold(e => throw e.head, v => v)
      for {
        updatedUser <- ZIO.succeed(user.updateGivenName(newValue))
        _           <- ZIO.debug(updatedUser)
      } yield assertTrue(updatedUser.givenName == newValue) &&
        assertTrue(updatedUser.email == email) &&
        assertTrue(updatedUser.givenName != givenName) &&
        assertTrue(updatedUser.familyName == familyName) &&
        assertTrue(updatedUser.password == password) &&
        assertTrue(updatedUser.language == language) &&
        assertTrue(updatedUser.status == status)
    },
    test("update the familyName") {
      val newValue = FamilyName.make("newFamilyName").fold(e => throw e.head, v => v)
      for {
        updatedUser <- ZIO.succeed(user.updateFamilyName(newValue))
        _           <- ZIO.debug(updatedUser)
      } yield assertTrue(updatedUser.familyName == newValue) &&
        assertTrue(updatedUser.email == email) &&
        assertTrue(updatedUser.givenName == givenName) &&
        assertTrue(updatedUser.familyName != familyName) &&
        assertTrue(updatedUser.password == password) &&
        assertTrue(updatedUser.language == language) &&
        assertTrue(updatedUser.status == status)
    },
    test("update the password") {
      val newValue = PasswordHash.make("newPassword1").fold(e => throw e.head, v => v)
      for {
        updatedUser <- ZIO.succeed(user.updatePassword(newValue))
        _           <- ZIO.debug(updatedUser)
      } yield assertTrue(updatedUser.password == newValue) &&
        assertTrue(updatedUser.email == email) &&
        assertTrue(updatedUser.givenName == givenName) &&
        assertTrue(updatedUser.familyName == familyName) &&
        assertTrue(updatedUser.password != password) &&
        assertTrue(updatedUser.language == language) &&
        assertTrue(updatedUser.status == status)
    },
    test("update the language") {
      val newValue = LanguageCode.make("fr").fold(e => throw e.head, v => v)
      for {
        updatedUser <- ZIO.succeed(user.updateLanguage(newValue))
        _           <- ZIO.debug(updatedUser)
      } yield assertTrue(updatedUser.language == newValue) &&
        assertTrue(updatedUser.email == email) &&
        assertTrue(updatedUser.givenName == givenName) &&
        assertTrue(updatedUser.familyName == familyName) &&
        assertTrue(updatedUser.password == password) &&
        assertTrue(updatedUser.language != language) &&
        assertTrue(updatedUser.status == status)
    },
    test("update the status") {
      val newValue = UserStatus.make(false).fold(e => throw e.head, v => v)
      for {
        updatedUser <- ZIO.succeed(user.updateStatus(newValue))
        _           <- ZIO.debug(updatedUser)
      } yield assertTrue(updatedUser.status == newValue) &&
        assertTrue(updatedUser.email == email) &&
        assertTrue(updatedUser.givenName == givenName) &&
        assertTrue(updatedUser.familyName == familyName) &&
        assertTrue(updatedUser.password == password) &&
        assertTrue(updatedUser.language == language) &&
        assertTrue(updatedUser.status != status)
    }
  )
}
