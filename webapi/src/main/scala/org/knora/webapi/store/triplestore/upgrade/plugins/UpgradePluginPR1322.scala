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

import org.knora.webapi.exceptions.InconsistentRepositoryDataException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.{OntologyConstants, StringFormatter}
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin


/**
 * Transforms a repository for Knora PR 1322.
 */
class UpgradePluginPR1322(featureFactoryConfig: FeatureFactoryConfig) extends UpgradePlugin {
    private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(featureFactoryConfig)
    private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

    // IRI objects representing the IRIs used in this transformation.
    private val ValueHasUUIDIri: IriNode = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ValueHasUUID)
    private val ValueCreationDateIri: IriNode = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ValueCreationDate)
    private val PreviousValueIri: IriNode = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.PreviousValue)

    override def transform(model: RdfModel): Unit = {
        // Add a random UUID to each current value version.
        for (valueIri: IriNode <- collectCurrentValueIris(model)) {
            model.add(
                subj = valueIri,
                pred = ValueHasUUIDIri,
                obj = nodeFactory.makeStringLiteral(stringFormatter.makeRandomBase64EncodedUuid)
            )
        }
    }

    /**
     * Collects the IRIs of all values that are current value versions.
     */
    private def collectCurrentValueIris(model: RdfModel): Iterator[IriNode] = {
        model.find(None, Some(ValueCreationDateIri), None).map(_.subj).filter {
            resource: RdfResource => model.find(
                subj = None,
                pred = Some(PreviousValueIri),
                obj = Some(resource)
            ).isEmpty
        }.map {
            case iriNode: IriNode => iriNode
            case other => throw InconsistentRepositoryDataException(s"Unexpected subject for $ValueCreationDateIri: $other")
        }
    }
}
