/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for Knora PR 1372.
 */
class UpgradePluginPR1372(featureFactoryConfig: FeatureFactoryConfig) extends UpgradePlugin {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(featureFactoryConfig)

  // IRI objects representing the IRIs used in this transformation.
  private val ValueCreationDateIri: IriNode = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ValueCreationDate)
  private val PreviousValueIri: IriNode     = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.PreviousValue)
  private val HasPermissionsIri: IriNode    = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.HasPermissions)

  override def transform(model: RdfModel): Unit =
    // Remove knora-base:hasPermissions from all past value versions.
    for (valueIri: IriNode <- collectPastValueIris(model)) {
      model.remove(
        subj = Some(valueIri),
        pred = Some(HasPermissionsIri),
        obj = None
      )
    }

  /**
   * Collects the IRIs of all values that are past value versions.
   */
  private def collectPastValueIris(model: RdfModel): Iterator[IriNode] =
    model
      .find(None, Some(ValueCreationDateIri), None)
      .map(_.subj)
      .filter { resource: RdfResource =>
        model
          .find(
            subj = None,
            pred = Some(PreviousValueIri),
            obj = Some(resource)
          )
          .nonEmpty
      }
      .map {
        case iriNode: IriNode => iriNode
        case other            => throw InconsistentRepositoryDataException(s"Unexpected subject for $ValueCreationDateIri: $other")
      }
}
