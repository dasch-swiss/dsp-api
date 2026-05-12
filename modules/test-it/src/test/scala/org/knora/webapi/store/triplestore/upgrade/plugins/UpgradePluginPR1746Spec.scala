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

object UpgradePluginPR1746Spec extends ZIOSpecDefault with UpgradePluginSpec {

  private def checkLiteral(model: RdfModel, subj: IriNode, pred: IriNode, expectedObj: RdfLiteral): Boolean =
    model
      .find(
        subj = Some(subj),
        pred = Some(pred),
        obj = None,
      )
      .toSet
      .headOption match {
      case Some(statement: Statement) =>
        statement.obj match {
          case rdfLiteral: RdfLiteral => rdfLiteral == expectedObj
          case other                  => throw AssertionException(s"Unexpected object for $pred: $other")
        }

      case None => throw AssertionException(s"No statement found with subject $subj and predicate $pred")
    }

  val spec: Spec[Any, Nothing] = suite("Upgrade plugin PR1746")(
    test("replace empty string with FIXME") {
      // Parse the input file.
      val model: RdfModel = trigFileToModel("test_data/upgrade/pr1746.trig")

      // Use the plugin to transform the input.
      val plugin = new UpgradePluginPR1746()
      plugin.transform(model)

      // Check that the empty valueHasString is replaced with FIXME.
      val firstOk = checkLiteral(
        model = model,
        subj = JenaNodeFactory.makeIriNode("http://rdfh.ch/0001/thing-with-empty-string/values/1"),
        pred = JenaNodeFactory.makeIriNode(OntologyConstants.KnoraBase.ValueHasString),
        expectedObj = JenaNodeFactory.makeStringLiteral("FIXME"),
      )

      // Check that the empty string literal value with lang tag is replaced with FIXME.
      val secondOk = checkLiteral(
        model = model,
        subj = JenaNodeFactory.makeIriNode("http://rdfh.ch/projects/XXXX"),
        pred = JenaNodeFactory.makeIriNode("http://www.knora.org/ontology/knora-admin#projectDescription"),
        expectedObj = JenaNodeFactory.makeStringWithLanguage(value = "FIXME", language = "en"),
      )

      assertTrue(firstOk, secondOk)
    },
  )
}
