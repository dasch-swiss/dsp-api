/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import sttp.tapir.AnyEndpoint
import zio.ZLayer

import org.knora.webapi.slice.lists.api.ListsEndpointsV2
import org.knora.webapi.slice.ontology.api.OntologiesEndpoints
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoEndpoints
import org.knora.webapi.slice.resources.api.MetadataEndpoints
import org.knora.webapi.slice.resources.api.ResourcesEndpoints
import org.knora.webapi.slice.resources.api.StandoffEndpoints
import org.knora.webapi.slice.resources.api.ValuesEndpoints
import org.knora.webapi.slice.search.api.SearchEndpoints
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2

final case class ApiV2Endpoints(
  private val authenticationEndpoints: AuthenticationEndpointsV2,
  private val listsEndpointsV2: ListsEndpointsV2,
  private val ontologiesEndpoints: OntologiesEndpoints,
  private val metadataEndpoints: MetadataEndpoints,
  private val resourceInfoEndpoints: ResourceInfoEndpoints,
  private val resourcesEndpoints: ResourcesEndpoints,
  private val searchEndpoints: SearchEndpoints,
  private val standoffEndpoints: StandoffEndpoints,
  private val valuesEndpoints: ValuesEndpoints,
) {

  val endpoints: Seq[AnyEndpoint] =
    authenticationEndpoints.endpoints ++
      metadataEndpoints.endpoints ++
      listsEndpointsV2.endpoints ++
      ontologiesEndpoints.endpoints ++
      resourceInfoEndpoints.endpoints ++
      resourcesEndpoints.endpoints ++
      searchEndpoints.endpoints ++
      standoffEndpoints.endpoints ++
      valuesEndpoints.endpoints
}

object ApiV2Endpoints {
  val layer = ZLayer.derive[ApiV2Endpoints]
}
