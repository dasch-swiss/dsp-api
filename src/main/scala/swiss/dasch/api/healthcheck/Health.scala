/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.healthcheck

sealed trait Health {
  self =>
  def isHealthy: Boolean = self match
    case UP   => true
    case DOWN => false
}

case object UP extends Health

case object DOWN extends Health

object Health {
  def fromBoolean(bool: Boolean): Health = if (bool) UP else DOWN
}
