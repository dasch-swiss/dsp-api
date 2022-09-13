/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID
import scala.concurrent.Future

import dsp.errors.BadRequestException
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesGetRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilV2

/**
 * Provides a routing function for API v2 routes that deal with values.
 */
class ValuesRouteV2(routeData: KnoraRouteData, appConfig: AppConfig)
    extends KnoraRoute(routeData, appConfig)
    with Authenticator {

  val valuesBasePath: PathMatcher[Unit] = PathMatcher("v2" / "values")

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    getValue() ~
      createValue() ~
      updateValue() ~
      deleteValue()

  private def getValue(): Route = path(valuesBasePath / Segment / Segment) {
    (resourceIriStr: IRI, valueUuidStr: String) =>
      get { requestContext =>
        val resourceIri: SmartIri =
          resourceIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid resource IRI: $resourceIriStr"))

        if (!resourceIri.isKnoraResourceIri) {
          throw BadRequestException(s"Invalid resource IRI: $resourceIriStr")
        }

        val valueUuid: UUID =
          stringFormatter.decodeUuidWithErr(
            valueUuidStr,
            throw BadRequestException(s"Invalid value UUID: $valueUuidStr")
          )

        val params: Map[String, String] = requestContext.request.uri.query().toMap

        // Was a version date provided?
        val versionDate: Option[Instant] = params.get("version").map { versionStr =>
          def errorFun: Nothing = throw BadRequestException(s"Invalid version date: $versionStr")

          // Yes. Try to parse it as an xsd:dateTimeStamp.
          try {
            stringFormatter.xsdDateTimeStampToInstant(versionStr, errorFun)
          } catch {
            // If that doesn't work, try to parse it as a Knora ARK timestamp.
            case _: Exception => stringFormatter.arkTimestampToInstant(versionStr, errorFun)
          }
        }

        val targetSchema: ApiV2Schema        = RouteUtilV2.getOntologySchema(requestContext)
        val schemaOptions: Set[SchemaOption] = RouteUtilV2.getSchemaOptions(requestContext)

        val requestMessageFuture: Future[ResourcesGetRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              appConfig
                            )
        } yield ResourcesGetRequestV2(
          resourceIris = Seq(resourceIri.toString),
          valueUuid = Some(valueUuid),
          versionDate = versionDate,
          targetSchema = targetSchema,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          appConfig = appConfig,
          appActor = appActor,
          log = log,
          targetSchema = targetSchema,
          schemaOptions = schemaOptions
        )
      }
  }

  private def createValue(): Route = path(valuesBasePath) {
    post {
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

          val requestMessageFuture: Future[CreateValueRequestV2] = for {
            requestingUser <- getUserADM(
                                requestContext = requestContext,
                                appConfig
                              )
            requestMessage: CreateValueRequestV2 <- CreateValueRequestV2.fromJsonLD(
                                                      requestDoc,
                                                      apiRequestID = UUID.randomUUID,
                                                      requestingUser = requestingUser,
                                                      appActor = appActor,
                                                      log = log
                                                    )
          } yield requestMessage

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            appConfig = appConfig,
            appActor = appActor,
            log = log,
            targetSchema = ApiV2Complex,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
      }
    }
  }

  private def updateValue(): Route = path(valuesBasePath) {
    put {
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

          val requestMessageFuture: Future[UpdateValueRequestV2] = for {
            requestingUser <- getUserADM(
                                requestContext = requestContext,
                                appConfig
                              )
            requestMessage: UpdateValueRequestV2 <- UpdateValueRequestV2.fromJsonLD(
                                                      requestDoc,
                                                      apiRequestID = UUID.randomUUID,
                                                      requestingUser = requestingUser,
                                                      appActor = appActor,
                                                      log = log
                                                    )
          } yield requestMessage

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            appConfig = appConfig,
            appActor = appActor,
            log = log,
            targetSchema = ApiV2Complex,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
      }
    }
  }

  private def deleteValue(): Route = path(valuesBasePath / "delete") {
    post {
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

          val requestMessageFuture: Future[DeleteValueRequestV2] = for {
            requestingUser <- getUserADM(
                                requestContext = requestContext,
                                appConfig
                              )
            requestMessage: DeleteValueRequestV2 <- DeleteValueRequestV2.fromJsonLD(
                                                      requestDoc,
                                                      apiRequestID = UUID.randomUUID,
                                                      requestingUser = requestingUser,
                                                      appActor = appActor,
                                                      log = log
                                                    )
          } yield requestMessage

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            appConfig = appConfig,
            appActor = appActor,
            log = log,
            targetSchema = ApiV2Complex,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
      }
    }
  }
}
