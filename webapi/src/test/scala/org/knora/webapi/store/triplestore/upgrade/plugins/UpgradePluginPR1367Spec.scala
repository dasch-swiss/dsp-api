/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
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

import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._

class UpgradePluginPR1367Spec extends UpgradePluginSpec {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(defaultFeatureFactoryConfig)

  "Upgrade plugin PR1367" should {
    "fix the datatypes of decimal literals" in {
      // Parse the input file.
      val model: RdfModel = trigFileToModel("test_data/upgrade/pr1367.trig")

      // Use the plugin to transform the input.
      val plugin = new UpgradePluginPR1367(defaultFeatureFactoryConfig)
      plugin.transform(model)

      // Check that the decimal datatype was fixed.

      val subj = nodeFactory.makeIriNode("http://rdfh.ch/0001/thing-with-history/values/1")
      val pred = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ValueHasDecimal)

      model
        .find(
          subj = Some(subj),
          pred = Some(pred),
          obj = None
        )
        .toSet
        .headOption match {
        case Some(statement: Statement) =>
          statement.obj match {
            case datatypeLiteral: DatatypeLiteral =>
              assert(datatypeLiteral.datatype == OntologyConstants.Xsd.Decimal)

            case other =>
              throw AssertionException(s"Unexpected object for $pred: $other")
          }

        case None => throw AssertionException(s"No statement found with subject $subj and predicate $pred")
      }
    }
  }
}
