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

  private val testUser1 = (for {
    givenName  <- GivenName.make("GivenName1")
    familyName <- FamilyName.make("familyName1")
    username   <- Username.make("username1")
    email      <- Email.make("email1@email.com")
    password   <- Password.make("password1")
    language   <- LanguageCode.make("en")
    user = User.make(
             givenName,
             familyName,
             username,
             email,
             password,
             language
           )
  } yield (user)).toZIO

  val userTests = suite("UserHandler")(
    test("store a user and retrieve by ID") {
      for {
        userHandler <- ZIO.service[UserHandler]
        testUser    <- testUser1
        userId <- userHandler.createUser(
                    testUser.username,
                    testUser.email,
                    testUser.givenName,
                    testUser.familyName,
                    testUser.password.get,
                    testUser.language
                  )

        retrievedUser <- userHandler.getUserById(userId)
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser == testUser)
    },
    test("store a user and retrieve by username") {
      for {
        userHandler <- ZIO.service[UserHandler]
        testUser    <- testUser1
        userId <- userHandler.createUser(
                    testUser.username,
                    testUser.email,
                    testUser.givenName,
                    testUser.familyName,
                    testUser.password.get,
                    testUser.language
                  )
        retrievedUser <- userHandler.getUserByUsername(testUser.username)
      } yield assertTrue(retrievedUser == testUser)
    },
    test("store a user and retrieve by email") {
      for {
        userHandler <- ZIO.service[UserHandler]
        testUser    <- testUser1
        userId <- userHandler.createUser(
                    testUser.username,
                    testUser.email,
                    testUser.givenName,
                    testUser.familyName,
                    testUser.password.get,
                    testUser.language
                  )
        retrievedUser <- userHandler.getUserByEmail(testUser.email)
      } yield assertTrue(retrievedUser == testUser)
    }
  ).provide(UserRepoMock.layer, UserHandler.layer)
}
