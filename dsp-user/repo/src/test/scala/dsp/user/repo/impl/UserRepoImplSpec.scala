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
import zio.test.Assertion._
import dsp.errors.DuplicateValueException
import dsp.user.sharedtestdata.SharedTestData

/**
 * This spec is used to test all [[dsp.user.repo.UserRepo]] implementations.
 */
object UserRepoImplSpec extends ZIOSpecDefault {

  def spec = (userRepoMockTests + userRepoLiveTests)

  private val user1: User = SharedTestData.simpleUser1
  private val user2: User = SharedTestData.simpleUser2

  val userTests =
    test("store several users and retrieve all") {
      for {
        _              <- UserRepo.storeUser(user1)
        _              <- UserRepo.storeUser(user2)
        retrievedUsers <- UserRepo.getUsers()
      } yield assertTrue(retrievedUsers.size == 2)
    } +
      test("store a user and retrieve by ID") {
        for {
          _             <- UserRepo.storeUser(user1)
          retrievedUser <- UserRepo.getUserById(user1.id)
        } yield assertTrue(retrievedUser == user1)
      } +
      test("store a user and retrieve the user by username") {
        for {
          _             <- UserRepo.storeUser(user1)
          retrievedUser <- UserRepo.getUserByUsername(user1.username)
        } yield assertTrue(retrievedUser == user1)
      } +
      test("store a user and retrieve the user by email") {
        for {
          _             <- UserRepo.storeUser(user1)
          retrievedUser <- UserRepo.getUserByEmail(user1.email)
        } yield {
          assertTrue(retrievedUser == user1) &&
          assertTrue(retrievedUser != user2)
        }
      } +
      test("return None if a username already exists") {
        for {
          _     <- UserRepo.storeUser(user1)
          error <- UserRepo.checkIfUsernameExists(user1.username).exit
        } yield assert(error)(
          fails(equalTo(None))
        )
      } +
      test("return success (Unit) if a username is unique") {
        for {
          _      <- UserRepo.storeUser(user1)
          result <- UserRepo.checkIfUsernameExists(SharedTestData.simpleUser2.username)
        } yield assertTrue(result == ())
      } +
      test("return None if an email already exists") {
        for {
          _     <- UserRepo.storeUser(user1)
          error <- UserRepo.checkIfEmailExists(user1.email).exit
        } yield assert(error)(
          fails(equalTo(None))
        )
      } +
      test("return success (Unit) if an email is unique") {
        for {
          _      <- UserRepo.storeUser(user1)
          result <- UserRepo.checkIfEmailExists(SharedTestData.simpleUser2.email)
        } yield assertTrue(result == ())
      } +
      test("store and delete a user") {
        for {
          userId                           <- UserRepo.storeUser(user1)
          userIdOfDeletedUser              <- UserRepo.deleteUser(userId)
          idIsDeleted                      <- UserRepo.getUserById(userIdOfDeletedUser).exit
          usernameIsDeleted                <- UserRepo.getUserByUsername(user1.username).exit
          emailIsDeleted                   <- UserRepo.getUserByEmail(user1.email).exit
          usernameIsDeletedFromLookupTable <- UserRepo.checkIfUsernameExists(user1.username)
          emailIsDeletedFromLookupTable    <- UserRepo.checkIfEmailExists(user1.email)
        } yield assertTrue(userId == user1.id) &&
          assert(idIsDeleted)(fails(equalTo(None))) &&
          assert(usernameIsDeleted)(fails(equalTo(None))) &&
          assert(emailIsDeleted)(fails(equalTo(None))) &&
          assertTrue(usernameIsDeletedFromLookupTable == ()) &&
          assertTrue(emailIsDeletedFromLookupTable == ())
      }

  val userRepoMockTests = suite("UserRepoMock")(
    userTests
  ).provide(UserRepoMock.layer)

  val userRepoLiveTests = suite("UserRepoLive")(
    userTests
  ).provide(UserRepoLive.layer)

}
