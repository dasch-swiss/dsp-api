/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e

import com.typesafe.scalalogging.LazyLogging

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import org.knora.webapi.config.AppConfig

/**
 * Collects E2E test requests and responses for use as client test data.
 *
 * @param settings the application settings.
 */
class ClientTestDataCollector(appConfig: AppConfig) extends LazyLogging {

  // write the client test data into this folder
  private val tempClientTestDataFolder: Path = Path.of("/tmp/client_test_data", "test-data")

  // Are we configured to collect client test data?
  private val collectClientTestData = appConfig.clientTestDataService.collectClientTestData
  logger.debug(s"collectClientTestData: $collectClientTestData")

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
    logger.debug(s"writeFile: filename: $filename")
    val fullPath = tempClientTestDataFolder.resolve(filename)
    logger.debug(s"writeFile: fullPath: $fullPath")
    Files.createDirectories(fullPath.getParent())
    Files.createFile(fullPath)
    Files.write(fullPath, s.getBytes(StandardCharsets.UTF_8))
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
