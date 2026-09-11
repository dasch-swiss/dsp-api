/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class AdminUsersQuerySpec extends ZIOSpecDefault {

  private def canonical(query: String): String = {
    val q = QueryFactory.create(query)
    q.getPrefixMapping.clearNsPrefixMap()
    q.toString
  }

  override def spec: Spec[TestEnvironment, Any] = suite("AdminUsersQuerySpec")(
    test("build renders the admin users CONSTRUCT query") {
      val expected =
        """CONSTRUCT { ?user a <http://www.knora.org/ontology/knora-admin#User> . }
          |WHERE { GRAPH <http://www.knora.org/data/admin> { ?user a <http://www.knora.org/ontology/knora-admin#User> . } }
          |""".stripMargin
      assertTrue(canonical(AdminUsersQuery.build.sparql) == canonical(expected))
    },
  )
}
