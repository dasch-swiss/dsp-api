/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2
import sttp.tapir.ztapir.ZServerEndpoint
import zio.ZLayer

import org.knora.webapi.slice.api.v2.authentication.AuthenticationServerEndpoints
import org.knora.webapi.slice.api.v2.lists.ListsV2ServerEndpoints
import org.knora.webapi.slice.api.v2.ontologies.OntologiesServerEndpoints
import org.knora.webapi.slice.api.v2.search.SearchServerEndpoints
import org.knora.webapi.slice.resources.api.ResourceInfoServerEndpoints
import org.knora.webapi.slice.resources.api.ResourcesApiServerEndpoints

class ApiV2ServerEndpoints(
  private val authenticationServerEndpoints: AuthenticationServerEndpoints,
  private val listsV2ServerEndpoints: ListsV2ServerEndpoints,
  private val ontologiesServerEndpoints: OntologiesServerEndpoints,
  private val resourceInfoServerEndpoints: ResourceInfoServerEndpoints,
  private val resourcesApiServerEndpoints: ResourcesApiServerEndpoints,
  private val searchServerEndpoints: SearchServerEndpoints,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] =
    (authenticationServerEndpoints.serverEndpoints ++
      listsV2ServerEndpoints.serverEndpoints ++
      resourceInfoServerEndpoints.serverEndpoints ++
      resourcesApiServerEndpoints.serverEndpoints ++
      searchServerEndpoints.serverEndpoints ++
      ontologiesServerEndpoints.serverEndpoints)
      .map(_.tag("API v2"))
}
object ApiV2ServerEndpoints {
  val layer = ZLayer.derive[ApiV2ServerEndpoints]
}
