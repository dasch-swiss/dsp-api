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
import dsp.user.domain.UserValueObjects
import dsp.user.domain.Iri

/**
 * In-memory user repository implementation for
 *
 * @param users       a map of users (UUID -> User).
 * @param lookupTable a map of username/email to UUID.
 */
final case class UserRepoInMem(
  users: TMap[UUID, User],
  lookupTable: TMap[String, UUID] // sealed trait for key type
) extends UserRepo {

  /**
   * @inheritDoc
   *
   * Stores the user with key UUID in the users map.
   * Stores the username and email with the associated UUID in the lookup table.
   */
  def storeUser(user: User): UIO[Unit] =
    (for {
      _ <- users.put(user.id.uuid, user)
      _ <- lookupTable.put(user.username.value, user.id.uuid)
      _ <- lookupTable.put(user.email.value, user.id.uuid)
    } yield ()).commit.tap(_ => ZIO.logDebug(s"Stored user: ${user.id}"))

  /**
   * @inheritDoc
   */
  def getAllUsers(): UIO[List[User]] = users.values.commit

  /**
   * @inheritDoc
   */
  def getUserById(id: UserId): UIO[Option[User]] =
    users.get(id.uuid).commit.tap(_ => ZIO.logDebug(s"Found user by ID: ${id}"))

  /**
   * @inheritDoc
   */
  def getUserByUsernameOrEmail(usernameOrEmail: String): ZIO[Any, Nothing, Option[User]] =
    (for {
      iri: UUID  <- lookupTable.get(usernameOrEmail).some
      user: User <- users.get(iri).some
    } yield user).commit.unsome.tap(_ => ZIO.logDebug(s"Found user by username or email: ${usernameOrEmail}"))

  /**
   * @inheritDoc
   */
  def deleteUser(id: UserId): IO[Option[Nothing], Unit] =
    (for {
      user: User <- users.get(id.uuid).some
      _          <- users.delete(id.uuid) // removes the values (User) for the key (UUID)
      _          <- lookupTable.delete(user.username.value) // remove the user also from the lookup table
    } yield ()).commit.tap(_ => ZIO.logDebug(s"Deleted user: ${id}"))
}

/**
 * Companion object providing the layer with an initialized implementation of UserRepo
 */
object UserRepoInMem {
  val test: ZLayer[Any, Nothing, UserRepo] = {
    ZLayer {
      for {
        users <- TMap.empty[UUID, User].commit
        lut   <- TMap.empty[String, UUID].commit
      } yield UserRepoInMem(users, lut)
    }.tap(_ => ZIO.debug(">>> In-memory user repository initialized <<<"))
  }
}
