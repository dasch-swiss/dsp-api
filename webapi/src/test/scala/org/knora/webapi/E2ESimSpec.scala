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

package org.knora.webapi

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import com.typesafe.config.Config
import io.gatling.core.scenario.Simulation
import org.knora.webapi.messages.app.appmessages.SetAllowReloadOverHTTPState
import org.knora.webapi.util.StringFormatter

import scala.languageFeature.postfixOps


/**
  * This class can be used in End-to-End testing. It starts the Knora server and
  * provides access to settings and logging.
  */
abstract class E2ESimSpec(_system: ActorSystem) extends Simulation with Core with KnoraService {

    /* needed by the core trait */
    implicit lazy val settings: SettingsImpl = Settings(system)
    StringFormatter.initForTest()

    def this(name: String, config: Config) = this(ActorSystem(name, config.withFallback(E2ESpec.defaultConfig)))

    def this(config: Config) = this(ActorSystem("PerfSpec", config.withFallback(E2ESpec.defaultConfig)))

    def this(name: String) = this(ActorSystem(name, E2ESpec.defaultConfig))

    def this() = this(ActorSystem("PerfSpec", E2ESpec.defaultConfig))

    /* needed by the core trait */
    implicit lazy val system: ActorSystem = _system

    /* needed by the core trait */
    implicit lazy val log: LoggingAdapter = akka.event.Logging(system, "PerfSpec")

    if (!settings.knoraApiUseHttp) throw HttpConfigurationException("PerfSpec tests currently require HTTP")

    // gatling config
    // val httpConf = http.warmUp("http://www.google.com") // set own warmup target instead og galting.io


    before {
        /* Set the startup flags and start the Knora Server */
        log.debug(s"Starting Knora Service")
        checkActorSystem()

        applicationStateActor ! SetAllowReloadOverHTTPState(true)

        startService()
    }

    after {
        /* Stop the server when everything else has finished */
        log.debug(s"Stopping Knora Service")
        stopService()
    }

}
