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

import java.util.UUID

import com.typesafe.scalalogging.Logger
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.InconsistentRepositoryDataException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.{OntologyConstants, StringFormatter}
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

import scala.util.{Failure, Success, Try}

/**
  * Transforms a repository for Knora PR 1885.
  */
class UpgradePluginPR1885(featureFactoryConfig: FeatureFactoryConfig) extends UpgradePlugin {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(featureFactoryConfig)
  private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

  private val ResourceHasUUIDIri: IriNode = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ResourceHasUUID)
  private val CreationDateIri: IriNode = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.CreationDate)

  override def transform(model: RdfModel): Unit = {
    // Add UUID to each resource.
    for (resourceIriNode: IriNode <- collectResourceIris(model)) {
      val resourceUUID = stringFormatter.getUUIDFromIriOrMakeRandom(resourceIriNode.iri)
      model.add(
        subj = resourceIriNode,
        pred = ResourceHasUUIDIri,
        obj = nodeFactory.makeStringLiteral(resourceUUID)
      )
    }
  }

  /**
    * Collects the IRIs of all resources.
    */
  private def collectResourceIris(model: RdfModel): Iterator[IriNode] = {
    model
      .find(None, Some(CreationDateIri), None)
      .map(_.subj)
      .map {
        case iriNode: IriNode => iriNode
        case other            => throw InconsistentRepositoryDataException(s"Unexpected subject for $CreationDateIri: $other")
      }
  }
}
