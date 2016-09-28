/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

/*
package org.knora.webapi.routing.v1

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import org.knora.webapi.{BadRequestException, SettingsImpl}
import org.knora.webapi.messages.v1.responder.sipimessages.SipiFileInfoGetRequestV1
import org.knora.webapi.routing.{Authenticator, Proxy, RouteUtilV1}
import org.knora.webapi.util.InputValidation
import spray.routing.Directives._
import spray.routing._

import scala.util.Try

/**
  * Provides a spray-routing function for the API routes that Sipi connects to.
  */
object SipiRouteV1 extends Authenticator with Proxy {

    /**
      * A spray-routing function for the API routes that Sipi connects to.
      */
    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v1" / "files" / Segment) { file =>
            get {
                requestContext =>
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        //val fileValueIRI = InputValidation.toIri(iri, () => throw BadRequestException(s"Invalid file value IRI: $iri"))
                        val filename = InputValidation.toSparqlEncodedString(file, () => throw BadRequestException(s"Invalid filename: '$file'"))
                        SipiFileInfoGetRequestV1(filename, userProfile)
                    }
                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        } /*~ path("v1" / "sipi" / Rest) { iiif =>
                println(iiif)
                proxyToUnmatchedPath(Uri("http://localhost:3333/v1/assets/"), Uri.Path(iiif))
        }*/
        // this proxy is meant to be used in case we decide that IIIF-URLs should not refer directly to Sipi,
        // but be processed and fowarded to Sipi

    }
}
*/