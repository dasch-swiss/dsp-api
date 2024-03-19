/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Task
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.responders.admin.PermissionsResponderADM
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.User

final case class KnoraUserToUserConverter(
  private val projectsService: ProjectService,
  private val groupsResponder: GroupsResponderADM,
  private val permissionService: PermissionsResponderADM,
) {

  def toUser(kUser: KnoraUser): Task[User] = for {
    projects <- ZIO.foreach(kUser.isInProject)(projectsService.findById).map(_.flatten)
    groups   <- ZIO.foreach(kUser.isInGroup.map(_.value))(groupsResponder.groupGetADM).map(_.flatten)
    permissionData <-
      permissionService.permissionsDataGetADM(
        kUser.isInProject.map(_.value),
        kUser.isInGroup.map(_.value),
        kUser.isInProjectAdminGroup.map(_.value),
        kUser.isInSystemAdminGroup.value,
      )
  } yield User(
    kUser.id.value,
    kUser.username.value,
    kUser.email.value,
    kUser.givenName.value,
    kUser.familyName.value,
    kUser.status.value,
    kUser.preferredLanguage.value,
    Some(kUser.password.value),
    groups,
    projects,
    permissionData,
  )
}
object KnoraUserToUserConverter {
  val layer = ZLayer.derive[KnoraUserToUserConverter]
}
