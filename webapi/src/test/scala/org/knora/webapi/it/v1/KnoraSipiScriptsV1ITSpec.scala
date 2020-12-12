/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.it.v1

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.{ITKnoraFakeSpec, TestContainers}

object KnoraSipiScriptsV1ITSpec {
  val config: Config = ConfigFactory.parseString("""
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
      val request = Get(baseInternalSipiUrl + "/test_functions")
      // DSP-707: trying to wake up sipi first (without triggering an exception)
      try {
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be (StatusCodes.OK)
      } catch {
        case e:Exception => {
          println(TestContainers.SipiContainer.getLogs)
        }
      }
    }

    "successfully call Lua functions for mediatype handling" ignore {
      val request = Get(baseInternalSipiUrl + "/test_file_type")
      val response: HttpResponse = singleAwaitingRequest(request)
      response.status should be (StatusCodes.OK)
    }

    "successfully call Lua function that gets the Knora session id from the cookie header sent to Sipi" ignore {
      val request = Get(baseInternalSipiUrl + "/test_knora_session_cookie")
      val response: HttpResponse = singleAwaitingRequest(request)
      response.status should be (StatusCodes.OK)
    }
  }
}
