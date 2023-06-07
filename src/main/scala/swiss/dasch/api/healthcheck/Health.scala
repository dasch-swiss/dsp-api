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
