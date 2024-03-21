/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

object UpgradePluginPR3112Spec extends ZIOSpecDefault with UpgradePluginTestOps {

  val spec: Spec[Any, Nothing] = suite("UpgradePluginPR3111")(
    test(
      "given project with invalid RestrictedView settings, " +
        "when the plugin is run, " +
        "then the size is retained or a default size is set.",
    ) {
      // given
      val plugin = new UpgradePluginPR3112()
      val triG =
        s"""
           |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
           |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
           |
           |GRAPH  <http://www.knora.org/data/admin> {
           | <http://rdfh.ch/projects/0001> a knora-admin:knoraProject ;
           |                              knora-admin:projectRestrictedViewWatermark true ;
           |                              knora-admin:projectRestrictedViewSize      "!512,512"^^xsd:string .
           |
           | <http://rdfh.ch/projects/0002> a knora-admin:knoraProject .
           |
           | <http://rdfh.ch/projects/0003> a knora-admin:knoraProject ;
           |                              knora-admin:projectRestrictedViewWatermark false.
           |
           | <http://rdfh.ch/projects/0004> a knora-admin:knoraProject ;
           |                              knora-admin:projectRestrictedViewWatermark true.
           |                              
           | <http://rdfh.ch/projects/0005> a knora-admin:knoraProject ;
           |                              knora-admin:projectRestrictedViewSize "!555,555"^^xsd:string .
           |}
           |""".stripMargin
      val model = createJenaModelFromTriG(triG)

      // when
      plugin.transform(model)

      // then
      val project0001HasOnlyViewSize =
        """
          |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |ASK {
          |  GRAPH <http://www.knora.org/data/admin> {
          |    <http://rdfh.ch/projects/0001> a knora-admin:knoraProject ;
          |                                   knora-admin:projectRestrictedViewSize      "!512,512"^^xsd:string .
          |    FILTER NOT EXISTS { <http://rdfh.ch/projects/0001> knora-admin:projectRestrictedViewWatermark ?any . }
          |  }
          |}
          |""".stripMargin

      val project0002HasDefaultRestrictedViewSize =
        """
          |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |ASK {
          |  GRAPH <http://www.knora.org/data/admin> {
          |    <http://rdfh.ch/projects/0002> a knora-admin:knoraProject ;
          |                                   knora-admin:projectRestrictedViewSize      "!128,128"^^xsd:string .
          |    FILTER NOT EXISTS { <http://rdfh.ch/projects/0002> knora-admin:projectRestrictedViewWatermark ?any . }
          |  }
          |}
          |""".stripMargin

      val watermarkFalseIsReplacedByDefaultRestrictedViewSize =
        """
          |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |ASK {
          |  GRAPH <http://www.knora.org/data/admin> {
          |    <http://rdfh.ch/projects/0003> a knora-admin:knoraProject ;
          |                                   knora-admin:projectRestrictedViewSize      "!128,128"^^xsd:string .
          |    FILTER NOT EXISTS { <http://rdfh.ch/projects/0003> knora-admin:projectRestrictedViewWatermark ?any . }
          |  }
          |}
          |""".stripMargin

      val validProjectsRemainUntouched =
        """
          |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |ASK {
          |  GRAPH <http://www.knora.org/data/admin> {
          |
          | <http://rdfh.ch/projects/0004> a knora-admin:knoraProject ;
          |                              knora-admin:projectRestrictedViewWatermark true.
          |
          | <http://rdfh.ch/projects/0005> a knora-admin:knoraProject ;
          |                              knora-admin:projectRestrictedViewSize "!555,555"^^xsd:string .
          |  }
          |}
          |""".stripMargin

      assertTrue(
        queryAsk(project0001HasOnlyViewSize, model),
        queryAsk(project0002HasDefaultRestrictedViewSize, model),
        queryAsk(watermarkFalseIsReplacedByDefaultRestrictedViewSize, model),
        queryAsk(validProjectsRemainUntouched, model),
      )
    },
  )
}
