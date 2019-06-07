/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.routing

import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._

/**
  * Akka HTTP directives which can be wrapped around a [[akka.http.scaladsl.server.Route]]].
  */
object AroundDirectives {

    /**
      * When wrapped around a [[akka.http.scaladsl.server.Route]], logs the time it took for the route to run.
      *
      * @param log the logging adapter
      */
    def logDuration(log: LoggingAdapter): Directive0 = extractRequestContext.flatMap { ctx =>
        val start = System.currentTimeMillis()
        mapResponse { resp =>
            val took = System.currentTimeMillis() - start
            log.info(s"[${resp.status.intValue()}] ${ctx.request.method.name} " + s"${ctx.request.uri} took: ${took}ms")
            resp
        }
    }
}
