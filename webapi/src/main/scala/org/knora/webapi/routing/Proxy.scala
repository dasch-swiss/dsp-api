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

package org.knora.webapi.routing

import akka.actor.ActorSystem
import akka.io.IO
import spray.can.Http
import spray.http.{Uri, HttpHeaders, HttpHeader, HttpRequest}
import spray.routing.{Route, RequestContext}

/**
  * This trait provides proxy capabilities to routes, i.e. it allows to tunnel a http requests to an external service
  * and pipe the result back to the client.
  *
  * A possible use case for a proxy is when a service not run by Knora should be called using a Knora route. This can be necessary in case
  * a cross-domain request is not possible due to browsers' security restraints.
  */
trait Proxy {

    /**
      * Used to tunnel the requests to another server and then pipe the result back to the client.
      * @param uri the new URI to which the request will be tunneled
      * @param unmatchedPath the part of the route that should be appended to the uri
      * @return
      */
    def proxyToUnmatchedPath(uri: Uri, unmatchedPath: Uri.Path)(implicit system: ActorSystem): Route = {
        proxyRequest(updateRequest(uri, unmatchedPath, updateUriUnmatchedPath))
    }

    private val updateUriUnmatchedPath = (uri: Uri, unmatchedPath: Uri.Path, ctx: RequestContext) => {
        println(s"URI: ${uri.toString}, unmatchedPath: ${unmatchedPath.toString}")
        val resUri = uri.withPath(uri.path ++ unmatchedPath)
        println(s"resulting URI: $resUri")
        resUri
    }

    private def updateRequest(uri: Uri, unmatchedPath: Uri.Path, updateUri: (Uri, Uri.Path, RequestContext) => Uri): RequestContext => HttpRequest =
        ctx => ctx.request.copy(
            uri = updateUri(uri, unmatchedPath, ctx),
            headers = stripHostHeader(ctx.request.headers)
        )

    private def proxyRequest(updateRequest: RequestContext => HttpRequest)(implicit system: ActorSystem): Route =
        ctx => IO(Http)(system) tell (updateRequest(ctx), ctx.responder)

    private def stripHostHeader(headers: List[HttpHeader]) = {
        println(s"headers before strip: ${headers.toString}")
        val strippedHeaders = headers filterNot (header => header is (HttpHeaders.Host.lowercaseName))
        println(s"headers after strip: ${strippedHeaders.toString}")
        strippedHeaders
    }






}