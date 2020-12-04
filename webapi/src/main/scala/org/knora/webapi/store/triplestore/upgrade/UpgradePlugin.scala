package org.knora.webapi.store.triplestore.upgrade

import org.knora.webapi.messages.util.rdf.RdfModel

/**
  * A trait for plugins that update a repository.
  */
trait UpgradePlugin {

  /**
    * Transforms a repository.
    *
    * @param model an [[RdfModel]] containing the repository data.
    */
  def transform(model: RdfModel): Unit
}
