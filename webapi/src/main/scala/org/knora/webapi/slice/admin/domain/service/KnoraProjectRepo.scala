/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service
import zio.Task

import dsp.valueobjects.RestrictedViewSize
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.repo.service.Repository

trait KnoraProjectRepo extends Repository[KnoraProject, ProjectIri] {
  def findById(id: ProjectIdentifierADM): Task[Option[KnoraProject]]
  def findByShortcode(code: Shortcode): Task[Option[KnoraProject]] =
    findById(ProjectIdentifierADM.ShortcodeIdentifier(code))
  def setProjectRestrictedViewSize(project: KnoraProject, size: RestrictedViewSize): Task[Unit]
}
