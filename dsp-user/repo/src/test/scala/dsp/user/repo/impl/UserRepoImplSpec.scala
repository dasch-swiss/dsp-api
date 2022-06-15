/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.repo.impl

import dsp.errors.BadRequestException
import dsp.user.api.UserRepo
import dsp.user.domain.User
import dsp.user.repo.impl.UserRepoLive
import dsp.user.repo.impl.UserRepoMock
import dsp.valueobjects.User._
import zio._
import zio.prelude.Validation
import zio.prelude.ZValidation
import zio.test._

/**
 * This spec is used to test all [[dsp.user.repo.UserRepo]] implementations.
 */
object UserRepoImplSpec extends ZIOSpecDefault {

  def spec = (userRepoMockTests + userRepoLiveTests)

  private val testUser1 = (for {
    givenName  <- GivenName.make("GivenName1")
    familyName <- FamilyName.make("familyName1")
    username   <- Username.make("username1")
    email      <- Email.make("email1@email.com")
    password   <- Password.make("password1")
    language   <- LanguageCode.make("en")
    status     <- UserStatus.make(true)
    user = User.make(
             givenName,
             familyName,
             username,
             email,
             password,
             language,
             status
           )
  } yield (user)).toZIO

  private val testUser2 = (for {
    givenName  <- GivenName.make("GivenName2")
    familyName <- FamilyName.make("familyName2")
    username   <- Username.make("username2")
    email      <- Email.make("email2@email.com")
    password   <- Password.make("password2")
    language   <- LanguageCode.make("en")
    status     <- UserStatus.make(true)
    user = User.make(
             givenName,
             familyName,
             username,
             email,
             password,
             language,
             status
           )
  } yield (user)).toZIO

  val userTests =
    test("store a user and retrieve by ID") {
      for {
        user          <- testUser1
        _             <- UserRepo.storeUser(user)
        retrievedUser <- UserRepo.getUserById(user.id)
      } yield assertTrue(retrievedUser == user)
    } +
      test("store a user and retrieve the user by username") {
        for {
          user          <- testUser1
          _             <- UserRepo.storeUser(user)
          retrievedUser <- UserRepo.getUserByUsername(user.username)
        } yield assertTrue(retrievedUser == user)
      } +
      test("store a user and retrieve the user by email") {
        for {
          user1         <- testUser1
          user2         <- testUser2
          _             <- UserRepo.storeUser(user1)
          retrievedUser <- UserRepo.getUserByEmail(user1.email)
        } yield {
          assertTrue(retrievedUser == user1) &&
          assertTrue(retrievedUser != user2)
        }
      }

  val userRepoMockTests = suite("UserRepoMock")(
    userTests
  ).provide(UserRepoMock.layer)

  val userRepoLiveTests = suite("UserRepoLive")(
    userTests
  ).provide(UserRepoLive.layer)

}
