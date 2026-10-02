/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.sparqlbuilder.Iri
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.GoldenTest
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

@RunWith(classOf[DspZTestJUnitRunner])
class ValueHasXmlBackfillQuerySpec extends ZIOSpecDefault with GoldenTest {

  private val graph  = Iri.unsafeFrom("http://www.knora.org/data/0001/anything")
  private val value1 = Iri.unsafeFrom("http://rdfh.ch/0001/res1/values/v1")
  private val value2 = Iri.unsafeFrom("http://rdfh.ch/0001/res2/values/v2")

  private val trickyXml = "<text a=\"x\">Tom & \"Jerry\"\n  <b>bold</b></text>"

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("ValueHasXmlBackfillQuerySpec")(
    test("selectCandidates") {
      val select = ValueHasXmlBackfillQuery.selectCandidates(graph)
      assertTrue(select.timeout == SparqlTimeout.Maintenance) && assertGolden(select.sparql, "selectCandidates")
    },
    test("constructStandoff with one value") {
      val construct = ValueHasXmlBackfillQuery.constructStandoff(graph, Seq(value1))
      assertTrue(construct.timeout == SparqlTimeout.Maintenance) && assertGolden(construct.sparql, "constructOne")
    },
    test("constructStandoff with two values") {
      val construct = ValueHasXmlBackfillQuery.constructStandoff(graph, Seq(value1, value2))
      assertGolden(construct.sparql, "constructTwo")
    },
    test("insertXml with one value and special characters") {
      val update = ValueHasXmlBackfillQuery.insertXml(graph, Seq(value1 -> trickyXml))
      assertTrue(update.timeout == SparqlTimeout.Maintenance) && assertGolden(update.sparql, "insertOne")
    },
    test("insertXml with two values") {
      val update = ValueHasXmlBackfillQuery.insertXml(graph, Seq(value1 -> "<text>a</text>", value2 -> trickyXml))
      assertGolden(update.sparql, "insertTwo")
    },
  )
}
