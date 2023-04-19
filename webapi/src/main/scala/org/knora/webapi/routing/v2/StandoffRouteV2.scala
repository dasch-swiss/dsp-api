/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import zio.Runtime
import zio.ZIO

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import dsp.errors.BadRequestException
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.messages.v2.responder.standoffmessages.CreateMappingRequestMetadataV2
import org.knora.webapi.messages.v2.responder.standoffmessages.CreateMappingRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.CreateMappingRequestXMLV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetStandoffPageRequestV2
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

/**
 * Provides a function for API routes that deal with search.
 */
final case class StandoffRouteV2()(
  private implicit val runtime: Runtime[
    AppConfig with Authenticator with IriConverter with StringFormatter with MessageRelay
  ],
  private implicit val system: ActorSystem
) extends LazyLogging {
  private implicit val ec: ExecutionContext = system.dispatcher

  def makeRoute: Route =
    path("v2" / "standoff" / Segment / Segment / Segment) {
      (resourceIriStr: String, valueIriStr: String, offsetStr: String) =>
        get { requestContext =>
          val requestTask = for {
            resourceIri <-
              RouteUtilZ
                .toSmartIri(resourceIriStr, s"Invalid resource IRI: $resourceIriStr")
                .filterOrFail(_.isKnoraResourceIri)(BadRequestException(s"Invalid resource IRI: $resourceIriStr"))
            valueIri <-
              RouteUtilZ
                .toSmartIri(valueIriStr, s"Invalid value IRI: $valueIriStr")
                .filterOrFail(_.isKnoraValueIri)(BadRequestException(s"Invalid value IRI: $valueIriStr"))
            offset <- ZIO
                        .fromOption(ValuesValidator.validateInt(offsetStr))
                        .orElseFail(BadRequestException(s"Invalid offset: $offsetStr"))
            targetSchema <- RouteUtilV2.getOntologySchema(requestContext)
            user         <- Authenticator.getUserADM(requestContext)
          } yield GetStandoffPageRequestV2(resourceIri.toString, valueIri.toString, offset, targetSchema, user)
          val schemaOptions: Set[SchemaOption] = SchemaOptions.ForStandoffSeparateFromTextValues
          RouteUtilV2.runRdfRouteZ(requestTask, requestContext, schemaOptionsOption = Some(schemaOptions))
        }
    } ~ path("v2" / "mapping") {
      post {
        entity(as[Multipart.FormData]) { formData: Multipart.FormData => requestContext =>
          val jsonPartKey = "json"
          val xmlPartKey  = "xml"
          type Name = String

          // collect all parts of the multipart as it arrives into a map
          val allPartsFuture: Future[Map[Name, String]] = formData.parts
            .mapAsync[(Name, String)](1) {
              case b: BodyPart if b.name == jsonPartKey =>
                // Logger.debug(s"inside allPartsFuture - processing $JSON_PART")
                b.toStrict(2.seconds).map { strict =>
                  // Logger.debug(strict.entity.data.utf8String)
                  (b.name, strict.entity.data.utf8String)
                }

              case b: BodyPart if b.name == xmlPartKey =>
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

          val requestMessageTask = for {
            requestingUser <- Authenticator.getUserADM(requestContext)
            allParts       <- ZIO.fromFuture(_ => allPartsFuture)
            jsonldDoc <-
              ZIO
                .fromOption(allParts.get(jsonPartKey))
                .orElseFail(
                  BadRequestException(s"MultiPart POST request was sent without required '$jsonPartKey' part!")
                )
                .mapAttempt(JsonLDUtil.parseJsonLD(_))
            apiRequestID <- RouteUtilZ.randomUuid()
            metadata <-
              ZIO.attempt(CreateMappingRequestMetadataV2.fromJsonLDSync(jsonldDoc, apiRequestID, requestingUser))
            xml <-
              ZIO
                .fromOption(allParts.get(xmlPartKey))
                .mapBoth(
                  _ => BadRequestException(s"MultiPart POST request was sent without required '$xmlPartKey' part!"),
                  CreateMappingRequestXMLV2
                )
          } yield CreateMappingRequestV2(metadata, xml, requestingUser, apiRequestID)
          RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
        }
      }
    }
}
