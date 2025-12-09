/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2
import sttp.tapir.ztapir.ZServerEndpoint
import zio.ZLayer

import org.knora.webapi.slice.api.v2.authentication.AuthenticationServerEndpoints
import org.knora.webapi.slice.api.v2.lists.ListsV2ServerEndpoints
import org.knora.webapi.slice.api.v2.mapping.StandoffServerEndpoints
import org.knora.webapi.slice.api.v2.metadata.MetadataServerEndpoints
import org.knora.webapi.slice.api.v2.ontologies.OntologiesServerEndpoints
import org.knora.webapi.slice.api.v2.resources.ResourcesServerEndpoints
import org.knora.webapi.slice.api.v2.resources.info.ResourceInfoServerEndpoints
import org.knora.webapi.slice.api.v2.search.SearchServerEndpoints
import org.knora.webapi.slice.api.v2.values.ValuesServerEndpoints

final class ApiV2ServerEndpoints(
  authenticationServerEndpoints: AuthenticationServerEndpoints,
  listsV2ServerEndpoints: ListsV2ServerEndpoints,
  metadataServerEndpoints: MetadataServerEndpoints,
  ontologiesServerEndpoints: OntologiesServerEndpoints,
  resourceInfoServerEndpoints: ResourceInfoServerEndpoints,
  resourcesServerEndpoints: ResourcesServerEndpoints,
  searchServerEndpoints: SearchServerEndpoints,
  standoffServerEndpoints: StandoffServerEndpoints,
  valuesServerEndpoints: ValuesServerEndpoints,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] =
    (authenticationServerEndpoints.serverEndpoints ++
      listsV2ServerEndpoints.serverEndpoints ++
      resourceInfoServerEndpoints.serverEndpoints ++
      valuesServerEndpoints.serverEndpoints ++
      metadataServerEndpoints.serverEndpoints ++
      resourcesServerEndpoints.serverEndpoints ++
      searchServerEndpoints.serverEndpoints ++
      standoffServerEndpoints.serverEndpoints ++
      ontologiesServerEndpoints.serverEndpoints)
      .map(_.tag("API v2"))
}
object ApiV2ServerEndpoints {
  val layer = ZLayer.derive[ApiV2ServerEndpoints]
}
