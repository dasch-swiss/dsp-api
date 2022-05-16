/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.handler

import zio.ZLayer
import zio._
import zio.test._
import dsp.user.domain._
import dsp.user.repo.UserRepo
import dsp.user.repo.UserRepoInMem

/**
 * This spec is used to test [[dsp.user.repo.UserRepo]].
 */
object UserRepoSpec extends ZIOSpec[UserRepo] {

  val bootstrap = ZLayer.make[UserRepo](UserRepoInMem.test)
  // the same tests should run for all implementations (UserRepoInMem and UserRepoLive)

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

  private val testUser2: User = User(
    id = UserId.make(),
    givenName = "GivenName2",
    familyName = "FamilyName2",
    username = Username.make("Username2"),
    email = Email.make("Email2"),
    password = "Password2",
    language = "en",
    role = "role"
  )

  val userTests = suite("UserRepo")(
    test("successfully store a user and retrieve by ID") {
      for {
        _             <- UserRepo.storeUser(testUser1)
        retrievedUser <- UserRepo.getUserById(testUser1.id)
      } yield assertTrue(retrievedUser == Some(testUser1))
    } +
      test("successfully retrieve the user by username") {
        for {
          retrievedUser <- UserRepo.getUserByUsernameOrEmail(testUser1.username.value)
        } yield assertTrue(retrievedUser == Some(testUser1))
      } +
      test("successfully retrieve the user by email") {
        for {
          retrievedUser <- UserRepo.getUserByUsernameOrEmail(testUser1.email.value)
        } yield assertTrue(retrievedUser == Some(testUser1)) &&
          assertTrue(retrievedUser != Some(testUser2))
      }
  )
}
