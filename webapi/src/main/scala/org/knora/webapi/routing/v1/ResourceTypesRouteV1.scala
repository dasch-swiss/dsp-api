/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import zio._

import dsp.errors.BadRequestException
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV1
import org.knora.webapi.routing.RouteUtilZ

/**
 * Provides a spray-routing function for API routes that deal with resource types.
 */
final case class ResourceTypesRouteV1()(
  private implicit val runtime: Runtime[Authenticator with MessageRelay with StringFormatter]
) {

  def makeRoute: Route =
    path("v1" / "resourcetypes" / Segment) { iri =>
      get { requestContext =>
        val requestMessage = for {
          userProfile     <- Authenticator.getUserADM(requestContext)
          resourceTypeIri <- RouteUtilZ.validateAndEscapeIri(iri, s"Invalid resource class IRI: $iri")
        } yield ResourceTypeGetRequestV1(resourceTypeIri, userProfile)
        RouteUtilV1.runJsonRouteZ(requestMessage, requestContext)
      }
    } ~ path("v1" / "resourcetypes") {
      get { requestContext =>
        val requestMessage = for {
          userADM <- Authenticator.getUserADM(requestContext)
          params   = requestContext.request.uri.query().toMap
          vocabularyId <- ZIO
                            .fromOption(params.get("vocabulary"))
                            .orElseFail(BadRequestException("Required param vocabulary is missing"))
          namedGraphIri <- vocabularyId match {
                             case "0" => ZIO.none // if param vocabulary is set to 0, query all named graphs
                             case _ =>
                               RouteUtilZ
                                 .validateAndEscapeIri(vocabularyId, s"Invalid vocabulary IRI: $vocabularyId")
                                 .map(Some(_))
                           }
        } yield ResourceTypesForNamedGraphGetRequestV1(namedGraphIri, userADM)
        RouteUtilV1.runJsonRouteZ(requestMessage, requestContext)
      }
    } ~ path("v1" / "propertylists") {
      get { requestContext =>
        val requestMessage = for {
          userADM                       <- Authenticator.getUserADM(requestContext)
          params                         = requestContext.request.uri.query().toMap
          vocabularyId: Option[String]   = params.get("vocabulary")
          resourceTypeId: Option[String] = params.get("restype")
          _ <- // Either the 'vocabulary' or the 'restype' param may be set, but not both.
            ZIO
              .fail(BadRequestException("Both vocabulary and restype params are set, only one is allowed"))
              .when(vocabularyId.nonEmpty && resourceTypeId.nonEmpty)
          msg <- vocabularyId match {
                   case Some("0") => // 0 means that all named graphs should be queried
                     ZIO.succeed(PropertyTypesForNamedGraphGetRequestV1(namedGraph = None, userADM))
                   case Some(vocId) =>
                     RouteUtilZ
                       .validateAndEscapeIri(vocId, s"Invalid vocabulary IRI: $vocId")
                       .map(namedGraphIri => PropertyTypesForNamedGraphGetRequestV1(Some(namedGraphIri), userADM))
                   case None => // no vocabulary id given, check for resource type
                     resourceTypeId match {
                       case Some(resTypeId) => // get property types for given resource type
                         RouteUtilZ
                           .validateAndEscapeIri(resTypeId, s"Invalid vocabulary IRI: $resTypeId")
                           .as(PropertyTypesForResourceTypeGetRequestV1(resTypeId, userADM))
                       case None => // no params given, get all property types (behaves like vocabulary=0)
                         ZIO.succeed(PropertyTypesForNamedGraphGetRequestV1(namedGraph = None, userADM))
                     }
                 }
        } yield msg
        RouteUtilV1.runJsonRouteZ(requestMessage, requestContext)
      }
    } ~ path("v1" / "vocabularies") {
      get { requestContext =>
        val requestMessage = Authenticator.getUserADM(requestContext).map(it => NamedGraphsGetRequestV1(userADM = it))
        RouteUtilV1.runJsonRouteZ(requestMessage, requestContext)
      }
    } ~ path("v1" / "vocabularies" / "reload") {
      get { requestContext =>
        val requestMessage = Authenticator.getUserADM(requestContext).map(LoadOntologiesRequestV1)
        RouteUtilV1.runJsonRouteZ(requestMessage, requestContext)
      }
    } ~ path("v1" / "subclasses" / Segment) { iri =>
      get { requestContext =>
        val requestMessage = for {
          userADM          <- Authenticator.getUserADM(requestContext)
          resourceClassIri <- RouteUtilZ.validateAndEscapeIri(iri, s"Invalid resource class IRI: $iri")
        } yield SubClassesGetRequestV1(resourceClassIri, userADM)
        RouteUtilV1.runJsonRouteZ(requestMessage, requestContext)
      }
    }
}
