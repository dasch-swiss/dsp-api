/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.ports

import zio.*

import org.knora.webapi.slice.ingest.domain.ProjectShortcode

/** Grants the Original to every caller whose token is in `granted`; an absent token is granted when `anonymous`. */
final class OriginalAccessInMemory(granted: Set[String], anonymous: Boolean) extends OriginalAccess {
  override def isOriginalGranted(
    token: Option[String],
    shortcode: ProjectShortcode,
    derivativeFilename: String,
  ): Task[Boolean] =
    ZIO.succeed(token.fold(anonymous)(granted.contains))
}

object OriginalAccessInMemory {
  def layer(granted: Set[String] = Set.empty, anonymous: Boolean = false): ULayer[OriginalAccess] =
    ZLayer.succeed(OriginalAccessInMemory(granted, anonymous))

  val grantAll: ULayer[OriginalAccess] = ZLayer.succeed(new OriginalAccess {
    override def isOriginalGranted(token: Option[String], shortcode: ProjectShortcode, filename: String) =
      ZIO.succeed(true)
  })
}
