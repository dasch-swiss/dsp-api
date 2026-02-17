/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.domain

import zio.nio.file.Path

enum PayloadEntry {
  case File(relativePath: String, sourcePath: Path)
  case Directory(prefix: String, sourcePath: Path)
}
