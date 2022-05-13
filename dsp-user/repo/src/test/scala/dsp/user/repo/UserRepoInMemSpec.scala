/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.handler

import zio.ZLayer
import zio._
import zio.test._
import dsp.user.domain.User
import dsp.user.domain.UserId
import dsp.user.repo.UserRepo
import dsp.user.repo.UserRepoInMem

/**
 * This spec is used to test [[dsp.user.repo.UserRepoInMem]].
 */
object UserRepoInMemSpec extends ZIOSpec[UserRepo] {

  val bootstrap = ZLayer.make[UserRepo](UserRepoInMem.layer)

  def spec = (userTests)

  private val user: User = User(
    id = UserId.make(),
    givenName = "GivenName",
    familyName = "FamilyName",
    username = "Username",
    email = "Email",
    password = "Password",
    language = "en",
    role = "role"
  )

  val userTests = suite("UserRepo")(
    test("successfully store a user and retrieve by ID") {
      for {
        _             <- UserRepo.store(user)
        retrievedUser <- UserRepo.getUserById(user.id)
      } yield assertTrue(retrievedUser == Some(user)) // always use assertTrue (not assert)
    }
  )
}
