/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.ContentTypes.`application/json`
import org.apache.pekko.http.scaladsl.model.StatusCodes.OK
import org.apache.pekko.http.scaladsl.server.RequestContext
import org.apache.pekko.http.scaladsl.server.RouteResult
import org.apache.pekko.util.ByteString
import zio.*

import java.util.UUID
import scala.concurrent.Future

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.IRI
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.ResponderRequest.KnoraRequestADM
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.KnoraResponseADM
import org.knora.webapi.messages.admin.responder.groupsmessages.*
import org.knora.webapi.messages.admin.responder.projectsmessages.*
import org.knora.webapi.messages.admin.responder.usersmessages.*
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.User

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
    response: KnoraResponseADM,
  ): ZIO[StringFormatter, Throwable, KnoraResponseADM] = ZIO.serviceWithZIO[StringFormatter] { sf =>
    ZIO.attempt {
      def projectAsExternalRepresentation(project: ProjectADM): ProjectADM = {
        val ontologiesExternal =
          project.ontologies.map(sf.toSmartIri(_)).map(_.toOntologySchema(ApiV2Complex).toString)
        project.copy(ontologies = ontologiesExternal)
      }

      def groupAsExternalRepresentation(group: Group): Group = {
        val projectExternal = projectAsExternalRepresentation(group.project)
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
          def projectAsExternalRepresentation(project: ProjectADM): ProjectADM = {
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

  /**
   * Sends a message to a responder and completes the HTTP request by returning the response as JSON.
   *
   * @param requestTask    A [[Task]] containing a [[KnoraRequestADM]] message that should be sent to the responder manager.
   * @param requestContext The pekko-http [[RequestContext]].
   * @return a [[Future]] containing a [[RouteResult]].
   */
  def runJsonRouteZ[R](
    requestTask: ZIO[R, Throwable, KnoraRequestADM],
    requestContext: RequestContext,
  )(implicit runtime: Runtime[R & StringFormatter & MessageRelay]): Future[RouteResult] =
    UnsafeZioRun.runToFuture(requestTask.flatMap(doRunJsonRoute(_, requestContext)))

  private def doRunJsonRoute(
    request: KnoraRequestADM,
    ctx: RequestContext,
  ): ZIO[StringFormatter & MessageRelay, Throwable, RouteResult] =
    createResponse(request).flatMap(completeContext(ctx, _))

  private def createResponse(
    request: KnoraRequestADM,
  ): ZIO[StringFormatter & MessageRelay, Throwable, HttpResponse] =
    for {
      knoraResponse         <- MessageRelay.ask[KnoraResponseADM](request)
      knoraResponseExternal <- transformResponseIntoExternalFormat(knoraResponse)
    } yield HttpResponse(
      OK,
      entity = HttpEntity(`application/json`, ByteString(knoraResponseExternal.toJsValue.asJsObject.compactPrint)),
    )

  private def completeContext(ctx: RequestContext, response: HttpResponse): Task[RouteResult] =
    ZIO.fromFuture(_ => ctx.complete(response))

  case class IriUserUuid(iri: IRI, user: User, uuid: UUID)
  case class IriUser(iri: IRI, user: User)

  def getIriUserUuid(
    iri: String,
    requestContext: RequestContext,
  ): ZIO[Authenticator & StringFormatter, Throwable, IriUserUuid] =
    for {
      r    <- getIriUser(iri, requestContext)
      uuid <- RouteUtilZ.randomUuid()
    } yield IriUserUuid(r.iri, r.user, uuid)

  def getIriUser(
    iri: String,
    requestContext: RequestContext,
  ): ZIO[Authenticator & StringFormatter, Throwable, IriUser] =
    for {
      validatedIri <- validateAndEscape(iri)
      user         <- Authenticator.getUserADM(requestContext)
    } yield IriUser(validatedIri, user)

  def validateAndEscape(iri: String): IO[BadRequestException, IRI] =
    Iri.validateAndEscapeIri(iri).toZIO.orElseFail(BadRequestException(s"Invalid IRI: $iri"))
}
