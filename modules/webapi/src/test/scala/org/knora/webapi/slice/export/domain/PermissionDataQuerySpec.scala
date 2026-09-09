/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

@RunWith(classOf[DspZTestJUnitRunner])
class PermissionDataQuerySpec extends ZIOSpecDefault {

  private def canonical(query: String): String = {
    val q = QueryFactory.create(query)
    q.getPrefixMapping.clearNsPrefixMap()
    q.toString
  }

  private val testProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")

  override def spec: Spec[TestEnvironment, Any] = suite("PermissionDataQuerySpec")(
    test("build renders the permission data CONSTRUCT query scoped to the project") {
      val expected =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
          |CONSTRUCT { ?s ?p ?o . }
          |WHERE { GRAPH <http://www.knora.org/data/permissions> { ?s knora-admin:forProject <http://rdfh.ch/projects/0001> ;
          |    ?p ?o . } }
          |""".stripMargin
      assertTrue(canonical(PermissionDataQuery.build(testProjectIri).sparql) == canonical(expected))
    },
  )
}
