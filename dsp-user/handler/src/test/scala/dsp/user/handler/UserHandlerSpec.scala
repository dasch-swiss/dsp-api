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

/**
 * This spec is used to test [[dsp.user.handler.UserHandler]].
 */
object UserHandlerSpec extends ZIOSpecDefault {

  def spec = (userTests)

  private val testUser1: User = User(
    id = UserId.make(),
    givenName = "GivenName",
    familyName = "FamilyName",
    username = Username.make("Username"),
    email = Email.make("Email"),
    password = Some("Password"),
    language = "en",
    role = "role"
  )

  val userTests = suite("UserHandler")(
    test("store a user and retrieve by ID") {
      for {
        userHandler   <- ZIO.service[UserHandler]
        _             <- userHandler.createUser(testUser1)
        retrievedUser <- userHandler.getUserById(testUser1.id, UserInformationType.Full)
        _             <- ZIO.debug(retrievedUser)
      } yield assertTrue(retrievedUser == Some(testUser1))
    },
    test("store a user and retrieve by IRI") {
      for {
        userHandler   <- ZIO.service[UserHandler]
        _             <- userHandler.createUser(testUser1)
        retrievedUser <- userHandler.getUserByIri(testUser1.id.iri, UserInformationType.Full)
      } yield assertTrue(retrievedUser == Some(testUser1))
    },
    test("store a user and retrieve by UUID") {
      for {
        userHandler   <- ZIO.service[UserHandler]
        _             <- userHandler.createUser(testUser1)
        retrievedUser <- userHandler.getUserByUuid(testUser1.id.uuid, UserInformationType.Full)
      } yield assertTrue(retrievedUser == Some(testUser1))
    },
    test("store a user and retrieve by username") {
      for {
        userHandler   <- ZIO.service[UserHandler]
        _             <- userHandler.createUser(testUser1)
        retrievedUser <- userHandler.getUserByUsername(testUser1.username, UserInformationType.Full)
      } yield assertTrue(retrievedUser == Some(testUser1))
    },
    test("store a user and retrieve by email") {
      for {
        userHandler   <- ZIO.service[UserHandler]
        _             <- userHandler.createUser(testUser1)
        retrievedUser <- userHandler.getUserByEmail(testUser1.email, UserInformationType.Full)
      } yield assertTrue(retrievedUser == Some(testUser1))
    }
  ).provide(UserRepoMock.layer, UserHandler.layer)
}
