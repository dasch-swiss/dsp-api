/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import org.apache.pekko
import zio.*
import zio.prelude.Validation

import java.util.UUID

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import dsp.valueobjects.Iri
import dsp.valueobjects.Iri.*
import dsp.valueobjects.List.*
import dsp.valueobjects.ListErrorMessages
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.*
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

import pekko.http.scaladsl.server.Directives.*
import pekko.http.scaladsl.server.PathMatcher
import pekko.http.scaladsl.server.Route

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
            listName <- ListName.make(apiRequest.name).toZIO.mapError(e => BadRequestException(e.getMessage))
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
            labels <- Labels.make(apiRequest.labels).toZIO.mapError(e => BadRequestException(e.getMessage))
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
            comments <- Comments.make(apiRequest.comments).toZIO.mapError(e => BadRequestException(e.getMessage))
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
          projectIri  = ProjectIri.from(apiRequest.projectIri)
          hasRootNode = ListIri.make(apiRequest.hasRootNode)
          position    = Position.make(apiRequest.position)
          name        = ListName.make(apiRequest.name)
          labels      = Labels.make(apiRequest.labels)
          comments    = Comments.make(apiRequest.comments)
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
