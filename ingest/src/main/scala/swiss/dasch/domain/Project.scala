/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.{Refined, RefinedTypeOps}
import eu.timepit.refined.string.MatchesRegex
import zio.json.JsonCodec
import zio.schema.Schema

import java.time.Instant

final case class Project(id: ProjectId, shortcode: ProjectShortcode, createdAt: Instant)

type ProjectId = PositiveInt
object ProjectId extends RefinedTypeOps[ProjectId, Int] {
  extension (i: Int) {
    inline def toProjectIdUnsafe = ProjectId.unsafeFrom(i)
    inline def toProjectId       = ProjectId.from(i)
  }
}

type ProjectShortcode = String Refined MatchesRegex["""^\p{XDigit}{4,4}$"""]
object ProjectShortcode extends RefinedTypeOps[ProjectShortcode, String] {

  override def from(str: String): Either[String, ProjectShortcode] = super.from(str.toUpperCase)

  extension (s: String) {
    def toShortcodeUnsafe: ProjectShortcode           = ProjectShortcode.unsafeFrom(s)
    def toShortcode: Either[String, ProjectShortcode] = ProjectShortcode.from(s)
  }

  given schema: Schema[ProjectShortcode]   = Schema[String].transformOrFail(ProjectShortcode.from, id => Right(id.value))
  given codec: JsonCodec[ProjectShortcode] = JsonCodec[String].transformOrFail(ProjectShortcode.from, _.value)
}
