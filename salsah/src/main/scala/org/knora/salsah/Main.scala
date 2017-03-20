/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.salsah

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.concurrent.Future

object Main extends App {
    implicit val system = ActorSystem("salsah-system")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher

    /**
      * The application's configuration.
      */
    val settings: SettingsImpl = Settings(system)

    val log = akka.event.Logging(system, this.getClass)

    log.info(s"Deployed: ${settings.deployed}")

    val handler = if (settings.deployed) {
        val workdir = settings.workingDirectory
        log.info(s"Working Directory: $workdir")
        get {
            getFromDirectory(s"$workdir/public")
        }
    } else {
        val wherami = System.getProperty("user.dir")
        log.info(s"user.dir: $wherami")
        get {
            getFromDirectory(s"$wherami/src/public")
        }
    }

    val (host, port) = (settings.hostName, settings.httpPort)

    log.info(s"Salsah online at http://$host:$port/index.html")

    val bindingFuture: Future[ServerBinding] =  Http().bindAndHandle(handler, host, port)

    bindingFuture onFailure {
        case ex: Exception =>
            log.error(ex, s"Failed to bind to $host:$port")
    }
}
