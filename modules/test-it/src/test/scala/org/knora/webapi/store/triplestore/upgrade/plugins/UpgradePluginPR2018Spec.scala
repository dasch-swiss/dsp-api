/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.junit.runner.RunWith
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.util.rdf.*

@RunWith(classOf[DspZTestJUnitRunner])
class UpgradePluginPR2018Spec extends ZIOSpecDefault with UpgradePluginSpec {

  val spec: Spec[Any, Nothing] = suite("Upgrade plugin PR2018")(
    test("add lastModificationDate to ontology not attached to SystemProject") {
      val model: RdfModel = trigFileToModel("test_data/upgrade/pr2018.trig")
      val plugin          = new UpgradePluginPR2018()
      plugin.transform(model)
      val repository: JenaRepository = model.asRepository

      // query that finds all ontologies with lastModificationDate
      val query: String =
        """
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |
          |SELECT ?ontology
          |FROM <http://www.knora.org/ontology/7777/test>
          |FROM <http://www.knora.org/ontology/0000/test>
          |FROM <http://www.knora.org/data/1111/test>
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
          Map("ontology" -> "http://www.knora.org/ontology/6666/test"),
        ),
      )

      val resultsOk = queryResult.results == expectedResultBody
      repository.shutDown()
      assertTrue(resultsOk)
    },
  )
}
