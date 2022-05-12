/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.repo

import zio._
import zio.stm._
import java.util.UUID
import dsp.user.domain.User
import dsp.user.domain.UserId

/**
 * In-memory user repository implementation
 *
 * @param users     a map of users (UUID -> User).
 * @param lut       lut = lookup table; a map of username or email to UUID.
 */
final case class UserRepoInMem(
  users: TMap[UUID, User],
  lut: TMap[String, UUID] // sealed trait for key type
) extends UserRepo {
  def store(user: User): UIO[Unit] = putUser(user)

  def lookup(id: UserId): UIO[Option[User]] = getUserById(id)

  def getAll(): UIO[List[User]] = users.values.commit

  /**
   * Deletes a user from the users map by its ID.
   *
   * @param id ID of the user.
   */
  def deleteUser(id: UserId): ZIO[Any, Option[Nothing], Unit] =
    (for {
      user: User <- users.get(id.uuid).some
      _          <- users.delete(id.uuid) // removes the values (User) for the key (UUID)
      _          <- lut.delete(user.username) // remove the user also from the lookup table
    } yield ()).commit.tap(_ => ZIO.logDebug(s"Deleted user: ${id}"))

  /**
   * Stores the user with key UUID in the users map.
   * Stores the username and email with the associated UUID in the lookup table.
   *
   * @param user the value to be stored
   */
  private def putUser(user: User): UIO[Unit] =
    (for {
      _ <- users.put(user.id.uuid, user)
      _ <- lut.put(user.username, user.id.uuid)
      _ <- lut.put(user.email, user.id.uuid)
    } yield ()).commit.tap(_ => ZIO.logDebug(s"Stored user: ${user.id}"))

  /**
   * Retrieves the user by ID.
   *
   * @param id the user's ID.
   * @return an optional [[User]].
   */
  private def getUserById(id: UserId): UIO[Option[User]] =
    users.get(id.uuid).commit.tap(_ => ZIO.logDebug(s"Found user by ID: ${id}"))

  /**
   * Retrieves the user by username or email.
   *
   * @param usernameOrEmail username or email of the user.
   * @return an optional [[User]].
   */
  private def getUserByUsernameOrEmail(usernameOrEmail: String): ZIO[Any, Nothing, Option[User]] =
    (for {
      iri: UUID  <- lut.get(usernameOrEmail).some
      user: User <- users.get(iri).some
    } yield user).commit.unsome.tap(_ => ZIO.logDebug(s"Found user by username or e-mail: ${usernameOrEmail}"))

}

/**
 * Companion object providing the layer with an initialized implementation
 */
object UserRepoInMem {
  val layer: ZLayer[Any, Nothing, UserRepo] = {
    ZLayer {
      for {
        users <- TMap.empty[UUID, User].commit
        lut   <- TMap.empty[String, UUID].commit
      } yield UserRepoInMem(users, lut)
    }.tap(_ => ZIO.debug(">>> In-memory user repository initialized <<<"))
  }
}
