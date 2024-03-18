/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.knora.webapi.slice.admin.domain.model.Group
import zio.ZLayer

final case class GroupsService(
  private val groupRepo: KnoraGroupRepo,
  projectADMService: ProjectADMService,
) {

  def findAll(): Unit = {
    ZIO.foreach(knoraGroupRepo.findAll()) { knoraGroup =>
      val projectADM = projectADMService.findById(knoraGroup.projectIri)
      Group(
        id = knoraGroup.id,
        name = knoraGroup.name,
        descriptions = knoraGroup.descriptions,
        project = projectADM,
        status = knoraGroup.status,
        selfjoin = knoraGroup.selfjoin,
      )
    }
  }
}

object GroupsService {
  def layer = ZLayer.derive[GroupsService]
}
