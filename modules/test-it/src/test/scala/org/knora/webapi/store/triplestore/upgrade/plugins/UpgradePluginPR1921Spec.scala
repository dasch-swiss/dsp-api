/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import dsp.errors.AssertionException
import org.knora.webapi.messages.util.rdf.*

object UpgradePluginPR1921Spec extends ZIOSpecDefault with UpgradePluginSpec {

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

  // Parse the input file.
  private val model: RdfModel = trigFileToModel("test_data/upgrade/pr1921.trig")
  // Use the plugin to transform the input.
  private val plugin = new UpgradePluginPR1921()
  plugin.transform(model)

  val spec: Spec[Any, Nothing] = suite("Upgrade plugin PR921")(
    test("replace simple strings in group descriptions with language strings") {
      // Check that a group description without language attribute gets a language attribute. String is marked as string.
      val ok1 = checkLiteral(
        model = model,
        subj = JenaNodeFactory.makeIriNode("http://rdfh.ch/groups/0105/group-without-language-attribute-1"),
        pred = JenaNodeFactory.makeIriNode("http://www.knora.org/ontology/knora-admin#groupDescriptions"),
        expectedObj =
          JenaNodeFactory.makeStringWithLanguage("A group description without language attribute.", language = "en"),
      )

      // Check that a group description without language attribute gets a language attribute. String is not marked as string.
      val ok2 = checkLiteral(
        model = model,
        subj = JenaNodeFactory.makeIriNode("http://rdfh.ch/groups/0105/group-without-language-attribute-2"),
        pred = JenaNodeFactory.makeIriNode("http://www.knora.org/ontology/knora-admin#groupDescriptions"),
        expectedObj =
          JenaNodeFactory.makeStringWithLanguage("A group description without language attribute.", language = "en"),
      )

      // Check that a group description with old predicate name and without language attribute gets a language attribute. String is marked as string.
      val ok3 = checkLiteral(
        model = model,
        subj = JenaNodeFactory.makeIriNode("http://rdfh.ch/groups/0105/group-without-language-attribute-3"),
        pred = JenaNodeFactory.makeIriNode("http://www.knora.org/ontology/knora-admin#groupDescriptions"),
        expectedObj = JenaNodeFactory.makeStringWithLanguage(
          "A group description with old predicate name and without language attribute.",
          language = "en",
        ),
      )

      // Check that a group description with old predicate name and without language attribute gets a language attribute. String is not marked as string.
      val ok4 = checkLiteral(
        model = model,
        subj = JenaNodeFactory.makeIriNode("http://rdfh.ch/groups/0105/group-without-language-attribute-4"),
        pred = JenaNodeFactory.makeIriNode("http://www.knora.org/ontology/knora-admin#groupDescriptions"),
        expectedObj = JenaNodeFactory.makeStringWithLanguage(
          "A group description with old predicate name and without language attribute.",
          language = "en",
        ),
      )

      assertTrue(ok1, ok2, ok3, ok4)
    },
    test("not change group descriptions which have language attributes") {
      // Check that a group description with a language attribute is not changed.
      val ok1 = checkLiteral(
        model = model,
        subj = JenaNodeFactory.makeIriNode("http://rdfh.ch/groups/0105/group-with-language-attribute-de"),
        pred = JenaNodeFactory.makeIriNode("http://www.knora.org/ontology/knora-admin#groupDescriptions"),
        expectedObj = JenaNodeFactory.makeStringWithLanguage(value = "Eine Gruppe mit Sprachattribut.", language = "de"),
      )

      // Check that a group description with default language attribute is not changed.
      val ok2 = checkLiteral(
        model = model,
        subj = JenaNodeFactory.makeIriNode("http://rdfh.ch/groups/0105/group-with-language-attribute-en"),
        pred = JenaNodeFactory.makeIriNode("http://www.knora.org/ontology/knora-admin#groupDescriptions"),
        expectedObj =
          JenaNodeFactory.makeStringWithLanguage(value = "A group with language attribute.", language = "en"),
      )

      assertTrue(ok1, ok2)
    },
  )
}
