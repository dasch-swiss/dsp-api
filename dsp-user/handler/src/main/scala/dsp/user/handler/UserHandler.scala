/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.handler

import dsp.user.domain._
import dsp.user.api.UserRepo
import zio._
import java.util.UUID
import dsp.user.error.UserError

/**
 * The user handler.
 *
 * @param repo  the user repository
 */
final case class UserHandler(repo: UserRepo) {
  // implement all possible requests from V2, but divide things up into smaller functions to keep it cleaner than before

  /**
   * Retrieve all users (sorted by IRI).
   */
  def getUsers(): UIO[List[User]] =
    repo.getUsers().map(_.sorted)

  // getSingleUserADM should be inspected in the route. According to the user identifier, the
  // right method from the userHandler should be called.

  /**
   * Retrieve the user by ID.
   *
   * @param id  the user's ID
   * @param userInformationType  the type of the requested profile (restricted or full).
   */
  def getUserById(id: UserId, userInformationType: UserInformationType): UIO[Option[User]] =
    for {
      user <- repo.getUserById(id)
      _    <- ZIO.debug("yyy", user)
      _ <- userInformationType match {
             case UserInformationType.Full       => ZIO.succeed(user)
             case UserInformationType.Restricted => ZIO.succeed(user.map(u => u.copy(password = None)))
           }
    } yield user

  /**
   * Retrieve the user by IRI.
   *
   * @param iri  the user's IRI
   * @param userInformationType  the type of the requested profile (restricted or full).
   */
  def getUserByIri(iri: Iri.UserIri, userInformationType: UserInformationType): UIO[Option[User]] = {
    val userId = UserId.fromIri(iri)
    for {
      user <- getUserById(userId, userInformationType)
    } yield user
  }

  /**
   * Retrieve the user by UUID.
   *
   * @param uuuid  the user's UUID
   * @param userInformationType  the type of the requested profile (restricted or full).
   */
  def getUserByUuid(uuid: UUID, userInformationType: UserInformationType): UIO[Option[User]] = {
    val userId = UserId.fromUuid(uuid)
    for {
      user <- getUserById(userId, userInformationType)
    } yield user
  }

  /**
   * Retrieve the user by username.
   *
   * @param username  the user's username
   * @param userInformationType  the type of the requested profile (restricted or full).
   */
  def getUserByUsername(username: Username, userInformationType: UserInformationType): UIO[Option[User]] =
    repo.getUserByUsernameOrEmail(username.value)

  /**
   * Retrieve the user by email.
   *
   * @param email  the user's email
   * @param userInformationType  the type of the requested profile (restricted or full).
   */
  def getUserByEmail(email: Email, userInformationType: UserInformationType): UIO[Option[User]] =
    repo.getUserByUsernameOrEmail(email.value)

  // TODO: ask Ivan how we handle errors
  def createUser(user: User): IO[Throwable, Unit] = {
    // check if username and email are not yet used
    val username = user.username
    val email    = user.email
    val users    = repo.getUsers()

    for {
      userByUsernameOption <- repo.getUserByUsernameOrEmail(username.value) // discuss naming conventions for options
      usernameTaken = userByUsernameOption match {
                        case None    => None
                        case Some(_) => Some(username)
                      }
      userByEmailOption <- repo.getUserByUsernameOrEmail(email.value)
      emailTaken = userByEmailOption match {
                     case None    => None
                     case Some(_) => Some(email)
                   }
      _ <- (usernameTaken, emailTaken) match {
             case (None, None) => repo.storeUser(user)
             case _            => ZIO.die(UserError.UserAlreadyExists(usernameTaken, emailTaken))
           }
    } yield ()

  }

  def updateUser(user: User): IO[Option[Nothing], Unit] = // all values should be changed separately
    for {
      currentUser <- getUserById(user.id, UserInformationType.Full)
      _           <- deleteUser(user.id)
      _           <- createUser(user).orDie
    } yield ()

  def deleteUser(id: UserId): IO[Option[Nothing], Unit] = repo.deleteUser(id) // set state to inactive
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
