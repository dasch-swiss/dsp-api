/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v1

import akka.http.scaladsl.model.StatusCodes
import zio._

import org.knora.webapi.ITKnoraLiveSpec
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol

/**
 * Causes an internal server error to see if logging is working correctly.
 */
class ErrorV1ITSpec extends ITKnoraLiveSpec with TriplestoreJsonProtocol {

  "Make a request that causes an internal server error" in {
    val request  = Get(baseApiUrl + "/v1/error/unitMsg")
    val response = singleAwaitingRequest(request, 5.seconds)
    // println(response.toString())
    assert(response.status == StatusCodes.InternalServerError)
  }

}
