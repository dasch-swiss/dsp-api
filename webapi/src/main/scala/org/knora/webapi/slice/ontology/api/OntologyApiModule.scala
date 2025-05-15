/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api
import zio.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.v2.OntologyResponderV2
import org.knora.webapi.slice.URModule
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.ontology.api.service.OntologiesRestService
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.OntologyCacheHelpers
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo

object OntologyApiModule
    extends URModule[
      AuthorizationRestService & AppConfig & BaseEndpoints & CardinalityService & IriConverter & KnoraResponseRenderer &
        OntologyCacheHelpers & OntologyRepo & OntologyResponderV2 & StringFormatter & TapirToPekkoInterpreter,
      OntologiesServerEndpoints & OntologiesEndpoints & OntologyV2RequestParser,
    ] { self =>

  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      RestCardinalityService.layer,
      OntologiesRestService.layer,
      OntologiesServerEndpoints.layer,
      OntologiesEndpoints.layer,
      OntologyV2RequestParser.layer,
    )
}
