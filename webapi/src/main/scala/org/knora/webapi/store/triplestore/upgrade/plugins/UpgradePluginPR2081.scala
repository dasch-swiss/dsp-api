/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import java.time.Instant

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for Knora PR 2081.
 * Fixes wrong date serialisations (all `xsd:dateTime` in the database should end on `Z` rather than specifying a time zone).
 */
class UpgradePluginPR2081 extends UpgradePlugin {
  override def transform(model: RdfModel): Unit = {
    val statementsToRemove: collection.mutable.Set[Statement] = collection.mutable.Set.empty
    val statementsToAdd: collection.mutable.Set[Statement]    = collection.mutable.Set.empty

    val newObjectValue: String => DatatypeLiteral = (in: String) =>
      JenaNodeFactory.makeDatatypeLiteral(Instant.parse(in).toString, OntologyConstants.Xsd.DateTime)
    val shouldTransform: DatatypeLiteral => Boolean = (literal: DatatypeLiteral) =>
      (literal.datatype == OntologyConstants.Xsd.DateTime &&
        literal.value != newObjectValue(literal.value).value)

    for (statement: Statement <- model) {
      statement.obj match {
        case literal: DatatypeLiteral if shouldTransform(literal) =>
          val newValue = newObjectValue(literal.value)
          statementsToRemove += statement
          statementsToAdd += JenaNodeFactory.makeStatement(
            subj = statement.subj,
            pred = statement.pred,
            obj = newValue,
            context = statement.context,
          )
        case _ => ()
      }
    }

    model.removeStatements(statementsToRemove.toSet)
    model.addStatements(statementsToAdd.toSet)
  }

}
