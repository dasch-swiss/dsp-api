/*
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

package org.knora.webapi.update.plugins

import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.util.Models
import org.eclipse.rdf4j.model.{Literal, Model}
import org.knora.webapi.OntologyConstants
import org.knora.webapi.util.JavaUtil._

class UpdatePluginPR1367Spec extends UpdatePluginSpec {
    private val valueFactory = SimpleValueFactory.getInstance

    "Update plugin PR1367" should {
        "fix the datatypes of decimal literals" in {
            // Parse the input file.
            val model: Model = trigFileToModel("src/test/resources/test-data/update/pr1367.trig")

            // Use the plugin to transform the input.
            val plugin = new UpdatePluginPR1367
            plugin.transform(model)

            // Check that the decimal datatype was fixed.
            val literal: Literal = Models.getPropertyLiteral(
                model,
                valueFactory.createIRI("http://rdfh.ch/0001/thing-with-history/values/1"),
                valueFactory.createIRI(OntologyConstants.KnoraBase.ValueHasDecimal)
            ).toOption.get

            assert(literal.getDatatype == valueFactory.createIRI(OntologyConstants.Xsd.Decimal))
        }
    }
}
*/
