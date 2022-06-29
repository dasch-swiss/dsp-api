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

  def spec = (compareUsersTest + createUserTest + updateUserTest)

  private val compareUsersTest = suite("compareUsers")(
    test("compare two users") {
      val user         = SharedTestData.user1
      val userEqual    = SharedTestData.user1 // same as user, i.e. has same UserId
      val userNotEqual = SharedTestData.user2

      assertTrue(user.equals(userEqual)) &&
      assertTrue(!user.equals(userNotEqual)) &&
      assertTrue(user == userEqual) &&
      assertTrue(user != userNotEqual)
    }
  )

  private val createUserTest = suite("createUser")(
    test("create a user") {
      for {
        givenName  <- SharedTestData.givenName1.toZIO
        familyName <- SharedTestData.familyName1.toZIO
        username   <- SharedTestData.username1.toZIO
        email      <- SharedTestData.email1.toZIO
        password   <- SharedTestData.password1.toZIO
        language   <- SharedTestData.languageEn.toZIO
        status     <- SharedTestData.statusTrue.toZIO
        user <- User
                  .make(
                    givenName,
                    familyName,
                    username,
                    email,
                    password,
                    language,
                    status
                  )
                  .toZIO
      } yield assertTrue(user.username == username) &&
        assertTrue(user.email == email) &&
        assertTrue(user.givenName == givenName) &&
        assertTrue(user.familyName == familyName) &&
        assertTrue(user.password == password) &&
        assertTrue(user.language == language) &&
        assertTrue(user.status == status)
    }
  )

  private val updateUserTest = suite("updateUser")(
    test("update the username") {
      for {
        user       <- SharedTestData.user1.toZIO
        newValue   <- Username.make("newUsername").toZIO
        updatedUser = user.updateUsername(newValue)
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
      for {
        user       <- SharedTestData.user1.toZIO
        newValue   <- Email.make("newEmail@mail.com").toZIO
        updatedUser = user.updateEmail(newValue)
      } yield assertTrue(updatedUser.email == newValue) &&
        assertTrue(updatedUser.email != user.email) &&
        assertTrue(updatedUser.givenName == user.givenName) &&
        assertTrue(updatedUser.familyName == user.familyName) &&
        assertTrue(updatedUser.password == user.password) &&
        assertTrue(updatedUser.language == user.language) &&
        assertTrue(updatedUser.status == user.status)
    },
    test("update the givenName") {
      for {
        user       <- SharedTestData.user1.toZIO
        newValue   <- GivenName.make("newGivenName").toZIO
        updatedUser = user.updateGivenName(newValue)
      } yield assertTrue(updatedUser.givenName == newValue) &&
        assertTrue(updatedUser.email == user.email) &&
        assertTrue(updatedUser.givenName != user.givenName) &&
        assertTrue(updatedUser.familyName == user.familyName) &&
        assertTrue(updatedUser.password == user.password) &&
        assertTrue(updatedUser.language == user.language) &&
        assertTrue(updatedUser.status == user.status)
    },
    test("update the familyName") {
      for {
        user       <- SharedTestData.user1.toZIO
        newValue   <- FamilyName.make("newFamilyName").toZIO
        updatedUser = user.updateFamilyName(newValue)
      } yield assertTrue(updatedUser.familyName == newValue) &&
        assertTrue(updatedUser.email == user.email) &&
        assertTrue(updatedUser.givenName == user.givenName) &&
        assertTrue(updatedUser.familyName != user.familyName) &&
        assertTrue(updatedUser.password == user.password) &&
        assertTrue(updatedUser.language == user.language) &&
        assertTrue(updatedUser.status == user.status)
    },
    test("update the password") {
      for {
        user             <- SharedTestData.user1.toZIO
        passwordStrength <- SharedTestData.passwordStrength.toZIO
        newValue         <- PasswordHash.make("newPassword1", passwordStrength).toZIO
        updatedUser       = user.updatePassword(newValue)
      } yield assertTrue(updatedUser.password == newValue) &&
        assertTrue(updatedUser.email == user.email) &&
        assertTrue(updatedUser.givenName == user.givenName) &&
        assertTrue(updatedUser.familyName == user.familyName) &&
        assertTrue(updatedUser.password != user.password) &&
        assertTrue(updatedUser.language == user.language) &&
        assertTrue(updatedUser.status == user.status)
    },
    test("update the language") {
      for {
        user       <- SharedTestData.user1.toZIO
        newValue   <- LanguageCode.make("fr").toZIO
        updatedUser = user.updateLanguage(newValue)
      } yield assertTrue(updatedUser.language == newValue) &&
        assertTrue(updatedUser.email == user.email) &&
        assertTrue(updatedUser.givenName == user.givenName) &&
        assertTrue(updatedUser.familyName == user.familyName) &&
        assertTrue(updatedUser.password == user.password) &&
        assertTrue(updatedUser.language != user.language) &&
        assertTrue(updatedUser.status == user.status)
    },
    test("update the status") {
      for {
        user       <- SharedTestData.user1.toZIO
        newValue   <- UserStatus.make(false).toZIO
        updatedUser = user.updateStatus(newValue)
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
