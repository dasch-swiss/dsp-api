/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.domain

import zio.Task

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

trait ResourceInfoRepo {
  def findByProjectAndResourceClass(projectIri: ProjectIri, resourceClass: InternalIri): Task[List[ResourceInfo]]
}
