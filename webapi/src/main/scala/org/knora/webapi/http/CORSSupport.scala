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

package org.knora.webapi.http

/**
  * This is taken from: http://jkinkead.blogspot.ch/2014/11/handling-cors-headers-with-spray-routing.html
  *
  * It is the unsecured version. There is also a secured version, but we don't use it, since we want to allow access
  * to the API from everywhere.
  */

import spray.http.{HttpHeaders, HttpOrigin, SomeOrigins}
import spray.routing.{Directive0, HttpService}


/**
  * Trait containing methods that provide CORS support.
  */
trait CORSSupport {
    this: HttpService =>

    val AccessControlAllowAll = HttpHeaders.RawHeader(
        "Access-Control-Allow-Origin", "*"
    )
    val AccessControlAllowNull = HttpHeaders.RawHeader(
        "Access-Control-Allow-Origin", "null"
    )
    val AccessControlAllowMethodsAll = HttpHeaders.RawHeader(
        "Access-Control-Allow-Methods", "GET, PUT, POST, DELETE, OPTIONS"
    )
    val AccessControlMaxAge = HttpHeaders.RawHeader(
        "Access-Control-Max-Age", "1000"
    )
    val AccessControlAllowHeadersAll = HttpHeaders.RawHeader(
        "Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization"
    )
    val AccessControlAllowCredentials = HttpHeaders.RawHeader(
        "Access-Control-Allow-Credentials", "true"
    )
    val Vary = HttpHeaders.RawHeader(
        "Vary", "Origin"
    )

    /**
      * Directive providing CORS header support. This should be included in any application serving
      * a REST API that's queried cross-origin (from a different host than the one serving the API).
      * See http://www.w3.org/TR/cors/ for full specification.
      *
      * This directive allows CORS from any host!
      */
    def allowAllHosts: Directive0 = mapInnerRoute {
        innerRoute =>
            optionalHeaderValueByType[HttpHeaders.Origin]() { originOption =>
                // If Origin is set add custom CORS headers and pass through.
                originOption flatMap {
                    case HttpHeaders.Origin(list) => list.find { case HttpOrigin(_, HttpHeaders.Host(hostname, _)) => true }
                } map {
                    goodOrigin =>
                        respondWithHeaders(
                            HttpHeaders.`Access-Control-Allow-Origin`(SomeOrigins(Seq(goodOrigin))),
                            //Vary,
                            AccessControlAllowMethodsAll,
                            AccessControlMaxAge,
                            AccessControlAllowHeadersAll,
                            AccessControlAllowCredentials) {
                            options {
                                complete {
                                    ""
                                }
                            } ~ innerRoute
                        }
                } getOrElse {
                    // Else, add standard CORS headers and pass though.
                    respondWithHeaders(
                        AccessControlAllowNull,
                        AccessControlAllowMethodsAll,
                        AccessControlMaxAge,
                        AccessControlAllowHeadersAll,
                        AccessControlAllowCredentials) {
                        options {
                            complete {
                                ""
                            }
                        } ~ innerRoute
                    }
                }
            }
    }

    /** Directive providing CORS header support. This should be included in any application serving
      * a REST API that's queried cross-origin (from a different host than the one serving the API).
      * See http://www.w3.org/TR/cors/ for full specification.
      *
      * @param allowedHostnames the set of hosts that are allowed to query the API. These should
      *                         not include the scheme or port; they're matched only against the hostname of the Origin
      *                         header.
      */
    def allowHosts(allowedHostnames: Set[String]): Directive0 = mapInnerRoute { innerRoute =>
        // Conditionally responds with "allowed" CORS headers, if the request origin's host is in the
        // allowed set, or if the request doesn't have an origin.
        optionalHeaderValueByType[HttpHeaders.Origin]() { originOption =>
            // If Origin is set and the host is in our allowed set, add CORS headers and pass through.
            originOption flatMap {
                case HttpHeaders.Origin(list) => list.find { case HttpOrigin(_, HttpHeaders.Host(hostname, _)) => allowedHostnames.contains(hostname) }
            } map {
                goodOrigin =>
                    respondWithHeaders(
                        HttpHeaders.`Access-Control-Allow-Headers`(Seq("Origin", "X-Requested-With", "Content-Type", "Accept")),
                        HttpHeaders.`Access-Control-Allow-Origin`(SomeOrigins(Seq(goodOrigin)))
                    ) {
                        options {
                            complete {
                                ""
                            }
                        } ~ innerRoute
                    }
            } getOrElse {
                // Else, pass through without headers.
                innerRoute
            }
        }
    }
}
