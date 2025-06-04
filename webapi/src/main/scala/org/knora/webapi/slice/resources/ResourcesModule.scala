/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources

import zio.URLayer

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.resources.service.MetadataService
import org.knora.webapi.store.triplestore.api.TriplestoreService

object ResourcesModule { self =>
  type Dependencies = KnoraProjectService & TriplestoreService & StringFormatter
  type Provided     = MetadataService
  val layer: URLayer[Dependencies, Provided] = MetadataService.layer
}
