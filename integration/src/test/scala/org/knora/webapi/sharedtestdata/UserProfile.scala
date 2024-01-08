/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.sharedtestdata

import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.slice.admin.domain.model.User

/**
 * Represents a user's profile.
 *
 * @param userData       basic information about the user.
 * @param groups         the groups that the user belongs to.
 * @param projects_info  the projects that the user belongs to.
 * @param sessionId      the sessionId,.
 * @param permissionData the user's permission data.
 */
case class UserProfile(
  userData: UserData = UserData(lang = "en"),
  groups: Seq[IRI] = Seq.empty[IRI],
  projects_info: Map[IRI, ProjectInfo] = Map.empty[IRI, ProjectInfo],
  isSystemUser: Boolean = false,
  permissionData: PermissionsDataADM = PermissionsDataADM()
)
object UserProfile {
  def from(userADM: User): UserProfile =
    if (userADM.isAnonymousUser) {
      UserProfile()
    } else {

      val v1Groups: Seq[IRI] = userADM.groups.map(_.id)

      val projectsWithoutBuiltinProjects = userADM.projects
        .filter(_.id != OntologyConstants.KnoraAdmin.SystemProject)
        .filter(_.id != OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject)
      val projectInfosV1 = projectsWithoutBuiltinProjects.map(ProjectInfo.from)
      val projects_info_v1: Map[IRI, ProjectInfo] =
        projectInfosV1.map(_.id).zip(projectInfosV1).toMap[IRI, ProjectInfo]

      UserProfile(
        userData = asUserData(userADM),
        groups = v1Groups,
        projects_info = projects_info_v1,
        permissionData = PermissionsDataADM(
          groupsPerProject = userADM.permissions.groupsPerProject,
          administrativePermissionsPerProject = userADM.permissions.administrativePermissionsPerProject
        )
      )
    }

  private def asUserData(userADM: User): UserData =
    UserData(
      user_id = if (userADM.isAnonymousUser) {
        None
      } else {
        Some(userADM.id)
      },
      email = Some(userADM.email),
      password = userADM.password,
      token = userADM.token,
      firstname = Some(userADM.givenName),
      lastname = Some(userADM.familyName),
      status = Some(userADM.status),
      lang = userADM.lang
    )
}

/**
 * Represents basic information about a user.
 *
 * @param user_id   The user's IRI.
 * @param email     The user's email address.
 * @param password  The user's hashed password.
 * @param token     The API token. Can be used instead of email/password for authentication.
 * @param firstname The user's given name.
 * @param lastname  The user's surname.
 * @param status    The user's status.
 * @param lang      The ISO 639-1 code of the user's preferred language.
 */
case class UserData(
  user_id: Option[IRI] = None,
  email: Option[String] = None,
  password: Option[String] = None,
  token: Option[String] = None,
  firstname: Option[String] = None,
  lastname: Option[String] = None,
  status: Option[Boolean] = Some(true),
  lang: String
)
