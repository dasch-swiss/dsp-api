/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.domain

import zio.test._

import dsp.user.domain.User
import dsp.user.sharedtestdata.SharedTestData
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.User._

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
      (for {
        id         <- SharedTestData.userId1
        givenName  <- SharedTestData.givenName1
        familyName <- SharedTestData.familyName1
        username   <- SharedTestData.username1
        email      <- SharedTestData.email1
        password   <- SharedTestData.password1
        language   <- SharedTestData.languageEn
        status     <- SharedTestData.statusTrue
        user <- User
                  .make(
                    id,
                    givenName,
                    familyName,
                    username,
                    email,
                    password,
                    language,
                    status
                  )
      } yield assertTrue(user.username == username) &&
        assertTrue(user.email == email) &&
        assertTrue(user.givenName == givenName) &&
        assertTrue(user.familyName == familyName) &&
        assertTrue(user.password == password) &&
        assertTrue(user.language == language) &&
        assertTrue(user.status == status)).toZIO
    }
  )

  private val updateUserTest = suite("updateUser")(
    test("update the username") {
      (for {
        user        <- SharedTestData.user1
        newValue    <- Username.make("newUsername")
        updatedUser <- user.updateUsername(newValue)
      } yield assertTrue(updatedUser.username == newValue) &&
        assertTrue(updatedUser.username != user.username) &&
        assertTrue(updatedUser.email == user.email) &&
        assertTrue(updatedUser.givenName == user.givenName) &&
        assertTrue(updatedUser.familyName == user.familyName) &&
        assertTrue(updatedUser.password == user.password) &&
        assertTrue(updatedUser.language == user.language) &&
        assertTrue(updatedUser.status == user.status)).toZIO
    },
    test("update the email") {
      (for {
        user        <- SharedTestData.user1
        newValue    <- Email.make("newEmail@mail.com")
        updatedUser <- user.updateEmail(newValue)
      } yield assertTrue(updatedUser.email == newValue) &&
        assertTrue(updatedUser.email != user.email) &&
        assertTrue(updatedUser.givenName == user.givenName) &&
        assertTrue(updatedUser.familyName == user.familyName) &&
        assertTrue(updatedUser.password == user.password) &&
        assertTrue(updatedUser.language == user.language) &&
        assertTrue(updatedUser.status == user.status)).toZIO
    },
    test("update the givenName") {
      (for {
        user        <- SharedTestData.user1
        newValue    <- GivenName.make("newGivenName")
        updatedUser <- user.updateGivenName(newValue)
      } yield assertTrue(updatedUser.givenName == newValue) &&
        assertTrue(updatedUser.email == user.email) &&
        assertTrue(updatedUser.givenName != user.givenName) &&
        assertTrue(updatedUser.familyName == user.familyName) &&
        assertTrue(updatedUser.password == user.password) &&
        assertTrue(updatedUser.language == user.language) &&
        assertTrue(updatedUser.status == user.status)).toZIO
    },
    test("update the familyName") {
      (for {
        user        <- SharedTestData.user1
        newValue    <- FamilyName.make("newFamilyName")
        updatedUser <- user.updateFamilyName(newValue)
      } yield assertTrue(updatedUser.familyName == newValue) &&
        assertTrue(updatedUser.email == user.email) &&
        assertTrue(updatedUser.givenName == user.givenName) &&
        assertTrue(updatedUser.familyName != user.familyName) &&
        assertTrue(updatedUser.password == user.password) &&
        assertTrue(updatedUser.language == user.language) &&
        assertTrue(updatedUser.status == user.status)).toZIO
    },
    test("update the password") {
      (for {
        user        <- SharedTestData.user1
        newValue    <- PasswordHash.make("newPassword1", PasswordStrength(12))
        updatedUser <- user.updatePassword(newValue)
      } yield assertTrue(updatedUser.password == newValue) &&
        assertTrue(updatedUser.email == user.email) &&
        assertTrue(updatedUser.givenName == user.givenName) &&
        assertTrue(updatedUser.familyName == user.familyName) &&
        assertTrue(updatedUser.password != user.password) &&
        assertTrue(updatedUser.language == user.language) &&
        assertTrue(updatedUser.status == user.status)).toZIO
    },
    test("update the language") {
      (for {
        user        <- SharedTestData.user1
        newValue    <- LanguageCode.make("fr")
        updatedUser <- user.updateLanguage(newValue)
      } yield assertTrue(updatedUser.language == newValue) &&
        assertTrue(updatedUser.email == user.email) &&
        assertTrue(updatedUser.givenName == user.givenName) &&
        assertTrue(updatedUser.familyName == user.familyName) &&
        assertTrue(updatedUser.password == user.password) &&
        assertTrue(updatedUser.language != user.language) &&
        assertTrue(updatedUser.status == user.status)).toZIO
    },
    test("update the status") {
      (for {
        user        <- SharedTestData.user1
        newValue    <- UserStatus.make(false)
        updatedUser <- user.updateStatus(newValue)
      } yield assertTrue(updatedUser.status == newValue) &&
        assertTrue(updatedUser.email == user.email) &&
        assertTrue(updatedUser.givenName == user.givenName) &&
        assertTrue(updatedUser.familyName == user.familyName) &&
        assertTrue(updatedUser.password == user.password) &&
        assertTrue(updatedUser.language == user.language) &&
        assertTrue(updatedUser.status != user.status)).toZIO
    }
  )
}
