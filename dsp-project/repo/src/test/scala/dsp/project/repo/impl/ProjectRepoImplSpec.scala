/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.project.repo.impl

import dsp.errors.BadRequestException
import dsp.project.api.ProjectRepo
// import dsp.user.domain.User
// import dsp.user.repo.impl.UserRepoLive
// import dsp.user.repo.impl.UserRepoMock
// import dsp.valueobjects.User._
import zio._
import zio.prelude.Validation
import zio.prelude.ZValidation
import zio.test._

// TODO: the following needs to be updated

// /**
//  * This spec is used to test all [[dsp.user.repo.UserRepo]] implementations.
//  */
// object UserRepoImplSpec extends ZIOSpecDefault {

//   def spec = (userRepoMockTests + userRepoLiveTests)

//   private val testUser1 = (for {
//     givenName  <- GivenName.make("GivenName1")
//     familyName <- FamilyName.make("familyName1")
//     username   <- Username.make("username1")
//     email      <- Email.make("email1@email.com")
//     password   <- Password.make("password1")
//     language   <- LanguageCode.make("en")
//     user = User.make(
//              givenName,
//              familyName,
//              username,
//              email,
//              password,
//              language
//            )
//   } yield (user)).toZIO

//   private val testUser2 = (for {
//     givenName  <- GivenName.make("GivenName2")
//     familyName <- FamilyName.make("familyName2")
//     username   <- Username.make("username2")
//     email      <- Email.make("email2@email.com")
//     password   <- Password.make("password2")
//     language   <- LanguageCode.make("en")
//     user = User.make(
//              givenName,
//              familyName,
//              username,
//              email,
//              password,
//              language
//            )
//   } yield (user)).toZIO

//   val userTests =
//     test("store a user and retrieve by ID") {
//       for {
//         user          <- testUser1
//         _             <- UserRepo.storeUser(user)
//         retrievedUser <- UserRepo.getUserById(user.id)
//       } yield assertTrue(retrievedUser == user)
//     } +
//       test("retrieve the user by username") {
//         for {
//           user          <- testUser1
//           _             <- UserRepo.storeUser(user)
//           retrievedUser <- UserRepo.getUserByUsernameOrEmail(user.username.value)
//         } yield assertTrue(retrievedUser == user)
//       } +
//       test("retrieve the user by email") {
//         for {
//           user1         <- testUser1
//           user2         <- testUser2
//           _             <- UserRepo.storeUser(user1)
//           retrievedUser <- UserRepo.getUserByUsernameOrEmail(user1.email.value)
//         } yield {
//           assertTrue(retrievedUser == user1) &&
//           assertTrue(retrievedUser != user2)
//         }
//       }

//   val userRepoMockTests = suite("UserRepoMock")(
//     userTests
//   ).provide(UserRepoMock.layer)

//   val userRepoLiveTests = suite("UserRepoLive")(
//     userTests
//   ).provide(UserRepoLive.layer)

// }
