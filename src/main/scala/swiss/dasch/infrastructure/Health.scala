/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import zio.json.{DeriveJsonCodec, JsonCodec, JsonDecoder, JsonEncoder}

sealed trait Status { self =>
  private def jsonString: String = {
    val str = self.getClass.getSimpleName
    if (str.endsWith("$")) { str.dropRight(1) }
    else { str }
  }
}

object Status {
  case object UP   extends Status
  case object DOWN extends Status
  given codec: JsonCodec[Status] = {
    val encoder: JsonEncoder[Status] = JsonEncoder[String].contramap(_.jsonString)
    val decoder: JsonDecoder[Status] = JsonDecoder[String].map {
      case "UP"   => UP
      case "DOWN" => DOWN
    }

    new JsonCodec[Status](encoder, decoder)
  }
}

trait HealthResponse {
  def status: Status
  def isHealthy: Boolean = status == Status.UP
}
final case class Health(status: Status) extends HealthResponse

object Health {
  given codec: JsonCodec[Health] = DeriveJsonCodec.gen[Health]
  def up(): Health               = Health(Status.UP)
  def down(): Health             = Health(Status.DOWN)
}
