/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.PathMatcher
import org.apache.pekko.http.scaladsl.server.Route
import zio.*

import dsp.errors.BadRequestException
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceRequestV2.AssetIngestState.*
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesGetRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.iiif.api.SipiService

/**
 * Provides a routing function for API v2 routes that deal with values.
 */
final case class ValuesRouteV2()(
  private implicit val runtime: Runtime[
    AppConfig & Authenticator & IriConverter & SipiService & StringFormatter & MessageRelay & ValuesResponderV2
  ]
) {

  private val valuesBasePath: PathMatcher[Unit] = PathMatcher("v2" / "values")

  def makeRoute: Route = getValue() ~ createValue() ~ updateValue() ~ deleteValue()

  private def getValue(): Route = path(valuesBasePath / Segment / Segment) {
    (resourceIriStr: IRI, valueUuidStr: String) =>
      get { requestContext =>
        val targetSchemaTask = RouteUtilV2.getOntologySchema(requestContext)
        val requestTask = for {
          resourceIri <- RouteUtilZ
                           .toSmartIri(resourceIriStr, s"Invalid resource IRI: $resourceIriStr")
                           .flatMap(RouteUtilZ.ensureIsKnoraResourceIri)
          valueUuid <- RouteUtilZ.decodeUuid(valueUuidStr)
          versionDate <- ZIO.foreach(RouteUtilZ.getStringValueFromQuery(requestContext, "version")) { versionStr =>
                           ZIO
                             .fromOption(
                               ValuesValidator
                                 .xsdDateTimeStampToInstant(versionStr)
                                 .orElse(ValuesValidator.arkTimestampToInstant(versionStr))
                             )
                             .orElseFail(BadRequestException(s"Invalid version date: $versionStr"))
                         }
          requestingUser <- Authenticator.getUserADM(requestContext)
          targetSchema   <- targetSchemaTask
        } yield ResourcesGetRequestV2(
          resourceIris = Seq(resourceIri.toString),
          valueUuid = Some(valueUuid),
          versionDate = versionDate,
          targetSchema = targetSchema,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteZ(
          requestTask,
          requestContext,
          targetSchemaTask,
          RouteUtilV2.getSchemaOptions(requestContext).map(Some(_))
        )
      }
  }

  private def createValue(): Route = path(valuesBasePath) {
    post {
      entity(as[String]) { jsonLdString => ctx =>
        {
          RouteUtilV2.completeResponse(
            for {
              requestingUser <- Authenticator.getUserADM(ctx)
              apiRequestId   <- Random.nextUUID
              header          = "X-Asset-Ingested"
              ingestState = if (ctx.request.headers.exists(_.name == header)) AssetIngested
                            else AssetInTemp
              valueToCreate <- CreateValueV2.fromJsonLd(ingestState, jsonLdString, requestingUser)
              response      <- ValuesResponderV2.createValueV2(valueToCreate, requestingUser, apiRequestId)
            } yield response,
            ctx
          )
        }
      }
    }
  }

  private def updateValue(): Route = path(valuesBasePath) {
    put {
      entity(as[String]) { jsonLdString => ctx =>
        {
          RouteUtilV2.completeResponse(
            for {
              requestingUser <- Authenticator.getUserADM(ctx)
              apiRequestId   <- Random.nextUUID
              updateValue    <- UpdateValueV2.fromJsonLd(jsonLdString, requestingUser)
              response       <- ValuesResponderV2.updateValueV2(updateValue, requestingUser, apiRequestId)
            } yield response,
            ctx
          )
        }
      }
    }
  }

  private def deleteValue(): Route = path(valuesBasePath / "delete") {
    post {
      entity(as[String]) { jsonLdString => requestContext =>
        {
          RouteUtilV2.completeResponse(
            for {
              requestingUser <- Authenticator.getUserADM(requestContext)
              apiRequestId   <- RouteUtilZ.randomUuid()
              deleteValue    <- DeleteValueV2.fromJsonLd(jsonLdString)
              response       <- ValuesResponderV2.deleteValueV2(deleteValue, requestingUser, apiRequestId)
            } yield response,
            requestContext
          )
        }
      }
    }
  }
}
