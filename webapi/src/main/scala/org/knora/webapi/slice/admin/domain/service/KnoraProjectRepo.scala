/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service
import zio.Task

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.common.service.Repository
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

trait KnoraProjectRepo extends Repository[KnoraProject, InternalIri] {
  def findById(id: ProjectIdentifierADM): Task[Option[KnoraProject]]
}
