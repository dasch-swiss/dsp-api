/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2
import org.knora.webapi.slice.lists.api.ListsV2ServerEndpoints
import org.knora.webapi.slice.ontology.api.OntologiesServerEndpoints
import org.knora.webapi.slice.resources.api.ResourceInfoServerEndpoints
import org.knora.webapi.slice.resources.api.ResourcesApiServerEndpoints
import org.knora.webapi.slice.search.api.SearchServerEndpoints
import org.knora.webapi.slice.security.api.AuthenticationServerEndpoints

object ApiV2Module {

  type Dependencies =
    // format: off
    AuthenticationServerEndpoints &
    ListsV2ServerEndpoints &
    OntologiesServerEndpoints &
    ResourceInfoServerEndpoints &
    ResourcesApiServerEndpoints &
    SearchServerEndpoints
    // format: on

  type Provided = ApiV2ServerEndpoints

  val layer = ApiV2ServerEndpoints.layer
}
