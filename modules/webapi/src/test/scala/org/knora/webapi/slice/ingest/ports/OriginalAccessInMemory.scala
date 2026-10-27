/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.ports

import zio.*

import org.knora.webapi.slice.ingest.domain.ProjectShortcode
import org.knora.webapi.slice.ingest.ports.OriginalAccessInMemory.Call

/** Decides from the token alone and records every question it was asked. */
final class OriginalAccessInMemory(decide: Option[String] => Task[Boolean], calls: Ref[List[Call]])
    extends OriginalAccess {

  override def isOriginalGranted(
    token: Option[String],
    shortcode: ProjectShortcode,
    derivativeFilename: String,
  ): Task[Boolean] =
    calls.update(_ :+ Call(token, shortcode, derivativeFilename)) *> decide(token)

  def getCalls: UIO[List[Call]] = calls.get
}

object OriginalAccessInMemory {
  final case class Call(token: Option[String], shortcode: ProjectShortcode, derivativeFilename: String)

  def layer(decide: Option[String] => Task[Boolean]): ULayer[OriginalAccessInMemory & OriginalAccess] =
    ZLayer.fromZIO(Ref.make(List.empty[Call]).map(OriginalAccessInMemory(decide, _))) >+>
      ZLayer.fromFunction((inMemory: OriginalAccessInMemory) => inMemory: OriginalAccess)

  val grantAll: ULayer[OriginalAccessInMemory & OriginalAccess]    = layer(_ => ZIO.succeed(true))
  val withholdAll: ULayer[OriginalAccessInMemory & OriginalAccess] = layer(_ => ZIO.succeed(false))
  val failAll: ULayer[OriginalAccessInMemory & OriginalAccess]     =
    layer(_ => ZIO.fail(IllegalStateException("the token does not authenticate")))
}
