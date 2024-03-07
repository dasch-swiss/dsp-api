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

final case class UserService(
  private val userRepo: KnoraUserRepo,
  private val userToKnoraUserConverter: KnoraUserToUserConverter,
) {

  private val toUser = (u: KnoraUser) => userToKnoraUserConverter.toUser(u).asSomeError

  def findUserByIri(iri: UserIri): Task[Option[User]] =
    userRepo.findById(iri).some.flatMap(toUser).unsome

  def findUsersByIris(iris: Seq[UserIri]): Task[Seq[User]] =
    ZIO.foreach(iris)(findUserByIri).map(_.flatten)

  def findUserByEmail(email: Email): Task[Option[User]] =
    userRepo.findByEmail(email).some.flatMap(toUser).unsome

  def findUserByUsername(username: Username): Task[Option[User]] =
    userRepo.findByUsername(username).some.flatMap(toUser).unsome

  def findAll: Task[Seq[User]] =
    userRepo.findAll().flatMap(ZIO.foreach(_)(userToKnoraUserConverter.toUser))
}

object UserService {
  def layer = ZLayer.derive[UserService]
}
