/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import org.knora.webapi.config.Sipi

object TestDataFileUtil {

  private val iiifBaseUrl: String = "IIIF_BASE_URL"

  def readTestData(folder: String, filename: String, sipi: Sipi): String =
    val path    = resolvePath(folder, filename)
    val content = FileUtil.readTextFile(path)
    content.replaceAll(iiifBaseUrl, sipi.externalBaseUrl)

  private def resolvePath(folder: String, filename: String): Path =
    val file = Paths.get(folder, filename)
    Paths.get("test_data", "generated_test_data").resolve(file).normalize()

  def writeTestData(folder: String, filename: String, sipi: Sipi, content: String): Path = {
    val path = resolvePath(folder, filename)
    Files.createDirectories(path.getParent)
    val contentWithSipiUrl = content.replaceAll(sipi.externalBaseUrl, iiifBaseUrl)
    FileUtil.writeTextFile(path, contentWithSipiUrl)
  }
}
