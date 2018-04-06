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

package org.knora.webapi.routing

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Route
import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.Info
import io.swagger.models.auth.BasicAuthDefinition
import io.swagger.models.{ExternalDocs, Scheme}
import org.knora.webapi.SettingsImpl
import org.knora.webapi.routing.admin.UsersRouteADM

import scala.concurrent.ExecutionContextExecutor

class SwaggerDocsRoute(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter) extends SwaggerHttpService {

    implicit val system: ActorSystem = _system
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    // List all routes here
    override val apiClasses: Set[Class[_]] = Set(
        classOf[UsersRouteADM]
    )

    override val schemes: List[Scheme] = if (settings.externalKnoraApiProtocol == "http") {
        List(Scheme.HTTP)
    } else if (settings.externalKnoraApiProtocol == "https") {
        List(Scheme.HTTPS)
    } else {
        List(Scheme.HTTP)
    }

    override val host = settings.externalKnoraApiHostPort //the url of your api, not swagger's json endpoint
    override val basePath = "/"    //the basePath for the API you are exposing
    override val apiDocsPath = "api-docs" //where you want the swagger-json endpoint exposed
    override val info = Info(version = "1.3.0") //provides license and other description details
    override val externalDocs = Some(new ExternalDocs("Knora Docs", "http://www.knora.org/documentation/manual/rst/"))
    override val securitySchemeDefinitions = Map("basicAuth" -> new BasicAuthDefinition())

    def knoraApiPath: Route = {
        routes
    }

}
