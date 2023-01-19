/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.api

import zio._
import zio.macros.accessible

import dsp.user.domain._
import dsp.valueobjects.Id.UserId
import dsp.valueobjects.User._

/**
 * The trait (interface) for the user repository. The user repository is responsible for storing and retrieving users.
 * Needs to be used by the user repository implementations.
 */
@accessible // with this annotation we don't have to write the companion object ourselves
trait UserRepo {

  /**
   * Writes a user to the repository (used for both create and update).
   * If this fails (e.g. the triplestore is not available), it's a non-recovable error. That's why we need UIO.
   *   When used, we should do it like: ...store(...).orDie
   *
   * @param user the user to write
   * @return     the Id of the stored user
   */
  def storeUser(user: User): UIO[UserId]

  /**
   * Gets all users from the repository.
   *
   * @return   a list of [[User]]
   */
  def getUsers(): UIO[List[User]]

  /**
   * Retrieves the user from the repository by ID.
   *
   * @param id the user's ID
   * @return a [[User]] or None if not found
   */
  def getUserById(id: UserId): IO[Option[Nothing], User]

  /**
   * Retrieves the user from the repository by username.
   *
   * @param username username of the user.
   * @return a [[User]] or None if not found
   */
  def getUserByUsername(username: Username): IO[Option[Nothing], User]

  /**
   * Retrieves the user from the repository by email.
   *
   * @param email email of the user.
   * @return a [[User]] or None if not found
   */
  def getUserByEmail(email: Email): IO[Option[Nothing], User]

  /**
   * Checks if a username exists in the repo.
   *
   * @param username username of the user.
   * @return Unit or None if not found
   */
  def checkIfUsernameExists(username: Username): IO[Option[Nothing], Unit]

  /**
   * Checks if an email exists in the repo.
   *
   * @param email email of the user.
   * @return Unit or None if not found
   */
  def checkIfEmailExists(email: Email): IO[Option[Nothing], Unit]

  /**
   * Deletes a [[User]] from the repository by its [[UserId]].
   *
   * @param id the user ID
   * @return   the Id of the User or None if not found
   */
  def deleteUser(id: UserId): IO[Option[Nothing], UserId]
}
