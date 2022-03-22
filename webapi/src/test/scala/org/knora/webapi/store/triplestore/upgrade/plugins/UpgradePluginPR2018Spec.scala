/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.messages.util.rdf._

class UpgradePluginPR2018Spec extends UpgradePluginSpec with LazyLogging {
  "Upgrade plugin PR2018" should {
    "add lastModificationDate to ontology not attached to SystemProject" in {
      val model: RdfModel = trigFileToModel("../test_data/upgrade/pr2018.trig")
      val plugin = new UpgradePluginPR2018(defaultFeatureFactoryConfig, logger)
      plugin.transform(model)
      val repository: RdfRepository = model.asRepository

      // query that finds all ontologies with lastModificationDate
      val query: String =
        """
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |
          |SELECT ?ontology
          |FROM <http://www.knora.org/ontology/7777/test>
          |FROM <http://www.knora.org/ontology/6666/test>
          |WHERE {
          |  ?ontology rdf:type owl:Ontology .
          |  ?ontology knora-base:lastModificationDate ?date
          |}
          |""".stripMargin

      val queryResult: SparqlSelectResult = repository.doSelect(query)

      // expect plugin to add lastModificationDate to test ontologies, thus listed in the query results
      val expectedResultBody: SparqlSelectResultBody = expectedResult(
        Seq(
          Map("ontology" -> "http://www.knora.org/ontology/7777/test"),
          Map("ontology" -> "http://www.knora.org/ontology/6666/test")
        )
      )

      assert(queryResult.results == expectedResultBody)
      repository.shutDown()
    }
  }
}
