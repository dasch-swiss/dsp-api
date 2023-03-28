package org.knora.webapi.slice.admin.domain.service
import zio.Task

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.common.service.Repository
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

trait KnoraProjectRepo extends Repository[KnoraProject, InternalIri] {
  def findByProjectIdentifier(id: ProjectIdentifierADM): Task[Option[KnoraProject]]
}
