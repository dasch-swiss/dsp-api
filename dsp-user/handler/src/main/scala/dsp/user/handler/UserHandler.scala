/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.handler

import zio._

import dsp.errors.DuplicateValueException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import dsp.errors.RequestRejectedException
import dsp.user.api.UserRepo
import dsp.user.domain.User
import dsp.util.UuidGenerator
import dsp.valueobjects.Id.UserId
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.User._

/**
 * The user handler.
 *
 * @param repo  the user repository
 */
final case class UserHandler(repo: UserRepo) {

  /**
   * Retrieves all users (sorted by IRI).
   */
  def getUsers(): UIO[List[User]] =
    repo.getUsers().map(_.sorted).tap(_ => ZIO.logInfo(s"Got all users"))

  /**
   * Retrieves the user by ID.
   *
   * @param id  the user's ID
   */
  def getUserById(id: UserId): IO[NotFoundException, User] =
    for {
      user <- repo
                .getUserById(id)
                .mapError(_ => NotFoundException(s"User with ID '${id}' not found"))
                .tap(_ => ZIO.logInfo(s"Looked up user by ID '${id}'"))
    } yield user

  /**
   * Retrieves the user by username.
   *
   * @param username  the user's username
   */
  def getUserByUsername(username: Username): IO[NotFoundException, User] =
    repo
      .getUserByUsername(username)
      .mapError(_ => NotFoundException(s"User with Username '${username.value}' not found"))
      .tap(_ => ZIO.logInfo(s"Looked up user by username '${username.value}'"))

  /**
   * Retrieves the user by email.
   *
   * @param email  the user's email
   */
  def getUserByEmail(email: Email): IO[NotFoundException, User] =
    repo
      .getUserByEmail(email)
      .mapError(_ => NotFoundException(s"User with Email '${email.value}' not found"))
      .tap(_ => ZIO.logInfo(s"Looked up user by email '${email.value}'"))

  /**
   * Checks if username is already taken
   *
   * @param username  the user's username
   */
  private def checkIfUsernameTaken(username: Username): IO[DuplicateValueException, Unit] =
    for {
      _ <- repo
             .checkIfUsernameExists(username)
             .mapError(_ => DuplicateValueException(s"Username '${username.value}' already taken"))
             .tapBoth(
               _ => ZIO.logInfo(s"Username '${username.value}' already taken"),
               _ => ZIO.logInfo(s"Checked if username '${username.value}' is already taken")
             )
    } yield ()

  /**
   * Checks if email is already taken
   *
   * @param email  the user's email
   */
  private def checkIfEmailTaken(email: Email): IO[DuplicateValueException, Unit] =
    for {
      _ <- repo
             .checkIfEmailExists(email)
             .mapError(_ => DuplicateValueException(s"Email '${email.value}' already taken"))
             .tapBoth(
               _ => ZIO.logInfo(s"Email '${email.value}' already taken"),
               _ => ZIO.logInfo(s"Checked if email '${email.value}' is already taken")
             )
    } yield ()

  /**
   * Migrates an existing user. Same as [[createUser]] but with an existing ID.
   *
   * @param id  the user's id
   *  @param username  the user's username
   *  @param email  the user's email
   *  @param givenName  the user's givenName
   *  @param familyName  the user's familyName
   *  @param password  the user's password (hashed)
   *  @param language  the user's language
   *  @param role  the user's role
   */
  def migrateUser(
    id: UserId,
    username: Username,
    email: Email,
    givenName: GivenName,
    familyName: FamilyName,
    password: PasswordHash,
    language: LanguageCode,
    status: UserStatus
  ): IO[Throwable, UserId] =
    (for {
      // _      <- checkIfIdExists(id)            // TODO implement this
      _      <- checkIfUsernameTaken(username) // TODO reserve username
      _      <- checkIfEmailTaken(email)       // TODO reserve email
      user   <- User.make(id, givenName, familyName, username, email, password, language, status).toZIO
      userId <- repo.storeUser(user)
    } yield userId).tap(userId => ZIO.logInfo(s"Migrated user with ID '${userId}'"))

  /**
   * Creates a new user
   *
   *  @param username  the user's username
   *  @param email  the user's email
   *  @param givenName  the user's givenName
   *  @param familyName  the user's familyName
   *  @param password  the user's password (hashed)
   *  @param language  the user's language
   *  @param role  the user's role
   *  @return the UserId of the newly created user
   */
  def createUser(
    username: Username,
    email: Email,
    givenName: GivenName,
    familyName: FamilyName,
    password: PasswordHash,
    language: LanguageCode,
    status: UserStatus
    // role: Role
  ): ZIO[UuidGenerator, Throwable, UserId] =
    (for {
      uuidGenerator <- ZIO.service[UuidGenerator]
      _             <- checkIfUsernameTaken(username) // TODO reserve username
      _             <- checkIfEmailTaken(email)       // TODO reserve email
      uuid          <- uuidGenerator.createRandomUuid
      id            <- UserId.make(uuid).toZIO
      user          <- User.make(id, givenName, familyName, username, email, password, language, status).toZIO
      userId        <- repo.storeUser(user)
    } yield userId).tap(userId => ZIO.logInfo(s"Created user with ID '${userId}'"))

  /**
   * Updates the username of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new username
   *  @return the UserId of the newly created user
   */
  def updateUsername(id: UserId, newValue: Username): IO[RequestRejectedException, UserId] =
    (for {
      _ <- checkIfUsernameTaken(newValue)
      // TODO reserve new username because it has to be unique
      // check if user exists and get him
      user        <- getUserById(id)
      userUpdated <- user.updateUsername(newValue).toZIO
      _           <- repo.storeUser(userUpdated)
    } yield id).tap(_ => ZIO.logInfo(s"Updated username with new value '${newValue.value}'"))

  /**
   * Updates the email of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new email
   *  @return the UserId of the newly created user
   */
  def updateEmail(id: UserId, newValue: Email): IO[RequestRejectedException, UserId] =
    (for {
      // TODO reserve new email because it has to be unique
      _ <- checkIfEmailTaken(newValue)
      // check if user exists and get him
      user        <- getUserById(id)
      userUpdated <- user.updateEmail(newValue).toZIO
      _           <- repo.storeUser(userUpdated)
    } yield id).tap(_ => ZIO.logInfo(s"Updated email with new value '${newValue.value}'"))

  /**
   * Updates the given name of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new given name
   *  @return the UserId of the newly created user
   */
  def updateGivenName(id: UserId, newValue: GivenName): IO[RequestRejectedException, UserId] =
    (for {
      // check if user exists and get him
      user        <- getUserById(id)
      userUpdated <- user.updateGivenName(newValue).toZIO
      _           <- repo.storeUser(userUpdated)
    } yield id).tap(_ => ZIO.logInfo(s"Updated givenName with new value '${newValue.value}'"))

  /**
   * Updates the family name of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new family name
   *  @return the UserId of the newly created user
   */
  def updateFamilyName(id: UserId, newValue: FamilyName): IO[RequestRejectedException, UserId] =
    (for {
      // check if user exists and get him, lock user
      user        <- getUserById(id)
      userUpdated <- user.updateFamilyName(newValue).toZIO
      _           <- repo.storeUser(userUpdated)
    } yield id).tap(_ => ZIO.logInfo(s"Updated familyName with new value '${newValue.value}'"))

  /**
   * Updates the password of a user
   *
   *  @param id  the user's ID
   *  @param newPassword  the new password
   *  @param currentPassword  the user's current password
   *  @param requestingUser  the requesting user
   *  @return the UserId of the newly created user
   */
  def updatePassword(
    id: UserId,
    newPassword: PasswordHash,
    currentPassword: PasswordHash,
    requestingUser: User
  ): IO[RequestRejectedException, UserId] = {
    // either the user himself or a sysadmin can change a user's password
    if (!requestingUser.id.equals(id)) { // TODO check role, user needs to be himself or sysadmin, i.e. sth like requestingUser.role.equals(SysAdmin)
      return ZIO.fail(
        ForbiddenException("User's password can only be changed by the user itself or a system administrator")
      )
    }

    // check if the provided current password (of either the user or the sysadmin) is correct
    if (!requestingUser.password.equals(currentPassword)) {
      return ZIO.fail(ForbiddenException("The supplied password does not match the requesting user's password"))
    }

    (for {
      // check if user exists and get him, lock user
      user        <- getUserById(id)
      userUpdated <- user.updatePassword(newPassword).toZIO
      _           <- repo.storeUser(userUpdated)
    } yield id).tap(_ => ZIO.logInfo(s"Updated password"))
  }

  /**
   * Updates the username of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new language
   *  @return the UserId of the newly created user
   */
  def updateLanguage(id: UserId, newValue: LanguageCode): IO[RequestRejectedException, UserId] =
    (for {
      // check if user exists and get him, lock user
      user        <- getUserById(id)
      userUpdated <- user.updateLanguage(newValue).toZIO
      _           <- repo.storeUser(userUpdated)
    } yield id).tap(_ => ZIO.logInfo(s"Updated language with new value '${newValue.value}'"))

  /**
   * Deletes the user which means that it is marked as deleted.
   *
   *  @param id  the user's ID
   *  @return the UserId of the newly created user
   */
  def deleteUser(id: UserId): IO[NotFoundException, UserId] =
    (for {
      _ <- repo
             .deleteUser(id)
             .mapError(_ => NotFoundException(s"User with ID '${id}' not found"))
    } yield id).tap(_ => ZIO.logInfo(s"Deleted user with ID '${id}'"))

}

object UserHandler {
  val layer: ZLayer[UserRepo & UuidGenerator, Nothing, UserHandler] =
    ZLayer
      .fromFunction(UserHandler.apply _)
      .tap(_ => ZIO.logInfo(">>> User handler initialized <<<"))
}
