/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.junit.runner.RunWith
import zio.test.*

import org.knora.sparqlbuilder.Iri
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.GoldenTest
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

@RunWith(classOf[DspZTestJUnitRunner])
class StandoffLinkRefCountQuerySpec extends ZIOSpecDefault with GoldenTest {

  private val graph = Iri.unsafeFrom("http://www.knora.org/data/0001/anything")

  override def spec: Spec[TestEnvironment & zio.Scope, Any] = suite("StandoffLinkRefCountQuerySpec")(
    test("selectMismatches") {
      val select = StandoffLinkRefCountQuery.selectMismatches(graph)
      assertTrue(select.timeout == SparqlTimeout.Maintenance) && assertGolden(select.sparql, "selectMismatches")
    },
  )
}
