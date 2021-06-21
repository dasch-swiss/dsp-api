/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
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

      // Check that <http://rdfh.ch/SOIukqJ_qqwrLYY2AzMWog/forbiddenResource> was removed.

      val query1: String =
        """SELECT ?p ?o
          |FROM <http://www.knora.org/data/aboosmPQTBmEjQRsNiVs0w>
          |WHERE {
          |    <http://rdfh.ch/SOIukqJ_qqwrLYY2AzMWog/forbiddenResource> ?p ?o .
          |}
          |""".stripMargin

      val queryResult1: SparqlSelectResult = repository.doSelect(selectQuery = query1)
      assert(queryResult1.results.bindings.isEmpty)

      // Check that other data is still there.

      val query2: String =
        """SELECT ?p ?o
          |FROM <http://www.knora.org/data/aboosmPQTBmEjQRsNiVs0w>
          |WHERE {
          |    <http://rdfh.ch/lists/aboosmPQTBmEjQRsNiVs0w/ynm01> ?p ?o .
          |}
          |""".stripMargin

      val queryResult2: SparqlSelectResult = repository.doSelect(selectQuery = query2)
      assert(queryResult2.results.bindings.nonEmpty)

      repository.shutDown()
    }
  }
}
