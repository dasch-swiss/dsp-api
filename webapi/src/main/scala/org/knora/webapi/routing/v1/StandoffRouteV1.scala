/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.routing.v1

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.knora.webapi.messages.v1.responder.standoffmessages.RepresentationV1JsonProtocol.createMappingApiRequestV1Format
import org.knora.webapi.messages.v1.responder.standoffmessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{BadRequestException, KnoraDispatchers, SettingsImpl}
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


/**
  * A route used to convert XML to standoff.
  */
object StandoffRouteV1 extends Authenticator {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, loggingAdapter: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)
        implicit val timeout: Timeout = settings.defaultTimeout
        implicit val materializer: ActorMaterializer = ActorMaterializer()
        val stringFormatter = StringFormatter.getGeneralInstance

        val responderManager = system.actorSelection("/user/responderManager")

        path("v1" / "mapping") {
            post {
                entity(as[Multipart.FormData]) { formdata: Multipart.FormData =>
                    requestContext =>

                        type Name = String

                        val JSON_PART = "json"
                        val XML_PART = "xml"

                        // collect all parts of the multipart as it arrives into a map
                        val allPartsFuture: Future[Map[Name, String]] = formdata.parts.mapAsync[(Name, String)](1) {
                            case b: BodyPart if b.name == JSON_PART => {
                                //loggingAdapter.debug(s"inside allPartsFuture - processing $JSON_PART")
                                b.toStrict(2.seconds).map { strict =>
                                    //loggingAdapter.debug(strict.entity.data.utf8String)
                                    (b.name, strict.entity.data.utf8String)
                                }

                            }
                            case b: BodyPart if b.name == XML_PART => {
                                //loggingAdapter.debug(s"inside allPartsFuture - processing $XML_PART")

                                b.toStrict(2.seconds).map {
                                    strict =>
                                        //loggingAdapter.debug(strict.entity.data.utf8String)
                                        (b.name, strict.entity.data.utf8String)
                                }

                            }
                            case b: BodyPart if b.name.isEmpty => throw BadRequestException("part of HTTP multipart request has no name")
                            case b: BodyPart => throw BadRequestException(s"multipart contains invalid name: ${b.name}")
                            case _ => throw BadRequestException("multipart request could not be handled")
                        }.runFold(Map.empty[Name, String])((map, tuple) => map + tuple)

                        val requestMessageFuture: Future[CreateMappingRequestV1] = for {

                            userProfile <- getUserADM(requestContext)

                            allParts: Map[Name, String] <- allPartsFuture

                            // get the json params and turn them into a case class
                            standoffApiJSONRequest: CreateMappingApiRequestV1 = try {

                                val jsonString: String = allParts.getOrElse(JSON_PART, throw BadRequestException(s"MultiPart POST request was sent without required '$JSON_PART' part!"))

                                jsonString.parseJson.convertTo[CreateMappingApiRequestV1]
                            } catch {
                                case e: DeserializationException => throw BadRequestException("JSON params structure is invalid: " + e.toString)
                            }

                            xml: String = allParts.getOrElse(XML_PART, throw BadRequestException(s"MultiPart POST request was sent without required '$XML_PART' part!")).toString
                            
                        } yield CreateMappingRequestV1(
                            projectIri = stringFormatter.validateAndEscapeIri(standoffApiJSONRequest.project_id, throw BadRequestException("invalid project IRI")),
                            xml = xml,
                            userProfile = userProfile,
                            label = stringFormatter.toSparqlEncodedString(standoffApiJSONRequest.label, throw BadRequestException("'label' contains invalid characters")),
                            mappingName = stringFormatter.toSparqlEncodedString(standoffApiJSONRequest.mappingName, throw BadRequestException("'mappingName' contains invalid characters")),
                            apiRequestID = UUID.randomUUID
                        )

                        RouteUtilV1.runJsonRouteWithFuture(
                            requestMessageFuture,
                            requestContext,
                            settings,
                            responderManager,
                            loggingAdapter
                        )
                }
            }
        }
    }
}