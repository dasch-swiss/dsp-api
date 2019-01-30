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

package org.knora.webapi.util

import spray.json._

import scala.util.{Failure, Success, Try}

/**
  * Utility functions for communicating with Sipi.
  */
object SipiUtil {
    /**
      * Tries to extract an error message from a Sipi HTTP response.
      *
      * @param sipiResponseStr the response received from Sipi.
      * @return the error message contained in the response, or the same string if it could not be parsed.
      */
    def getSipiErrorMessage(sipiResponseStr: String): String = {
        if (sipiResponseStr.isEmpty) {
            sipiResponseStr
        } else {
            Try(sipiResponseStr.parseJson) match {
                case Success(jsValue: JsValue) =>
                    jsValue match {
                        case jsObject: JsObject =>
                            jsObject.fields.get("message") match {
                                case Some(JsString(str)) => str
                                case _ => sipiResponseStr
                            }

                        case _ => sipiResponseStr
                    }

                case Failure(_) => sipiResponseStr
            }
        }
    }
}
