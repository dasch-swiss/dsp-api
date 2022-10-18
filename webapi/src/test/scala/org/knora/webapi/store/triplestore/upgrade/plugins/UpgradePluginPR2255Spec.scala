/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.LazyLogging

import dsp.errors.AssertionException
import org.knora.webapi.messages.util.rdf._

class UpgradePluginPR2255Spec extends UpgradePluginSpec with LazyLogging {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()

  "Upgrade plugin PR2255" should {
    "transform project IRIs" in {
      // Parse the input file.
      val model: RdfModel = trigFileToModel("../test_data/upgrade/pr2255.trig")

      // Use the plugin to transform the input.
      val plugin = new UpgradePluginPR2255(log)
      plugin.transform(model)

      // Check project IRI was changed.
      val subj =
        nodeFactory.makeIriNode("http://rdfh.ch/projects/0123456789")

      model
        .find(
          subj = Some(subj),
          pred = None,
          obj = None
        )
        .toSet
        .headOption match {
        case Some(statement: Statement) =>
          statement.subj match {
            case node: IriNode =>
              assert(node.iri == "http://rdfh.ch/projects/0123456789")

            case _ => ()
          }

        case None => throw AssertionException(s"No statement found with subject: $subj")
      }
    }
  }
}
