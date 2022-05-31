/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.repo.impl

import zio.ZLayer
import zio._
import zio.test._
import dsp.user.domain._
import dsp.user.repo.impl.UserRepoLive
import dsp.user.repo.impl.UserRepoMock
import zio.test.ZIOSpecDefault
import dsp.user.api.UserRepo

/**
 * This spec is used to test all [[dsp.user.repo.UserRepo]] implementations.
 */
object UserRepoSpec extends ZIOSpecDefault {

  def spec = (userRepoMockTests + userRepoLiveTests)

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

  private val testUser2: User = User(
    id = UserId.make(),
    givenName = "GivenName2",
    familyName = "FamilyName2",
    username = Username.make("Username2"),
    email = Email.make("Email2"),
    password = Some("Password2"),
    language = "en",
    role = "role"
  )

  val userTests =
    test("store a user and retrieve by ID") {
      for {
        _             <- UserRepo.storeUser(testUser1)
        retrievedUser <- UserRepo.getUserById(testUser1.id)
      } yield assertTrue(retrievedUser == Some(testUser1))
    } +
      test("retrieve the user by username") {
        for {
          _             <- UserRepo.storeUser(testUser1)
          retrievedUser <- UserRepo.getUserByUsernameOrEmail(testUser1.username.value)
        } yield assertTrue(retrievedUser == Some(testUser1))
      } +
      test("retrieve the user by email") {
        for {
          _             <- UserRepo.storeUser(testUser1)
          retrievedUser <- UserRepo.getUserByUsernameOrEmail(testUser1.email.value)
        } yield assertTrue(retrievedUser == Some(testUser1)) &&
          assertTrue(retrievedUser != Some(testUser2))
      }

  val userRepoMockTests = suite("UserRepoMock")(
    userTests
  ).provide(UserRepoMock.layer)

  val userRepoLiveTests = suite("UserRepoLive")(
    userTests
  ).provide(UserRepoLive.layer)

}
