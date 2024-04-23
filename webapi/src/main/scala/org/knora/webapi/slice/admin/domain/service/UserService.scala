/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Task
import zio.ZLayer

import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username

final case class UserService(
  private val knoraUserService: KnoraUserService,
  private val userConverter: KnoraUserToUserConverter,
) {

  def findUserByIri(iri: UserIri): Task[Option[User]] =
    knoraUserService.findById(iri).flatMap(userConverter.toUser)

  def findUserByEmail(email: Email): Task[Option[User]] =
    knoraUserService.findByEmail(email).flatMap(userConverter.toUser)

  def findUserByUsername(username: Username): Task[Option[User]] =
    knoraUserService.findByUsername(username).flatMap(userConverter.toUser)

  def findByProjectMembership(project: KnoraProject): Task[Seq[User]] =
    knoraUserService.findByProjectMembership(project).flatMap(userConverter.toUser)

  def findByProjectAdminMembership(project: KnoraProject): Task[Seq[User]] =
    knoraUserService.findByProjectAdminMembership(project).flatMap(userConverter.toUser)

  def findAllRegularUsers: Task[Seq[User]] =
    knoraUserService.findAllRegularUsers().flatMap(userConverter.toUser)

  def findByGroupMembership(groupIri: GroupIri): Task[Seq[User]] =
    knoraUserService.findByGroupMembership(groupIri).flatMap(userConverter.toUser)
}

object UserService {
  def layer = ZLayer.derive[UserService]
}
