/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.handler

import dsp.user.domain.User
import dsp.user.domain._
import dsp.user.repo.impl.UserRepoMock
import dsp.valueobjects.User._
import zio.ZLayer
import zio._
import zio.test._

/**
 * This spec is used to test [[dsp.user.handler.UserHandler]].
 */
object UserHandlerSpec extends ZIOSpecDefault {

  def spec = (userTests)

  private val givenName  = GivenName.make("GivenName1").fold(e => throw e.head, v => v)
  private val familyName = FamilyName.make("familyName1").fold(e => throw e.head, v => v)
  private val username   = Username.make("username1").fold(e => throw e.head, v => v)
  private val email      = Email.make("email1@email.com").fold(e => throw e.head, v => v)
  private val password   = Password.make("password1").fold(e => throw e.head, v => v)
  private val language   = LanguageCode.make("en").fold(e => throw e.head, v => v)

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
