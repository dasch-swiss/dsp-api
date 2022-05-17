/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.handler

import dsp.user.domain._
import dsp.user.repo.UserRepo
import zio._
import java.util.UUID
import zio.macros.accessible

/**
 * The user handler.
 *
 * @param repo  the user repository
 */
final case class UserHandler(repo: UserRepo) {
  def getAll(): ZIO[Any, Nothing, List[User]]                  = repo.getAllUsers()
  def getUserById(id: UserId): ZIO[Any, Nothing, Option[User]] = repo.getUserById(id)
  def getUserByIri(iri: Iri.UserIri): ZIO[Any, Nothing, Option[User]] = {
    val userId = UserId.fromString(iri.toString())
    getUserById(userId)
  }

  def getUserByUuid(uuid: UUID): ZIO[Any, Nothing, Option[User]] = ???
  def getUserByUsername(username: Username): ZIO[Any, Nothing, Option[User]] =
    repo.getUserByUsernameOrEmail(username.value)
  def getUserByEmail(email: Email): ZIO[Any, Nothing, Option[User]] =
    repo.getUserByUsernameOrEmail(email.value)
  def createUser(user: User): UIO[Unit] = repo.storeUser(user)
  def updateUser(user: User): IO[Option[Nothing], Unit] =
    for {
      currentUser <- getUserById(user.id) //.orElse()?
      _           <- deleteUser(user.id)
      _           <- createUser(user)
    } yield ()
  def deleteUser(id: UserId): IO[Option[Nothing], Unit] = repo.deleteUser(id)
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
