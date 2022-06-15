/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.handler

import dsp.errors.BadRequestException
import dsp.errors.DuplicateValueException
import dsp.errors.NotFoundException
import dsp.errors.RequestRejectedException
import dsp.user.api.UserRepo
import dsp.user.domain.User
import dsp.valueobjects.User._
import zio._

import java.util.UUID
import dsp.valueobjects.UserId
import dsp.errors.ForbiddenException

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * The user handler.
 *
 * @param repo  the user repository
 */
final case class UserHandler(repo: UserRepo) {
  // implement all possible requests from V2, but divide things up into smaller functions to keep it cleaner than before

  /**
   * Retrieves all users (sorted by IRI).
   */
  def getUsers(): UIO[List[User]] =
    repo.getUsers().map(_.sorted)

  /**
   * Retrieves the user by ID.
   *
   * @param id  the user's ID
   */
  def getUserById(id: UserId): IO[NotFoundException, User] =
    for {
      user <- repo.getUserById(id).mapError(_ => NotFoundException("User not found"))
    } yield user

  /**
   * Retrieves the user by username.
   *
   * @param username  the user's username
   */
  def getUserByUsername(username: Username): IO[NotFoundException, User] =
    repo
      .getUserByUsername(username)
      .mapError(_ => NotFoundException(s"User with Username ${username.value} not found"))

  /**
   * Retrieves the user by email.
   *
   * @param email  the user's email
   */
  def getUserByEmail(email: Email): IO[NotFoundException, User] =
    repo
      .getUserByEmail(email)
      .mapError(_ => NotFoundException(s"User with Email ${email.value} not found"))

  /**
   * Checks if username is already taken
   *
   * @param username  the user's username
   */
  private def checkUsernameTaken(username: Username): IO[DuplicateValueException, Unit] =
    for {
      _ <- repo
             .checkUsernameExists(username)
             .mapError(_ => DuplicateValueException(s"Username ${username.value} already exists"))
    } yield ()

  /**
   * Checks if email is already taken
   *
   * @param email  the user's email
   */
  private def checkEmailTaken(email: Email): IO[DuplicateValueException, Unit] =
    for {
      _ <- repo
             .checkEmailExists(email)
             .mapError(_ => DuplicateValueException(s"Email ${email.value} already exists"))
    } yield ()

  /**
   * Creates a new user
   *
   *  @param username  the user's username
   *  @param email  the user's email
   *  @param givenName  the user's givenName
   *  @param familyName  the user's familyName
   *  @param password  the user's password
   *  @param language  the user's language
   *  @param role  the user's role
   */
  def createUser(
    username: Username,
    email: Email,
    givenName: GivenName,
    familyName: FamilyName,
    password: Password,
    language: LanguageCode,
    status: UserStatus
    //role: Role
  ): IO[DuplicateValueException, UserId] =
    for {
      _      <- checkUsernameTaken(username) // TODO reserve username
      _      <- checkEmailTaken(email) // TODO reserve email
      user   <- ZIO.succeed(User.make(givenName, familyName, username, email, password, language, status))
      userId <- repo.storeUser(user) // we assume that this can't fail because all validations have passed
    } yield userId

  /**
   * Updates the username of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new username
   */
  def updateUsername(id: UserId, newValue: Username): IO[RequestRejectedException, UserId] =
    for {
      _ <- checkUsernameTaken(newValue)
      // TODO reserve new username because it has to be unique
      // check if user exists and get him
      user        <- getUserById(id)
      userUpdated <- ZIO.succeed(user.updateUsername(newValue))
      _           <- repo.storeUser(userUpdated)
    } yield id

  /**
   * Updates the email of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new email
   */
  def updateEmail(id: UserId, newValue: Email): IO[RequestRejectedException, UserId] =
    for {
      // TODO reserve new email because it has to be unique
      _ <- checkEmailTaken(newValue)
      // check if user exists and get him
      user        <- getUserById(id)
      userUpdated <- ZIO.succeed(user.updateEmail(newValue))
      _           <- repo.storeUser(userUpdated)
    } yield id

  /**
   * Updates the given name of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new given name
   */
  def updateGivenName(id: UserId, newValue: GivenName): IO[RequestRejectedException, UserId] =
    for {
      // check if user exists and get him
      user        <- getUserById(id)
      userUpdated <- ZIO.succeed(user.updateGivenName(newValue))
      _           <- repo.storeUser(userUpdated)
    } yield id

  /**
   * Updates the family name of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new family name
   */
  def updateFamilyName(id: UserId, newValue: FamilyName): IO[RequestRejectedException, UserId] =
    for {
      // check if user exists and get him, lock user
      user        <- getUserById(id)
      userUpdated <- ZIO.succeed(user.updateFamilyName(newValue))
      _           <- repo.storeUser(userUpdated)
    } yield id

  /**
   * Updates the password of a user
   *
   *  @param id  the user's ID
   *  @param newPassword  the new password
   *  @param currentPassword  the user's current password
   *  @param requestingUser  the requesting user
   */
  def updatePassword(id: UserId, newPassword: Password, currentPassword: Password, requestingUser: User) = {
    // either the user himself or a sysadmin can change a user's password
    if (!requestingUser.id.equals(id)) { // TODO check role, user needs to be himself or sysadmin, i.e. sth like requestingUser.role.equals(SysAdmin)
      ZIO.fail(ForbiddenException("User's password can only be changed by the user itself or a system administrator"))
    }

    // check if the provided current password (of either the user or the sysadmin) is correct
    if (!requestingUser.passwordMatch(currentPassword)) {
      ZIO.fail(ForbiddenException("The supplied password does not match the requesting user's password"))
    }

    // hash the new password
    // encoder = new BCryptPasswordEncoder(settings.bcryptPasswordStrength), TODO read this from the settings, like so: config.getInt("app.bcrypt-password-strength")
    val encoder = new BCryptPasswordEncoder(12)
    val newHashedPassword = Password
      .make(encoder.encode(newPassword.value))
      .fold(e => throw e.head, value => value)

    for {
      // check if user exists and get him, lock user
      user        <- getUserById(id)
      userUpdated <- ZIO.succeed(user.updatePassword(newPassword))
      _           <- repo.storeUser(userUpdated)
    } yield id
  }

  /**
   * Updates the username of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new language
   */
  def updateLanguage(id: UserId, newValue: LanguageCode): IO[RequestRejectedException, UserId] =
    for {
      // check if user exists and get him, lock user
      user        <- getUserById(id)
      userUpdated <- ZIO.succeed(user.updateLanguage(newValue))
      _           <- repo.storeUser(userUpdated)
    } yield id

  /**
   * Deletes the user which means that it is marked as deleted.
   *
   *  @param id  the user's ID
   */
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
