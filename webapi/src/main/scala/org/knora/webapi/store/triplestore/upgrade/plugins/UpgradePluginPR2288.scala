package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.Logger
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

class UpgradePluginPR2288(log: Logger) extends UpgradePlugin {

  /**
   * Transforms a repository.
   *
   * @param model an [[RdfModel]] containing the repository data.
   */
  override def transform(model: RdfModel): Unit = {
    log.info("Starting update")


    log.info("Finished update")
  }
}
