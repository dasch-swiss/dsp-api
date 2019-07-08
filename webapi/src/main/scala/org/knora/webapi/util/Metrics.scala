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

package org.knora.webapi.util

import com.typesafe.scalalogging.Logger

/**
  * A set of methods used for measuring stuff that is happening.
  */
object Metrics {

    /**
      * Measures the time the future needs to complete.
      *
      * Example:
      *
      * val f = Timing.timed {
      *     Future {
      *         work inside the future
      *     }
      * }
      *
      * @param message the custom message that.
      * @param f the future we want to time.
      * @param logger the logger used to output the message.
      */
    def timed[T](message: String)(f: => T)(implicit logger: Logger): T = {
        val start = System.currentTimeMillis()
        try f finally logger.info(s"$message: " + (System.currentTimeMillis() - start) + "ms")
    }

}
