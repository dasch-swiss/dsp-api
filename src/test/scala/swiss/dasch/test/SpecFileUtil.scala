/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.test

import zio.nio.file.Path

object SpecFileUtil {
  def pathFromResource(resource: String): Path = Path(getClass.getResource(resource).getPath)
}
