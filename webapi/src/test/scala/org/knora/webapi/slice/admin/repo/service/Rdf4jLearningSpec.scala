/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.eclipse.rdf4j.model.vocabulary.*
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.prefix
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary

object Rdf4jLearningSpec extends ZIOSpecDefault {
  val spec: Spec[Any, Nothing] = suite("Rdf4jLearningSpec")(
    test("A simple construct query") {
      val (s, p, o) = (variable("s"), variable("p"), variable("o"))
      val query = Queries
        .CONSTRUCT(tp(s, p, o))
        .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS))
        .where(
          s
            .has(RDF.TYPE, Vocabulary.KnoraAdmin.User)
            .and(tp(s, p, o))
            .from(Rdf.iri(adminDataNamedGraph.value))
        )
      val queryString = query.getQueryString
      assertTrue(
        queryString == """|PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                          |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
                          |CONSTRUCT { ?s ?p ?o . }
                          |WHERE { GRAPH <http://www.knora.org/data/admin> { ?s rdf:type knora-admin:User .
                          |?s ?p ?o . } }
                          |""".stripMargin
      )
    }
  )
}
