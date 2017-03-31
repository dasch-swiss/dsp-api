/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import scala.io.{Codec, Source}

/**
  * Functions for reading and writing files.
  */
object FileUtil {
    /**
      * Writes a string to a file.
      *
      * @param file the destination file.
      * @param content the string to write.
      */
    def writeFile(file: File, content: String): Unit = {
        Files.write(Paths.get(file.getCanonicalPath), content.getBytes(StandardCharsets.UTF_8))
    }

    /**
      * Reads a file into a string.
      *
      * @param file the source file.
      * @return the contents of the file.
      */
    def readFile(file: File): String = {
        Source.fromFile(file)(Codec.UTF8).mkString
    }
}
