/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.RouteResult
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import dsp.errors.UnexpectedMessageException
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.ResponderRequest.KnoraRequestADM
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.KnoraResponseADM
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages._

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
    response: KnoraResponseADM
  ): KnoraResponseADM = {
    val sf = StringFormatter.getGeneralInstance

    def projectAsExternalRepresentation(project: ProjectADM): ProjectADM = {
      val ontologiesExternal = project.ontologies.map(sf.toSmartIri(_)).map(_.toOntologySchema(ApiV2Complex).toString)
      project.copy(ontologies = ontologiesExternal)
    }

    def groupAsExternalRepresentation(group: GroupADM): GroupADM = {
      val projectExternal = projectAsExternalRepresentation(group.project)
      group.copy(project = projectExternal)
    }

    def userAsExternalRepresentation(user: UserADM): UserADM = {
      val groupsExternal   = user.groups.map { g: GroupADM => groupAsExternalRepresentation(g) }
      val projectsExternal = user.projects.map { p: ProjectADM => projectAsExternalRepresentation(p) }
      user.copy(groups = groupsExternal, projects = projectsExternal)
    }

    response match {
      case ProjectsGetResponseADM(projects) => ProjectsGetResponseADM(projects.map(projectAsExternalRepresentation(_)))
      case ProjectGetResponseADM(project)   => ProjectGetResponseADM(projectAsExternalRepresentation(project))
      case ProjectMembersGetResponseADM(members) =>
        ProjectMembersGetResponseADM(members.map(userAsExternalRepresentation(_)))
      case ProjectAdminMembersGetResponseADM(members) =>
        ProjectAdminMembersGetResponseADM(members.map(userAsExternalRepresentation(_)))
      case ProjectOperationResponseADM(project) => ProjectOperationResponseADM(projectAsExternalRepresentation(project))

      case GroupsGetResponseADM(groups) => GroupsGetResponseADM(groups.map(groupAsExternalRepresentation(_)))
      case GroupGetResponseADM(group)   => GroupGetResponseADM(groupAsExternalRepresentation(group))
      case GroupMembersGetResponseADM(members) =>
        GroupMembersGetResponseADM(members.map(userAsExternalRepresentation(_)))
      case GroupOperationResponseADM(group) => GroupOperationResponseADM(groupAsExternalRepresentation(group))

      case UsersGetResponseADM(users) => UsersGetResponseADM(users.map(userAsExternalRepresentation(_)))
      case UserResponseADM(user)      => UserResponseADM(userAsExternalRepresentation(user))
      case UserProjectMembershipsGetResponseADM(projects) =>
        UserProjectMembershipsGetResponseADM(projects.map(projectAsExternalRepresentation(_)))
      case UserProjectAdminMembershipsGetResponseADM(projects) =>
        UserProjectAdminMembershipsGetResponseADM(projects.map(projectAsExternalRepresentation(_)))
      case UserGroupMembershipsGetResponseADM(groups) =>
        UserGroupMembershipsGetResponseADM(groups.map(groupAsExternalRepresentation(_)))
      case UserOperationResponseADM(user) => UserOperationResponseADM(userAsExternalRepresentation(user))

      case _ => response
    }
  }

  /**
   * Sends a message to a responder and completes the HTTP request by returning the response as JSON.
   *
   * @param requestMessageF      a future containing a [[KnoraRequestADM]] message that should be sent to the responder manager.
   * @param requestContext       the akka-http [[RequestContext]].
   * @param appActor             a reference to the application actor.
   * @param log                  a logging adapter.
   * @param timeout              a timeout for `ask` messages.
   * @param executionContext     an execution context for futures.
   * @return a [[Future]] containing a [[RouteResult]].
   */
  def runJsonRoute(
    requestMessageF: Future[KnoraRequestADM],
    requestContext: RequestContext,
    appActor: ActorRef,
    log: Logger
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {

    val httpResponse: Future[HttpResponse] = for {

      requestMessage <- requestMessageF

      // Make sure the responder sent a reply of type KnoraResponseADM.
      knoraResponse <- (appActor.ask(requestMessage)).map {
                         case replyMessage: KnoraResponseADM => replyMessage

                         case other =>
                           // The responder returned an unexpected message type (not an exception). This isn't the client's
                           // fault, so log it and return an error message to the client.
                           throw UnexpectedMessageException(
                             s"Responder sent a reply of type ${other.getClass.getCanonicalName}"
                           )
                       }

      knoraResponseExternal = transformResponseIntoExternalFormat(knoraResponse)
      jsonResponse          = knoraResponseExternal.toJsValue.asJsObject
    } yield HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        jsonResponse.compactPrint
      )
    )

    requestContext.complete(httpResponse)
  }
}
