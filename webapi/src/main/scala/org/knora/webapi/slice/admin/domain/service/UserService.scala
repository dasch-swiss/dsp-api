/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Task
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.store.cache.CacheService

final case class UserService(
  private val userRepo: KnoraUserRepo,
  private val userToKnoraUserConverter: KnoraUserToUserConverter,
  private val cacheService: CacheService,
) {

  def findUserByIri(iri: UserIri): Task[Option[User]] =
    fromCacheOrRepo(iri, cacheService.getUserByIri, userRepo.findById)

  def findUsersByIris(iris: Seq[UserIri]): Task[Seq[User]] =
    ZIO.foreach(iris)(findUserByIri).map(_.flatten)

  def findUserByEmail(email: Email): Task[Option[User]] =
    fromCacheOrRepo(email, cacheService.getUserByEmail, userRepo.findByEmail)

  def findUserByUsername(username: Username): Task[Option[User]] =
    fromCacheOrRepo(username, cacheService.getUserByUsername, userRepo.findByUsername)

  def findAll: Task[Seq[User]] =
    userRepo.findAll().flatMap(ZIO.foreach(_)(userToKnoraUserConverter.toUser))

  private def fromCacheOrRepo[A](
    id: A,
    fromCache: A => Task[Option[User]],
    fromRepo: A => Task[Option[KnoraUser]],
  ): Task[Option[User]] =
    fromCache(id).flatMap {
      case Some(user) => ZIO.some(user)
      case None =>
        fromRepo(id).flatMap(ZIO.foreach(_)(userToKnoraUserConverter.toUser)).tap {
          case Some(user) => cacheService.putUser(user)
          case None       => ZIO.unit
        }
    }
}

object UserService {
  def layer = ZLayer.derive[UserService]
}
