/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.apache.commons.io.FilenameUtils
import zio.nio.file.Path

object PathOps {
  extension (path: Path) {
    def fileExtension: String =
      Option(FilenameUtils.getExtension(path.filename.toString)).getOrElse("")
  }
}
