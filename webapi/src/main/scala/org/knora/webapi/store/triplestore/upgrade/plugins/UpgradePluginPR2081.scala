/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.Logger
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin
import java.time.Instant

/**
 * Transforms a repository for Knora PR 2081.
 * Fixes wrong date serialisations (all `xsd:dateTime` in the database should end on `Z` rather than specifying a time zone).
 */
class UpgradePluginPR2081(log: Logger) extends UpgradePlugin {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()

  override def transform(model: RdfModel): Unit = {
    val statementsToRemove: collection.mutable.Set[Statement] = collection.mutable.Set.empty
    val statementsToAdd: collection.mutable.Set[Statement]    = collection.mutable.Set.empty

    val newObjectValue: String => DatatypeLiteral = (in: String) =>
      nodeFactory.makeDatatypeLiteral(Instant.parse(in).toString, OntologyConstants.Xsd.DateTime)
    val shouldTransform: DatatypeLiteral => Boolean = (literal: DatatypeLiteral) =>
      (literal.datatype == OntologyConstants.Xsd.DateTime &&
        literal.value != newObjectValue(literal.value).value)

    for (statement: Statement <- model) {
      statement.obj match {
        case literal: DatatypeLiteral if shouldTransform(literal) =>
          val newValue = newObjectValue(literal.value)
          log.debug(s"Transformed ${literal.value} => ${newValue.value}")
          statementsToRemove += statement
          statementsToAdd += nodeFactory.makeStatement(
            subj = statement.subj,
            pred = statement.pred,
            obj = newValue,
            context = statement.context
          )
        case other => ()
      }
    }

    model.removeStatements(statementsToRemove.toSet)
    model.addStatements(statementsToAdd.toSet)
  }

}
