/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.infrastructure

import zio.json.DeriveJsonCodec
import zio.json.JsonCodec
import zio.json.JsonEncoder

import scala.util.Try

import org.knora.webapi.slice.ingest.infrastructure.Health.Status
import org.knora.webapi.slice.ingest.infrastructure.Health.Status.*

final case class Health(status: Status) {
  def isHealthy: Boolean = status == UP

  def aggregate(other: Health): Health =
    (this, other) match {
      case (Health(Status.UP), Health(Status.UP)) => Health.up
      case _                                      => Health.down
    }
}

object Health {
  enum Status {
    case UP   extends Status
    case DOWN extends Status
  }

  object Status {
    given encoder: JsonCodec[Status] = JsonCodec[String].transformOrFail(
      str => Try(Status.valueOf(str)).toEither.left.map(_.getMessage),
      _.toString,
    )
  }

  given encoder: JsonCodec[Health] = DeriveJsonCodec.gen[Health]
  val up: Health                   = Health(UP)
  val down: Health                 = Health(DOWN)
}
