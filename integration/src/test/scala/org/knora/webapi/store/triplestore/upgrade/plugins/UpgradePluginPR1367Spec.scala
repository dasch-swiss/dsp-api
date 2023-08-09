/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import dsp.errors.AssertionException
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._

class UpgradePluginPR1367Spec extends UpgradePluginSpec {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()

  "Upgrade plugin PR1367" should {
    "fix the datatypes of decimal literals" in {
      // Parse the input file.
      val model: RdfModel = trigFileToModel("../test_data/upgrade/pr1367.trig")

      // Use the plugin to transform the input.
      val plugin = new UpgradePluginPR1367()
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
