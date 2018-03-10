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
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.messages.app.appmessages.SetAllowReloadOverHTTPState
import org.knora.webapi.util.StringFormatter
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.languageFeature.postfixOps

object ITKnoraLiveSpec {
    val defaultConfig: Config = ConfigFactory.load()
}

/**
  * This class can be used in End-to-End testing. It starts the Knora server and
  * provides access to settings and logging.
  */
class ITKnoraLiveSpec(_system: ActorSystem) extends Core with KnoraService with Suite with BeforeAndAfterAll {
    /* needed by the core trait */
    implicit lazy val settings: SettingsImpl = Settings(system)
    StringFormatter.initForTest()

    def this(name: String, config: Config) = this(ActorSystem(name, config.withFallback(ITKnoraLiveSpec.defaultConfig)))

    def this(config: Config) = this(ActorSystem("IntegrationTests", config.withFallback(ITKnoraLiveSpec.defaultConfig)))

    def this(name: String) = this(ActorSystem(name, ITKnoraLiveSpec.defaultConfig))

    def this() = this(ActorSystem("IntegrationTests", ITKnoraLiveSpec.defaultConfig))

    /* needed by the core trait */
    implicit lazy val system: ActorSystem = _system

    /* needed by the core trait */
    implicit lazy val log: LoggingAdapter = akka.event.Logging(system, "ITSpec")

    if (!settings.knoraApiUseHttp) throw HttpConfigurationException("Integration tests currently require HTTP")

    protected val baseApiUrl: String = settings.knoraApiHttpBaseUrl
    protected val baseSipiUrl: String = s"${settings.sipiBaseUrl}:${settings.sipiPort}"

    implicit protected val postfix: postfixOps = scala.language.postfixOps

    override def beforeAll: Unit = {
        /* Set the startup flags and start the Knora Server */
        log.debug(s"Starting Knora Service")
        checkActorSystem()
        applicationStateActor ! SetAllowReloadOverHTTPState(true)
        startService()
    }

    override def afterAll: Unit = {
        /* Stop the server when everything else has finished */
        log.debug(s"Stopping Knora Service")
        stopService()
    }

}
