/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import spray.json._
import zio.Runtime

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._

import dsp.errors.BadRequestException
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.v1.responder.standoffmessages.RepresentationV1JsonProtocol.createMappingApiRequestV1Format
import org.knora.webapi.messages.v1.responder.standoffmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilV1

/**
 * A route used to convert XML to standoff.
 */
final case class StandoffRouteV1(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator with MessageRelay]
) extends KnoraRoute(routeData, runtime) {

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    path("v1" / "mapping") {
      post {
        entity(as[Multipart.FormData]) { formdata: Multipart.FormData => requestContext =>
          type Name = String

          val JSON_PART = "json"
          val XML_PART  = "xml"

          // collect all parts of the multipart as it arrives into a map
          val allPartsFuture: Future[Map[Name, String]] = formdata.parts
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
              case _           => throw BadRequestException("multipart request could not be handled")
            }
            .runFold(Map.empty[Name, String])((map, tuple) => map + tuple)

          val requestMessageFuture: Future[CreateMappingRequestV1] = for {

            userProfile <- getUserADM(requestContext)

            allParts: Map[Name, String] <- allPartsFuture

            // get the json params and turn them into a case class
            standoffApiJSONRequest: CreateMappingApiRequestV1 =
              try {

                val jsonString: String = allParts.getOrElse(
                  JSON_PART,
                  throw BadRequestException(s"MultiPart POST request was sent without required '$JSON_PART' part!")
                )

                jsonString.parseJson.convertTo[CreateMappingApiRequestV1]
              } catch {
                case e: DeserializationException =>
                  throw BadRequestException("JSON params structure is invalid: " + e.toString)
              }

            xml: String =
              allParts
                .getOrElse(
                  XML_PART,
                  throw BadRequestException(s"MultiPart POST request was sent without required '$XML_PART' part!")
                )
          } yield CreateMappingRequestV1(
            xml = xml,
            label = stringFormatter.toSparqlEncodedString(
              standoffApiJSONRequest.label,
              throw BadRequestException("'label' contains invalid characters")
            ),
            projectIri = stringFormatter.validateAndEscapeIri(
              standoffApiJSONRequest.project_id,
              throw BadRequestException("invalid project IRI")
            ),
            mappingName = stringFormatter.toSparqlEncodedString(
              standoffApiJSONRequest.mappingName,
              throw BadRequestException("'mappingName' contains invalid characters")
            ),
            userProfile = userProfile,
            apiRequestID = UUID.randomUUID
          )

          RouteUtilV1.runJsonRouteF(requestMessageFuture, requestContext)
        }
      }
    }
}
