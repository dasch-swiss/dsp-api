/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2

import sttp.tapir.ztapir.ZServerEndpoint
import zio.ZLayer

import org.knora.webapi.slice.lists.api.ListsV2ServerEndpoints
import org.knora.webapi.slice.ontology.api.OntologiesServerEndpoints
import org.knora.webapi.slice.resources.api.ResourceInfoServerEndpoints
import org.knora.webapi.slice.resources.api.ResourcesApiServerEndpoints
import org.knora.webapi.slice.search.api.SearchServerEndpoints
import org.knora.webapi.slice.security.api.AuthenticationServerEndpoints

class ApiV2ServerEndpoints(
  private val authenticationServerEndpoints: AuthenticationServerEndpoints,
  private val listsV2ServerEndpoints: ListsV2ServerEndpoints,
  private val ontologiesServerEndpoints: OntologiesServerEndpoints,
  private val resourceInfoServerEndpoints: ResourceInfoServerEndpoints,
  private val resourcesApiServerEndpoints: ResourcesApiServerEndpoints,
  private val searchServerEndpoints: SearchServerEndpoints,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] =
    authenticationServerEndpoints.serverEndpoints ++
      listsV2ServerEndpoints.serverEndpoints ++
      resourceInfoServerEndpoints.serverEndpoints ++
      resourcesApiServerEndpoints.serverEndpoints ++
      searchServerEndpoints.serverEndpoints ++
      ontologiesServerEndpoints.serverEndpoints
}
object ApiV2ServerEndpoints {
  val layer = ZLayer.derive[ApiV2ServerEndpoints]
}
