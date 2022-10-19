/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.LazyLogging

import org.knora.webapi.messages.util.rdf._

class UpgradePluginPR2255Spec extends UpgradePluginSpec with LazyLogging {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()

  "Upgrade plugin PR2255" should {
    "transform all project IRIs, that no old project IRI can be found in the data" in {
      // Parse the input file.
      val model: RdfModel = trigFileToModel("../test_data/upgrade/pr2255.trig")

      val numberOfStatementsBeforeTransformation = model.iterator.size

      // Use the plugin to transform the input.
      val plugin = new UpgradePluginPR2255(log)
      plugin.transform(model)

      val numberOfStatementsAfterTransformation = model.iterator.size

      // there should be the same number of statements before and after the transformation
      assert(numberOfStatementsBeforeTransformation == numberOfStatementsAfterTransformation)

      // Check projects IRIs was changed.
      ProjectsIrisToChange.newToOldIrisMap.foreach { case (oldIri, _) =>
        for {
          statement <- model

          // there shoudln't be any old project IRI in the data anymore,
          // both as a subject and an object
          _ = assert(statement.subj.stringValue != oldIri)
          _ = assert(statement.obj.stringValue != oldIri)

        } yield ()
      }
    }
  }
}
