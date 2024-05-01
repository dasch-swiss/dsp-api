/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.AdminKnoraResponse
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.slice.admin.api.model._
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.User
import zio.*

/**
 * Convenience methods for Knora Admin routes.
 */
object RouteUtilADM {

  /**
   * Transforms all ontology IRIs referenced inside a KnoraResponseADM into their external format.
   *
   * @param response the response that should be transformed
   * @return the transformed [[KnoraResponseADM]]
   */
  def transformResponseIntoExternalFormat(
    response: AdminKnoraResponse,
  ): ZIO[StringFormatter, Throwable, AdminKnoraResponse] = ZIO.serviceWithZIO[StringFormatter] { sf =>
    ZIO.attempt {
      def projectAsExternalRepresentation(project: Project): Project = {
        val ontologiesExternal =
          project.ontologies.map(sf.toSmartIri(_)).map(_.toOntologySchema(ApiV2Complex).toString)
        project.copy(ontologies = ontologiesExternal)
      }

      def groupAsExternalRepresentation(group: Group): Group = {
        val projectExternal = group.project.map(projectAsExternalRepresentation)
        group.copy(project = projectExternal)
      }

      def userAsExternalRepresentation(user: User): User = {
        val groupsExternal   = user.groups.map(groupAsExternalRepresentation)
        val projectsExternal = user.projects.map(projectAsExternalRepresentation)
        user.copy(groups = groupsExternal, projects = projectsExternal)
      }

      response match {
        case ProjectMembersGetResponseADM(members) =>
          ProjectMembersGetResponseADM(members.map(userAsExternalRepresentation))
        case ProjectAdminMembersGetResponseADM(members) =>
          ProjectAdminMembersGetResponseADM(members.map(userAsExternalRepresentation))
        case ProjectOperationResponseADM(project) =>
          ProjectOperationResponseADM(projectAsExternalRepresentation(project))

        case GroupsGetResponseADM(groups) => GroupsGetResponseADM(groups.map(groupAsExternalRepresentation))
        case GroupGetResponseADM(group)   => GroupGetResponseADM(groupAsExternalRepresentation(group))
        case GroupMembersGetResponseADM(members) =>
          GroupMembersGetResponseADM(members.map(userAsExternalRepresentation))

        case UsersGetResponseADM(users) => UsersGetResponseADM(users.map(userAsExternalRepresentation))
        case UserResponseADM(user)      => UserResponseADM(userAsExternalRepresentation(user))
        case UserProjectMembershipsGetResponseADM(projects) =>
          UserProjectMembershipsGetResponseADM(projects.map(projectAsExternalRepresentation))
        case UserProjectAdminMembershipsGetResponseADM(projects) =>
          UserProjectAdminMembershipsGetResponseADM(projects.map(projectAsExternalRepresentation))
        case UserGroupMembershipsGetResponseADM(groups) =>
          UserGroupMembershipsGetResponseADM(groups.map(groupAsExternalRepresentation))

        case _ => response
      }
    }
  }

  def transformResponseIntoExternalFormat[A](response: A): ZIO[StringFormatter, Throwable, A] =
    ZIO
      .serviceWithZIO[StringFormatter] { sf =>
        ZIO.attempt {
          def projectAsExternalRepresentation(project: Project): Project = {
            val ontologiesExternal =
              project.ontologies.map(sf.toSmartIri(_)).map(_.toOntologySchema(ApiV2Complex).toString)
            project.copy(ontologies = ontologiesExternal)
          }

          response match {
            case ProjectsGetResponse(projects) => ProjectsGetResponse(projects.map(projectAsExternalRepresentation))
            case ProjectGetResponse(project)   => ProjectGetResponse(projectAsExternalRepresentation(project))
            case _                             => response
          }
        }
      }
      .map(_.asInstanceOf[A])
}
