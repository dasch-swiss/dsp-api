/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

/**
 * Pins the rendered SPARQL of [[ProjectExportQueries]] against the output of the RDF4J
 * SparqlBuilder predecessor. The `expected` strings below are that builder's verbatim
 * `getQueryString` output, compared after canonicalisation by Jena.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ProjectExportQueriesSpec extends ZIOSpecDefault {

  private def canonical(query: String): String = {
    val parsed = QueryFactory.create(query)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  private val project = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")

  override def spec: Spec[Any, Nothing] = suite("ProjectExportQueries")(
    test("adminData renders the project, member and group branches") {
      val actual   = ProjectExportQueries.adminData(project).sparql
      val expected =
        """|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
           |CONSTRUCT { <http://rdfh.ch/projects/0001> ?projectPred ?projectObj .
           |?user ?userPred ?userObj .
           |?group ?groupPred ?groupObj . }
           |WHERE { { <http://rdfh.ch/projects/0001> ?projectPred ?projectObj . } UNION { ?user ?userPred ?userObj ;
           |    knora-admin:isInProject <http://rdfh.ch/projects/0001> . } UNION { ?group ?groupPred ?groupObj ;
           |    knora-admin:belongsToProject <http://rdfh.ch/projects/0001> . } }""".stripMargin
      assertTrue(canonical(actual) == canonical(expected))
    },
    test("permissionData renders the permissions of the project") {
      val actual   = ProjectExportQueries.permissionData(project).sparql
      val expected =
        """|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
           |CONSTRUCT { ?s ?p ?o . }
           |WHERE { ?s knora-admin:forProject <http://rdfh.ch/projects/0001> ;
           |    ?p ?o . }""".stripMargin
      assertTrue(canonical(actual) == canonical(expected))
    },
  )
}
