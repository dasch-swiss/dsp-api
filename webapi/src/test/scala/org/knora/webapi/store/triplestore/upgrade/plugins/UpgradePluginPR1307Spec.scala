/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.knora.webapi.messages.util.rdf._

class UpgradePluginPR1307Spec extends UpgradePluginSpec {
  "Upgrade plugin PR1307" should {
    "update text values with standoff" in {
      // Parse the input file.
      val model: RdfModel = trigFileToModel("test_data/upgrade/pr1307.trig")

      // Use the plugin to transform the input.
      val plugin = new UpgradePluginPR1307(defaultFeatureFactoryConfig)
      plugin.transform(model)

      // Make an in-memory repository containing the transformed model.
      val repository: RdfRepository = model.asRepository

      // Check that knora-base:valueHasMaxStandoffStartIndex was added.

      val query1: String =
        """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |SELECT ?s ?maxStartIndex
          |FROM <http://www.knora.org/data/0001/anything>
          |WHERE {
          |    ?s knora-base:valueHasMaxStandoffStartIndex ?maxStartIndex .
          |}
          |""".stripMargin

      val queryResult1: SparqlSelectResult = repository.doSelect(selectQuery = query1)

      val expectedResult1: SparqlSelectResultBody = expectedResult(
        Seq(
          Map(
            "s" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ",
            "maxStartIndex" -> "7"
          )
        )
      )

      assert(queryResult1.results == expectedResult1)

      // Check that the standoff tags' IRIs were changed correctly.

      val query2: String =
        """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |SELECT ?tag
          |FROM <http://www.knora.org/data/0001/anything>
          |WHERE {
          |    <http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ> knora-base:valueHasStandoff ?tag .
          |} ORDER BY ?tag
          |""".stripMargin

      val queryResult2: SparqlSelectResult = repository.doSelect(selectQuery = query2)

      val expectedResult2: SparqlSelectResultBody = expectedResult(
        Seq(
          Map("tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/0"),
          Map("tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1"),
          Map("tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/2"),
          Map("tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/3"),
          Map("tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/4"),
          Map("tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/5"),
          Map("tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/6"),
          Map("tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/7")
        )
      )

      assert(queryResult2.results == expectedResult2)

      // Check that the objects of knora-base:standoffTagHasStartParent were changed correctly.

      val query3: String =
        """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |SELECT ?tag ?startIndex ?startParent
          |FROM <http://www.knora.org/data/0001/anything>
          |WHERE {
          |    ?tag knora-base:standoffTagHasStartIndex ?startIndex .
          |
          |    OPTIONAL {
          |        ?tag knora-base:standoffTagHasStartParent ?startParent .
          |    }
          |} ORDER BY ?tag
          |""".stripMargin

      val queryResult3: SparqlSelectResult = repository.doSelect(selectQuery = query3)

      val expectedResult3: SparqlSelectResultBody = expectedResult(
        Seq(
          Map(
            "startIndex" -> "0",
            "tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/0"
          ),
          Map(
            "startIndex" -> "1",
            "startParent" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/0",
            "tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1"
          ),
          Map(
            "startIndex" -> "2",
            "startParent" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1",
            "tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/2"
          ),
          Map(
            "startIndex" -> "3",
            "startParent" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1",
            "tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/3"
          ),
          Map(
            "startIndex" -> "4",
            "startParent" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1",
            "tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/4"
          ),
          Map(
            "startIndex" -> "5",
            "startParent" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1",
            "tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/5"
          ),
          Map(
            "startIndex" -> "6",
            "startParent" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1",
            "tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/6"
          ),
          Map(
            "startIndex" -> "7",
            "startParent" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1",
            "tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/7"
          )
        )
      )

      assert(queryResult3.results == expectedResult3)

      repository.shutDown()
    }
  }
}
