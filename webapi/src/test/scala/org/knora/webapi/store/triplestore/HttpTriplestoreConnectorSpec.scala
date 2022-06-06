/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
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
      appActor ! SimulateTimeoutRequest()

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        assert(msg.cause.isInstanceOf[TriplestoreTimeoutException])
        assert(
          msg.cause.getMessage == "The triplestore took too long to process a request. This can happen because the triplestore needed too much time to search through the data that is currently in the triplestore. Query optimisation may help."
        )
      }
    }
  }
}
