package org.knora.webapi.store.triplestore.upgrade.plugins

import zio._
import zio.test._

object UpgradePluginXXXSpec extends UpgradePluginZSpec {

  def plugin = new UpgradePluginXXX()

  def spec = suite("UpgradePluginXXX") {
    test("test") {
      for {
        model <- trigFileToModel("../test_data/upgrade/xxx.trig")
        res    = plugin.transform(model)
      } yield assertTrue(true)
    }
  }
}
