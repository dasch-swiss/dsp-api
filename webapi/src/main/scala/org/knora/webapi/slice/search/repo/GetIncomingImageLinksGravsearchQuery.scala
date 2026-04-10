/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.repo

import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.ResourceIri

object GetIncomingImageLinksGravsearchQuery extends QueryBuilderHelper {

  // Built via string interpolation because Gravsearch requires the main resource to be a variable
  // assigned with BIND — rdf4j's SparqlBuilder does not support BIND.
  def build(resourceIri: ResourceIri): String = {
    val resourceIriStr = toRdfIri(resourceIri).getQueryString

    s"""|PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
        |
        |CONSTRUCT {
        |  ?resource knora-api:isMainResource true .
        |
        |  ?representation knora-api:isPartOf ?resource ;
        |      knora-api:hasStillImageFileValue ?fileValue .
        |} WHERE {
        |  BIND($resourceIriStr AS ?resource)
        |
        |  ?resource a knora-api:Resource .
        |
        |  ?representation knora-api:isPartOf ?resource ;
        |    a knora-api:StillImageRepresentation ;
        |    knora-api:hasStillImageFileValue ?fileValue .
        |}
        |""".stripMargin
  }
}
