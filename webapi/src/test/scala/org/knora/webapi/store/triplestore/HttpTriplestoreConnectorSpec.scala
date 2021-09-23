/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

package org.knora.webapi.store.triplestore

import akka.testkit.ImplicitSender
import org.knora.webapi.CoreSpec
import org.knora.webapi.exceptions.TriplestoreTimeoutException
import org.knora.webapi.messages.store.triplestoremessages.SimulateTimeoutRequest

import scala.concurrent.duration._

class HttpTriplestoreConnectorSpec extends CoreSpec() with ImplicitSender {
  private val timeout = 10.seconds

  "The HttpTriplestoreConnector" should {
    "report a connection timeout with an appropriate error message" in {
      storeManager ! SimulateTimeoutRequest()

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        assert(msg.cause.isInstanceOf[TriplestoreTimeoutException])
        assert(
          msg.cause.getMessage == "The triplestore took too long to process a request. This can happen because the triplestore needed too much time to search through the data that is currently in the triplestore. Query optimisation may help."
        )
      }
    }
  }
}
