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

import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.{IRI, Literal, Model, Statement}
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

import scala.collection.JavaConverters._

/**
  * Transforms a repository for Knora PR 1367.
  */
class UpgradePluginPR1367 extends UpgradePlugin {
    private val valueFactory = SimpleValueFactory.getInstance

    // RDF4J IRI objects representing the IRIs used in this transformation.
    private val XsdValueHasDecimalIri: IRI = valueFactory.createIRI("http://www.w3.org/2001/XMLSchema#valueHasDecimal")

    override def transform(model: Model): Unit = {
        // Fix the datatypes of decimal literals.
        for (statement: Statement <- model.asScala.toSet) {
            statement.getObject match {
                case literal: Literal =>
                    if (literal.getDatatype == XsdValueHasDecimalIri) {
                        model.remove(
                            statement.getSubject,
                            statement.getPredicate,
                            statement.getObject,
                            statement.getContext
                        )

                        model.add(
                            statement.getSubject,
                            statement.getPredicate,
                            valueFactory.createLiteral(BigDecimal(statement.getObject.stringValue).underlying),
                            statement.getContext
                        )
                    }

                case _ => ()
            }
        }
    }
}
