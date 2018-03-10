/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.salsah

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config


/**
  * Reads application settings that come from `application.conf`.
  */
class SettingsImpl(config: Config) extends Extension {

    val hostName = config.getString("app.http.hostname")
    val httpPort = config.getInt("app.http.http-port")
    val httpsPort = config.getInt("app.http.https-port")

    // used for testing
    val headless = config.getBoolean("app.testing.headless")

    // used in deployment
    val deployed = config.getBoolean("app.deployed")
    val workingDirectory = config.getString("app.workdir")

    // Javascript Configuration
    val webapiUrl = config.getString("app.jsconf.webapi-url")
    val sipiUrl = config.getString("app.jsconf.sipi-url")
}


object Settings extends ExtensionId[SettingsImpl] with ExtensionIdProvider {

    override def lookup() = Settings

    override def createExtension(system: ExtendedActorSystem) =
        new SettingsImpl(system.settings.config)

    /**
      * Java API: retrieve the Settings extension for the given system.
      */
    override def get(system: ActorSystem): SettingsImpl = super.get(system)
}