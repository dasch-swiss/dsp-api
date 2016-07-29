/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

import org.kiama.output.PrettyPrinter

/**
  * Pretty-prints Scala objects.
  */
object ScalaPrettyPrinter extends PrettyPrinter {
    /**
      * Converts any Scala value into a pretty-printed string.
      *
      * @param value the value to be converted.
      * @return a pretty-printed string.
      */
    def prettyPrint(value: Any): String = {
        pretty(any(value))
    }

    /*
    private val testList = Vector(
        Map(
            "first" ->
                Map(
                    "a" -> "one",
                    "b" -> "two",
                    "c" -> "three",
                    "d" -> "four",
                    "e" -> "five",
                    "f" -> "six"
                ),
            "second" ->
                Map(
                    "a" -> "one",
                    "b" -> "two",
                    "c" -> "three",
                    "d" -> "four",
                    "e" -> "five",
                    "f" -> "six"
                )

        ),
        Map(
            "first" ->
                Map(
                    "a" -> "one",
                    "b" -> "two",
                    "c" -> "three",
                    "d" -> "four",
                    "e" -> "five",
                    "f" -> "six"
                ),
            "second" ->
                Map(
                    "a" -> "one",
                    "b" -> "two",
                    "c" -> "three",
                    "d" -> "four",
                    "e" -> "five",
                    "f" -> "six"
                )

        )
    )

    val doc = any(testList)
    println(pretty(doc))
    */
}
