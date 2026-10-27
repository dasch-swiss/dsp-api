/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.domain

import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.RefinedTypeOps
import eu.timepit.refined.string.MatchesRegex
import zio.json.JsonCodec
import zio.schema.Schema

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
