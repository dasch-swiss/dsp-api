/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.domain

import zio.Task
import zio.macros.accessible

@accessible
trait ResourceInfoRepo {
  def findByProjectAndResourceClass(
    projectIri: InternalIri,
    resourceClass: InternalIri
  ): Task[List[ResourceInfo]]
}
