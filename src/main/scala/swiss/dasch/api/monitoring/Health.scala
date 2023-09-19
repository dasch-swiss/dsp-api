/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.monitoring

import zio.json.{ DeriveJsonEncoder, JsonEncoder }

sealed trait Status { self =>
  private def jsonString: String = {
    val str = self.getClass.getSimpleName
    if (str.endsWith("$")) { str.dropRight(1) }
    else { str }
  }
}
case object UP extends Status
case object DOWN extends Status

object Status {
  given encoder: JsonEncoder[Status] = JsonEncoder[String].contramap(_.jsonString)
}

final case class Health(status: Status)

object Health {
  given encoder: JsonEncoder[Health] = DeriveJsonEncoder.gen[Health]
  def up(): Health                   = Health(UP)
  def down(): Health                 = Health(DOWN)
}
