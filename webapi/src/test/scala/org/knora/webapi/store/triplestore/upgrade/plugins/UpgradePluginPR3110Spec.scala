/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

object UpgradePluginPR3110Spec extends ZIOSpecDefault with UpgradePluginTestOps {

  val spec: Spec[Any, Nothing] = suite("UpgradePluginPR3110")(
    test(
      "given a project with a belongsToInstitution, " +
        "and given an institution, " +
        "when the plugin is run, " +
        "then the institution and belongsToInstitution are removed.",
    ) {
      // given
      val plugin = new UpgradePluginPR3110()
      val triG =
        s"""
           |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
           |
           |GRAPH  <http://www.knora.org/data/admin> {
           | <http://rdfh.ch/projects/0001> a knora-admin:Project ;
           |                              knora-admin:belongsToInstitution <http://rdfh.ch/institution/0001> .
           |
           | <http://rdfh.ch/institution/0001> a knora-admin:Institution;
           |                              knora-admin:institutionName "Asylum" .
           |}
           |""".stripMargin
      val model = createJenaModelFromTriG(triG)

      // when
      plugin.transform(model)

      // then
      val wasInstitutionDeleted =
        """
          |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
          |
          |ASK {
          |  GRAPH <http://www.knora.org/data/admin> {
          |    ?s a knora-admin:Institution ;
          |      ?p ?o .
          |  }
          |}
          |""".stripMargin
      val doesProjectExist =
        """
          |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
          |
          |ASK {
          |  GRAPH <http://www.knora.org/data/admin> {
          |    <http://rdfh.ch/projects/0001> a knora-admin:Project .
          |    FILTER NOT EXISTS { <http://rdfh.ch/projects/0001> knora-admin:belongsToInstitution <http://rdfh.ch/institution/0001> } .
          |  }
          |}
          |""".stripMargin
      assertTrue(!queryAsk(wasInstitutionDeleted, model), queryAsk(doesProjectExist, model))
    },
  )
}
