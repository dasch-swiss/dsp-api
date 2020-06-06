package org.knora.webapi.store.triplestore.upgrade.plugins

import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for Knora PR 1615.
 */
class UpgradePluginPR1615 extends UpgradePlugin {
    private val valueFactory = SimpleValueFactory.getInstance

    // RDF4J IRI objects representing the IRIs used in this transformation.
    private val ForbiddenResourceIri = valueFactory.createIRI("http://rdfh.ch/0000/forbiddenResource")

    override def transform(model: Model): Unit = {
        // Remove the singleton instance of knora-base:ForbiddenResource.
        model.remove(ForbiddenResourceIri, null, null)
    }
}
