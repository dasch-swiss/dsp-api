/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route
import zio._

import dsp.errors.BadRequestException
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesGetRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

/**
 * Provides a routing function for API v2 routes that deal with values.
 */
final case class ValuesRouteV2()(
  private implicit val runtime: Runtime[
    AppConfig with Authenticator with IriConverter with StringFormatter with MessageRelay
  ]
) {

  private val valuesBasePath: PathMatcher[Unit] = PathMatcher("v2" / "values")

  def makeRoute: Route = getValue() ~ createValue() ~ updateValue() ~ deleteValue()

  private def getValue(): Route = path(valuesBasePath / Segment / Segment) {
    (resourceIriStr: IRI, valueUuidStr: String) =>
      get { requestContext =>
        val targetSchemaTask                 = RouteUtilV2.getOntologySchema(requestContext)
        val schemaOptions: Set[SchemaOption] = RouteUtilV2.getSchemaOptionsUnsafe(requestContext)
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

        RouteUtilV2.runRdfRouteZ(requestTask, requestContext, targetSchemaTask, Some(schemaOptions))
      }
  }

  private def createValue(): Route = path(valuesBasePath) {
    post {
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestTask = for {
            requestDoc     <- RouteUtilV2.parseJsonLd(jsonRequest)
            requestingUser <- Authenticator.getUserADM(requestContext)
            apiRequestID   <- RouteUtilZ.randomUuid()
            msg            <- CreateValueRequestV2.fromJsonLd(requestDoc, apiRequestID, requestingUser)
          } yield msg
          RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
        }
      }
    }
  }

  private def updateValue(): Route = path(valuesBasePath) {
    put {
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestTask = for {
            requestDoc     <- RouteUtilV2.parseJsonLd(jsonRequest)
            requestingUser <- Authenticator.getUserADM(requestContext)
            apiRequestId   <- RouteUtilZ.randomUuid()
            msg            <- UpdateValueRequestV2.fromJsonLd(requestDoc, apiRequestId, requestingUser)
          } yield msg
          RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
        }
      }
    }
  }

  private def deleteValue(): Route = path(valuesBasePath / "delete") {
    post {
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestTask = for {
            requestDoc     <- RouteUtilV2.parseJsonLd(jsonRequest)
            requestingUser <- Authenticator.getUserADM(requestContext)
            apiRequestId   <- RouteUtilZ.randomUuid()
            msg            <- DeleteValueRequestV2.fromJsonLd(requestDoc, apiRequestId, requestingUser)
          } yield msg
          RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
        }
      }
    }
  }
}
