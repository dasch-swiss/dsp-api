/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import dsp.errors.{BadRequestException, ForbiddenException}
import dsp.valueobjects.Iri
import dsp.valueobjects.Iri.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.{PathMatcher, Route}
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.*
import org.knora.webapi.routing.RouteUtilADM.*
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData}
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.ListErrorMessages
import org.knora.webapi.slice.admin.domain.model.ListProperties.{Comments, Labels, ListName, Position}
import org.knora.webapi.slice.common.ToValidation.{validateOneWithFrom, validateOptionWithFrom}
import zio.*
import zio.prelude.Validation

import java.util.UUID

/**
 * Provides routes to update list items.
 *
 * @param routeData the [[KnoraRouteData]] to be used in constructing the route.
 */
final case class UpdateListItemsRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator & StringFormatter & MessageRelay]
) extends KnoraRoute(routeData, runtime)
    with ListADMJsonProtocol {

  val listsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "lists")

  def makeRoute: Route =
    updateNodeName() ~
      updateNodeLabels() ~
      updateNodeComments() ~
      updateNodePosition() ~
      updateList()

  /**
   * Update name of an existing list node, either root or child.
   */
  private def updateNodeName(): Route =
    path(listsBasePath / Segment / "name") { iri =>
      put {
        entity(as[ChangeNodeNameApiRequestADM]) { apiRequest => requestContext =>
          val task = for {
            nodeIri <- Iri
                         .validateAndEscapeIri(iri)
                         .toZIO
                         .orElseFail(BadRequestException(s"Invalid param node IRI: $iri"))
            listName <- ZIO.fromEither(ListName.from(apiRequest.name)).mapError(BadRequestException.apply)
            payload   = NodeNameChangePayloadADM(listName)
            uuid     <- getUserUuid(requestContext)
          } yield NodeNameChangeRequestADM(nodeIri, payload, uuid.user, uuid.uuid)
          runJsonRouteZ(task, requestContext)
        }
      }
    }

  /**
   * Update labels of an existing list node, either root or child.
   */
  private def updateNodeLabels(): Route =
    path(listsBasePath / Segment / "labels") { iri =>
      put {
        entity(as[ChangeNodeLabelsApiRequestADM]) { apiRequest => requestContext =>
          val task = for {
            nodeIri <- Iri
                         .validateAndEscapeIri(iri)
                         .toZIO
                         .orElseFail(BadRequestException(s"Invalid param node IRI: $iri"))
            labels <- validateOneWithFrom(apiRequest.labels, Labels.from, BadRequestException.apply).toZIO
            payload = NodeLabelsChangePayloadADM(labels)
            uuid   <- getUserUuid(requestContext)
          } yield NodeLabelsChangeRequestADM(nodeIri, payload, uuid.user, uuid.uuid)
          runJsonRouteZ(task, requestContext)
        }
      }
    }

  /**
   * Updates comments of an existing list node, either root or child.
   */
  private def updateNodeComments(): Route =
    path(listsBasePath / Segment / "comments") { iri =>
      put {
        entity(as[ChangeNodeCommentsApiRequestADM]) { apiRequest => requestContext =>
          val task = for {
            nodeIri <- Iri
                         .validateAndEscapeIri(iri)
                         .toZIO
                         .orElseFail(BadRequestException(s"Invalid param node IRI: $iri"))
            comments <- ZIO.fromEither(Comments.from(apiRequest.comments)).mapError(BadRequestException.apply)
            payload   = NodeCommentsChangePayloadADM(comments)
            uuid     <- getUserUuid(requestContext)
          } yield NodeCommentsChangeRequestADM(nodeIri, payload, uuid.user, uuid.uuid)
          runJsonRouteZ(task, requestContext)
        }
      }
    }

  /**
   * Updates position of an existing list child node.
   */
  private def updateNodePosition(): Route =
    path(listsBasePath / Segment / "position") { iri =>
      put {
        entity(as[ChangeNodePositionApiRequestADM]) { apiRequest => requestContext =>
          val task = getIriUserUuid(iri, requestContext)
            .map(r => NodePositionChangeRequestADM(r.iri, apiRequest, r.user, r.uuid))
          runJsonRouteZ(task, requestContext)
        }
      }
    }

  /**
   * Updates existing list node, either root or child.
   */
  private def updateList(): Route = path(listsBasePath / Segment) { iri =>
    put {
      entity(as[ListNodeChangeApiRequestADM]) { apiRequest => requestContext =>
        val validatedPayload = for {
          _          <- ZIO.fail(BadRequestException("Route and payload listIri mismatch.")).when(iri != apiRequest.listIri)
          listIri     = ListIri.make(apiRequest.listIri)
          projectIri  = validateOneWithFrom(apiRequest.projectIri, ProjectIri.from, BadRequestException.apply)
          hasRootNode = ListIri.make(apiRequest.hasRootNode)
          position    = validateOptionWithFrom(apiRequest.position, Position.from, BadRequestException.apply)
          name        = validateOptionWithFrom(apiRequest.name, ListName.from, BadRequestException.apply)
          labels      = validateOptionWithFrom(apiRequest.labels, Labels.from, BadRequestException.apply)
          comments    = validateOptionWithFrom(apiRequest.comments, Comments.from, BadRequestException.apply)
        } yield Validation.validateWith(listIri, projectIri, hasRootNode, position, name, labels, comments)(
          ListNodeChangePayloadADM
        )

        val requestMessage = for {
          payload <- validatedPayload.flatMap(_.toZIO)
          user    <- Authenticator.getUserADM(requestContext)
          _ <- ZIO
                 .fail(ForbiddenException(ListErrorMessages.ListNodeCreatePermission))
                 .when(!user.permissions.isProjectAdmin(payload.projectIri.value) && !user.permissions.isSystemAdmin)
        } yield NodeInfoChangeRequestADM(payload.listIri.value, payload, user, UUID.randomUUID())
        runJsonRouteZ(requestMessage, requestContext)
      }
    }
  }
}
