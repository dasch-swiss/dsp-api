/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraBase.LastModificationDate
import org.knora.webapi.messages.OntologyConstants.Owl.Ontology
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

import java.time.Instant

/**
 * Transforms a repository for DSP-API PR 2018.
 */
class UpgradePluginPR2018(featureFactoryConfig: FeatureFactoryConfig) extends UpgradePlugin {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(featureFactoryConfig)

  private val newModificationDate = Instant.now.toString

  private val ontologyType: IriNode = nodeFactory.makeIriNode(Ontology)

  override def transform(model: RdfModel): Unit =
    model.add(
      subj = nodeFactory.makeIriNode("http://www.knora.org/ontology/7777/test"),
      pred = nodeFactory.makeIriNode(LastModificationDate),
      obj = nodeFactory.makeDatatypeLiteral(
        value = newModificationDate,
        datatype = OntologyConstants.Xsd.DateTime
      )
    )
}
