/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.compatible.Assertion

import dsp.errors.AssertionException
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.*

class UpgradePluginPR1746Spec extends UpgradePluginSpec with LazyLogging {
  private def checkLiteral(model: RdfModel, subj: IriNode, pred: IriNode, expectedObj: RdfLiteral): Assertion =
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
          case rdfLiteral: RdfLiteral => assert(rdfLiteral == expectedObj)
          case other                  => throw AssertionException(s"Unexpected object for $pred: $other")
        }

      case None => throw AssertionException(s"No statement found with subject $subj and predicate $pred")
    }

  "Upgrade plugin PR1746" should {
    "replace empty string with FIXME" in {
      // Parse the input file.
      val model: RdfModel = trigFileToModel("test_data/upgrade/pr1746.trig")

      // Use the plugin to transform the input.
      val plugin = new UpgradePluginPR1746(log)
      plugin.transform(model)

      // Check that the empty valueHasString is replaced with FIXME.
      checkLiteral(
        model = model,
        subj = JenaNodeFactory.makeIriNode("http://rdfh.ch/0001/thing-with-empty-string/values/1"),
        pred = JenaNodeFactory.makeIriNode(OntologyConstants.KnoraBase.ValueHasString),
        expectedObj = JenaNodeFactory.makeStringLiteral("FIXME"),
      )

      // Check that the empty string literal value with lang tag is replaced with FIXME.
      checkLiteral(
        model = model,
        subj = JenaNodeFactory.makeIriNode("http://rdfh.ch/projects/XXXX"),
        pred = JenaNodeFactory.makeIriNode("http://www.knora.org/ontology/knora-admin#projectDescription"),
        expectedObj = JenaNodeFactory.makeStringWithLanguage(value = "FIXME", language = "en"),
      )
    }
  }
}
