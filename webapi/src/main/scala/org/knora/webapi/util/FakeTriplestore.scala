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
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import akka.event.LoggingAdapter
import org.apache.commons.io.FileUtils

import scala.collection.breakOut
import scala.io.{Codec, Source}

/**
  * A fake triplestore for use in performance testing. This feature is activated in `application.conf`.
  */
object FakeTriplestore {

    private var fakeTriplestoreDir: Option[File] = None

    private var queryNum = 0

    var data = Map.empty[String, String]

    def init(dataDir: File) = {
        fakeTriplestoreDir = Some(dataDir)
    }

    def clear() = {
        FileUtils.deleteDirectory(fakeTriplestoreDir.get)
        fakeTriplestoreDir.get.mkdirs()
        ()
    }

    def load() = {
        val dataToWrap: Map[String, String] = fakeTriplestoreDir.get.listFiles.map {
            queryDir =>
                val sparql = readFile(queryDir.listFiles.filter(_.getName.endsWith(".rq")).head)
                val result = readFile(queryDir.listFiles.filter(_.getName.endsWith(".json")).head)
                (sparql, result)
        }(breakOut)
        data = new ErrorHandlingMap(dataToWrap, { key: String => s"No result has been stored in the fake triplestore for this query: $key" })
    }

    def add(sparql: String, result: String, log: LoggingAdapter): Unit = {
        this.synchronized {
            log.info("Collecting data for fake triplestore")
            val paddedQueryNum = f"$queryNum%04d"
            val queryDir = new File(fakeTriplestoreDir.get, paddedQueryNum)
            queryDir.mkdirs()
            val sparqlFile = new File(queryDir, s"query-$paddedQueryNum.rq")
            writeFile(sparqlFile, sparql)
            val resultFile = new File(queryDir, s"response-$paddedQueryNum.json")
            writeFile(resultFile, result)
            queryNum += 1
        }
    }

    private def writeFile(file: File, content: String) = {
        Files.write(Paths.get(file.getCanonicalPath), content.getBytes(StandardCharsets.UTF_8))
    }

    private def readFile(file: File) = {
        Source.fromFile(file)(Codec.UTF8).mkString
    }
}
