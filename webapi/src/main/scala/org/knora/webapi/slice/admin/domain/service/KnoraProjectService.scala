package org.knora.webapi.slice.admin.domain.service

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

final case class KnoraProjectService(knoraProjectRepo: KnoraProjectRepo) {

  def findById(id: ProjectIri) = knoraProjectRepo.findById(id)
}

object KnoraProjectService {
  val layer = zio.ZLayer.derive[KnoraProjectService]
}
