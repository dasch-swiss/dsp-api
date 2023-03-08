/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.knora.webapi.messages.util.rdf._

class UpgradePluginPR1322Spec extends UpgradePluginSpec {
  "Upgrade plugin PR1322" should {
    "add UUIDs to values" in {
      // Parse the input file.
      val model: RdfModel = trigFileToModel("../test_data/upgrade/pr1322.trig")

      // Use the plugin to transform the input.
      val plugin = new UpgradePluginPR1322()
      plugin.transform(model)

      // Make an in-memory repository containing the transformed model.
      val repository: RdfRepository = model.asRepository

      // Check that UUIDs were added.

      val query: String =
        """
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |SELECT ?value WHERE {
          |    ?value knora-base:valueHasUUID ?valueHasUUID .
          |} ORDER BY ?value
          |""".stripMargin

      val queryResult1: SparqlSelectResult = repository.doSelect(selectQuery = query)

      val expectedResultBody: SparqlSelectResultBody = expectedResult(
        Seq(
          Map(
            "value" -> "http://rdfh.ch/0001/thing-with-history/values/1c"
          ),
          Map(
            "value" -> "http://rdfh.ch/0001/thing-with-history/values/2c"
          )
        )
      )

      assert(queryResult1.results == expectedResultBody)

      repository.shutDown()
    }
  }
}
