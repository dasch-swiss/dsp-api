package org.knora.webapi.store.triplestore.upgrade.plugins

import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

object UpgradePluginPR3111Spec extends ZIOSpecDefault with UpgradePluginTestOps {

  val spec: Spec[Any, Nothing] = suite("UpgradePluginPR3111")(
    test(
      "given a project with a restricted view watermark, " +
        "when the plugin is run, " +
        "then the watermark is removed.",
    ) {
      // given
      val plugin = new UpgradePluginPR3111()
      val triG =
        s"""
           |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
           |
           |GRAPH  <http://www.knora.org/data/admin> {
           | <http://rdfh.ch/projects/0001> a knora-admin:Project ;
           |                              knora-admin:projectRestrictedViewWatermark "path_to_image" .
           |
           | <http://rdfh.ch/projects/0002> a knora-admin:Project ;
           |                              knora-admin:projectRestrictedViewWatermark true .
           |}
           |""".stripMargin
      val model = createJenaModelFromTriG(triG)

      // when
      plugin.transform(model)

      // then
      val doProjectsStillExist =
        """
          |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
          |
          |ASK {
          |  GRAPH <http://www.knora.org/data/admin> {
          |    <http://rdfh.ch/projects/0001> a knora-admin:Project .
          |    <http://rdfh.ch/projects/0002> a knora-admin:Project ;
          |       knora-admin:projectRestrictedViewWatermark true .
          |  }
          |}
          |""".stripMargin
      val doesProjectRestrictedViewWatermark =
        """
          |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
          |
          |ASK {
          |  GRAPH <http://www.knora.org/data/admin> {
          |   ?s knora-admin:projectRestrictedViewWatermark "path_to_image" .
          |  }
          |}
          |""".stripMargin
      assertTrue(queryAsk(doProjectsStillExist, model), !queryAsk(doesProjectRestrictedViewWatermark, model))
    },
  )
}
