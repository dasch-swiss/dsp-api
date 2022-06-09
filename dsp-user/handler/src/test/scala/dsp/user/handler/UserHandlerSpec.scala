/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.handler

import zio.ZLayer
import zio._
import zio.test._
import dsp.user.domain._
import dsp.user.repo.impl.UserRepoMock
import dsp.user.domain.User
import dsp.valueobjects.User._

/**
 * This spec is used to test [[dsp.user.handler.UserHandler]].
 */
object UserHandlerSpec extends ZIOSpecDefault {

  def spec = (userTests)

  private val givenNameZio  = GivenName.make("GivenName1").toZIO
  private val familyNameZio = FamilyName.make("familyName1").toZIO
  private val usernameZio   = Username.make("username1").toZIO
  private val emailZio      = Email.make("email1@email.com").toZIO
  private val passwordZio   = Password.make("password1").toZIO
  private val languageZio   = LanguageCode.make("en").toZIO

  val userTests = suite("UserHandler")(
    test("store a user and retrieve by ID") {
      for {
        userHandler <- ZIO.service[UserHandler]

        givenName  <- givenNameZio
        familyName <- familyNameZio
        username   <- usernameZio
        email      <- emailZio
        password   <- passwordZio
        language   <- languageZio

        userId <- userHandler.createUser(
                    username = username,
                    email = email,
                    givenName = givenName,
                    familyName = familyName,
                    password = password,
                    language = language
                  )

        retrievedUser <- userHandler.getUserById(userId)
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser.username == username) &&
        assertTrue(retrievedUser.email == email) &&
        assertTrue(retrievedUser.givenName == givenName) &&
        assertTrue(retrievedUser.familyName == familyName) &&
        assertTrue(retrievedUser.password == Some(password)) &&
        assertTrue(retrievedUser.language == language)
    },
    test("store a user and retrieve by username") {
      for {
        userHandler <- ZIO.service[UserHandler]

        givenName  <- givenNameZio
        familyName <- familyNameZio
        username   <- usernameZio
        email      <- emailZio
        password   <- passwordZio
        language   <- languageZio

        userId <- userHandler.createUser(
                    username = username,
                    email = email,
                    givenName = givenName,
                    familyName = familyName,
                    password = password,
                    language = language
                  )

        retrievedUser <- userHandler.getUserByUsername(username)
      } yield assertTrue(retrievedUser.username == username) &&
        assertTrue(retrievedUser.email == email) &&
        assertTrue(retrievedUser.givenName == givenName) &&
        assertTrue(retrievedUser.familyName == familyName) &&
        assertTrue(retrievedUser.password == Some(password)) &&
        assertTrue(retrievedUser.language == language)
    },
    test("store a user and retrieve by email") {
      for {
        userHandler <- ZIO.service[UserHandler]

        givenName  <- givenNameZio
        familyName <- familyNameZio
        username   <- usernameZio
        email      <- emailZio
        password   <- passwordZio
        language   <- languageZio

        userId <- userHandler.createUser(
                    username = username,
                    email = email,
                    givenName = givenName,
                    familyName = familyName,
                    password = password,
                    language = language
                  )

        retrievedUser <- userHandler.getUserByEmail(email)
      } yield assertTrue(retrievedUser.username == username) &&
        assertTrue(retrievedUser.email == email) &&
        assertTrue(retrievedUser.givenName == givenName) &&
        assertTrue(retrievedUser.familyName == familyName) &&
        assertTrue(retrievedUser.password == Some(password)) &&
        assertTrue(retrievedUser.language == language)
    }
  ).provide(UserRepoMock.layer, UserHandler.layer)
}
