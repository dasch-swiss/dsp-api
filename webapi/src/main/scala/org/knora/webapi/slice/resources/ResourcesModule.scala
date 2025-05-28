/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources

import zio.URLayer

import org.knora.webapi.slice.URModule
import org.knora.webapi.slice.admin.AdminModule
import org.knora.webapi.slice.common.BaseModule
import org.knora.webapi.slice.ontology.CoreModule
import org.knora.webapi.slice.resources.service.MetadataService

object ResourcesModule
    extends URModule[
      AdminModule.Provided & BaseModule.Provided & CoreModule.Provided,
      MetadataService,
    ] {
  override val layer: URLayer[Dependencies, Provided] = MetadataService.layer
}
