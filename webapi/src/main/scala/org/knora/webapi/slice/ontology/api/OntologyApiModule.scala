/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api
import zio.*

import org.knora.webapi.responders.v2.OntologyResponderV2
import org.knora.webapi.slice.URModule
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.ontology.api.service.OntologiesRestService
import org.knora.webapi.slice.ontology.domain.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo

object OntologyApiModule
    extends URModule[
      AuthorizationRestService & BaseEndpoints & HandlerMapper & IriConverter & KnoraResponseRenderer & OntologyRepo &
        OntologyResponderV2 & TapirToPekkoInterpreter,
      OntologiesApiRoutes & OntologiesEndpoints & OntologyV2RequestParser,
    ] { self =>

  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      OntologiesRestService.layer,
      OntologiesEndpointsHandler.layer,
      OntologiesEndpoints.layer,
      OntologyV2RequestParser.layer,
      OntologiesApiRoutes.layer,
    )
}
