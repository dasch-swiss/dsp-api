/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Task
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.RestrictedView

final case class KnoraProjectService(knoraProjectRepo: KnoraProjectRepo) {
  def findById(id: ProjectIri): Task[Option[KnoraProject]]           = knoraProjectRepo.findById(id)
  def findById(id: ProjectIdentifierADM): Task[Option[KnoraProject]] = knoraProjectRepo.findById(id)
  def findByShortcode(code: Shortcode): Task[Option[KnoraProject]]   = knoraProjectRepo.findByShortcode(code)
  def findAll(): Task[List[KnoraProject]]                            = knoraProjectRepo.findAll()
  def setProjectRestrictedView(project: KnoraProject, settings: RestrictedView): Task[RestrictedView] = {
    val newSettings = settings match {
      case RestrictedView.Watermark(false) => RestrictedView.default
      case s                               => s
    }
    knoraProjectRepo.save(project.copy(restrictedView = newSettings)).as(newSettings)
  }
}

object KnoraProjectService {
  val layer = ZLayer.derive[KnoraProjectService]
}
