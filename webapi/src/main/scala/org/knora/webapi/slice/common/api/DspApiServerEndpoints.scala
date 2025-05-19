/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.admin.api.AdminApiServerEndpoints
import org.knora.webapi.slice.infrastructure.api.ManagementServerEndpoints
import org.knora.webapi.slice.lists.api.ListsServerEndpointsV2
import org.knora.webapi.slice.ontology.api.OntologiesServerEndpoints
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoServerEndpoints
import org.knora.webapi.slice.resources.api.ResourcesApiServerEndpoints
import org.knora.webapi.slice.search.api.SearchServerEndpoints
import org.knora.webapi.slice.security.api.AuthenticationServerEndpointsV2
import org.knora.webapi.slice.shacl.api.ShaclServerEndpoints

final case class DspApiServerEndpoints(
  private val adminApi: AdminApiServerEndpoints,
  private val authentication: AuthenticationServerEndpointsV2,
  private val listsApiV2: ListsServerEndpointsV2,
  private val management: ManagementServerEndpoints,
  private val ontologies: OntologiesServerEndpoints,
  private val resourceInfo: ResourceInfoServerEndpoints,
  private val resourcesApi: ResourcesApiServerEndpoints,
  private val search: SearchServerEndpoints,
  private val shacl: ShaclServerEndpoints,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] =
    adminApi.serverEndpoints ++
      authentication.serverEndpoints ++
      listsApiV2.serverEndpoints ++
      management.serverEndpoints ++
      ontologies.serverEndpoints ++
      resourceInfo.serverEndpoints ++
      resourcesApi.serverEndpoints ++
      search.serverEndpoints ++
      shacl.serverEndpoints
}

object DspApiServerEndpoints {
  val layer = ZLayer.derive[DspApiServerEndpoints]
}
