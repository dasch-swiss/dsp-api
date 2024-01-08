/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.impl

import org.knora.webapi.config.Fuseki
import org.knora.webapi.config.Triplestore

trait FusekiTriplestore {
  val mimeTypeApplicationJson              = "application/json"
  val mimeTypeApplicationSparqlResultsJson = "application/sparql-results+json"
  val mimeTypeTextTurtle                   = "text/turtle"
  val mimeTypeApplicationSparqlUpdate      = "application/sparql-update"
  val mimeTypeApplicationNQuads            = "application/n-quads"

  val triplestoreConfig: Triplestore
  val fusekiConfig: Fuseki = triplestoreConfig.fuseki
  val paths: FusekiPaths   = FusekiPaths(fusekiConfig)
}

case class FusekiPaths(config: Fuseki) {
  val checkServer = "/$/server"
  val repository  = s"/${config.repositoryName}"
  val data        = s"$repository/data"
  val get         = s"$repository/get"
  val query       = s"$repository/query"
  val update      = s"$repository/update"
  val datasets    = "/$/datasets"
}
