/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import spray.json._
import zio.Runtime
import zio.ZIO

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import dsp.errors.BadRequestException
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v1.responder.standoffmessages.RepresentationV1JsonProtocol.createMappingApiRequestV1Format
import org.knora.webapi.messages.v1.responder.standoffmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV1
import org.knora.webapi.routing.RouteUtilZ

/**
 * A route used to convert XML to standoff.
 */
final case class StandoffRouteV1()(
  private implicit val runtime: Runtime[StringFormatter with Authenticator with MessageRelay],
  private implicit val system: ActorSystem
) {

  private val jsonPartKey = "json"
  private val xmlPartKey  = "xml"

  def makeRoute: Route =
    path("v1" / "mapping") {
      post {
        entity(as[Multipart.FormData]) { formData: Multipart.FormData => requestContext =>
          val msg = for {
            allParts <- parseFormData(formData)
            xml <-
              ZIO
                .fromOption(allParts.get(xmlPartKey))
                .orElseFail(
                  BadRequestException(s"MultiPart POST request was sent without required '$xmlPartKey' part!")
                )
            standoffApiJSONRequest <-
              ZIO
                .fromOption(allParts.get(jsonPartKey))
                .orElseFail(
                  BadRequestException(s"MultiPart POST request was sent without required '$jsonPartKey' part!")
                )
                .mapAttempt(_.parseJson.convertTo[CreateMappingApiRequestV1])
                .mapError { case e: DeserializationException =>
                  BadRequestException("JSON params structure is invalid: " + e.toString)
                }
            label <-
              RouteUtilV1.toSparqlEncodedString(standoffApiJSONRequest.label, "'label' contains invalid characters")
            projectIri <- RouteUtilZ.validateAndEscapeIri(standoffApiJSONRequest.project_id, "invalid project IRI")
            mappingName <- RouteUtilV1.toSparqlEncodedString(
                             standoffApiJSONRequest.mappingName,
                             "'mappingName' contains invalid characters"
                           )
            userProfile <- Authenticator.getUserADM(requestContext)
            uuid        <- RouteUtilV1.randomUuid()
          } yield CreateMappingRequestV1(xml, label, projectIri, mappingName, userProfile, uuid)
          RouteUtilV1.runJsonRouteZ(msg, requestContext)
        }
      }
    }

  private def parseFormData(formData: Multipart.FormData)(implicit system: ActorSystem) = ZIO.fromFuture {
    implicit ec: ExecutionContext =>
      type Name = String
      formData.parts
        .mapAsync[(Name, String)](1) {
          case b: BodyPart if b.name == jsonPartKey =>
            b.toStrict(2.seconds).map(strict => (b.name, strict.entity.data.utf8String))
          case b: BodyPart if b.name == xmlPartKey =>
            b.toStrict(2.seconds).map(strict => (b.name, strict.entity.data.utf8String))
          case b: BodyPart if b.name.isEmpty => throw BadRequestException("part of HTTP multipart request has no name")
          case b: BodyPart                   => throw BadRequestException(s"multipart contains invalid name: ${b.name}")
          case _                             => throw BadRequestException("multipart request could not be handled")
        }
        .runFold(Map.empty[Name, String])((map, tuple) => map + tuple)
  }
}
