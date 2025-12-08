package org.knora.webapi.slice.api
import zio.*

import org.knora.webapi.slice.admin.api.AdminApiServerEndpoints
import org.knora.webapi.slice.api.v2.ApiV2ServerEndpoints
import org.knora.webapi.slice.api.v3.ApiV3ServerEndpoints
import org.knora.webapi.slice.infrastructure.api.ManagementServerEndpoints
import org.knora.webapi.slice.shacl.api.ShaclServerEndpoints

object ApiModule {

  type Dependencies =
    AdminApiServerEndpoints & ApiV2ServerEndpoints & ApiV3ServerEndpoints & ShaclServerEndpoints &
      ManagementServerEndpoints

  type Provided = Endpoints

  val layer: URLayer[Dependencies, Provided] = Endpoints.layer

}
