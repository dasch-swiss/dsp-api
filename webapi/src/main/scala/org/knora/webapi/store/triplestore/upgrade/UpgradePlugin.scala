package org.knora.webapi.store.triplestore.upgrade

import org.eclipse.rdf4j.model.Model

/**
 * A trait for plugins that update a repository.
 */
trait UpgradePlugin {
    /**
     * Transforms a repository.
     *
     * @param model a [[Model]] containing the repository data.
     */
    def transform(model: Model): Unit
}
