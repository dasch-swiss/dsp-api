/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for Knora PR 1615.
 */
class UpgradePluginPR1615() extends UpgradePlugin {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()

  // IRI objects representing the IRIs used in this transformation.
  private val ForbiddenResourceIri: IriNode = nodeFactory.makeIriNode("http://rdfh.ch/0000/forbiddenResource")

  override def transform(model: RdfModel): Unit =
    // Remove the singleton instance of knora-base:ForbiddenResource.
    model.remove(
      subj = Some(ForbiddenResourceIri),
      pred = None,
      obj = None
    )
}
