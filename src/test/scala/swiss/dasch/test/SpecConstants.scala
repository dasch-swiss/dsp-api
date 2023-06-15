/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.test

import swiss.dasch.domain.ProjectShortcode

object SpecConstants {

  val nonExistentProject: ProjectShortcode = "0042".toProjectShortcode
  val existingProject: ProjectShortcode    = "0001".toProjectShortcode
  val emptyProject: ProjectShortcode       = "0002".toProjectShortcode

  extension (s: String) {
    def toProjectShortcode: ProjectShortcode = ProjectShortcode
      .make(s)
      .fold(err => throw new IllegalArgumentException(err), identity)
  }
}
