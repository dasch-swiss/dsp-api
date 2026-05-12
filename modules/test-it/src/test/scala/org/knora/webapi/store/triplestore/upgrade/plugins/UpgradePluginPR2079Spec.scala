/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import dsp.errors.AssertionException
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.*

object UpgradePluginPR2079Spec extends ZIOSpecDefault with UpgradePluginSpec {

  val spec: Spec[Any, Nothing] = suite("Upgrade plugin PR2079")(
    test("fix the missing valueHasUri datatype") {
      // Parse the input file.
      val model: RdfModel = trigFileToModel("test_data/upgrade/pr2079.trig")

      // Use the plugin to transform the input.
      val plugin = new UpgradePluginPR2079()
      plugin.transform(model)

      // Check that the datatype was fixed.
      val subj = JenaNodeFactory.makeIriNode("http://rdfh.ch/0103/fN89IUgvSSyMxJ7XWssP9w/values/Rl2rfjDlRBWeuRr-EgIgCw")
      val pred = JenaNodeFactory.makeIriNode(OntologyConstants.KnoraBase.ValueHasUri)

      val datatypeOk = model
        .find(
          subj = Some(subj),
          pred = Some(pred),
          obj = None,
        )
        .toSet
        .headOption match {
        case Some(statement: Statement) =>
          statement.obj match {
            case datatypeLiteral: DatatypeLiteral =>
              datatypeLiteral.datatype == OntologyConstants.Xsd.Uri

            case other =>
              throw AssertionException(s"Unexpected object for $pred: $other")
          }

        case None => throw AssertionException(s"No statement found with subject $subj and predicate $pred")
      }

      assertTrue(datatypeOk)
    },
  )
}
