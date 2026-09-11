/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.InternalFilename

/**
 * The two expected strings below are the RDF4J SparqlBuilder output this query was ported from, kept
 * verbatim as fixtures. They are compared canonically — parsed by Jena with the prefix map cleared — so
 * the port is pinned to the same query, not to the old builder's whitespace and prefix rendering.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class FileValuePermissionsQuerySpec extends ZIOSpecDefault {

  private val testFilename: InternalFilename = InternalFilename.unsafeFrom("0001/test-image.jp2")

  /** Parse and re-serialise, with prefixes expanded, so only the query itself is compared. */
  private def canonical(sparql: String): String = {
    val q = QueryFactory.create(sparql)
    q.getPrefixMapping.clearNsPrefixMap()
    q.toString
  }

  override def spec: Spec[TestEnvironment, Any] = suite("FileValuePermissionsQuerySpec")(
    test("should produce correct SELECT query for a filename") {
      val actual   = FileValuePermissionsQuery.build(testFilename).sparql
      val expected =
        """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |SELECT ?creator ?project ?permissions
          |WHERE { ?fileValue knora-base:internalFilename "0001/test-image.jp2" .
          |?currentFileValue knora-base:previousValue* ?fileValue ;
          |    knora-base:hasPermissions ?permissions ;
          |    knora-base:attachedToUser ?creator .
          |?resource ?prop ?currentFileValue ;
          |    knora-base:attachedToProject ?project .
          |{ ?fileValue ?objPred ?objObj .
          |FILTER ( ?objPred != knora-base:previousValue ) }
          |?currentFileValue knora-base:isDeleted false .
          |?resource knora-base:isDeleted false . }
          |""".stripMargin
      assertTrue(canonical(actual) == canonical(expected))
    },
    test("should produce correct SELECT query for a filename with special characters") {
      val specialFilename: InternalFilename = InternalFilename.unsafeFrom("0001/file-with-special_chars.jp2")
      val actual                            = FileValuePermissionsQuery.build(specialFilename).sparql
      val expected: String                  =
        """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |SELECT ?creator ?project ?permissions
          |WHERE { ?fileValue knora-base:internalFilename "0001/file-with-special_chars.jp2" .
          |?currentFileValue knora-base:previousValue* ?fileValue ;
          |    knora-base:hasPermissions ?permissions ;
          |    knora-base:attachedToUser ?creator .
          |?resource ?prop ?currentFileValue ;
          |    knora-base:attachedToProject ?project .
          |{ ?fileValue ?objPred ?objObj .
          |FILTER ( ?objPred != knora-base:previousValue ) }
          |?currentFileValue knora-base:isDeleted false .
          |?resource knora-base:isDeleted false . }
          |""".stripMargin
      assertTrue(canonical(actual) == canonical(expected))
    },
    test("keeps the optimiser hint that resolves ?fileValue before the previousValue* closure") {
      // DEV-6803: unnecessary for correctness, but it guides Jena to resolve ?fileValue's properties
      // before the expensive closure. Its own group keeps it a separate basic graph pattern.
      val q = FileValuePermissionsQuery.build(testFilename).sparql
      assertTrue(
        q.contains("?fileValue ?objPred ?objObj ."),
        q.contains("FILTER (?objPred != knora-base:previousValue)"),
        // The hint must sit between the previousValue* closure and the isDeleted triples, not at the end.
        q.indexOf("?objPred") > q.indexOf("knora-base:previousValue* ?fileValue"),
        q.indexOf("?objPred") < q.indexOf("?currentFileValue knora-base:isDeleted"),
      )
    },
  )
}
