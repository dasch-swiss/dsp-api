/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.domain

import zio.Task

import java.time.Instant

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.domain.InternalIri

trait ResourceInfoRepo {

  /**
   * Lists the resources of a project, deleted ones included.
   *
   * @param resourceClass when given, only resources of exactly this class (no subclasses)
   * @param cutoff        when given, only resources whose `lastModificationDate`, else `creationDate`, is at or after it
   */
  def find(
    projectIri: ProjectIri,
    resourceClass: Option[InternalIri],
    cutoff: Option[Instant],
  ): Task[List[ResourceInfo]]
}
