/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.knora.webapi.messages.util.rdf._

class UpgradePluginPR1615Spec extends UpgradePluginSpec {
  "Upgrade plugin PR1615" should {
    "remove the instance of ForbiddenResource" in {
      // Parse the input file.
      val model: RdfModel = trigFileToModel("test_data/upgrade/pr1615.trig")

      // Use the plugin to transform the input.
      val plugin = new UpgradePluginPR1615(defaultFeatureFactoryConfig)
      plugin.transform(model)

      // Make an in-memory repository containing the transformed model.
      val repository: RdfRepository = model.asRepository

      // Check that <http://rdfh.ch/0000/forbiddenResource> was removed.

      val query1: String =
        """SELECT ?p ?o
          |FROM <http://www.knora.org/data/0000/SystemProject>
          |WHERE {
          |    <http://rdfh.ch/0000/forbiddenResource> ?p ?o .
          |}
          |""".stripMargin

      val queryResult1: SparqlSelectResult = repository.doSelect(selectQuery = query1)
      assert(queryResult1.results.bindings.isEmpty)

      // Check that other data is still there.

      val query2: String =
        """SELECT ?p ?o
          |FROM <http://www.knora.org/data/0000/SystemProject>
          |WHERE {
          |    <http://rdfh.ch/lists/FFFF/ynm01> ?p ?o .
          |}
          |""".stripMargin

      val queryResult2: SparqlSelectResult = repository.doSelect(selectQuery = query2)
      assert(queryResult2.results.bindings.nonEmpty)

      repository.shutDown()
    }
  }
}
