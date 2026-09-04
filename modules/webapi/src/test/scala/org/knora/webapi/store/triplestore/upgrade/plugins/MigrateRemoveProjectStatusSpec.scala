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

@RunWith(classOf[DspZTestJUnitRunner])
class MigrateRemoveProjectStatusSpec extends ZIOSpecDefault with UpgradePluginTestOps {

  private val triG =
    s"""
       |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
       |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
       |
       |GRAPH <http://www.knora.org/data/admin> {
       |  <http://rdfh.ch/projects/0001> a knora-admin:knoraProject ;
       |                                 knora-admin:projectShortname "example"^^xsd:string ;
       |                                 knora-admin:status           true .
       |
       |  <http://rdfh.ch/users/normaluser> a knora-admin:User ;
       |                                    knora-admin:status true .
       |
       |  <http://www.knora.org/ontology/knora-admin#ProjectMember> a knora-admin:UserGroup ;
       |                                                            knora-admin:status true .
       |}
       |""".stripMargin

  private val projectHasNoStatus =
    """
      |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
      |
      |ASK {
      |  GRAPH <http://www.knora.org/data/admin> {
      |    <http://rdfh.ch/projects/0001> a knora-admin:knoraProject ;
      |                                   knora-admin:projectShortname "example" .
      |    FILTER NOT EXISTS { <http://rdfh.ch/projects/0001> knora-admin:status ?any . }
      |  }
      |}
      |""".stripMargin

  private val userStatusSurvives =
    """
      |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
      |
      |ASK {
      |  GRAPH <http://www.knora.org/data/admin> {
      |    <http://rdfh.ch/users/normaluser> knora-admin:status true .
      |  }
      |}
      |""".stripMargin

  private val groupStatusSurvives =
    """
      |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
      |
      |ASK {
      |  GRAPH <http://www.knora.org/data/admin> {
      |    <http://www.knora.org/ontology/knora-admin#ProjectMember> knora-admin:status true .
      |  }
      |}
      |""".stripMargin

  val spec: Spec[Any, Nothing] = suite("MigrateRemoveProjectStatus")(
    test(
      "given a project, a user and a group each carrying a status triple, " +
        "when the plugin runs, " +
        "then the project status is deleted while the user and group status survive.",
    ) {
      val plugin = new MigrateRemoveProjectStatus()
      val model  = createJenaModelFromTriG(triG)

      plugin.transform(model)

      assertTrue(
        queryAsk(projectHasNoStatus, model),
        queryAsk(userStatusSurvives, model),
        queryAsk(groupStatusSurvives, model),
      )
    },
    test(
      "given the plugin already ran, " +
        "when it runs a second time, " +
        "then the result is unchanged (idempotent).",
    ) {
      val plugin = new MigrateRemoveProjectStatus()
      val model  = createJenaModelFromTriG(triG)

      plugin.transform(model)
      plugin.transform(model)

      assertTrue(
        queryAsk(projectHasNoStatus, model),
        queryAsk(userStatusSurvives, model),
        queryAsk(groupStatusSurvives, model),
      )
    },
  )
}
