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
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for Knora PR 1367.
 */
class UpgradePluginPR1367(featureFactoryConfig: FeatureFactoryConfig) extends UpgradePlugin {
    private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(featureFactoryConfig)

    override def transform(model: RdfModel): Unit = {
        // Fix the datatypes of decimal literals.

        val statementsToRemove: collection.mutable.Set[Statement] = collection.mutable.Set.empty
        val statementsToAdd: collection.mutable.Set[Statement] = collection.mutable.Set.empty

        for (statement: Statement <- model) {
            statement.obj match {
                case literal: DatatypeLiteral =>
                    if (literal.datatype == "http://www.w3.org/2001/XMLSchema#valueHasDecimal") {
                        statementsToRemove += statement

                        statementsToAdd += nodeFactory.makeStatement(
                            subj = statement.subj,
                            pred = statement.pred,
                            obj = nodeFactory.makeDatatypeLiteral(literal.value, OntologyConstants.Xsd.Decimal),
                            context = statement.context
                        )
                    }

                case _ => ()
            }
        }

        model.removeStatements(statementsToRemove.toSet)
        model.addStatements(statementsToAdd.toSet)
    }
}
