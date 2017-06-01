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

import akka.event.LoggingAdapter
import org.apache.commons.io.FileUtils

/**
  * A fake triplestore for use in performance testing. This feature is activated in `application.conf`.
  */
object FakeTriplestore {

    private var fakeTriplestoreDir: Option[File] = None

    private var queryNum = 0

    var data = Map.empty[String, String]

    def init(dataDir: File): Unit = {
        fakeTriplestoreDir = Some(dataDir)
    }

    def clear(): Unit = {
        FileUtils.deleteDirectory(fakeTriplestoreDir.get)
        fakeTriplestoreDir.get.mkdirs()
        ()
    }

    def load(): Unit = {
        val dataToWrap = fakeTriplestoreDir.get.listFiles.map {
            queryDir =>
                val sparql = FileUtil.readTextFile(queryDir.listFiles.filter(_.getName.endsWith(".rq")).head)
                val result = FileUtil.readTextFile(queryDir.listFiles.filter(_.getName.endsWith(".json")).head)
                sparql -> result
        }.toMap

        data = new ErrorHandlingMap(dataToWrap, { key: String => s"No result has been stored in the fake triplestore for this query: $key" })
    }

    def add(sparql: String, result: String, log: LoggingAdapter): Unit = {
        this.synchronized {
            log.info("Collecting data for fake triplestore")
            val paddedQueryNum = f"$queryNum%04d"
            val queryDir = new File(fakeTriplestoreDir.get, paddedQueryNum)
            queryDir.mkdirs()
            val sparqlFile = new File(queryDir, s"query-$paddedQueryNum.rq")
            FileUtil.writeTextFile(sparqlFile, sparql)
            val resultFile = new File(queryDir, s"response-$paddedQueryNum.json")
            FileUtil.writeTextFile(resultFile, result)
            queryNum += 1
        }
    }

}
