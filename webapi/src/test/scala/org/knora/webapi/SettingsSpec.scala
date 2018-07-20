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

        "provide access to all config values" in {

            settings.internalKnoraApiHost should be ("0.0.0.0")
            settings.internalKnoraApiPort should be (3333)
            settings.internalKnoraApiBaseUrl should be ("http://0.0.0.0:3333")

            settings.externalKnoraApiProtocol should be ("http")
            settings.externalKnoraApiHost should be ("0.0.0.0")
            settings.externalKnoraApiPort should be (3333)
            settings.externalKnoraApiBaseUrl should be ("http://0.0.0.0:3333")

            settings.internalSipiProtocol should be ("http")
            settings.internalSipiHost should be ("localhost")
            settings.internalSipiPort should be (1024)
            settings.internalSipiBaseUrl should be ("http://localhost:1024")

            settings.externalSipiProtocol should be ("http")
            settings.externalSipiHost should be ("localhost")
            settings.externalSipiPort should be (1024)
            settings.externalSipiBaseUrl should be ("http://localhost:1024")

            settings.sipiPrefix should be ("knora")
            settings.sipiFileServerPrefix should be ("server")

            settings.externalSipiIIIFGetUrl should be ("http://localhost:1024/knora")

            settings.internalSipiFileServerGetUrl should be ("http://localhost:1024/server/knora")
            settings.externalSipiFileServerGetUrl should be ("http://localhost:1024/server/knora")

            settings.internalSipiImageConversionUrl should be ("http://localhost:1024")

            settings.prometheusReporter should be (false)
            settings.zipkinReporter should be (false)
            settings.jaegerReporter should be (false)
            settings.dataDogReporter should be (false)


        }
    }
}
