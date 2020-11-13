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
import org.eclipse.rdf4j.model.util.Models
import org.eclipse.rdf4j.model.{Literal, Model}
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.util.JavaUtil._

class UpgradePluginPR1746Spec extends UpgradePluginSpec {
    private val valueFactory = SimpleValueFactory.getInstance

    "Upgrade plugin PR1746" should {
        "replace empty string with FIXME" in {
            // Parse the input file.
            val model: Model = trigFileToModel("test_data/upgrade/pr1746.trig")

            // Use the plugin to transform the input.
            val plugin = new UpgradePluginPR1746
            plugin.transform(model)

            // Check that the empty valueHasString is replaced with FIXME.
            val literal: Literal = Models.getPropertyLiteral(
                model,
                valueFactory.createIRI("http://rdfh.ch/0001/thing-with-empty-string/values/1"),
                valueFactory.createIRI(OntologyConstants.KnoraBase.ValueHasString)
            ).toOption.get

            assert(literal.stringValue() == "FIXME")

            // Check that the empty string literal value with lang tag is replaced with FIXME.
            val stringLiteral: Literal = Models.getPropertyLiteral(
                model,
                valueFactory.createIRI("http://rdfh.ch/projects/XXXX"),
                valueFactory.createIRI("http://www.knora.org/ontology/knora-admin#projectDescription")
            ).toOption.get

            assert(stringLiteral.getLabel == "FIXME")
            assert(stringLiteral.getLanguage.get == "en")
        }
    }
}
