/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.repo

import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.ResourceIri

object GetResourceWithSpecifiedPropertiesGravsearchQuery extends QueryBuilderHelper {

  // Built via string interpolation because Gravsearch requires the main resource to be a variable
  // assigned with BIND — rdf4j's SparqlBuilder does not support BIND.
  // Property IRIs are converted to API v2 complex schema (not internal schema) because this is
  // a Gravsearch query that is parsed by GravsearchParser, not a direct triplestore query.
  def build(resourceIri: ResourceIri, propertyIris: Seq[PropertyIri]): String = {
    val resourceIriStr    = toRdfIri(resourceIri).getQueryString
    val apiV2PropertyIris = propertyIris.map(_.toComplexSchema)

    val constructClauses = apiV2PropertyIris.zipWithIndex.map { case (propertyIri, index) =>
      s"  ?resource <$propertyIri> ?propertyObj$index ."
    }
      .mkString("\n")

    val optionalClauses = apiV2PropertyIris.zipWithIndex.map { case (propertyIri, index) =>
      s"""|  OPTIONAL {
          |    ?resource <$propertyIri> ?propertyObj$index .
          |  }""".stripMargin
    }
      .mkString("\n")

    s"""|PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
        |
        |CONSTRUCT {
        |  ?resource knora-api:isMainResource true .
        |$constructClauses
        |} WHERE {
        |  BIND($resourceIriStr AS ?resource)
        |
        |  ?resource a knora-api:Resource .
        |
        |$optionalClauses
        |}
        |""".stripMargin
  }
}
