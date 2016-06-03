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

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite, WordSpecLike}
import spray.client.pipelining._
import spray.http.{HttpRequest, HttpResponse}

import scala.concurrent.Future
import scala.concurrent.duration._

trait E2ETestCore extends Core {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)

    implicit val system = ActorSystem("E2ETest", config.withFallback(E2ESpec.defaultConfig))
}

object E2ESpec {
    val defaultConfig: Config = ConfigFactory.load()
}

/**
  * This class can be used in End-to-End testing. It starts the Knora server and
  * provides access to settings and logging.
  */
class E2ESpec extends E2ETestCore with KnoraService with Suite with WordSpecLike with Matchers with BeforeAndAfterAll {

    override val log = akka.event.Logging(system, this.getClass())

    val pipe: HttpRequest => Future[HttpResponse] = sendReceive

    val baseApiUrl = settings.baseApiUrl

    implicit val postfix = scala.language.postfixOps

    override def beforeAll: Unit = {
        /* Set the startup flags and start the Knora Server */
        log.debug(s"Starting Knora Service")
        StartupFlags.allowResetTriplestoreContentOperationOverHTTP send true
        checkActorSystem
        startService
    }

    override def afterAll: Unit = {
        /* Stop the server when everything else has finished */
        log.debug(s"Stopping Knora Service")
        stopService
    }

}
