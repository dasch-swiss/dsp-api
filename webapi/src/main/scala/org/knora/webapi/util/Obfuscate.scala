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

import java.io.File

import scala.io.Source

object Obfuscate extends App {

    val outputPath: File = new File("_test_data/all_data/output/")
    // make sure the otput directory is there
    outputPath.mkdirs()

    val paths = List(Source.fromFile("_test_data/all_data/dokubib-data.ttl"), Source.fromFile("_test_data/all_data/dokubib-data.ttl"))

    for (path <- paths) {
        val iter = path.getLines()

        val line = iter.next()
        if (line.contains("knora-base:valueHasString")) {
            // find first """
            // get text after first """
            // search for closing """
            // if opening and closing """ are on the same line, then obfuscate and write out line
            // if opening and closing are not on the same line, then save and look for closing """ on later lines, after finding all text obfuscate and write out multi line
        } else {
            // write out as is
        }

        if (line.contains("rdfs-label")) {}
    }

}