/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import zio.Runtime

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._

import dsp.errors.BadRequestException
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.messages.v2.responder.standoffmessages.CreateMappingRequestMetadataV2
import org.knora.webapi.messages.v2.responder.standoffmessages.CreateMappingRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.CreateMappingRequestXMLV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetStandoffPageRequestV2
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilV2

/**
 * Provides a function for API routes that deal with search.
 */
final case class StandoffRouteV2(
  private val routeData: KnoraRouteData,
  override implicit protected val runtime: Runtime[AppConfig with Authenticator with MessageRelay]
) extends KnoraRoute(routeData, runtime) {

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    path("v2" / "standoff" / Segment / Segment / Segment) {
      (resourceIriStr: String, valueIriStr: String, offsetStr: String) =>
        get { requestContext =>
          val resourceIri: SmartIri =
            resourceIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid resource IRI: $resourceIriStr"))

          if (!resourceIri.isKnoraResourceIri) {
            throw BadRequestException(s"Invalid resource IRI: $resourceIriStr")
          }

          val valueIri: SmartIri =
            valueIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid value IRI: $valueIriStr"))

          if (!valueIri.isKnoraValueIri) {
            throw BadRequestException(s"Invalid value IRI: $valueIriStr")
          }

          val offset: Int =
            ValuesValidator.validateInt(offsetStr).getOrElse(throw BadRequestException(s"Invalid offset: $offsetStr"))
          val targetSchema: ApiV2Schema = RouteUtilV2.getOntologySchema(requestContext)
          val requestTask = Authenticator
            .getUserADM(requestContext)
            .map(GetStandoffPageRequestV2(resourceIri.toString, valueIri.toString, offset, targetSchema, _))
          val schemaOptions: Set[SchemaOption] = SchemaOptions.ForStandoffSeparateFromTextValues
          RouteUtilV2.runRdfRouteZ(requestTask, requestContext, ApiV2Complex, Some(schemaOptions))
        }
    } ~ path("v2" / "mapping") {
      post {
        entity(as[Multipart.FormData]) { formData: Multipart.FormData => requestContext =>
          val JSON_PART = "json"
          val XML_PART  = "xml"
          type Name = String

          val apiRequestID = UUID.randomUUID

          // collect all parts of the multipart as it arrives into a map
          val allPartsFuture: Future[Map[Name, String]] = formData.parts
            .mapAsync[(Name, String)](1) {
              case b: BodyPart if b.name == JSON_PART =>
                // Logger.debug(s"inside allPartsFuture - processing $JSON_PART")
                b.toStrict(2.seconds).map { strict =>
                  // Logger.debug(strict.entity.data.utf8String)
                  (b.name, strict.entity.data.utf8String)
                }

              case b: BodyPart if b.name == XML_PART =>
                // Logger.debug(s"inside allPartsFuture - processing $XML_PART")

                b.toStrict(2.seconds).map { strict =>
                  // Logger.debug(strict.entity.data.utf8String)
                  (b.name, strict.entity.data.utf8String)
                }

              case b: BodyPart if b.name.isEmpty =>
                throw BadRequestException("part of HTTP multipart request has no name")

              case b: BodyPart => throw BadRequestException(s"multipart contains invalid name: ${b.name}")

              case _ => throw BadRequestException("multipart request could not be handled")
            }
            .runFold(Map.empty[Name, String])((map, tuple) => map + tuple)

          val requestMessageFuture: Future[CreateMappingRequestV2] = for {
            requestingUser              <- getUserADM(requestContext)
            allParts: Map[Name, String] <- allPartsFuture
            jsonldDoc =
              JsonLDUtil.parseJsonLD(
                allParts
                  .getOrElse(
                    JSON_PART,
                    throw BadRequestException(s"MultiPart POST request was sent without required '$JSON_PART' part!")
                  )
                  .toString
              )

            metadata: CreateMappingRequestMetadataV2 <- CreateMappingRequestMetadataV2.fromJsonLD(
                                                          jsonLDDocument = jsonldDoc,
                                                          apiRequestID = apiRequestID,
                                                          requestingUser = requestingUser,
                                                          appActor = appActor,
                                                          log = log
                                                        )

            xml: String =
              allParts
                .getOrElse(
                  XML_PART,
                  throw BadRequestException(s"MultiPart POST request was sent without required '$XML_PART' part!")
                )
                .toString
          } yield CreateMappingRequestV2(
            metadata = metadata,
            xml = CreateMappingRequestXMLV2(xml),
            requestingUser = requestingUser,
            apiRequestID = apiRequestID
          )

          RouteUtilV2.runRdfRouteF(requestMessageFuture, requestContext)
        }
      }
    }
}
