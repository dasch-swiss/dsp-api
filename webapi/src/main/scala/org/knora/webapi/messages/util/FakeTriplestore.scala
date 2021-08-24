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

package org.knora.webapi.messages.util

import akka.event.LoggingAdapter
import org.apache.commons.io.FileUtils
import org.knora.webapi.util.FileUtil

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/**
 * A fake triplestore for use in performance testing. This feature is activated in `application.conf`.
 */
object FakeTriplestore {

  var data                                     = Map.empty[String, String]
  private var fakeTriplestoreDir: Option[Path] = None
  private var queryNum                         = 0

  def init(dataDir: Path): Unit =
    fakeTriplestoreDir = Some(dataDir)

  def clear(): Unit = {
    FileUtils.deleteDirectory(fakeTriplestoreDir.get.toFile)
    Files.createDirectories(fakeTriplestoreDir.get)
    ()
  }

  def load(): Unit = {
    val dataToWrap = Files
      .newDirectoryStream(fakeTriplestoreDir.get)
      .asScala
      .map { queryDir =>
        val sparql = FileUtil.readTextFile(
          Files
            .newDirectoryStream(queryDir)
            .asScala
            .filter(_.getFileName.toString.endsWith(".rq"))
            .head
        )
        val result = FileUtil.readTextFile(
          Files
            .newDirectoryStream(queryDir)
            .asScala
            .filter(_.getFileName.toString.endsWith(".json"))
            .head
        )
        sparql -> result
      }
      .toMap

    data = new ErrorHandlingMap(
      dataToWrap,
      { key: String =>
        s"No result has been stored in the fake triplestore for this query: $key"
      }
    )
  }

  def add(sparql: String, result: String, log: LoggingAdapter): Unit =
    this.synchronized {
      log.info("Collecting data for fake triplestore")
      val paddedQueryNum = f"$queryNum%04d"
      val queryDir       = fakeTriplestoreDir.get.resolve(paddedQueryNum)
      Files.createDirectories(queryDir)
      val sparqlFile = queryDir.resolve(s"query-$paddedQueryNum.rq")
      FileUtil.writeTextFile(sparqlFile, sparql)
      val resultFile = queryDir.resolve(s"response-$paddedQueryNum.json")
      FileUtil.writeTextFile(resultFile, result)
      queryNum += 1
    }
}
