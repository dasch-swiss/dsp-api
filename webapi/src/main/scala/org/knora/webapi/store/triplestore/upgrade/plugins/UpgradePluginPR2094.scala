/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for Knora PR 2094.
 * Transforms incorrect value of valueHasUri from node to string type adding also
 * missing datatype ^^<http://www.w3.org/2001/XMLSchema#anyURI>
 */
class UpgradePluginPR2094 extends UpgradePlugin {
  override def transform(model: RdfModel): Unit = {
    val statementsToRemove: collection.mutable.Set[Statement] = collection.mutable.Set.empty
    val statementsToAdd: collection.mutable.Set[Statement]    = collection.mutable.Set.empty
    val newObjectValue: String => DatatypeLiteral             = (value: String) =>
      JenaNodeFactory.makeDatatypeLiteral(value, OntologyConstants.Xsd.Uri)

    for (statement: Statement <- model) {
      if (statement.pred.iri == OntologyConstants.KnoraBase.ValueHasUri) {
        statement.obj match {
          case node: IriNode =>
            statementsToRemove += statement

            statementsToAdd += JenaNodeFactory.makeStatement(
              subj = statement.subj,
              pred = statement.pred,
              obj = newObjectValue(node.iri),
              context = statement.context,
            )
          case _ => ()
        }
      }
    }

    model.removeStatements(statementsToRemove.toSet)
    model.addStatements(statementsToAdd.toSet)
  }
}
