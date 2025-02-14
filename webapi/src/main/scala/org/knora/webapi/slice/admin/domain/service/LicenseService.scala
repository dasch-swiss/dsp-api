/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service
import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.admin.repo.LicenseRepo

case class LicenseService(repo: LicenseRepo) {

  /**
   * Currently, the project is not used in the implementation and all allowed licenses are returned.
   *
   * @param id the Project for which the licenses are retrieved.
   * @return Returns the licenses available in the project.
   */
  def findByProjectId(id: ProjectIri): UIO[Chunk[License]] =
    repo.findAll().orDie
}

object LicenseService {
  val layer = ZLayer.derive[LicenseService]
}
