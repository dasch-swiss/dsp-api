package org.knora.webapi.slice.admin.domain.service
import zio.Task

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.slice.common.service.Repository
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

trait ProjectRepo extends Repository[ProjectADM, InternalIri] {
  def findByProjectIdentifier(id: ProjectIdentifierADM): Task[Option[ProjectADM]]
}
