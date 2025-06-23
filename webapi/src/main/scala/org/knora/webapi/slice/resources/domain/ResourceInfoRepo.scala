/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.domain

import zio.Task

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

trait ResourceInfoRepo {
  def findByProjectAndResourceClass(projectIri: ProjectIri, resourceClass: InternalIri): Task[List[ResourceInfo]]
}
