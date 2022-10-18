/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.Logger

import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for DSP-API PR2255.
 * Transforms incorrect value of project IRIs from the one containing either shortcode or
 * not suppored UUID version to UUID v4 base64 encoded.
 */
class UpgradePluginPR2255(log: Logger) extends UpgradePlugin {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()

  override def transform(model: RdfModel): Unit = {
    val statementsToRemove: collection.mutable.Set[Statement] = collection.mutable.Set.empty
    val statementsToAdd: collection.mutable.Set[Statement]    = collection.mutable.Set.empty

    val iriToFind   = "http://rdfh.ch/projects/0001"
    val iriToChange = "http://rdfh.ch/projects/0123456789"
    val updatedNode = nodeFactory.makeIriNode(iriToChange)

    var count = 0

    for (statement: Statement <- model) {
      if (statement.subj.stringValue == iriToFind) {
        count = count + 1

        statementsToRemove += statement

        statementsToAdd += nodeFactory.makeStatement(
          subj = updatedNode,
          pred = statement.pred,
          obj = statement.obj
        )
      }

      if (statement.obj.stringValue == iriToFind) {
        count = count + 1

        statementsToRemove += statement

        statementsToAdd += nodeFactory.makeStatement(
          subj = statement.subj,
          pred = statement.pred,
          obj = updatedNode
        )
      }
    }

    model.removeStatements(statementsToRemove.toSet)
    model.addStatements(statementsToAdd.toSet)

    log.info(
      s"Transformed $count projectIris."
    )
  }
}
