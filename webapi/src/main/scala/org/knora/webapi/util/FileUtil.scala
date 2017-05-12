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

import java.io.{ByteArrayOutputStream, File}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.zip.{ZipEntry, ZipOutputStream}

import resource._

import scala.io.{Codec, Source}

/**
  * Functions for reading and writing files.
  */
object FileUtil {
    /**
      * Writes a string to a file.
      *
      * @param file    the destination file.
      * @param content the string to write.
      */
    def writeTextFile(file: File, content: String): Unit = {
        Files.write(Paths.get(file.getCanonicalPath), content.getBytes(StandardCharsets.UTF_8))
    }

    /**
      * Reads a text file into a string.
      *
      * @param file the source file.
      * @return the contents of the file.
      */
    def readTextFile(file: File): String = {
        Source.fromFile(file)(Codec.UTF8).mkString
    }

    /**
      * Writes a byte array to a file.
      *
      * @param file    the destination file.
      * @param content the binary data to write.
      */
    def writeBinaryFile(file: File, content: Array[Byte]): Unit = {
        Files.write(Paths.get(file.getCanonicalPath), content)
    }

    /**
      * Generates a byte array representing a Zip file containing the specified data. The Zip file data is
      * generated in memory only; no disk access is performed.
      *
      * @param contents a map of file names to byte arrays representing file contents.
      * @return a byte array containing the Zip file data.
      */
    def createZipFileBytes(contents: Map[String, Array[Byte]]): Array[Byte] = {
        val managedBytes: ExtractableManagedResource[Array[Byte]] = managed(new ByteArrayOutputStream()).map {
            byteArrayOutputStream =>
                for (zipOutputStream <- managed(new ZipOutputStream(byteArrayOutputStream))) {
                    contents.foreach {
                        case (filename: String, content: Array[Byte]) =>
                            val entry: ZipEntry = new ZipEntry(filename)
                            zipOutputStream.putNextEntry(entry)
                            zipOutputStream.write(content)
                            zipOutputStream.closeEntry()
                    }
                }

                byteArrayOutputStream.toByteArray
        }

        managedBytes.tried.get
    }
}
