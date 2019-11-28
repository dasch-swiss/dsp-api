/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.e2e.v1

import akka.event.LoggingAdapter
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.ITKnoraFakeSpec
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol


object KnoraSipiScriptsV1ITSpec {
    val config: Config = ConfigFactory.parseString(
        """
          |akka.loglevel = "DEBUG"
          |akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing Knora-Sipi scripts. Sipi must be running with the config file
  * `sipi.knora-config.lua`. This spec uses the KnoraFakeService to start a faked `webapi` server that always allows
  * access to files.
  */
class KnoraSipiScriptsV1ITSpec extends ITKnoraFakeSpec(KnoraSipiScriptsV1ITSpec.config) with TriplestoreJsonProtocol {

    implicit override val log: LoggingAdapter = akka.event.Logging(system, this.getClass)

    "Calling Knora Sipi Scripts" should {

        "successfully call C++ functions from Lua scripts" in {
            val request = Get(baseSipiUrl + "/test_functions" )
            getResponseString(request)
        }

        "successfully call Lua functions for mediatype handling" in {
            val request = Get(baseSipiUrl + "/test_file_type" )
            getResponseString(request)
        }

        "successfully call Lua function that gets the Knora session id from the cookie header sent to Sipi" in {
            val request = Get(baseSipiUrl + "/test_knora_session_cookie" )
            getResponseString(request)
        }
    }
}
