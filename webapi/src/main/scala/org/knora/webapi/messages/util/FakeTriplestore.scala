/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import java.nio.file.{Files, Path}

import akka.event.LoggingAdapter
import org.apache.commons.io.FileUtils
import org.knora.webapi.util.FileUtil

import scala.jdk.CollectionConverters._

/**
 * A fake triplestore for use in performance testing. This feature is activated in `application.conf`.
 */
object FakeTriplestore {

  private var fakeTriplestoreDir: Option[Path] = None

  private var queryNum = 0

  var data = Map.empty[String, String]

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
