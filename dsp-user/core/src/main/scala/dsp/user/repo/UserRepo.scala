/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.repo

import dsp.user.domain._
import zio._
import zio.macros.accessible
import java.util.UUID

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
   * @return     Unit
   */
  def storeUser(user: User): UIO[Unit]

  /**
   * Gets all users from the repository.
   *
   * @return   a list of [[User]]
   */
  def getAllUsers(): UIO[List[User]]

  /**
   * Retrieves the user from the repository by ID.
   *
   * @param id the user's ID
   * @return an optional [[User]]
   */
  def getUserById(id: UserId): UIO[Option[User]]

  /**
   * Retrieves the user from the repository by username or email.
   *
   * @param usernameOrEmail username or email of the user.
   * @return an optional [[User]].
   */
  def getUserByUsernameOrEmail(usernameOrEmail: String): ZIO[Any, Nothing, Option[User]]

  /**
   * Deletes a [[User]] from the repository by its [[UserId]].
   *
   * @param id the user ID
   * @return   Unit or None if not found
   */
  def deleteUser(id: UserId): IO[Option[Nothing], Unit]
}
