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
