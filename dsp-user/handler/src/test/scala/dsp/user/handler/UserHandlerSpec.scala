/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.handler

import dsp.errors.NotFoundException
import dsp.user.domain.User
import dsp.user.domain._
import dsp.user.repo.impl.UserRepoMock
import dsp.valueobjects.Id.UserId
import dsp.valueobjects.User._
import zio.ZLayer
import zio._
import zio.test.Assertion._
import zio.test._
import dsp.valueobjects.UserErrorMessages
import dsp.errors.DuplicateValueException
import dsp.user.sharedtestdata.SharedTestData
import dsp.errors.ForbiddenException

/**
 * This spec is used to test [[dsp.user.handler.UserHandler]].
 */
object UserHandlerSpec extends ZIOSpecDefault {

  def spec = (userTests)

  val userTests = suite("UserHandler")(
    test("return an empty map when trying to get all users but there are none") {
      for {
        userHandler <- ZIO.service[UserHandler]

        retrievedUsers <- userHandler.getUsers()
      } yield assertTrue(retrievedUsers.size == 0)
    },
    test("store several users and retrieve all") {
      for {
        userHandler <- ZIO.service[UserHandler]

        _ <- userHandler.createUser(
               username = SharedTestData.simpleUsername1,
               email = SharedTestData.simpleEmail1,
               givenName = SharedTestData.simpleGivenName1,
               familyName = SharedTestData.simpleFamilyName1,
               password = SharedTestData.simplePassword1,
               language = SharedTestData.languageEn,
               status = SharedTestData.statusTrue
             )

        _ <- userHandler.createUser(
               username = SharedTestData.simpleUsername2,
               email = SharedTestData.simpleEmail2,
               givenName = SharedTestData.simpleGivenName1,
               familyName = SharedTestData.simpleFamilyName1,
               password = SharedTestData.simplePassword1,
               language = SharedTestData.languageEn,
               status = SharedTestData.statusTrue
             )

        _ <- userHandler.createUser(
               username = SharedTestData.simpleUsername3,
               email = SharedTestData.simpleEmail3,
               givenName = SharedTestData.simpleGivenName1,
               familyName = SharedTestData.simpleFamilyName1,
               password = SharedTestData.simplePassword1,
               language = SharedTestData.languageEn,
               status = SharedTestData.statusTrue
             )

        retrievedUsers <- userHandler.getUsers()
      } yield assertTrue(retrievedUsers.size == 3)
    },
    test("return an Error when creating a user if a username is already taken") {
      for {
        userHandler <- ZIO.service[UserHandler]

        _ <- userHandler
               .createUser(
                 username = SharedTestData.simpleUsername1,
                 email = SharedTestData.simpleEmail1,
                 givenName = SharedTestData.simpleGivenName1,
                 familyName = SharedTestData.simpleFamilyName1,
                 password = SharedTestData.simplePassword1,
                 language = SharedTestData.languageEn,
                 status = SharedTestData.statusTrue
               )

        error <- userHandler
                   .createUser(
                     username = SharedTestData.simpleUsername1,
                     email = SharedTestData.simpleEmail2,
                     givenName = SharedTestData.simpleGivenName2,
                     familyName = SharedTestData.simpleFamilyName2,
                     password = SharedTestData.simplePassword2,
                     language = SharedTestData.languageEn,
                     status = SharedTestData.statusTrue
                   )
                   .exit

      } yield assert(error)(
        fails(equalTo(DuplicateValueException(s"Username '${SharedTestData.simpleUsername1.value}' already taken")))
      )
    },
    test("return an Error when creating a user if a email is already taken") {
      for {
        userHandler <- ZIO.service[UserHandler]

        _ <- userHandler
               .createUser(
                 username = SharedTestData.simpleUsername1,
                 email = SharedTestData.simpleEmail1,
                 givenName = SharedTestData.simpleGivenName1,
                 familyName = SharedTestData.simpleFamilyName1,
                 password = SharedTestData.simplePassword1,
                 language = SharedTestData.languageEn,
                 status = SharedTestData.statusTrue
               )

        error <- userHandler
                   .createUser(
                     username = SharedTestData.simpleUsername2,
                     email = SharedTestData.simpleEmail1,
                     givenName = SharedTestData.simpleGivenName2,
                     familyName = SharedTestData.simpleFamilyName2,
                     password = SharedTestData.simplePassword2,
                     language = SharedTestData.languageEn,
                     status = SharedTestData.statusTrue
                   )
                   .exit

      } yield assert(error)(
        fails(equalTo(DuplicateValueException(s"Email '${SharedTestData.simpleEmail1.value}' already taken")))
      )
    },
    test("store a user and retrieve by ID") {
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = SharedTestData.simpleUsername1,
                    email = SharedTestData.simpleEmail1,
                    givenName = SharedTestData.simpleGivenName1,
                    familyName = SharedTestData.simpleFamilyName1,
                    password = SharedTestData.simplePassword1,
                    language = SharedTestData.languageEn,
                    status = SharedTestData.statusTrue
                  )

        retrievedUser <- userHandler.getUserById(userId)
      } yield assertTrue(retrievedUser.username == SharedTestData.simpleUsername1) &&
        assertTrue(retrievedUser.email == SharedTestData.simpleEmail1) &&
        assertTrue(retrievedUser.givenName == SharedTestData.simpleGivenName1) &&
        assertTrue(retrievedUser.familyName == SharedTestData.simpleFamilyName1) &&
        assertTrue(retrievedUser.password == SharedTestData.simplePassword1) &&
        assertTrue(retrievedUser.language == SharedTestData.languageEn) &&
        assertTrue(retrievedUser.status == SharedTestData.statusTrue)
    },
    test("return an Error if user not found by ID") {

      val newUserId = UserId.make()
      for {
        userHandler <- ZIO.service[UserHandler]
        error       <- userHandler.getUserById(newUserId).exit
      } yield assert(error)(fails(equalTo(NotFoundException(s"User with ID '${newUserId}' not found"))))
    },
    test("store a user and retrieve by username") {
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = SharedTestData.simpleUsername1,
                    email = SharedTestData.simpleEmail1,
                    givenName = SharedTestData.simpleGivenName1,
                    familyName = SharedTestData.simpleFamilyName1,
                    password = SharedTestData.simplePassword1,
                    language = SharedTestData.languageEn,
                    status = SharedTestData.statusTrue
                  )

        retrievedUser <- userHandler.getUserByUsername(SharedTestData.simpleUsername1)
      } yield assertTrue(retrievedUser.username == SharedTestData.simpleUsername1) &&
        assertTrue(retrievedUser.email == SharedTestData.simpleEmail1) &&
        assertTrue(retrievedUser.givenName == SharedTestData.simpleGivenName1) &&
        assertTrue(retrievedUser.familyName == SharedTestData.simpleFamilyName1) &&
        assertTrue(retrievedUser.password == SharedTestData.simplePassword1) &&
        assertTrue(retrievedUser.language == SharedTestData.languageEn) &&
        assertTrue(retrievedUser.status == SharedTestData.statusTrue)
    },
    test("return an Error if user not found by username") {
      val username = Username.make("usernameThatDoesNotExist").fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]
        error       <- userHandler.getUserByUsername(username).exit
      } yield assert(error)(fails(equalTo(NotFoundException(s"User with Username '${username.value}' not found"))))
    },
    test("store a user and retrieve by email") {
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = SharedTestData.simpleUsername1,
                    email = SharedTestData.simpleEmail1,
                    givenName = SharedTestData.simpleGivenName1,
                    familyName = SharedTestData.simpleFamilyName1,
                    password = SharedTestData.simplePassword1,
                    language = SharedTestData.languageEn,
                    status = SharedTestData.statusTrue
                  )

        retrievedUser <- userHandler.getUserByEmail(SharedTestData.simpleEmail1)
      } yield assertTrue(retrievedUser.username == SharedTestData.simpleUsername1) &&
        assertTrue(retrievedUser.email == SharedTestData.simpleEmail1) &&
        assertTrue(retrievedUser.givenName == SharedTestData.simpleGivenName1) &&
        assertTrue(retrievedUser.familyName == SharedTestData.simpleFamilyName1) &&
        assertTrue(retrievedUser.password == SharedTestData.simplePassword1) &&
        assertTrue(retrievedUser.language == SharedTestData.languageEn) &&
        assertTrue(retrievedUser.status == SharedTestData.statusTrue)
    },
    test("return an Error if user not found by email") {

      val email = Email.make("emailThat@DoesNotExi.st").fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]
        error       <- userHandler.getUserByEmail(email).exit
      } yield assert(error)(fails(equalTo(NotFoundException(s"User with Email '${email.value}' not found"))))
    },
    test("store a user and update the username") {
      val newValue = Username.make("newUsername").fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = SharedTestData.simpleUsername1,
                    email = SharedTestData.simpleEmail1,
                    givenName = SharedTestData.simpleGivenName1,
                    familyName = SharedTestData.simpleFamilyName1,
                    password = SharedTestData.simplePassword1,
                    language = SharedTestData.languageEn,
                    status = SharedTestData.statusTrue
                  )

        idOfUpdatedUser <- userHandler.updateUsername(userId, newValue)
        retrievedUser   <- userHandler.getUserById(userId)
      } yield assertTrue(retrievedUser.username == newValue) &&
        assertTrue(retrievedUser.username != SharedTestData.simpleUsername1) &&
        assertTrue(retrievedUser.email == SharedTestData.simpleEmail1) &&
        assertTrue(retrievedUser.givenName == SharedTestData.simpleGivenName1) &&
        assertTrue(retrievedUser.familyName == SharedTestData.simpleFamilyName1) &&
        assertTrue(retrievedUser.password == SharedTestData.simplePassword1) &&
        assertTrue(retrievedUser.language == SharedTestData.languageEn) &&
        assertTrue(retrievedUser.status == SharedTestData.statusTrue)
    },
    test("return an error if the username is taken when trying to update the username") {
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = SharedTestData.simpleUsername1,
                    email = SharedTestData.simpleEmail1,
                    givenName = SharedTestData.simpleGivenName1,
                    familyName = SharedTestData.simpleFamilyName1,
                    password = SharedTestData.simplePassword1,
                    language = SharedTestData.languageEn,
                    status = SharedTestData.statusTrue
                  )

        error <- userHandler.updateUsername(userId, SharedTestData.simpleUsername1).exit
      } yield assert(error)(
        fails(equalTo(DuplicateValueException(s"Username '${SharedTestData.simpleUsername1.value}' already taken")))
      )
    },
    test("store a user and update the email") {
      val newValue = Email.make("new.mail1@email.com").fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = SharedTestData.simpleUsername1,
                    email = SharedTestData.simpleEmail1,
                    givenName = SharedTestData.simpleGivenName1,
                    familyName = SharedTestData.simpleFamilyName1,
                    password = SharedTestData.simplePassword1,
                    language = SharedTestData.languageEn,
                    status = SharedTestData.statusTrue
                  )

        idOfUpdatedUser <- userHandler.updateEmail(userId, newValue)
        retrievedUser   <- userHandler.getUserById(userId)
      } yield assertTrue(retrievedUser.email == newValue) &&
        assertTrue(retrievedUser.username == SharedTestData.simpleUsername1) &&
        assertTrue(retrievedUser.email != SharedTestData.simpleEmail1) &&
        assertTrue(retrievedUser.givenName == SharedTestData.simpleGivenName1) &&
        assertTrue(retrievedUser.familyName == SharedTestData.simpleFamilyName1) &&
        assertTrue(retrievedUser.password == SharedTestData.simplePassword1) &&
        assertTrue(retrievedUser.language == SharedTestData.languageEn) &&
        assertTrue(retrievedUser.status == SharedTestData.statusTrue)
    },
    test("return an error if the email is taken when trying to update the email") {
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = SharedTestData.simpleUsername1,
                    email = SharedTestData.simpleEmail1,
                    givenName = SharedTestData.simpleGivenName1,
                    familyName = SharedTestData.simpleFamilyName1,
                    password = SharedTestData.simplePassword1,
                    language = SharedTestData.languageEn,
                    status = SharedTestData.statusTrue
                  )

        error <- userHandler.updateEmail(userId, SharedTestData.simpleEmail1).exit
      } yield assert(error)(
        fails(equalTo(DuplicateValueException(s"Email '${SharedTestData.simpleEmail1.value}' already taken")))
      )
    },
    test("store a user and update the givenName") {
      val newValue = GivenName.make("newGivenName").fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = SharedTestData.simpleUsername1,
                    email = SharedTestData.simpleEmail1,
                    givenName = SharedTestData.simpleGivenName1,
                    familyName = SharedTestData.simpleFamilyName1,
                    password = SharedTestData.simplePassword1,
                    language = SharedTestData.languageEn,
                    status = SharedTestData.statusTrue
                  )

        idOfUpdatedUser <- userHandler.updateGivenName(userId, newValue)
        retrievedUser   <- userHandler.getUserById(userId)
      } yield assertTrue(retrievedUser.givenName == newValue) &&
        assertTrue(retrievedUser.username == SharedTestData.simpleUsername1) &&
        assertTrue(retrievedUser.email == SharedTestData.simpleEmail1) &&
        assertTrue(retrievedUser.givenName != SharedTestData.simpleGivenName1) &&
        assertTrue(retrievedUser.familyName == SharedTestData.simpleFamilyName1) &&
        assertTrue(retrievedUser.password == SharedTestData.simplePassword1) &&
        assertTrue(retrievedUser.language == SharedTestData.languageEn) &&
        assertTrue(retrievedUser.status == SharedTestData.statusTrue)
    },
    test("store a user and update the familyName") {
      val newValue = FamilyName.make("newFamilyName").fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = SharedTestData.simpleUsername1,
                    email = SharedTestData.simpleEmail1,
                    givenName = SharedTestData.simpleGivenName1,
                    familyName = SharedTestData.simpleFamilyName1,
                    password = SharedTestData.simplePassword1,
                    language = SharedTestData.languageEn,
                    status = SharedTestData.statusTrue
                  )

        idOfUpdatedUser <- userHandler.updateFamilyName(userId, newValue)
        retrievedUser   <- userHandler.getUserById(userId)
      } yield assertTrue(retrievedUser.familyName == newValue) &&
        assertTrue(retrievedUser.username == SharedTestData.simpleUsername1) &&
        assertTrue(retrievedUser.email == SharedTestData.simpleEmail1) &&
        assertTrue(retrievedUser.givenName == SharedTestData.simpleGivenName1) &&
        assertTrue(retrievedUser.familyName != SharedTestData.simpleFamilyName1) &&
        assertTrue(retrievedUser.password == SharedTestData.simplePassword1) &&
        assertTrue(retrievedUser.language == SharedTestData.languageEn) &&
        assertTrue(retrievedUser.status == SharedTestData.statusTrue)
    },
    test("store a user and update the password") {
      val newValue = PasswordHash.make("newPassword1").fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = SharedTestData.simpleUsername1,
                    email = SharedTestData.simpleEmail1,
                    givenName = SharedTestData.simpleGivenName1,
                    familyName = SharedTestData.simpleFamilyName1,
                    password = SharedTestData.simplePassword1,
                    language = SharedTestData.languageEn,
                    status = SharedTestData.statusTrue
                  )
        storedUser      <- userHandler.getUserById(userId)
        idOfUpdatedUser <- userHandler.updatePassword(userId, newValue, storedUser.password, storedUser)
        retrievedUser   <- userHandler.getUserById(userId)
      } yield assertTrue(retrievedUser.password == newValue) &&
        assertTrue(retrievedUser.username == SharedTestData.simpleUsername1) &&
        assertTrue(retrievedUser.email == SharedTestData.simpleEmail1) &&
        assertTrue(retrievedUser.givenName == SharedTestData.simpleGivenName1) &&
        assertTrue(retrievedUser.familyName == SharedTestData.simpleFamilyName1) &&
        assertTrue(retrievedUser.password != SharedTestData.simplePassword1) &&
        assertTrue(retrievedUser.language == SharedTestData.languageEn) &&
        assertTrue(retrievedUser.status == SharedTestData.statusTrue)
    },
    test(
      "return an error when the supplied password does not match the requesting user's password when trying to update the password"
    ) {
      val newValue      = PasswordHash.make("newPassword1").fold(e => throw e.head, v => v)
      val wrongPassword = PasswordHash.make("wrongPassword1").fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = SharedTestData.simpleUsername1,
                    email = SharedTestData.simpleEmail1,
                    givenName = SharedTestData.simpleGivenName1,
                    familyName = SharedTestData.simpleFamilyName1,
                    password = SharedTestData.simplePassword1,
                    language = SharedTestData.languageEn,
                    status = SharedTestData.statusTrue
                  )
        storedUser <- userHandler.getUserById(userId)
        error      <- userHandler.updatePassword(userId, newValue, wrongPassword, storedUser).exit
      } yield assert(error)(
        fails(equalTo(ForbiddenException("The supplied password does not match the requesting user's password")))
      )
    },
    test(
      "return an error when the requesting user is not the user whose password is asked to be changed when trying to update the password"
    ) {
      val newValue  = PasswordHash.make("newPassword1").fold(e => throw e.head, v => v)
      val otherUser = SharedTestData.simpleUser2
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = SharedTestData.simpleUsername1,
                    email = SharedTestData.simpleEmail1,
                    givenName = SharedTestData.simpleGivenName1,
                    familyName = SharedTestData.simpleFamilyName1,
                    password = SharedTestData.simplePassword1,
                    language = SharedTestData.languageEn,
                    status = SharedTestData.statusTrue
                  )

        storedUser <- userHandler.getUserById(userId)
        error      <- userHandler.updatePassword(userId, newValue, storedUser.password, otherUser).exit
      } yield assert(error)(
        fails(
          equalTo(
            ForbiddenException("User's password can only be changed by the user itself or a system administrator")
          )
        )
      )
    },
    test("store a user and update the language") {
      val newValue = LanguageCode.make("fr").fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = SharedTestData.simpleUsername1,
                    email = SharedTestData.simpleEmail1,
                    givenName = SharedTestData.simpleGivenName1,
                    familyName = SharedTestData.simpleFamilyName1,
                    password = SharedTestData.simplePassword1,
                    language = SharedTestData.languageEn,
                    status = SharedTestData.statusTrue
                  )

        idOfUpdatedUser <- userHandler.updateLanguage(userId, newValue)
        retrievedUser   <- userHandler.getUserById(userId)
      } yield assertTrue(retrievedUser.language == newValue) &&
        assertTrue(retrievedUser.username == SharedTestData.simpleUsername1) &&
        assertTrue(retrievedUser.email == SharedTestData.simpleEmail1) &&
        assertTrue(retrievedUser.givenName == SharedTestData.simpleGivenName1) &&
        assertTrue(retrievedUser.familyName == SharedTestData.simpleFamilyName1) &&
        assertTrue(retrievedUser.password == SharedTestData.simplePassword1) &&
        assertTrue(retrievedUser.language != SharedTestData.languageEn) &&
        assertTrue(retrievedUser.status == SharedTestData.statusTrue)
    },
    test("delete a user") {
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = SharedTestData.simpleUsername1,
                    email = SharedTestData.simpleEmail1,
                    givenName = SharedTestData.simpleGivenName1,
                    familyName = SharedTestData.simpleFamilyName1,
                    password = SharedTestData.simplePassword1,
                    language = SharedTestData.languageEn,
                    status = SharedTestData.statusTrue
                  )

        id               <- userHandler.deleteUser(userId)
        idNotFound       <- userHandler.getUserById(userId).exit
        usernameNotFound <- userHandler.getUserByUsername(SharedTestData.simpleUsername1).exit
        emailNotFound    <- userHandler.getUserByEmail(SharedTestData.simpleEmail1).exit

        // create new user with same values
        newUserId <- userHandler.createUser(
                       username = SharedTestData.simpleUsername1,
                       email = SharedTestData.simpleEmail1,
                       givenName = SharedTestData.simpleGivenName1,
                       familyName = SharedTestData.simpleFamilyName1,
                       password = SharedTestData.simplePassword1,
                       language = SharedTestData.languageEn,
                       status = SharedTestData.statusTrue
                     )
      } yield assertTrue(id == userId) &&
        assertTrue(userId != newUserId) &&
        assert(idNotFound)(fails(equalTo(NotFoundException(s"User with ID '${userId}' not found")))) &&
        assert(usernameNotFound)(
          fails(equalTo(NotFoundException(s"User with Username '${SharedTestData.simpleUsername1.value}' not found")))
        ) &&
        assert(emailNotFound)(
          fails(equalTo(NotFoundException(s"User with Email '${SharedTestData.simpleEmail1.value}' not found")))
        )
    },
    test("return an error if the ID of a user is not found when trying to delete the user") {
      for {
        userHandler <- ZIO.service[UserHandler]
        error       <- userHandler.deleteUser(SharedTestData.simpleUser2.id).exit
      } yield assert(error)(
        fails(equalTo(NotFoundException(s"User with ID '${SharedTestData.simpleUser2.id}' not found")))
      )
    }
  ).provide(UserRepoMock.layer, UserHandler.layer)
}
