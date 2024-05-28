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
  val checkServer: List[String] = List("$", "server")
  val repository: List[String]  = List(config.repositoryName)
  val data: List[String]        = repository :+ "data"
  val get: List[String]         = repository :+ "get"
  val query: List[String]       = repository :+ "query"
  val update: List[String]      = repository :+ "update"
  val datasets: List[String]    = List("$", "datasets")
}
