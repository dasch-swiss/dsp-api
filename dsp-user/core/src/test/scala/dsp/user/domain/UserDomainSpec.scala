/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.domain

import dsp.user.domain.User
import dsp.user.domain._
import dsp.valueobjects.User._
import dsp.user.sharedtestdata.SharedTestData
import zio.ZLayer
import zio._
import zio.test._

/**
 * This spec is used to test [[dsp.user.domain.UserDomain]].
 */
object UserDomainSpec extends ZIOSpecDefault {

  def spec = (userDomainTests)

  val userDomainTests = suite("UserDomain")(
    test("compare two users") {
      val user         = SharedTestData.simpleUser1
      val userEqual    = SharedTestData.simpleUser1 // same as user, i.e. has same UserId
      val userNotEqual = SharedTestData.simpleUser2
      for {
        isEqualtoItself        <- ZIO.succeed(user.equals(user))
        isEqualtoEqualUser     <- ZIO.succeed(user.equals(userEqual))
        isEqualtoDifferentUser <- ZIO.succeed(user.equals(userNotEqual))
      } yield assertTrue(isEqualtoItself == true) &&
        assertTrue(isEqualtoEqualUser == true) &&
        assertTrue(isEqualtoDifferentUser == false)
    },
    test("update the username") {
      val user     = SharedTestData.simpleUser1
      val newValue = Username.make("newUsername").fold(e => throw e.head, v => v)
      for {
        updatedUser <- ZIO.succeed(user.updateUsername(newValue))
        _           <- ZIO.debug(updatedUser)
      } yield assertTrue(updatedUser.username == newValue) &&
        assertTrue(updatedUser.username != user.username) &&
        assertTrue(updatedUser.email == user.email) &&
        assertTrue(updatedUser.givenName == user.givenName) &&
        assertTrue(updatedUser.familyName == user.familyName) &&
        assertTrue(updatedUser.password == user.password) &&
        assertTrue(updatedUser.language == user.language) &&
        assertTrue(updatedUser.status == user.status)
    },
    test("update the email") {
      val user     = SharedTestData.simpleUser1
      val newValue = Email.make("newEmail@mail.com").fold(e => throw e.head, v => v)
      for {
        updatedUser <- ZIO.succeed(user.updateEmail(newValue))
        _           <- ZIO.debug(updatedUser)
      } yield assertTrue(updatedUser.email == newValue) &&
        assertTrue(updatedUser.email != user.email) &&
        assertTrue(updatedUser.givenName == user.givenName) &&
        assertTrue(updatedUser.familyName == user.familyName) &&
        assertTrue(updatedUser.password == user.password) &&
        assertTrue(updatedUser.language == user.language) &&
        assertTrue(updatedUser.status == user.status)
    },
    test("update the givenName") {
      val user     = SharedTestData.simpleUser1
      val newValue = GivenName.make("newGivenName").fold(e => throw e.head, v => v)
      for {
        updatedUser <- ZIO.succeed(user.updateGivenName(newValue))
        _           <- ZIO.debug(updatedUser)
      } yield assertTrue(updatedUser.givenName == newValue) &&
        assertTrue(updatedUser.email == user.email) &&
        assertTrue(updatedUser.givenName != user.givenName) &&
        assertTrue(updatedUser.familyName == user.familyName) &&
        assertTrue(updatedUser.password == user.password) &&
        assertTrue(updatedUser.language == user.language) &&
        assertTrue(updatedUser.status == user.status)
    },
    test("update the familyName") {
      val user     = SharedTestData.simpleUser1
      val newValue = FamilyName.make("newFamilyName").fold(e => throw e.head, v => v)
      for {
        updatedUser <- ZIO.succeed(user.updateFamilyName(newValue))
        _           <- ZIO.debug(updatedUser)
      } yield assertTrue(updatedUser.familyName == newValue) &&
        assertTrue(updatedUser.email == user.email) &&
        assertTrue(updatedUser.givenName == user.givenName) &&
        assertTrue(updatedUser.familyName != user.familyName) &&
        assertTrue(updatedUser.password == user.password) &&
        assertTrue(updatedUser.language == user.language) &&
        assertTrue(updatedUser.status == user.status)
    },
    test("update the password") {
      val user     = SharedTestData.simpleUser1
      val newValue = PasswordHash.make("newPassword1").fold(e => throw e.head, v => v)
      for {
        updatedUser <- ZIO.succeed(user.updatePassword(newValue))
        _           <- ZIO.debug(updatedUser)
      } yield assertTrue(updatedUser.password == newValue) &&
        assertTrue(updatedUser.email == user.email) &&
        assertTrue(updatedUser.givenName == user.givenName) &&
        assertTrue(updatedUser.familyName == user.familyName) &&
        assertTrue(updatedUser.password != user.password) &&
        assertTrue(updatedUser.language == user.language) &&
        assertTrue(updatedUser.status == user.status)
    },
    test("update the language") {
      val user     = SharedTestData.simpleUser1
      val newValue = LanguageCode.make("fr").fold(e => throw e.head, v => v)
      for {
        updatedUser <- ZIO.succeed(user.updateLanguage(newValue))
        _           <- ZIO.debug(updatedUser)
      } yield assertTrue(updatedUser.language == newValue) &&
        assertTrue(updatedUser.email == user.email) &&
        assertTrue(updatedUser.givenName == user.givenName) &&
        assertTrue(updatedUser.familyName == user.familyName) &&
        assertTrue(updatedUser.password == user.password) &&
        assertTrue(updatedUser.language != user.language) &&
        assertTrue(updatedUser.status == user.status)
    },
    test("update the status") {
      val user     = SharedTestData.simpleUser1
      val newValue = UserStatus.make(false).fold(e => throw e.head, v => v)
      for {
        updatedUser <- ZIO.succeed(user.updateStatus(newValue))
        _           <- ZIO.debug(updatedUser)
      } yield assertTrue(updatedUser.status == newValue) &&
        assertTrue(updatedUser.email == user.email) &&
        assertTrue(updatedUser.givenName == user.givenName) &&
        assertTrue(updatedUser.familyName == user.familyName) &&
        assertTrue(updatedUser.password == user.password) &&
        assertTrue(updatedUser.language == user.language) &&
        assertTrue(updatedUser.status != user.status)
    }
  )
}
