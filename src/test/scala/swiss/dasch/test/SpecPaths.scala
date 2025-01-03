/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.test

import zio.nio.file.Path

object SpecPaths {
  def pathFromResource(resource: String): Path =
    Path(getClass.getClassLoader.getResource(resource).getPath)

  val testFolder: Path   = pathFromResource("test-folder-structure")
  val testZip: Path      = pathFromResource("test-import.zip")
  val testTextFile: Path = pathFromResource("test.txt")
}
