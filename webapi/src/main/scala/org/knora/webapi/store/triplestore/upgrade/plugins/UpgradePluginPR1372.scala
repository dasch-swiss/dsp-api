/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.{IRI, Model, Resource}
import org.knora.webapi.constances.OntologyConstants
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

import scala.collection.JavaConverters._

/**
 * Transforms a repository for Knora PR 1372.
 */
class UpgradePluginPR1372 extends UpgradePlugin {
    private val valueFactory = SimpleValueFactory.getInstance

    // RDF4J IRI objects representing the IRIs used in this transformation.
    private val ValueCreationDateIri: IRI = valueFactory.createIRI(OntologyConstants.KnoraBase.ValueCreationDate)
    private val PreviousValueIri: IRI = valueFactory.createIRI(OntologyConstants.KnoraBase.PreviousValue)
    private val HasPermissionsIri: IRI = valueFactory.createIRI(OntologyConstants.KnoraBase.HasPermissions)

    override def transform(model: Model): Unit = {
        // Remove knora-base:hasPermissions from all past value versions.
        for (valueIri: IRI <- collectPastValueIris(model)) {
            model.remove(
                valueIri,
                HasPermissionsIri,
                null
            )
        }
    }

    /**
     * Collects the IRIs of all values that are past value versions.
     */
    private def collectPastValueIris(model: Model): Set[IRI] = {
        model.filter(null, ValueCreationDateIri, null).subjects.asScala.toSet.filter {
            resource: Resource => model.filter(null, PreviousValueIri, resource).asScala.toSet.nonEmpty
        }.map {
            resource: Resource => valueFactory.createIRI(resource.stringValue)
        }
    }
}
