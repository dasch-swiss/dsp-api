/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.LazyLogging

import dsp.errors.AssertionException
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._

class UpgradePluginPR2094Spec extends UpgradePluginSpec with LazyLogging {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()

  "Upgrade plugin PR2094" should {
    "fix the missing valueHasUri datatype" in {
      // Parse the input file.
      val model: RdfModel = trigFileToModel("../test_data/upgrade/pr2094.trig")

      // Use the plugin to transform the input.
      val plugin = new UpgradePluginPR2094(log)
      plugin.transform(model)

      // Check that the datatype was fixed.
      val subj =
        nodeFactory.makeIriNode("http://rdfh.ch/0103/5LE8P57nROClWUxEPJhiug/values/fEbt5NzaSe6GnCqKoF4Nhg/standoff/2")
      val pred = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ValueHasUri)

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
              assert(datatypeLiteral.datatype == OntologyConstants.Xsd.Uri)

            case other =>
              throw AssertionException(s"Unexpected object for $pred: $other")
          }

        case None => throw AssertionException(s"No statement found with subject $subj and predicate $pred")
      }
    }
  }
}
