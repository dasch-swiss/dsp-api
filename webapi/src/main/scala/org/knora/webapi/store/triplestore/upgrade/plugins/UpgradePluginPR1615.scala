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

import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
  * Transforms a repository for Knora PR 1615.
  */
class UpgradePluginPR1615(featureFactoryConfig: FeatureFactoryConfig) extends UpgradePlugin {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(featureFactoryConfig)

  // IRI objects representing the IRIs used in this transformation.
  private val ForbiddenResourceIri: IriNode = nodeFactory.makeIriNode("http://rdfh.ch/0000/forbiddenResource")

  override def transform(model: RdfModel): Unit = {
    // Remove the singleton instance of knora-base:ForbiddenResource.
    model.remove(
      subj = Some(ForbiddenResourceIri),
      pred = None,
      obj = None
    )
  }
}
