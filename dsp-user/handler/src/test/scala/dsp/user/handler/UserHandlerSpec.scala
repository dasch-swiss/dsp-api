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

/**
 * This spec is used to test [[dsp.user.handler.UserHandler]].
 */
object UserHandlerSpec extends ZIOSpecDefault {

  def spec = (userTests)

  private val givenName  = GivenName.make("GivenName1").fold(e => throw e.head, v => v)
  private val familyName = FamilyName.make("familyName1").fold(e => throw e.head, v => v)
  private val username   = Username.make("username1").fold(e => throw e.head, v => v)
  private val email      = Email.make("email1@email.com").fold(e => throw e.head, v => v)
  private val password   = PasswordHash.make("password1").fold(e => throw e.head, v => v)
  private val language   = LanguageCode.make("en").fold(e => throw e.head, v => v)
  private val status     = UserStatus.make(true).fold(e => throw e.head, v => v)

  val userTests = suite("UserHandler")(
    test("store a user and retrieve by ID") {
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = username,
                    email = email,
                    givenName = givenName,
                    familyName = familyName,
                    password = password,
                    language = language,
                    status = status
                  )

        retrievedUser <- userHandler.getUserById(userId)
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser.username == username) &&
        assertTrue(retrievedUser.email == email) &&
        assertTrue(retrievedUser.givenName == givenName) &&
        assertTrue(retrievedUser.familyName == familyName) &&
        assertTrue(retrievedUser.password == password) &&
        assertTrue(retrievedUser.language == language) &&
        assertTrue(retrievedUser.status == status)
    },
    test("store a user and retrieve by username") {
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = username,
                    email = email,
                    givenName = givenName,
                    familyName = familyName,
                    password = password,
                    language = language,
                    status = status
                  )

        retrievedUser <- userHandler.getUserByUsername(username)
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser.username == username) &&
        assertTrue(retrievedUser.email == email) &&
        assertTrue(retrievedUser.givenName == givenName) &&
        assertTrue(retrievedUser.familyName == familyName) &&
        assertTrue(retrievedUser.password == password) &&
        assertTrue(retrievedUser.language == language) &&
        assertTrue(retrievedUser.status == status)
    },
    test("store a user and retrieve by email") {
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = username,
                    email = email,
                    givenName = givenName,
                    familyName = familyName,
                    password = password,
                    language = language,
                    status = status
                  )

        retrievedUser <- userHandler.getUserByEmail(email)
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser.username == username) &&
        assertTrue(retrievedUser.email == email) &&
        assertTrue(retrievedUser.givenName == givenName) &&
        assertTrue(retrievedUser.familyName == familyName) &&
        assertTrue(retrievedUser.password == password) &&
        assertTrue(retrievedUser.language == language) &&
        assertTrue(retrievedUser.status == status)
    },
    test("return a NotFoundError if user not found by ID") {

      val newUserId = UserId.make()
      for {
        userHandler <- ZIO.service[UserHandler]
        error       <- userHandler.getUserById(newUserId).exit
      } yield assert(error)(fails(equalTo(NotFoundException(s"User with ID ${newUserId} not found"))))
    },
    test("return a NotFoundError if user not found by username") {
      val username = Username.make("usernameThatDoesNotExist").fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]
        error       <- userHandler.getUserByUsername(username).exit
      } yield assert(error)(fails(equalTo(NotFoundException(s"User with Username ${username.value} not found"))))
    },
    test("return a NotFoundError if user not found by email") {

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
                    username = username,
                    email = email,
                    givenName = givenName,
                    familyName = familyName,
                    password = password,
                    language = language,
                    status = status
                  )

        user          <- userHandler.getUserByEmail(email)
        retrievedUser <- ZIO.succeed(user.updateUsername(newValue))
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser.username == newValue) &&
        assertTrue(retrievedUser.username != username) &&
        assertTrue(retrievedUser.email == email) &&
        assertTrue(retrievedUser.givenName == givenName) &&
        assertTrue(retrievedUser.familyName == familyName) &&
        assertTrue(retrievedUser.password == password) &&
        assertTrue(retrievedUser.language == language) &&
        assertTrue(retrievedUser.status == status)
    },
    test("store a user and update the email") {
      val newValue = Email.make("new.mail1@email.com").fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = username,
                    email = email,
                    givenName = givenName,
                    familyName = familyName,
                    password = password,
                    language = language,
                    status = status
                  )

        user          <- userHandler.getUserById(userId)
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
    test("store a user and update the givenName") {
      val newValue = GivenName.make("newGivenName").fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = username,
                    email = email,
                    givenName = givenName,
                    familyName = familyName,
                    password = password,
                    language = language,
                    status = status
                  )

        user          <- userHandler.getUserById(userId)
        retrievedUser <- ZIO.succeed(user.updateGivenName(newValue))
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser.givenName == newValue) &&
        assertTrue(retrievedUser.email == email) &&
        assertTrue(retrievedUser.givenName != givenName) &&
        assertTrue(retrievedUser.familyName == familyName) &&
        assertTrue(retrievedUser.password == password) &&
        assertTrue(retrievedUser.language == language) &&
        assertTrue(retrievedUser.status == status)
    },
    test("store a user and update the familyName") {
      val newValue = FamilyName.make("newFamilyName").fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = username,
                    email = email,
                    givenName = givenName,
                    familyName = familyName,
                    password = password,
                    language = language,
                    status = status
                  )

        user          <- userHandler.getUserById(userId)
        retrievedUser <- ZIO.succeed(user.updateFamilyName(newValue))
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser.familyName == newValue) &&
        assertTrue(retrievedUser.email == email) &&
        assertTrue(retrievedUser.givenName == givenName) &&
        assertTrue(retrievedUser.familyName != familyName) &&
        assertTrue(retrievedUser.password == password) &&
        assertTrue(retrievedUser.language == language) &&
        assertTrue(retrievedUser.status == status)
    },
    test("store a user and update the password") {
      val newValue = PasswordHash.make("newPassword1").fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = username,
                    email = email,
                    givenName = givenName,
                    familyName = familyName,
                    password = password,
                    language = language,
                    status = status
                  )

        user          <- userHandler.getUserById(userId)
        retrievedUser <- ZIO.succeed(user.updatePassword(newValue))
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser.password == newValue) &&
        assertTrue(retrievedUser.email == email) &&
        assertTrue(retrievedUser.givenName == givenName) &&
        assertTrue(retrievedUser.familyName == familyName) &&
        assertTrue(retrievedUser.password != password) &&
        assertTrue(retrievedUser.language == language) &&
        assertTrue(retrievedUser.status == status)
    },
    test("store a user and update the language") {
      val newValue = LanguageCode.make("fr").fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = username,
                    email = email,
                    givenName = givenName,
                    familyName = familyName,
                    password = password,
                    language = language,
                    status = status
                  )

        user          <- userHandler.getUserById(userId)
        retrievedUser <- ZIO.succeed(user.updateLanguage(newValue))
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser.language == newValue) &&
        assertTrue(retrievedUser.email == email) &&
        assertTrue(retrievedUser.givenName == givenName) &&
        assertTrue(retrievedUser.familyName == familyName) &&
        assertTrue(retrievedUser.password == password) &&
        assertTrue(retrievedUser.language != language) &&
        assertTrue(retrievedUser.status == status)
    },
    test("store a user and update the status") {
      val newValue = UserStatus.make(false).fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]

        userId <- userHandler.createUser(
                    username = username,
                    email = email,
                    givenName = givenName,
                    familyName = familyName,
                    password = password,
                    language = language,
                    status = status
                  )

        user          <- userHandler.getUserById(userId)
        retrievedUser <- ZIO.succeed(user.updateStatus(newValue))
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser.status == newValue) &&
        assertTrue(retrievedUser.email == email) &&
        assertTrue(retrievedUser.givenName == givenName) &&
        assertTrue(retrievedUser.familyName == familyName) &&
        assertTrue(retrievedUser.password == password) &&
        assertTrue(retrievedUser.language == language) &&
        assertTrue(retrievedUser.status != status)
    }
  ).provide(UserRepoMock.layer, UserHandler.layer)
}
