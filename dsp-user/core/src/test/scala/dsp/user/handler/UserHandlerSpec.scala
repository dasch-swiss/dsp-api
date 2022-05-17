/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.handler

import zio.ZLayer
import zio._
import zio.test._
import dsp.user.domain._
import dsp.user.handler.UserHandler
import dsp.user.repo.UserRepoMock

/**
 * This spec is used to test [[dsp.user.repo.UserHandler]].
 */
object UserHandlerSpec extends ZIOSpec[UserHandler] {

  val bootstrap = ZLayer.make[UserHandler](
    UserHandler.layer,
    UserRepoMock.layer
  )

  def spec = (userTests)

  private val testUser1: User = User(
    id = UserId.make(),
    givenName = "GivenName",
    familyName = "FamilyName",
    username = Username.make("Username"),
    email = Email.make("Email"),
    password = "Password",
    language = "en",
    role = "role"
  )

  val userTests = suite("UserHandler")(
    test("store a user and retrieve by ID") {
      for {
        userHandler   <- ZIO.service[UserHandler]
        _             <- userHandler.createUser(testUser1)
        retrievedUser <- userHandler.getUserById(testUser1.id)
      } yield assertTrue(retrievedUser == Some(testUser1))
    }
  )
}
