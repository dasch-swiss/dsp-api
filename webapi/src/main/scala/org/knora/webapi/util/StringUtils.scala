/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util

object StringUtils {

    /**
      * This implicit class allows to extend the scala string class. Example:
      *
      * {{code}}
      * import org.knora.webapi.util.StringUtils._
      *
      * object Main extends App {
      *   println("1".toBoolean)
      * }
      * {{code}}
      *
      * @param s the string object to which we want to add our methods.
      */
    implicit class StringImprovements(val s: String) {

        /**
          * Extend string with our own implementation of 'toBoolean'
          * @return
          */
        def toBooleanExtended = stringToBoolean(s)
    }

    /**
      * Turn a string value into a Boolean value.
      *
      * @param s the string value to be converted.
      * @return a Boolean
      */
    def stringToBoolean(s: String): Boolean = {
        if (s == "true" ||  s == "1") {
            true
        } else if (s == "false" || s == "0") {
            false
        } else {
            s.toBoolean
        }
    }

}
