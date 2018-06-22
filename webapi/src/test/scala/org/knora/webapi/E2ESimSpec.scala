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

package org.knora.webapi

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import io.gatling.core.scenario.Simulation
import org.knora.webapi.messages.admin.responder.storesmessages.{ResetTriplestoreContentRequestADM, ResetTriplestoreContentResponseADM}
import org.knora.webapi.messages.app.appmessages.SetAllowReloadOverHTTPState
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.util.StringFormatter

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.languageFeature.postfixOps

object E2ESimSpec {

    val config: Config = ConfigFactory.load()

    val defaultConfig: Config = ConfigFactory.parseString(
        """
          akka.loglevel = "INFO"
          akka.stdout-loglevel = "INFO"
        """.stripMargin
    ).withFallback(config)
}

/**
  * This class can be used in End-to-End testing. It starts the Knora server and
  * provides access to settings and logging.
  */
abstract class E2ESimSpec(_system: ActorSystem) extends Simulation with Core with KnoraService {

    /* needed by the core trait */
    implicit lazy val settings: SettingsImpl = Settings(system)

    StringFormatter.initForTest()

    def this(name: String, config: Config) = this(ActorSystem(name, config.withFallback(E2ESimSpec.defaultConfig)))

    def this(config: Config) = this(ActorSystem("PerfSpec", config.withFallback(E2ESimSpec.defaultConfig)))

    def this(name: String) = this(ActorSystem(name, E2ESimSpec.defaultConfig))

    def this() = this(ActorSystem("PerfSpec", E2ESimSpec.defaultConfig))

    /* needed by the core trait */
    implicit lazy val system: ActorSystem = _system

    /* needed by the core trait */
    implicit lazy val log: LoggingAdapter = akka.event.Logging(system, "PerfSpec")

    // needs to be overridden in subclass
    val rdfDataObjects: Seq[RdfDataObject]

    before {
        /* Set the startup flags and start the Knora Server */
        log.info(s"executing before setup started")

        applicationStateActor ! SetAllowReloadOverHTTPState(true)

        startService()

        implicit val timeout: Timeout = 300.second
        Await.result(responderManager ? ResetTriplestoreContentRequestADM(rdfDataObjects), 300.second).asInstanceOf[ResetTriplestoreContentResponseADM]
        log.info(s"executing before setup finished")
    }

    after {
        /* Stop the server when everything else has finished */
        log.info(s"executing after setup")
        stopService()
    }
}
