/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.handler

import dsp.errors.DuplicateValueException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import dsp.user.domain.User
import dsp.user.domain._
import dsp.user.repo.impl.UserRepoMock
import dsp.user.sharedtestdata.SharedTestData
import dsp.valueobjects.Id.UserId
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.User._
import dsp.valueobjects.UserErrorMessages
import zio.ZLayer
import zio._
import zio.test.Assertion._
import zio.test._

/**
 * This spec is used to test [[dsp.user.handler.UserHandler]].
 */
object UserHandlerSpec extends ZIOSpecDefault {

  def spec = (getAllUsersTests + createUserTest + getUserByTest + updateUserTest + deleteUserTest)

  private val getAllUsersTests = suite("getAllUsers")(
    test("return an empty map when trying to get all users but there are none") {
      for {
        userHandler <- ZIO.service[UserHandler]

        retrievedUsers <- userHandler.getUsers()
      } yield assertTrue(retrievedUsers.size == 0)
    },
    test("store several users and retrieve all") {
      for {
        userHandler <- ZIO.service[UserHandler]

        username1   <- SharedTestData.username1.toZIO
        email1      <- SharedTestData.email1.toZIO
        givenName1  <- SharedTestData.givenName1.toZIO
        familyName1 <- SharedTestData.familyName1.toZIO
        password1   <- SharedTestData.password1.toZIO

        username2   <- SharedTestData.username2.toZIO
        email2      <- SharedTestData.email2.toZIO
        givenName2  <- SharedTestData.givenName2.toZIO
        familyName2 <- SharedTestData.familyName2.toZIO
        password2   <- SharedTestData.password2.toZIO

        username3   <- SharedTestData.username3.toZIO
        email3      <- SharedTestData.email3.toZIO
        givenName3  <- SharedTestData.givenName3.toZIO
        familyName3 <- SharedTestData.familyName3.toZIO
        password3   <- SharedTestData.password3.toZIO

        language <- SharedTestData.languageEn.toZIO
        status   <- SharedTestData.statusTrue.toZIO

        _ <- userHandler.createUser(
               username1,
               email1,
               givenName1,
               familyName1,
               password1,
               language,
               status
             )

        _ <- userHandler.createUser(
               username2,
               email2,
               givenName2,
               familyName2,
               password2,
               language,
               status
             )

        _ <- userHandler.createUser(
               username3,
               email3,
               givenName3,
               familyName3,
               password3,
               language,
               status
             )

        retrievedUsers <- userHandler.getUsers()
      } yield assertTrue(retrievedUsers.size == 3)
    }
  ).provide(UserRepoMock.layer, UserHandler.layer)

  private val createUserTest = suite("createUser")(
    test("return an Error when creating a user if a username is already taken") {
      for {
        userHandler <- ZIO.service[UserHandler]

        username1   <- SharedTestData.username1.toZIO
        email1      <- SharedTestData.email1.toZIO
        givenName1  <- SharedTestData.givenName1.toZIO
        familyName1 <- SharedTestData.familyName1.toZIO
        password1   <- SharedTestData.password1.toZIO

        username2   <- SharedTestData.username2.toZIO
        email2      <- SharedTestData.email2.toZIO
        givenName2  <- SharedTestData.givenName2.toZIO
        familyName2 <- SharedTestData.familyName2.toZIO
        password2   <- SharedTestData.password2.toZIO

        language <- SharedTestData.languageEn.toZIO
        status   <- SharedTestData.statusTrue.toZIO

        _ <- userHandler.createUser(
               username1,
               email1,
               givenName1,
               familyName1,
               password1,
               language,
               status
             )

        error <- userHandler
                   .createUser(
                     username = username1,
                     email = email2,
                     givenName = givenName2,
                     familyName = familyName2,
                     password = password2,
                     language = language,
                     status = status
                   )
                   .exit

      } yield assert(error)(
        fails(equalTo(DuplicateValueException(s"Username '${username1.value}' already taken")))
      )
    },
    test("return an Error when creating a user if a email is already taken") {
      for {
        userHandler <- ZIO.service[UserHandler]

        username1   <- SharedTestData.username1.toZIO
        email1      <- SharedTestData.email1.toZIO
        givenName1  <- SharedTestData.givenName1.toZIO
        familyName1 <- SharedTestData.familyName1.toZIO
        password1   <- SharedTestData.password1.toZIO

        username2   <- SharedTestData.username2.toZIO
        email2      <- SharedTestData.email2.toZIO
        givenName2  <- SharedTestData.givenName2.toZIO
        familyName2 <- SharedTestData.familyName2.toZIO
        password2   <- SharedTestData.password2.toZIO

        language <- SharedTestData.languageEn.toZIO
        status   <- SharedTestData.statusTrue.toZIO

        _ <- userHandler.createUser(
               username1,
               email1,
               givenName1,
               familyName1,
               password1,
               language,
               status
             )

        error <- userHandler
                   .createUser(
                     username = username2,
                     email = email1,
                     givenName = givenName2,
                     familyName = familyName2,
                     password = password2,
                     language = language,
                     status = status
                   )
                   .exit

      } yield assert(error)(
        fails(equalTo(DuplicateValueException(s"Email '${email1.value}' already taken")))
      )
    }
  ).provide(UserRepoMock.layer, UserHandler.layer)

  private val getUserByTest = suite("getUserBy")(
    test("store a user and retrieve by ID") {
      for {
        userHandler <- ZIO.service[UserHandler]

        username1   <- SharedTestData.username1.toZIO
        email1      <- SharedTestData.email1.toZIO
        givenName1  <- SharedTestData.givenName1.toZIO
        familyName1 <- SharedTestData.familyName1.toZIO
        password1   <- SharedTestData.password1.toZIO
        language    <- SharedTestData.languageEn.toZIO
        status      <- SharedTestData.statusTrue.toZIO

        userId <- userHandler.createUser(
                    username1,
                    email1,
                    givenName1,
                    familyName1,
                    password1,
                    language,
                    status
                  )

        retrievedUser <- userHandler.getUserById(userId)
      } yield assertTrue(retrievedUser.username == username1) &&
        assertTrue(retrievedUser.email == email1) &&
        assertTrue(retrievedUser.givenName == givenName1) &&
        assertTrue(retrievedUser.familyName == familyName1) &&
        assertTrue(retrievedUser.password == password1) &&
        assertTrue(retrievedUser.language == language) &&
        assertTrue(retrievedUser.status == status)
    },
    test("return an Error if user not found by ID") {
      for {
        userHandler <- ZIO.service[UserHandler]
        newUserId   <- UserId.make().toZIO
        error       <- userHandler.getUserById(newUserId).exit
      } yield assert(error)(fails(equalTo(NotFoundException(s"User with ID '${newUserId}' not found"))))
    },
    test("store a user and retrieve by username") {
      for {
        userHandler <- ZIO.service[UserHandler]

        username1   <- SharedTestData.username1.toZIO
        email1      <- SharedTestData.email1.toZIO
        givenName1  <- SharedTestData.givenName1.toZIO
        familyName1 <- SharedTestData.familyName1.toZIO
        password1   <- SharedTestData.password1.toZIO
        language    <- SharedTestData.languageEn.toZIO
        status      <- SharedTestData.statusTrue.toZIO

        userId <- userHandler.createUser(
                    username1,
                    email1,
                    givenName1,
                    familyName1,
                    password1,
                    language,
                    status
                  )

        retrievedUser <- userHandler.getUserByUsername(username1)
      } yield assertTrue(retrievedUser.username == username1) &&
        assertTrue(retrievedUser.email == email1) &&
        assertTrue(retrievedUser.givenName == givenName1) &&
        assertTrue(retrievedUser.familyName == familyName1) &&
        assertTrue(retrievedUser.password == password1) &&
        assertTrue(retrievedUser.language == language) &&
        assertTrue(retrievedUser.status == status)
    },
    test("return an Error if user not found by username") {
      val username = Username.make("usernameThatDoesNotExist").fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]
        error       <- userHandler.getUserByUsername(username).exit
      } yield assert(error)(fails(equalTo(NotFoundException(s"User with Username '${username.value}' not found"))))
    },
    test("store a user and retrieve by email") {
      for {
        userHandler <- ZIO.service[UserHandler]

        username1   <- SharedTestData.username1.toZIO
        email1      <- SharedTestData.email1.toZIO
        givenName1  <- SharedTestData.givenName1.toZIO
        familyName1 <- SharedTestData.familyName1.toZIO
        password1   <- SharedTestData.password1.toZIO
        language    <- SharedTestData.languageEn.toZIO
        status      <- SharedTestData.statusTrue.toZIO

        userId <- userHandler.createUser(
                    username1,
                    email1,
                    givenName1,
                    familyName1,
                    password1,
                    language,
                    status
                  )

        retrievedUser <- userHandler.getUserByEmail(email1)
      } yield assertTrue(retrievedUser.username == username1) &&
        assertTrue(retrievedUser.email == email1) &&
        assertTrue(retrievedUser.givenName == givenName1) &&
        assertTrue(retrievedUser.familyName == familyName1) &&
        assertTrue(retrievedUser.password == password1) &&
        assertTrue(retrievedUser.language == language) &&
        assertTrue(retrievedUser.status == status)
    },
    test("return an Error if user not found by email") {

      val email = Email.make("emailThat@DoesNotExi.st").fold(e => throw e.head, v => v)
      for {
        userHandler <- ZIO.service[UserHandler]
        error       <- userHandler.getUserByEmail(email).exit
      } yield assert(error)(fails(equalTo(NotFoundException(s"User with Email '${email.value}' not found"))))
    }
  ).provide(UserRepoMock.layer, UserHandler.layer)

  private val updateUserTest = suite("updateUser")(
    test("store a user and update the username") {
      for {
        userHandler <- ZIO.service[UserHandler]

        newValue <- Username.make("newUsername").toZIO

        username1   <- SharedTestData.username1.toZIO
        email1      <- SharedTestData.email1.toZIO
        givenName1  <- SharedTestData.givenName1.toZIO
        familyName1 <- SharedTestData.familyName1.toZIO
        password1   <- SharedTestData.password1.toZIO
        language    <- SharedTestData.languageEn.toZIO
        status      <- SharedTestData.statusTrue.toZIO

        userId <- userHandler.createUser(
                    username1,
                    email1,
                    givenName1,
                    familyName1,
                    password1,
                    language,
                    status
                  )
        idOfUpdatedUser <- userHandler.updateUsername(userId, newValue)
        retrievedUser   <- userHandler.getUserById(userId)
      } yield assertTrue(retrievedUser.username == newValue) &&
        assertTrue(retrievedUser.username != username1) &&
        assertTrue(retrievedUser.email == email1) &&
        assertTrue(retrievedUser.givenName == givenName1) &&
        assertTrue(retrievedUser.familyName == familyName1) &&
        assertTrue(retrievedUser.password == password1) &&
        assertTrue(retrievedUser.language == language) &&
        assertTrue(retrievedUser.status == status)
    },
    test("return an error if the username is taken when trying to update the username") {
      for {
        userHandler <- ZIO.service[UserHandler]

        username1   <- SharedTestData.username1.toZIO
        email1      <- SharedTestData.email1.toZIO
        givenName1  <- SharedTestData.givenName1.toZIO
        familyName1 <- SharedTestData.familyName1.toZIO
        password1   <- SharedTestData.password1.toZIO
        language    <- SharedTestData.languageEn.toZIO
        status      <- SharedTestData.statusTrue.toZIO

        userId <- userHandler.createUser(
                    username1,
                    email1,
                    givenName1,
                    familyName1,
                    password1,
                    language,
                    status
                  )

        error <- userHandler.updateUsername(userId, username1).exit
      } yield assert(error)(
        fails(equalTo(DuplicateValueException(s"Username '${username1.value}' already taken")))
      )
    },
    test("store a user and update the email") {
      for {
        userHandler <- ZIO.service[UserHandler]

        newValue <- Email.make("new.mail1@email.com").toZIO

        username1   <- SharedTestData.username1.toZIO
        email1      <- SharedTestData.email1.toZIO
        givenName1  <- SharedTestData.givenName1.toZIO
        familyName1 <- SharedTestData.familyName1.toZIO
        password1   <- SharedTestData.password1.toZIO
        language    <- SharedTestData.languageEn.toZIO
        status      <- SharedTestData.statusTrue.toZIO

        userId <- userHandler.createUser(
                    username1,
                    email1,
                    givenName1,
                    familyName1,
                    password1,
                    language,
                    status
                  )

        idOfUpdatedUser <- userHandler.updateEmail(userId, newValue)
        retrievedUser   <- userHandler.getUserById(userId)
      } yield assertTrue(retrievedUser.email == newValue) &&
        assertTrue(retrievedUser.username == username1) &&
        assertTrue(retrievedUser.email != email1) &&
        assertTrue(retrievedUser.givenName == givenName1) &&
        assertTrue(retrievedUser.familyName == familyName1) &&
        assertTrue(retrievedUser.password == password1) &&
        assertTrue(retrievedUser.language == language) &&
        assertTrue(retrievedUser.status == status)
    },
    test("return an error if the email is taken when trying to update the email") {
      for {
        userHandler <- ZIO.service[UserHandler]

        username1   <- SharedTestData.username1.toZIO
        email1      <- SharedTestData.email1.toZIO
        givenName1  <- SharedTestData.givenName1.toZIO
        familyName1 <- SharedTestData.familyName1.toZIO
        password1   <- SharedTestData.password1.toZIO
        language    <- SharedTestData.languageEn.toZIO
        status      <- SharedTestData.statusTrue.toZIO

        userId <- userHandler.createUser(
                    username1,
                    email1,
                    givenName1,
                    familyName1,
                    password1,
                    language,
                    status
                  )

        error <- userHandler.updateEmail(userId, email1).exit
      } yield assert(error)(
        fails(equalTo(DuplicateValueException(s"Email '${email1.value}' already taken")))
      )
    },
    test("store a user and update the givenName") {
      for {
        userHandler <- ZIO.service[UserHandler]

        newValue <- GivenName.make("newGivenName").toZIO

        username1   <- SharedTestData.username1.toZIO
        email1      <- SharedTestData.email1.toZIO
        givenName1  <- SharedTestData.givenName1.toZIO
        familyName1 <- SharedTestData.familyName1.toZIO
        password1   <- SharedTestData.password1.toZIO
        language    <- SharedTestData.languageEn.toZIO
        status      <- SharedTestData.statusTrue.toZIO

        userId <- userHandler.createUser(
                    username1,
                    email1,
                    givenName1,
                    familyName1,
                    password1,
                    language,
                    status
                  )

        idOfUpdatedUser <- userHandler.updateGivenName(userId, newValue)
        retrievedUser   <- userHandler.getUserById(userId)
      } yield assertTrue(retrievedUser.givenName == newValue) &&
        assertTrue(retrievedUser.username == username1) &&
        assertTrue(retrievedUser.email == email1) &&
        assertTrue(retrievedUser.givenName != givenName1) &&
        assertTrue(retrievedUser.familyName == familyName1) &&
        assertTrue(retrievedUser.password == password1) &&
        assertTrue(retrievedUser.language == language) &&
        assertTrue(retrievedUser.status == status)
    },
    test("store a user and update the familyName") {
      for {
        userHandler <- ZIO.service[UserHandler]

        newValue <- FamilyName.make("newFamilyName").toZIO

        username1   <- SharedTestData.username1.toZIO
        email1      <- SharedTestData.email1.toZIO
        givenName1  <- SharedTestData.givenName1.toZIO
        familyName1 <- SharedTestData.familyName1.toZIO
        password1   <- SharedTestData.password1.toZIO
        language    <- SharedTestData.languageEn.toZIO
        status      <- SharedTestData.statusTrue.toZIO

        userId <- userHandler.createUser(
                    username1,
                    email1,
                    givenName1,
                    familyName1,
                    password1,
                    language,
                    status
                  )

        idOfUpdatedUser <- userHandler.updateFamilyName(userId, newValue)
        retrievedUser   <- userHandler.getUserById(userId)
      } yield assertTrue(retrievedUser.familyName == newValue) &&
        assertTrue(retrievedUser.username == username1) &&
        assertTrue(retrievedUser.email == email1) &&
        assertTrue(retrievedUser.givenName == givenName1) &&
        assertTrue(retrievedUser.familyName != familyName1) &&
        assertTrue(retrievedUser.password == password1) &&
        assertTrue(retrievedUser.language == language) &&
        assertTrue(retrievedUser.status == status)
    },
    test("store a user and update the password") {
      for {
        userHandler <- ZIO.service[UserHandler]

        passwordStrength <- PasswordStrength.make(12).toZIO
        newValue         <- PasswordHash.make("newPassword1", passwordStrength).toZIO

        username1   <- SharedTestData.username1.toZIO
        email1      <- SharedTestData.email1.toZIO
        givenName1  <- SharedTestData.givenName1.toZIO
        familyName1 <- SharedTestData.familyName1.toZIO
        password1   <- SharedTestData.password1.toZIO
        language    <- SharedTestData.languageEn.toZIO
        status      <- SharedTestData.statusTrue.toZIO

        userId <- userHandler.createUser(
                    username1,
                    email1,
                    givenName1,
                    familyName1,
                    password1,
                    language,
                    status
                  )
        storedUser      <- userHandler.getUserById(userId)
        idOfUpdatedUser <- userHandler.updatePassword(userId, newValue, storedUser.password, storedUser)
        retrievedUser   <- userHandler.getUserById(userId)
      } yield assertTrue(retrievedUser.password == newValue) &&
        assertTrue(retrievedUser.username == username1) &&
        assertTrue(retrievedUser.email == email1) &&
        assertTrue(retrievedUser.givenName == givenName1) &&
        assertTrue(retrievedUser.familyName == familyName1) &&
        assertTrue(retrievedUser.password != password1) &&
        assertTrue(retrievedUser.language == language) &&
        assertTrue(retrievedUser.status == status)
    },
    test(
      "return an error when the supplied password does not match the requesting user's password when trying to update the password"
    ) {
      for {
        userHandler <- ZIO.service[UserHandler]

        passwordStrength <- PasswordStrength.make(12).toZIO
        newValue         <- PasswordHash.make("newPassword1", passwordStrength).toZIO
        wrongPassword    <- PasswordHash.make("wrongPassword1", passwordStrength).toZIO

        username1   <- SharedTestData.username1.toZIO
        email1      <- SharedTestData.email1.toZIO
        givenName1  <- SharedTestData.givenName1.toZIO
        familyName1 <- SharedTestData.familyName1.toZIO
        password1   <- SharedTestData.password1.toZIO
        language    <- SharedTestData.languageEn.toZIO
        status      <- SharedTestData.statusTrue.toZIO

        userId <- userHandler.createUser(
                    username1,
                    email1,
                    givenName1,
                    familyName1,
                    password1,
                    language,
                    status
                  )
        storedUser <- userHandler.getUserById(userId)
        error      <- userHandler.updatePassword(userId, newValue, wrongPassword, storedUser).exit
      } yield assert(error)(
        fails(equalTo(ForbiddenException("The supplied password does not match the requesting user's password")))
      )
    },
    test(
      "return an error when the requesting user is not the user whose password is asked to be changed when trying to update the password"
    ) {
      for {
        userHandler <- ZIO.service[UserHandler]

        passwordStrength <- PasswordStrength.make(12).toZIO
        newValue         <- PasswordHash.make("newPassword1", passwordStrength).toZIO

        username1   <- SharedTestData.username1.toZIO
        email1      <- SharedTestData.email1.toZIO
        givenName1  <- SharedTestData.givenName1.toZIO
        familyName1 <- SharedTestData.familyName1.toZIO
        password1   <- SharedTestData.password1.toZIO
        language    <- SharedTestData.languageEn.toZIO
        status      <- SharedTestData.statusTrue.toZIO

        userId <- userHandler.createUser(
                    username1,
                    email1,
                    givenName1,
                    familyName1,
                    password1,
                    language,
                    status
                  )

        storedUser <- userHandler.getUserById(userId)
        otherUser  <- SharedTestData.user2.toZIO
        error      <- userHandler.updatePassword(userId, newValue, storedUser.password, otherUser).exit
      } yield assert(error)(
        fails(
          equalTo(
            ForbiddenException("User's password can only be changed by the user itself or a system administrator")
          )
        )
      )
    },
    test("store a user and update the language") {
      for {
        userHandler <- ZIO.service[UserHandler]

        newValue <- LanguageCode.make("fr").toZIO

        username1   <- SharedTestData.username1.toZIO
        email1      <- SharedTestData.email1.toZIO
        givenName1  <- SharedTestData.givenName1.toZIO
        familyName1 <- SharedTestData.familyName1.toZIO
        password1   <- SharedTestData.password1.toZIO
        language    <- SharedTestData.languageEn.toZIO
        status      <- SharedTestData.statusTrue.toZIO

        userId <- userHandler.createUser(
                    username1,
                    email1,
                    givenName1,
                    familyName1,
                    password1,
                    language,
                    status
                  )

        idOfUpdatedUser <- userHandler.updateLanguage(userId, newValue)
        retrievedUser   <- userHandler.getUserById(userId)
      } yield assertTrue(retrievedUser.language == newValue) &&
        assertTrue(retrievedUser.username == username1) &&
        assertTrue(retrievedUser.email == email1) &&
        assertTrue(retrievedUser.givenName == givenName1) &&
        assertTrue(retrievedUser.familyName == familyName1) &&
        assertTrue(retrievedUser.password == password1) &&
        assertTrue(retrievedUser.language != language) &&
        assertTrue(retrievedUser.status == status)
    }
  ).provide(UserRepoMock.layer, UserHandler.layer)

  private val deleteUserTest = suite("deleteUser")(
    test("delete a user") {
      for {
        userHandler <- ZIO.service[UserHandler]

        username1   <- SharedTestData.username1.toZIO
        email1      <- SharedTestData.email1.toZIO
        givenName1  <- SharedTestData.givenName1.toZIO
        familyName1 <- SharedTestData.familyName1.toZIO
        password1   <- SharedTestData.password1.toZIO
        language    <- SharedTestData.languageEn.toZIO
        status      <- SharedTestData.statusTrue.toZIO

        userId <- userHandler.createUser(
                    username1,
                    email1,
                    givenName1,
                    familyName1,
                    password1,
                    language,
                    status
                  )

        id               <- userHandler.deleteUser(userId)
        idNotFound       <- userHandler.getUserById(userId).exit
        usernameNotFound <- userHandler.getUserByUsername(username1).exit
        emailNotFound    <- userHandler.getUserByEmail(email1).exit

        // create new user with same values

        newUserId <- userHandler.createUser(
                       username1,
                       email1,
                       givenName1,
                       familyName1,
                       password1,
                       language,
                       status
                     )
      } yield assertTrue(id == userId) &&
        assertTrue(userId != newUserId) &&
        assert(idNotFound)(fails(equalTo(NotFoundException(s"User with ID '${userId}' not found")))) &&
        assert(usernameNotFound)(
          fails(equalTo(NotFoundException(s"User with Username '${username1.value}' not found")))
        ) &&
        assert(emailNotFound)(
          fails(equalTo(NotFoundException(s"User with Email '${email1.value}' not found")))
        )
    },
    test("return an error if the ID of a user is not found when trying to delete the user") {
      for {
        userHandler <- ZIO.service[UserHandler]
        userId      <- UserId.make().toZIO
        error       <- userHandler.deleteUser(userId).exit
      } yield assert(error)(
        fails(equalTo(NotFoundException(s"User with ID '${userId}' not found")))
      )
    }
  ).provide(UserRepoMock.layer, UserHandler.layer)
}
