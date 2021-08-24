/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

import akka.http.scaladsl.model.StatusCodes
import org.knora.webapi.ITKnoraLiveSpec
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol

import scala.concurrent.duration._

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
