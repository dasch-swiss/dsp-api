/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e

import org.knora.webapi.exceptions.TestConfigurationException
import org.knora.webapi.settings.KnoraSettingsImpl
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}
import java.io.File
import java.io.BufferedWriter
import java.io.FileWriter
import java.nio.file.Paths
import java.nio.file.Path
import com.typesafe.scalalogging.LazyLogging

/**
 * Collects E2E test requests and responses for use as client test data.
 *
 * @param settings the application settings.
 */
class ClientTestDataCollector(settings: KnoraSettingsImpl) extends LazyLogging {

  // write the client test data into this folder
  private val folderName: String = "client-test-data"

  // Are we configured to collect client test data?
  private val collectClientTestData = settings.collectClientTestData
  println(s"==============> $collectClientTestData")

  /**
   * Stores a client test data file.
   *
   * @param fileContent the content of the file to be stored.
   */
  def addFile(fileContent: TestDataFileContent): Unit =
    if (collectClientTestData) {
      // If configured to write client test data, then write to folder
      writeFile(fileContent.filePath.toString, fileContent.text)
    }


  /**
   * write a `String` to the `filename`.
   */
  private def writeFile(filename: String, s: String): Unit = {
    println("ClientTestDataCollector - writeFile: filename: " + filename)
    val file = new File(Path.of(folderName, filename).toString())
    println("ClientTestDataCollector - writeFile: file: " + file.toString())
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(s)
    bw.close()
}
}

/**
 * Represents a file containing generated client API test data.
 *
 * @param filePath the file path in which the test data should be saved.
 * @param text     the source code.
 */
case class TestDataFileContent(filePath: TestDataFilePath, text: String)

/**
 * Represents the filesystem path of a file containing generated test data.
 *
 * @param directoryPath the path of the directory containing the file,
 *                      relative to the root directory of the source tree.
 * @param filename      the filename, without the file extension.
 * @param fileExtension the file extension.
 */
case class TestDataFilePath(directoryPath: Seq[String], filename: String, fileExtension: String) {
  override def toString: String =
    (directoryPath :+ filename + "." + fileExtension).mkString("/")
}
