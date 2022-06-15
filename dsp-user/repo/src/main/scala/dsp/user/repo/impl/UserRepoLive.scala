/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.repo.impl

import dsp.errors.NotFoundException
import dsp.user.api.UserRepo
import dsp.user.domain.User
import zio._
import zio.stm.TMap

import java.util.UUID
import dsp.valueobjects.UserId

/**
 * User repository live implementation
 *
 * @param users       a map of users (UUID -> User).
 * @param lookupTable a map of username/email to UUID.
 */
final case class UserRepoLive(
  users: TMap[UUID, User],
  lookupTable: TMap[String, UUID] // sealed trait for key type
) extends UserRepo {

  /**
   * @inheritDoc
   *
   * Stores the user with key UUID in the users map.
   * Stores the username and email with the associated UUID in the lookup table.
   */
  def storeUser(user: User): UIO[UserId] =
    (for {
      _ <- users.put(user.id.uuid, user)
      _ <- lookupTable.put(user.username.value, user.id.uuid)
      _ <- lookupTable.put(user.email.value, user.id.uuid)
    } yield user.id).commit.tap(_ => ZIO.logDebug(s"Stored user: ${user.id}"))

  /**
   * @inheritDoc
   */
  def getUsers(): UIO[List[User]] = users.values.commit

  /**
   * @inheritDoc
   */
  def getUserById(id: UserId): IO[Option[Nothing], User] =
    (for {
      user <- users.get(id.uuid).some
    } yield user).commit.tap(_ => ZIO.logDebug(s"Found user by ID: ${id}"))

  /**
   * @inheritDoc
   */
  def getUserByUsernameOrEmail(usernameOrEmail: String): IO[Option[Nothing], User] =
    (for {
      iri  <- lookupTable.get(usernameOrEmail).some
      user <- users.get(iri).some
    } yield user).commit.tap(_ => ZIO.logDebug(s"Found user by username or email: ${usernameOrEmail}"))

  /**
   * @inheritDoc
   */
  def checkUsernameOrEmailExists(usernameOrEmail: String): IO[Option[Nothing], Unit] =
    (for {
      iriOption: Option[UUID] <- lookupTable.get(usernameOrEmail)
      _ = iriOption match {
            case None    => ZIO.succeed(()) // username or email does not exist
            case Some(_) => ZIO.fail(None)  // username or email does exist
          }
    } yield ()).commit.tap(_ => ZIO.logInfo(s"Username/email '${usernameOrEmail}' was checked"))

  /**
   * @inheritDoc
   */
  def deleteUser(id: UserId): IO[Option[Nothing], UserId] =
    (for {
      user <- users.get(id.uuid).some
      _    <- users.delete(id.uuid) // removes the values (User) for the key (UUID)
      _    <- lookupTable.delete(user.username.value) // remove the user also from the lookup table
    } yield id).commit.tap(_ => ZIO.logDebug(s"Deleted user: ${id}"))
}

/**
 * Companion object providing the layer with an initialized implementation of UserRepo
 */
object UserRepoLive {
  val layer: ZLayer[Any, Nothing, UserRepo] = {
    ZLayer {
      for {
        users       <- TMap.empty[UUID, User].commit
        lookupTable <- TMap.empty[String, UUID].commit
      } yield UserRepoLive(users, lookupTable)
    }.tap(_ => ZIO.debug(">>> User repository initialized <<<"))
  }
}
