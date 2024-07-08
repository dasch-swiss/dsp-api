/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import sttp.model.MediaType
import zio.Task
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.ApiV2Schema
import org.knora.webapi.Rendering
import org.knora.webapi.SchemaRendering
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.AdminKnoraResponseADM
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupGetResponseADM
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.GroupMembersGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserGroupMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectAdminMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.messages.util.rdf.RdfFormat
import org.knora.webapi.messages.v2.responder.KnoraResponseV2
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.api.model.ProjectAdminMembersGetResponseADM
import org.knora.webapi.slice.admin.api.model.ProjectGetResponse
import org.knora.webapi.slice.admin.api.model.ProjectMembersGetResponseADM
import org.knora.webapi.slice.admin.api.model.ProjectOperationResponseADM
import org.knora.webapi.slice.admin.api.model.ProjectsGetResponse
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse

/**
 * Renders a [[KnoraResponseV2]] as a [[RenderedResponse]] (type alias for a [[String]]) ready to be returned to the client.
 */
final class KnoraResponseRenderer(config: AppConfig, stringFormatter: StringFormatter) {
  def render(response: KnoraResponseV2, opts: FormatOptions): Task[(RenderedResponse, MediaType)] =
    ZIO.attempt(response.format(opts, config)).map((_, opts.rdfFormat.mediaType))

  /**
   * Transforms all ontology IRIs from an [[AdminKnoraResponseADM]] into their external format.
   *
   * @param response the response that should be transformed
   * @return the transformed [[AdminKnoraResponseADM]]
   */
  def toExternal[A <: AdminKnoraResponseADM](response: A): Task[A] =
    transformResponseIntoExternalFormat(response).mapAttempt(_.asInstanceOf[A])

  private def transformResponseIntoExternalFormat(
    response: AdminKnoraResponseADM,
  ): Task[AdminKnoraResponseADM] =
    ZIO.attempt {

      def projectAsExternalRepresentation(project: Project): Project = {
        val ontologiesExternal =
          project.ontologies.map(stringFormatter.toSmartIri(_)).map(_.toOntologySchema(ApiV2Complex).toString)
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

        case ProjectGetResponse(project)   => ProjectGetResponse(projectAsExternalRepresentation(project))
        case ProjectsGetResponse(projects) => ProjectsGetResponse(projects.map(projectAsExternalRepresentation))

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

object KnoraResponseRenderer {

  type RenderedResponse = String

  final case class FormatOptions(rdfFormat: RdfFormat, schema: ApiV2Schema, rendering: Set[Rendering]) {
    lazy val schemaRendering: SchemaRendering = SchemaRendering(schema, rendering)
  }
  object FormatOptions {
    val default: FormatOptions = FormatOptions(RdfFormat.default, ApiV2Schema.default, Set.empty)
  }

  val layer = ZLayer.derive[KnoraResponseRenderer]
}
