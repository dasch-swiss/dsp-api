/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.knora.webapi.messages.util.rdf.*

class UpgradePluginPR4116Spec extends UpgradePluginSpec {
  private val adminGraph     = "http://www.knora.org/data/admin"
  private val lifecycleQuery =
    s"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
        |
        |SELECT ?project ?lifecycle
        |FROM <$adminGraph>
        |WHERE {
        |    ?project a knora-admin:knoraProject ;
        |             knora-admin:projectLifecycle ?lifecycle .
        |} ORDER BY ?project
        |""".stripMargin

  "Upgrade plugin PR4116" should {

    "set projectLifecycle to \"draft\" on projects that do not yet have one" in {
      val trig =
        s"""|@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
            |
            |<$adminGraph> {
            |  <http://rdfh.ch/projects/0001> a knora-admin:knoraProject ;
            |    knora-admin:projectShortcode "0001" .
            |  <http://rdfh.ch/projects/0002> a knora-admin:knoraProject ;
            |    knora-admin:projectShortcode "0002" .
            |}
            |""".stripMargin
      val model = stringToModel(trig)

      new UpgradePluginPR4116().transform(model)

      val repository = model.asRepository
      try {
        val result = repository.doSelect(lifecycleQuery)
        assert(
          result.results == expectedResult(
            Seq(
              Map("project" -> "http://rdfh.ch/projects/0001", "lifecycle" -> "draft"),
              Map("project" -> "http://rdfh.ch/projects/0002", "lifecycle" -> "draft"),
            ),
          ),
        )
      } finally repository.shutDown()
    }

    "leave an existing projectLifecycle value untouched" in {
      val trig =
        s"""|@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
            |
            |<$adminGraph> {
            |  <http://rdfh.ch/projects/0001> a knora-admin:knoraProject ;
            |    knora-admin:projectShortcode "0001" ;
            |    knora-admin:projectLifecycle "published" .
            |  <http://rdfh.ch/projects/0002> a knora-admin:knoraProject ;
            |    knora-admin:projectShortcode "0002" .
            |}
            |""".stripMargin
      val model = stringToModel(trig)

      new UpgradePluginPR4116().transform(model)

      val repository = model.asRepository
      try {
        val result = repository.doSelect(lifecycleQuery)
        assert(
          result.results == expectedResult(
            Seq(
              Map("project" -> "http://rdfh.ch/projects/0001", "lifecycle" -> "published"),
              Map("project" -> "http://rdfh.ch/projects/0002", "lifecycle" -> "draft"),
            ),
          ),
        )
      } finally repository.shutDown()
    }

    "be idempotent when run twice" in {
      val trig =
        s"""|@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
            |
            |<$adminGraph> {
            |  <http://rdfh.ch/projects/0001> a knora-admin:knoraProject ;
            |    knora-admin:projectShortcode "0001" .
            |}
            |""".stripMargin
      val model = stringToModel(trig)

      val plugin = new UpgradePluginPR4116()
      plugin.transform(model)
      plugin.transform(model)

      val repository = model.asRepository
      try {
        val result = repository.doSelect(lifecycleQuery)
        assert(
          result.results == expectedResult(
            Seq(Map("project" -> "http://rdfh.ch/projects/0001", "lifecycle" -> "draft")),
          ),
        )
      } finally repository.shutDown()
    }
  }
}
