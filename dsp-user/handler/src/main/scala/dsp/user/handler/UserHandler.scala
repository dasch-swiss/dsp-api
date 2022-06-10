/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.handler

import dsp.user.api.UserRepo
import zio._
import java.util.UUID
import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import dsp.errors.DuplicateValueException
import dsp.user.domain.User
import dsp.user.domain.UserId
import dsp.valueobjects.User._
import dsp.errors.RequestRejectedException

/**
 * The user handler.
 *
 * @param repo  the user repository
 */
final case class UserHandler(repo: UserRepo) {
  // implement all possible requests from V2, but divide things up into smaller functions to keep it cleaner than before

  /**
   * Retrieve all users (sorted by IRI).
   */
  def getUsers(): UIO[List[User]] =
    repo.getUsers().map(_.sorted)

  // getSingleUserADM should be inspected in the route. According to the user identifier, the
  // right method from the userHandler should be called.

  /**
   * Retrieve the user by ID.
   *
   * @param id  the user's ID
   */
  def getUserById(id: UserId): IO[NotFoundException, User] =
    for {
      user <- repo.getUserById(id).mapError(_ => NotFoundException("User not found"))
    } yield user

  /**
   * Retrieve the user by username.
   *
   * @param username  the user's username
   */
  def getUserByUsername(username: Username): IO[NotFoundException, User] =
    repo
      .getUserByUsernameOrEmail(username.value)
      .mapError(_ => NotFoundException(s"User with Username ${username.value} not found"))

  /**
   * Retrieve the user by email.
   *
   * @param email  the user's email
   */
  def getUserByEmail(email: Email): IO[NotFoundException, User] =
    repo
      .getUserByUsernameOrEmail(email.value)
      .mapError(_ => NotFoundException(s"User with Email ${email.value} not found"))

  /**
   * Check if username is already taken
   *
   * @param username  the user's username
   */
  private def checkUsernameTaken(username: Username): IO[DuplicateValueException, Unit] =
    for {
      _ <- repo
             .checkUsernameOrEmailExists(username.value)
             .mapError(_ => DuplicateValueException(s"Username ${username.value} already exists"))
    } yield ()

  /**
   * Check if email is already taken
   *
   * @param email  the user's email
   */
  private def checkEmailTaken(email: Email): IO[DuplicateValueException, Unit] =
    for {
      _ <- repo
             .checkUsernameOrEmailExists(email.value)
             .mapError(_ => DuplicateValueException(s"Email ${email.value} already exists"))
    } yield ()

  def createUser(
    username: Username,
    email: Email,
    givenName: GivenName,
    familyName: FamilyName,
    password: Password,
    language: LanguageCode
    //role: Role
  ): IO[DuplicateValueException, UserId] =
    for {
      _      <- checkUsernameTaken(username) // also lock/reserve username
      _      <- checkEmailTaken(email) // also lock/reserve email
      user   <- ZIO.succeed(User.make(givenName, familyName, username, email, password, language))
      userId <- repo.storeUser(user) // we assume that this can't fail
    } yield userId

  def updateUsername(id: UserId, value: Username): IO[RequestRejectedException, UserId] =
    for {
      _ <- checkUsernameTaken(value)
      // lock/reserve username
      // check if user exists and get him, lock user
      user        <- getUserById(id)
      userUpdated <- ZIO.succeed(user.updateUsername(value))
      _           <- repo.storeUser(userUpdated)
    } yield id

  def updatePassword(id: UserId, newPassword: Password, currentPassword: Password, requestingUser: User) = ???
  // either the user himself or a sysadmin can change a user's password
  // in both cases we need the current password of either the user itself or the sysadmin

  def deleteUser(id: UserId): IO[NotFoundException, UserId] =
    for {
      _ <- repo
             .deleteUser(id)
             .mapError(_ => NotFoundException(s"User with ID ${id} not found"))
    } yield id

}

/**
 * Companion object providing the layer with an initialized implementation
 */
object UserHandler {
  val layer: ZLayer[UserRepo, Nothing, UserHandler] = {
    ZLayer {
      for {
        repo <- ZIO.service[UserRepo]
      } yield UserHandler(repo)
    }.tap(_ => ZIO.debug(">>> User handler initialized <<<"))
  }
}
