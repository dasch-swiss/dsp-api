/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2
import zio.telemetry.opentelemetry.tracing.Tracing

import org.knora.webapi.config.AppConfig
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.slice.api.v2.authentication.AuthenticationServerEndpoints
import org.knora.webapi.slice.api.v2.lists.ListsV2ServerEndpoints
import org.knora.webapi.slice.api.v2.ontologies.OntologiesServerEndpoints
import org.knora.webapi.slice.api.v2.ontologies.OntologyApiModule
import org.knora.webapi.slice.api.v2.resources.info.ResourceInfoServerEndpoints
import org.knora.webapi.slice.api.v2.search.SearchServerEndpoints
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.resources.api.ResourcesApiServerEndpoints
import org.knora.webapi.slice.resources.domain.ResourceInfoRepo
import org.knora.webapi.slice.security.Authenticator

object ApiV2Module {

  type Dependencies =
    // format: off
    AppConfig &
    Authenticator &
    BaseEndpoints &
    IriConverter &
    KnoraResponseRenderer &
    ListsResponder &
    ResourcesApiServerEndpoints &
    ResourceInfoRepo &
    OntologyApiModule.Dependencies &
    SearchResponderV2 &
    Tracing
    // format: on

  type Provided = ApiV2ServerEndpoints

  val layer = AuthenticationServerEndpoints.layer >+>
    ListsV2ServerEndpoints.layer >+>
    OntologyApiModule.layer >+>
    ResourceInfoServerEndpoints.layer >+>
    SearchServerEndpoints.layer >>>
    ApiV2ServerEndpoints.layer
}
