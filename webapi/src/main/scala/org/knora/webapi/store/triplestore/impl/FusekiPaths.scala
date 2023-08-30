package org.knora.webapi.store.triplestore.impl

import org.knora.webapi.config.Fuseki

trait FusekiTriplestore {
  val mimeTypeApplicationJson              = "application/json"
  val mimeTypeApplicationSparqlResultsJson = "application/sparql-results+json"
  val mimeTypeTextTurtle                   = "text/turtle"
  val mimeTypeApplicationSparqlUpdate      = "application/sparql-update"
  val mimeTypeApplicationNQuads            = "application/n-quads"
  def paths(config: Fuseki): FusekiPaths   = FusekiPaths(config)
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
