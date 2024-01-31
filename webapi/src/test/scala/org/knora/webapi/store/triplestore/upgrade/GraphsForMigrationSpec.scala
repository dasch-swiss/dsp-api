/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade

import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.slice.resourceinfo.domain.InternalIri

object GraphsForMigrationSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("GraphsForMigration")(
    test("Merging MigrateAllGraphs with MigrateSpecificGraphs results in MigrateAllGraphs") {
      val specificGraphs = MigrateSpecificGraphs.from(Seq(InternalIri("http://example.com/graph1")))
      val actual         = MigrateAllGraphs.merge(specificGraphs)
      assertTrue(actual == MigrateAllGraphs)
    },
    test("Merging MigrateSpecificGraphs with MigrateAllGraphs results in MigrateAllGraphs") {
      val specificGraphs = MigrateSpecificGraphs.from(Seq(InternalIri("http://example.com/graph1")))
      val actual         = specificGraphs.merge(MigrateAllGraphs)
      assertTrue(actual == MigrateAllGraphs)
    },
    test("Merging two different MigrateSpecificGraphs with duplicate IRI should result in distinct IRI") {
      val specificGraphs1 = MigrateSpecificGraphs.from(Seq(InternalIri("http://example.com/graph1")))
      val specificGraphs2 = MigrateSpecificGraphs.from(
        Seq(InternalIri("http://example.com/graph1"), InternalIri("http://example.com/graph2"))
      )
      val actual = specificGraphs1.merge(specificGraphs2)
      assertTrue(
        actual == MigrateSpecificGraphs.from(
          Seq(InternalIri("http://example.com/graph1"), InternalIri("http://example.com/graph2"))
        )
      )
    },
    test("Creating with MigrateSpecificGraphs.from(Seq) should contain the builtIn") {
      val actual = MigrateSpecificGraphs.from(Seq.empty)
      assertTrue(actual == MigrateSpecificGraphs.builtIn)
    },
    test("Creating with MigrateSpecificGraphs.from(IRI) should contain the builtIn") {
      val actual = MigrateSpecificGraphs.from(InternalIri("http://example.com/graph1"))
      assertTrue(MigrateSpecificGraphs.builtIn.graphIris.subsetOf(actual.graphIris))
    }
  )
}
