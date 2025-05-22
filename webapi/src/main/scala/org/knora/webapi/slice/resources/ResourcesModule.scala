package org.knora.webapi.slice.resources

import zio.URLayer

import org.knora.webapi.slice.URModule
import org.knora.webapi.slice.admin.AdminModule
import org.knora.webapi.slice.common.BaseModule
import org.knora.webapi.slice.infrastructure.InfrastructureModule
import org.knora.webapi.slice.ontology.CoreModule
import org.knora.webapi.slice.resources.service.ResourcesMetadataService

object ResourcesModule
    extends URModule[
      AdminModule.Provided & BaseModule.Provided & CoreModule.Provided & InfrastructureModule.Provided,
      ResourcesMetadataService,
    ] {
  override val layer: URLayer[Dependencies, Provided] = ResourcesMetadataService.layer
}
