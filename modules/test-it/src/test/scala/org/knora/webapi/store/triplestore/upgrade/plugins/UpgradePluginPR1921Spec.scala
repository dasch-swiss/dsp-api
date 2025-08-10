/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.compatible.Assertion

import dsp.errors.AssertionException
import org.knora.webapi.messages.util.rdf.*

class UpgradePluginPR1921Spec extends UpgradePluginSpec with LazyLogging {
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

  "Upgrade plugin PR921" should {
    // Parse the input file.
    val model: RdfModel = trigFileToModel("test_data/upgrade/pr1921.trig")
    // Use the plugin to transform the input.
    val plugin = new UpgradePluginPR1921(log)
    plugin.transform(model)

    "replace simple strings in group descriptions with language strings" in {
      // Check that a group description without language attribute gets a language attribute. String is marked as string.
      checkLiteral(
        model = model,
        subj = JenaNodeFactory.makeIriNode("http://rdfh.ch/groups/0105/group-without-language-attribute-1"),
        pred = JenaNodeFactory.makeIriNode("http://www.knora.org/ontology/knora-admin#groupDescriptions"),
        expectedObj =
          JenaNodeFactory.makeStringWithLanguage("A group description without language attribute.", language = "en"),
      )

      // Check that a group description without language attribute gets a language attribute. String is not marked as string.
      checkLiteral(
        model = model,
        subj = JenaNodeFactory.makeIriNode("http://rdfh.ch/groups/0105/group-without-language-attribute-2"),
        pred = JenaNodeFactory.makeIriNode("http://www.knora.org/ontology/knora-admin#groupDescriptions"),
        expectedObj =
          JenaNodeFactory.makeStringWithLanguage("A group description without language attribute.", language = "en"),
      )

      // Check that a group description with old predicate name and without language attribute gets a language attribute. String is marked as string.
      checkLiteral(
        model = model,
        subj = JenaNodeFactory.makeIriNode("http://rdfh.ch/groups/0105/group-without-language-attribute-3"),
        pred = JenaNodeFactory.makeIriNode("http://www.knora.org/ontology/knora-admin#groupDescriptions"),
        expectedObj = JenaNodeFactory.makeStringWithLanguage(
          "A group description with old predicate name and without language attribute.",
          language = "en",
        ),
      )

      // Check that a group description with old predicate name and without language attribute gets a language attribute. String is not marked as string.
      checkLiteral(
        model = model,
        subj = JenaNodeFactory.makeIriNode("http://rdfh.ch/groups/0105/group-without-language-attribute-4"),
        pred = JenaNodeFactory.makeIriNode("http://www.knora.org/ontology/knora-admin#groupDescriptions"),
        expectedObj = JenaNodeFactory.makeStringWithLanguage(
          "A group description with old predicate name and without language attribute.",
          language = "en",
        ),
      )
    }
    "not change group descriptions which have language attributes" in {
      // Check that a group description with a language attribute is not changed.
      checkLiteral(
        model = model,
        subj = JenaNodeFactory.makeIriNode("http://rdfh.ch/groups/0105/group-with-language-attribute-de"),
        pred = JenaNodeFactory.makeIriNode("http://www.knora.org/ontology/knora-admin#groupDescriptions"),
        expectedObj = JenaNodeFactory.makeStringWithLanguage(value = "Eine Gruppe mit Sprachattribut.", language = "de"),
      )

      // Check that a group description with default language attribute is not changed.
      checkLiteral(
        model = model,
        subj = JenaNodeFactory.makeIriNode("http://rdfh.ch/groups/0105/group-with-language-attribute-en"),
        pred = JenaNodeFactory.makeIriNode("http://www.knora.org/ontology/knora-admin#groupDescriptions"),
        expectedObj =
          JenaNodeFactory.makeStringWithLanguage(value = "A group with language attribute.", language = "en"),
      )
    }
  }
}
