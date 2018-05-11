/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.routing.v2

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.knora.webapi.messages.v2.responder.standoffmessages.{CreateMappingRequestMetadataV2, CreateMappingRequestV2, CreateMappingRequestXMLV2}
import org.knora.webapi.routing.{Authenticator, RouteUtilV2}
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.jsonld.JsonLDUtil
import org.knora.webapi.{ApiV2WithValueObjects, BadRequestException, SettingsImpl}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}



/**
  * Provides a function for API routes that deal with search.
  */
object StandoffRouteV2 extends Authenticator {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContextExecutor = system.dispatcher
        implicit val timeout: Timeout = settings.defaultTimeout
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        implicit val materializer = ActorMaterializer()
        val responderManager = system.actorSelection("/user/responderManager")

        path("v2" / "mapping") {
            post {
                entity(as[Multipart.FormData]) { formdata: Multipart.FormData =>
                    requestContext =>

                        val JSON_PART = "json"
                        val XML_PART = "xml"

                        type Name = String

                        val userProfile = getUserADM(requestContext)

                        val apiRequestUUID = UUID.randomUUID


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

                        val requestMessageFuture: Future[CreateMappingRequestV2] = allPartsFuture.map {
                            allParts: Map[Name, String] =>

                                val jsonldDoc = JsonLDUtil.parseJsonLD(allParts.getOrElse(JSON_PART, throw BadRequestException(s"MultiPart POST request was sent without required '$JSON_PART' part!")).toString)

                                val metadata: CreateMappingRequestMetadataV2 = CreateMappingRequestMetadataV2.fromJsonLD(jsonldDoc, apiRequestUUID, userProfile)

                                val xml: String = allParts.getOrElse(XML_PART, throw BadRequestException(s"MultiPart POST request was sent without required '$XML_PART' part!")).toString

                                CreateMappingRequestV2(
                                    metadata,
                                    CreateMappingRequestXMLV2(xml),
                                    userProfile,
                                    apiRequestUUID
                                )

                        }

                        RouteUtilV2.runJsonRouteWithFuture(
                            requestMessageFuture,
                            requestContext,
                            settings,
                            responderManager,
                            log,
                            ApiV2WithValueObjects
                        )

                }

            }

        }
    }
}