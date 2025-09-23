/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.routing
import org.knora.webapi.slice.admin.api.AdminApiServerEndpoints
import org.knora.webapi.slice.infrastructure.api.ManagementServerEndpoints
import org.knora.webapi.slice.lists.api.ListsV2ServerEndpoints
import org.knora.webapi.slice.ontology.api.OntologiesServerEndpoints
import org.knora.webapi.slice.resources.api.ResourceInfoServerEndpoints
import org.knora.webapi.slice.resources.api.ResourcesApiServerEndpoints
import org.knora.webapi.slice.search.api.SearchServerEndpoints
import org.knora.webapi.slice.security.api.AuthenticationServerEndpoints
import org.knora.webapi.slice.shacl.api.ShaclServerEndpoints

final case class Endpoints(
  adminApiServerEndpoints: AdminApiServerEndpoints,
  authenticationServerEndpoints: AuthenticationServerEndpoints,
  listsV2ServerEndpoints: ListsV2ServerEndpoints,
  resourceInfoServerEndpoints: ResourceInfoServerEndpoints,
  resourcesApiServerEndpoints: ResourcesApiServerEndpoints,
  searchServerEndpoints: SearchServerEndpoints,
  shaclServerEndpoints: ShaclServerEndpoints,
  managementServerEndpoints: ManagementServerEndpoints,
  ontologiesServerEndpoints: OntologiesServerEndpoints,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] =
    adminApiServerEndpoints.serverEndpoints ++
      authenticationServerEndpoints.serverEndpoints ++
      listsV2ServerEndpoints.serverEndpoints ++
      managementServerEndpoints.serverEndpoints ++
      ontologiesServerEndpoints.serverEndpoints ++
      resourceInfoServerEndpoints.serverEndpoints ++
      resourcesApiServerEndpoints.serverEndpoints ++
      searchServerEndpoints.serverEndpoints ++
      shaclServerEndpoints.serverEndpoints
}
object Endpoints {
  val layer = ZLayer.derive[Endpoints]
}
