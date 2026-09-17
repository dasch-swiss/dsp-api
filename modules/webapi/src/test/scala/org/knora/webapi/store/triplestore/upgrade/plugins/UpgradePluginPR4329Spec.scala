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
class UpgradePluginPR4329Spec extends ZIOSpecDefault with UpgradePluginTestOps {

  val spec: Spec[Any, Nothing] = suite("UpgradePluginPR4329")(
    test(
      "given projects with restricted view settings, " +
        "when the plugin is run, " +
        "then the no-op and backfilled-default sizes are removed and every chosen setting is kept.",
    ) {
      // given
      val plugin = new UpgradePluginPR4329()
      val triG   =
        s"""
           |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
           |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
           |
           |GRAPH  <http://www.knora.org/data/admin> {
           | <http://rdfh.ch/projects/0001> a knora-admin:knoraProject ;
           |                              knora-admin:projectRestrictedViewSize      "pct:100"^^xsd:string .
           |
           | <http://rdfh.ch/projects/0002> a knora-admin:knoraProject ;
           |                              knora-admin:projectRestrictedViewSize      "!128,128"^^xsd:string .
           |
           | <http://rdfh.ch/projects/0003> a knora-admin:knoraProject ;
           |                              knora-admin:projectRestrictedViewSize      "!512,512"^^xsd:string .
           |
           | <http://rdfh.ch/projects/0004> a knora-admin:knoraProject ;
           |                              knora-admin:projectRestrictedViewSize      "pct:1"^^xsd:string .
           |
           | <http://rdfh.ch/projects/0005> a knora-admin:knoraProject ;
           |                              knora-admin:projectRestrictedViewWatermark true .
           |}
           |""".stripMargin
      val model = createJenaModelFromTriG(triG)

      // when
      plugin.transform(model)

      // then
      def hasNoRestrictedViewSize(project: String) =
        s"""
           |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
           |
           |ASK {
           |  GRAPH <http://www.knora.org/data/admin> {
           |    <$project> a knora-admin:knoraProject .
           |    FILTER NOT EXISTS { <$project> knora-admin:projectRestrictedViewSize ?any . }
           |  }
           |}
           |""".stripMargin

      val chosenSettingsRemainUntouched =
        """
          |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |ASK {
          |  GRAPH <http://www.knora.org/data/admin> {
          |    <http://rdfh.ch/projects/0003> knora-admin:projectRestrictedViewSize      "!512,512"^^xsd:string .
          |    <http://rdfh.ch/projects/0004> knora-admin:projectRestrictedViewSize      "pct:1"^^xsd:string .
          |    <http://rdfh.ch/projects/0005> knora-admin:projectRestrictedViewWatermark true .
          |  }
          |}
          |""".stripMargin

      assertTrue(
        queryAsk(hasNoRestrictedViewSize("http://rdfh.ch/projects/0001"), model),
        queryAsk(hasNoRestrictedViewSize("http://rdfh.ch/projects/0002"), model),
        queryAsk(chosenSettingsRemainUntouched, model),
      )
    },
  )
}
