/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.repo.impl

import zio._
import zio.stm._
import java.util.UUID
import dsp.user.domain.User
import dsp.user.domain.UserId
import dsp.user.domain.Iri
import dsp.user.api.UserRepo

/**
 * User repo test implementation. Mocks the user repo for tests.
 *
 * @param users       a map of users (UUID -> User).
 * @param lookupTable a map of users (username/email -> UUID).
 */
final case class UserRepoMock(
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
    } yield user.id).commit.tap(_ => ZIO.logInfo(s"Stored user: ${user.id.uuid}"))

  /**
   * @inheritDoc
   */
  def getUsers(): UIO[List[User]] =
    users.values.commit.tap(userList => ZIO.logInfo(s"Looked up all users, found ${userList.size}"))

  /**
   * @inheritDoc
   */
  def getUserById(id: UserId): IO[Option[Nothing], User] =
    users
      .get(id.uuid)
      .commit
      .some
      .tapBoth(
        _ => ZIO.logInfo(s"Couldn't find user with UUID '${id.uuid}'"),
        _ => ZIO.logInfo(s"Looked up user by UUID '${id.uuid}'")
      )

  /**
   * @inheritDoc
   */
  def getUserByUsernameOrEmail(usernameOrEmail: String): IO[Option[Nothing], User] =
    (for {
      iri: UUID  <- lookupTable.get(usernameOrEmail).some
      user: User <- users.get(iri).some
    } yield user).commit.tapBoth(
      _ => ZIO.logInfo(s"Couldn't find user with username/email '${usernameOrEmail}'"),
      _ => ZIO.logInfo(s"Looked up user by username/email '${usernameOrEmail}'")
    )

  /**
   * @inheritDoc
   */
  def deleteUser(id: UserId): IO[Option[Nothing], UserId] =
    (for {
      user: User <- users.get(id.uuid).some
      _          <- users.delete(id.uuid) // removes the values (User) for the key (UUID)
      _          <- lookupTable.delete(user.username.value) // remove the user also from the lookup table
    } yield id).commit.tap(_ => ZIO.logDebug(s"Deleted user: ${id}"))
}

/**
 * Companion object providing the layer with an initialized implementation of UserRepo
 */
object UserRepoMock {
  val layer: ZLayer[Any, Nothing, UserRepo] = {
    ZLayer {
      for {
        users <- TMap.empty[UUID, User].commit
        lut   <- TMap.empty[String, UUID].commit
      } yield UserRepoMock(users, lut)
    }.tap(_ => ZIO.debug(">>> In-memory user repository initialized <<<"))
  }
}
