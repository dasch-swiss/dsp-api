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

  private val userDomainTests = suite("UserDomain")(
    test("compare two users") {
      val user         = SharedTestData.normalUser1
      val userEqual    = SharedTestData.normalUser1 // same as user, i.e. has same UserId
      val userNotEqual = SharedTestData.normalUser2

      val isEqualtoItself        = user.equals(user)
      val isEqualtoEqualUser     = user.equals(userEqual)
      val isEqualtoDifferentUser = user.equals(userNotEqual)

      assertTrue(isEqualtoItself) &&
      assertTrue(isEqualtoEqualUser) &&
      assertTrue(!isEqualtoDifferentUser)
    },
    test("create a user") {
      val user =
        User.make(
          SharedTestData.givenName1,
          SharedTestData.familyName1,
          SharedTestData.username1,
          SharedTestData.email1,
          SharedTestData.password1,
          SharedTestData.languageEn,
          SharedTestData.statusTrue
        )

      assertTrue(user.username == SharedTestData.username1) &&
      assertTrue(user.email == SharedTestData.email1) &&
      assertTrue(user.givenName == SharedTestData.givenName1) &&
      assertTrue(user.familyName == SharedTestData.familyName1) &&
      assertTrue(user.password == SharedTestData.password1) &&
      assertTrue(user.language == SharedTestData.languageEn) &&
      assertTrue(user.status == SharedTestData.statusTrue)
    },
    test("update the username") {
      val user     = SharedTestData.normalUser1
      val newValue = Username.make("newUsername").fold(e => throw e.head, v => v)

      val updatedUser = user.updateUsername(newValue)

      assertTrue(updatedUser.username == newValue) &&
      assertTrue(updatedUser.username != user.username) &&
      assertTrue(updatedUser.email == user.email) &&
      assertTrue(updatedUser.givenName == user.givenName) &&
      assertTrue(updatedUser.familyName == user.familyName) &&
      assertTrue(updatedUser.password == user.password) &&
      assertTrue(updatedUser.language == user.language) &&
      assertTrue(updatedUser.status == user.status)
    },
    test("update the email") {
      val user     = SharedTestData.normalUser1
      val newValue = Email.make("newEmail@mail.com").fold(e => throw e.head, v => v)

      val updatedUser = user.updateEmail(newValue)

      assertTrue(updatedUser.email == newValue) &&
      assertTrue(updatedUser.email != user.email) &&
      assertTrue(updatedUser.givenName == user.givenName) &&
      assertTrue(updatedUser.familyName == user.familyName) &&
      assertTrue(updatedUser.password == user.password) &&
      assertTrue(updatedUser.language == user.language) &&
      assertTrue(updatedUser.status == user.status)
    },
    test("update the givenName") {
      val user     = SharedTestData.normalUser1
      val newValue = GivenName.make("newGivenName").fold(e => throw e.head, v => v)

      val updatedUser = user.updateGivenName(newValue)

      assertTrue(updatedUser.givenName == newValue) &&
      assertTrue(updatedUser.email == user.email) &&
      assertTrue(updatedUser.givenName != user.givenName) &&
      assertTrue(updatedUser.familyName == user.familyName) &&
      assertTrue(updatedUser.password == user.password) &&
      assertTrue(updatedUser.language == user.language) &&
      assertTrue(updatedUser.status == user.status)
    },
    test("update the familyName") {
      val user     = SharedTestData.normalUser1
      val newValue = FamilyName.make("newFamilyName").fold(e => throw e.head, v => v)

      val updatedUser = user.updateFamilyName(newValue)

      assertTrue(updatedUser.familyName == newValue) &&
      assertTrue(updatedUser.email == user.email) &&
      assertTrue(updatedUser.givenName == user.givenName) &&
      assertTrue(updatedUser.familyName != user.familyName) &&
      assertTrue(updatedUser.password == user.password) &&
      assertTrue(updatedUser.language == user.language) &&
      assertTrue(updatedUser.status == user.status)
    },
    test("update the password") {
      val user     = SharedTestData.normalUser1
      val newValue = PasswordHash.make("newPassword1", SharedTestData.passwordStrength).fold(e => throw e.head, v => v)

      val updatedUser = user.updatePassword(newValue)

      assertTrue(updatedUser.password == newValue) &&
      assertTrue(updatedUser.email == user.email) &&
      assertTrue(updatedUser.givenName == user.givenName) &&
      assertTrue(updatedUser.familyName == user.familyName) &&
      assertTrue(updatedUser.password != user.password) &&
      assertTrue(updatedUser.language == user.language) &&
      assertTrue(updatedUser.status == user.status)
    },
    test("update the language") {
      val user     = SharedTestData.normalUser1
      val newValue = LanguageCode.make("fr").fold(e => throw e.head, v => v)

      val updatedUser = user.updateLanguage(newValue)

      assertTrue(updatedUser.language == newValue) &&
      assertTrue(updatedUser.email == user.email) &&
      assertTrue(updatedUser.givenName == user.givenName) &&
      assertTrue(updatedUser.familyName == user.familyName) &&
      assertTrue(updatedUser.password == user.password) &&
      assertTrue(updatedUser.language != user.language) &&
      assertTrue(updatedUser.status == user.status)
    },
    test("update the status") {
      val user     = SharedTestData.normalUser1
      val newValue = UserStatus.make(false).fold(e => throw e.head, v => v)

      val updatedUser = user.updateStatus(newValue)

      assertTrue(updatedUser.status == newValue) &&
      assertTrue(updatedUser.email == user.email) &&
      assertTrue(updatedUser.givenName == user.givenName) &&
      assertTrue(updatedUser.familyName == user.familyName) &&
      assertTrue(updatedUser.password == user.password) &&
      assertTrue(updatedUser.language == user.language) &&
      assertTrue(updatedUser.status != user.status)
    }
  )
}
