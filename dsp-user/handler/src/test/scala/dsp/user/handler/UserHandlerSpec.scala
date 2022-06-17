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

/**
 * This spec is used to test [[dsp.user.handler.UserHandler]].
 */
object UserHandlerSpec extends ZIOSpecDefault {

  def spec = (userTests)

  val userTests = suite("UserHandler")(
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
        _              <- ZIO.debug(retrievedUsers)
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
               .tap(ZIO.debug(_))

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

        _ <- ZIO.debug(error)
      } yield assert(error)(
        fails(equalTo(DuplicateValueException(s"Username ${SharedTestData.simpleUsername1.value} already exists")))
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
        _             <- ZIO.debug(retrievedUser)
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
      } yield assert(error)(fails(equalTo(NotFoundException(s"User with ID ${newUserId} not found"))))
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
        _             <- ZIO.debug(retrievedUser)
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
      } yield assert(error)(fails(equalTo(NotFoundException(s"User with Username ${username.value} not found"))))
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
        _             <- ZIO.debug(retrievedUser)
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
      } yield assert(error)(fails(equalTo(NotFoundException(s"User with Email ${email.value} not found"))))
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

        user          <- userHandler.getUserById(userId)
        retrievedUser <- ZIO.succeed(user.updateUsername(newValue))
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser.username == newValue) &&
        assertTrue(retrievedUser.username != SharedTestData.simpleUsername1) &&
        assertTrue(retrievedUser.email == SharedTestData.simpleEmail1) &&
        assertTrue(retrievedUser.givenName == SharedTestData.simpleGivenName1) &&
        assertTrue(retrievedUser.familyName == SharedTestData.simpleFamilyName1) &&
        assertTrue(retrievedUser.password == SharedTestData.simplePassword1) &&
        assertTrue(retrievedUser.language == SharedTestData.languageEn) &&
        assertTrue(retrievedUser.status == SharedTestData.statusTrue)
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

        user          <- userHandler.getUserById(userId)
        retrievedUser <- ZIO.succeed(user.updateEmail(newValue))
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser.email == newValue) &&
        assertTrue(retrievedUser.username == SharedTestData.simpleUsername1) &&
        assertTrue(retrievedUser.email != SharedTestData.simpleEmail1) &&
        assertTrue(retrievedUser.givenName == SharedTestData.simpleGivenName1) &&
        assertTrue(retrievedUser.familyName == SharedTestData.simpleFamilyName1) &&
        assertTrue(retrievedUser.password == SharedTestData.simplePassword1) &&
        assertTrue(retrievedUser.language == SharedTestData.languageEn) &&
        assertTrue(retrievedUser.status == SharedTestData.statusTrue)
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

        user          <- userHandler.getUserById(userId)
        retrievedUser <- ZIO.succeed(user.updateGivenName(newValue))
        _             <- ZIO.debug(retrievedUser)
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

        user          <- userHandler.getUserById(userId)
        retrievedUser <- ZIO.succeed(user.updateFamilyName(newValue))
        _             <- ZIO.debug(retrievedUser)
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

        user          <- userHandler.getUserById(userId)
        retrievedUser <- ZIO.succeed(user.updatePassword(newValue))
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser.password == newValue) &&
        assertTrue(retrievedUser.username == SharedTestData.simpleUsername1) &&
        assertTrue(retrievedUser.email == SharedTestData.simpleEmail1) &&
        assertTrue(retrievedUser.givenName == SharedTestData.simpleGivenName1) &&
        assertTrue(retrievedUser.familyName == SharedTestData.simpleFamilyName1) &&
        assertTrue(retrievedUser.password != SharedTestData.simplePassword1) &&
        assertTrue(retrievedUser.language == SharedTestData.languageEn) &&
        assertTrue(retrievedUser.status == SharedTestData.statusTrue)
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

        user          <- userHandler.getUserById(userId)
        retrievedUser <- ZIO.succeed(user.updateLanguage(newValue))
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser.language == newValue) &&
        assertTrue(retrievedUser.username == SharedTestData.simpleUsername1) &&
        assertTrue(retrievedUser.email == SharedTestData.simpleEmail1) &&
        assertTrue(retrievedUser.givenName == SharedTestData.simpleGivenName1) &&
        assertTrue(retrievedUser.familyName == SharedTestData.simpleFamilyName1) &&
        assertTrue(retrievedUser.password == SharedTestData.simplePassword1) &&
        assertTrue(retrievedUser.language != SharedTestData.languageEn) &&
        assertTrue(retrievedUser.status == SharedTestData.statusTrue)
    },
    test("store a user and update the status") {
      val newValue = UserStatus.make(false).fold(e => throw e.head, v => v)
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

        user          <- userHandler.getUserById(userId)
        retrievedUser <- ZIO.succeed(user.updateStatus(newValue))
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser.status == newValue) &&
        assertTrue(retrievedUser.username == SharedTestData.simpleUsername1) &&
        assertTrue(retrievedUser.email == SharedTestData.simpleEmail1) &&
        assertTrue(retrievedUser.givenName == SharedTestData.simpleGivenName1) &&
        assertTrue(retrievedUser.familyName == SharedTestData.simpleFamilyName1) &&
        assertTrue(retrievedUser.password == SharedTestData.simplePassword1) &&
        assertTrue(retrievedUser.language == SharedTestData.languageEn) &&
        assertTrue(retrievedUser.status != SharedTestData.statusTrue)
    }
  ).provide(UserRepoMock.layer, UserHandler.layer)
}
