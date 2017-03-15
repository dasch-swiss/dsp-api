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

package org.knora.webapi

import com.typesafe.config.ConfigFactory

object SettingsSpec {
    val config = ConfigFactory.parseString(
        """
        akka {
            # akka.loglevel = "DEBUG"
            # akka.stdout-loglevel = "DEBUG"
        }
        """.stripMargin)
}

class SettingsSpec extends CoreSpec("SettingsActorTestSystem", SettingsSpec.config) {

    "The Settings Object" should {

        "provide access to all config values" ignore {

            settings.triplestoreType should ===("fuseki")

        }
    }
}
