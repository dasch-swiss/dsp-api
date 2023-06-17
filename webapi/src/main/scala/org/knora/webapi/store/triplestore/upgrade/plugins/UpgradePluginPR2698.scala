package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.Logger
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin
import org.knora.webapi.messages.util.rdf.RdfModel

class UpgradePluginPR2689(log: Logger) extends UpgradePlugin {

  override def transform(model: RdfModel): Unit = ???

}
