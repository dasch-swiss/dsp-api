/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api
import zio.*

import org.knora.webapi.slice.URModule
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

object OntologyApiModule extends URModule[IriConverter, OntologyV2RequestParser] { self =>

  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](OntologyV2RequestParser.layer)
}
