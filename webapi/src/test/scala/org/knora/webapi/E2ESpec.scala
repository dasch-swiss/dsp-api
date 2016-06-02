/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi

import org.scalatest.{BeforeAndAfterAll, Matchers, Suite, WordSpecLike}
import spray.client.pipelining._
import spray.http.{HttpRequest, HttpResponse}

import scala.concurrent.Future

/**
  * This class can be used in End-to-End testing. It starts the Knora server and
  * provides access to settings and logging.
  */
class E2ESpec extends Suite with WordSpecLike with Matchers with BeforeAndAfterAll {

    /* get the actor system Knora is using */
    implicit val system = KnoraService.system
    import system.dispatcher

    val settings = Settings(system)
    val logger = akka.event.Logging(system, this.getClass())
    val log = logger

    val pipe: HttpRequest => Future[HttpResponse] = sendReceive

    val baseApiUrl = settings.baseApiUrl

    implicit val postfix = scala.language.postfixOps

    override def beforeAll: Unit = {
        /* Set the startup flags and start the Knora Server */
        log.debug(s"Starting Knora Service")
        StartupFlags.allowResetTriplestoreContentOperationOverHTTP send true
        KnoraService.startService
    }

    override def afterAll: Unit = {
        /* Stop the server when everything else has finished */
        log.debug(s"Stopping Knora Service")
        KnoraService.stopService
    }

}
