/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.repo.impl

import zio._
import zio.stm.TMap

import java.util.UUID

import dsp.user.api.UserRepo
import dsp.user.domain.User
import dsp.valueobjects.Id.UserId
import dsp.valueobjects.User._

/**
 * User repo test implementation. Mocks the user repo for tests.
 *
 * @param users       a map of users (UUID -> User).
 * @param lookupTableUsernameUuid a map of users (Username -> UUID).
 * @param lookupTableEmailUuid a map of users (Email -> UUID).
 */
final case class UserRepoMock(
  users: TMap[UUID, User],
  lookupTableUsernameToUuid: TMap[Username, UUID],
  lookupTableEmailToUuid: TMap[Email, UUID]
) extends UserRepo {

  /**
   * @inheritDoc
   *
   * Stores the user with key UUID in the users map.
   * Stores the username and email with the associated UUID in the lookup tables.
   */
  def storeUser(user: User): UIO[UserId] =
    (for {
      _ <- users.put(user.id.uuid, user)
      _ <- lookupTableUsernameToUuid.put(user.username, user.id.uuid)
      _ <- lookupTableEmailToUuid.put(user.email, user.id.uuid)
    } yield user.id).commit.tap(_ => ZIO.logInfo(s"Stored user with ID '${user.id.uuid}'"))

  /**
   * @inheritDoc
   */
  def getUsers(): UIO[List[User]] =
    users.values.commit.tap(userList => ZIO.logInfo(s"Looked up all users, found ${userList.size} users"))

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
  def getUserByUsername(username: Username): IO[Option[Nothing], User] =
    (for {
      iri: UUID  <- lookupTableUsernameToUuid.get(username).some
      user: User <- users.get(iri).some
    } yield user).commit.tapBoth(
      _ => ZIO.logInfo(s"Couldn't find user with username '${username.value}'"),
      _ => ZIO.logInfo(s"Looked up user by username '${username.value}'")
    )

  /**
   * @inheritDoc
   */
  def getUserByEmail(email: Email): IO[Option[Nothing], User] =
    (for {
      iri: UUID  <- lookupTableEmailToUuid.get(email).some
      user: User <- users.get(iri).some
    } yield user).commit.tapBoth(
      _ => ZIO.logInfo(s"Couldn't find user with email '${email.value}'"),
      _ => ZIO.logInfo(s"Looked up user by email '${email.value}'")
    )

  /**
   * @inheritDoc
   */
  def checkIfUsernameExists(username: Username): IO[Option[Nothing], Unit] =
    (for {
      usernameExists <- lookupTableUsernameToUuid.contains(username).commit
      _ <- usernameExists match {
             case false => ZIO.unit       // username does not exist
             case true  => ZIO.fail(None) // username does exist
           }
    } yield ()).tap(_ => ZIO.logInfo(s"Username '${username.value}' was checked"))

  /**
   * @inheritDoc
   */
  def checkIfEmailExists(email: Email): IO[Option[Nothing], Unit] =
    (for {
      emailExists <- lookupTableEmailToUuid.contains(email).commit
      _ <- emailExists match {
             case false => ZIO.unit       // email does not exist
             case true  => ZIO.fail(None) // email does exist
           }
    } yield ()).tap(_ => ZIO.logInfo(s"Email '${email.value}' was checked"))

  /**
   * @inheritDoc
   */
  def deleteUser(id: UserId): IO[Option[Nothing], UserId] =
    // val userStatusFalse = UserStatus.make(false).fold(e => throw e.head, v => v)

    (for {
      user: User <- users.get(id.uuid).some
      // _          <- ZIO.succeed(user.updateStatus(userStatusFalse)) TODO how do we deal deleted users?
      _ <- users.delete(id.uuid)                           // removes the values (User) for the key (UUID)
      _ <- lookupTableUsernameToUuid.delete(user.username) // remove the user also from the lookup table
      _ <- lookupTableEmailToUuid.delete(user.email)       // remove the user also from the lookup table
    } yield id).commit.tap(_ => ZIO.logInfo(s"Deleted user: ${id}"))

}

/**
 * Companion object providing the layer with an initialized implementation of UserRepo
 */
object UserRepoMock {
  val layer: ZLayer[Any, Nothing, UserRepo] =
    ZLayer {
      for {
        users       <- TMap.empty[UUID, User].commit
        lutUsername <- TMap.empty[Username, UUID].commit
        lutEmail    <- TMap.empty[Email, UUID].commit
      } yield UserRepoMock(users, lutUsername, lutEmail)
    }.tap(_ => ZIO.logInfo(">>> In-memory user repository initialized <<<"))
}
