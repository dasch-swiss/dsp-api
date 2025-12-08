package org.knora.webapi.slice.api
import zio.*

import org.knora.webapi.slice.admin.api.AdminApiServerEndpoints
import org.knora.webapi.slice.api.v2.ApiV2Module
import org.knora.webapi.slice.api.v3.ApiV3Module
import org.knora.webapi.slice.api.v3.ApiV3ServerEndpoints
import org.knora.webapi.slice.infrastructure.api.ManagementServerEndpoints
import org.knora.webapi.slice.shacl.api.ShaclServerEndpoints

object ApiModule {

  type Dependencies =
    // format: off
    AdminApiServerEndpoints &
    ApiV2Module.Dependencies &
    ApiV3Module.Dependencies &
    ManagementServerEndpoints &
    ShaclServerEndpoints
    // format: on

  type Provided = Endpoints

  val layer: URLayer[Dependencies, Provided] =
    (ApiV2Module.layer <*> ApiV3Module.layer)
      >>> Endpoints.layer
}
