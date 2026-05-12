/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.messages.util.rdf.*

object UpgradePluginPR1615Spec extends ZIOSpecDefault with UpgradePluginSpec {

  val spec: Spec[Any, Nothing] = suite("Upgrade plugin PR1615")(
    test("remove the instance of ForbiddenResource") {
      // Parse the input file.
      val model: RdfModel = trigFileToModel("test_data/upgrade/pr1615.trig")

      // Use the plugin to transform the input.
      val plugin = new UpgradePluginPR1615()
      plugin.transform(model)

      // Make an in-memory repository containing the transformed model.
      val repository: JenaRepository = model.asRepository

      // Check that <http://rdfh.ch/0000/forbiddenResource> was removed.

      val query1: String =
        """SELECT ?p ?o
          |FROM <http://www.knora.org/data/0000/SystemProject>
          |WHERE {
          |    <http://rdfh.ch/0000/forbiddenResource> ?p ?o .
          |}
          |""".stripMargin

      val queryResult1: SparqlSelectResult = repository.doSelect(selectQuery = query1)
      val firstResultEmpty                 = queryResult1.isEmpty

      // Check that other data is still there.

      val query2: String =
        """SELECT ?p ?o
          |FROM <http://www.knora.org/data/0000/SystemProject>
          |WHERE {
          |    <http://rdfh.ch/lists/FFFF/ynm01> ?p ?o .
          |}
          |""".stripMargin

      val queryResult2: SparqlSelectResult = repository.doSelect(selectQuery = query2)
      val secondResultNonEmpty             = queryResult2.nonEmpty

      repository.shutDown()

      assertTrue(firstResultEmpty, secondResultNonEmpty)
    },
  )
}
