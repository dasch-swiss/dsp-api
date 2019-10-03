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

package org.knora.upgrade.plugins

import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.{IRI, Model, Resource}
import org.knora.webapi.OntologyConstants
import org.knora.upgrade.UpgradePlugin
import org.knora.webapi.util.StringFormatter

import scala.collection.JavaConverters._

/**
  * Transforms a repository for Knora PR 1322.
  */
class UpdatePluginPR1322 extends UpgradePlugin {
    private val valueFactory = SimpleValueFactory.getInstance
    private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

    // RDF4J IRI objects representing the IRIs used in this transformation.
    private val ValueHasUUIDIri: IRI = valueFactory.createIRI(OntologyConstants.KnoraBase.ValueHasUUID)
    private val ValueCreationDateIri : IRI= valueFactory.createIRI(OntologyConstants.KnoraBase.ValueCreationDate)
    private val PreviousValueIri: IRI = valueFactory.createIRI(OntologyConstants.KnoraBase.PreviousValue)

    override def transform(model: Model): Unit = {
        // Add a random UUID to each current value version.
        for (valueIri: IRI <- collectCurrentValueIris(model)) {
            model.add(
                valueIri,
                ValueHasUUIDIri,
                valueFactory.createLiteral(stringFormatter.makeRandomBase64EncodedUuid)
            )
        }
    }

    /**
      * Collects the IRIs of all values that are current value versions.
      */
    private def collectCurrentValueIris(model: Model): Set[IRI] = {
        model.filter(null, ValueCreationDateIri, null).subjects.asScala.toSet.filter {
            resource: Resource => model.filter(null, PreviousValueIri, resource).asScala.toSet.isEmpty
        }.map {
            resource: Resource => valueFactory.createIRI(resource.stringValue)
        }
    }
}
