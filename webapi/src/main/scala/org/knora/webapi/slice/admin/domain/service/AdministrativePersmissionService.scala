package org.knora.webapi.slice.admin.domain.service

import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionRepo
import zio.ZLayer

final case class AdministrativePermissionService(repo: AdministrativePermissionRepo) {}

object AdministrativePermissionService {
  val layer = ZLayer.derive[AdministrativePermissionService]
}
