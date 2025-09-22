/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for Knora PR 1921.
 */
class UpgradePluginPR1921 extends UpgradePlugin {
  // Group descriptions without language attribute get language attribute defined in DEFAULT_LANG
  private val DEFAULT_LANG = "en"

  override def transform(model: RdfModel): Unit = {
    val statementsToRemove: collection.mutable.Set[Statement] = collection.mutable.Set.empty
    val statementsToAdd: collection.mutable.Set[Statement]    = collection.mutable.Set.empty

    val newPredicateLabel: IriNode =
      JenaNodeFactory.makeIriNode("http://www.knora.org/ontology/knora-admin#groupDescriptions")

    def updateGroupDescription(statement: Statement, languageTag: Option[String]): Unit =
      languageTag match {
        // the group description did not have a language attribute
        case Some(lang) =>
          val groupDescriptionWithLanguage: RdfLiteral =
            JenaNodeFactory.makeStringWithLanguage(statement.obj.stringValue, lang)

          statementsToRemove += statement

          statementsToAdd += JenaNodeFactory.makeStatement(
            subj = statement.subj,
            pred = newPredicateLabel,
            obj = groupDescriptionWithLanguage,
            context = statement.context,
          )

        // the group description did already have a language attribute
        case None =>
          statementsToRemove += statement

          statementsToAdd += JenaNodeFactory.makeStatement(
            subj = statement.subj,
            pred = newPredicateLabel,
            obj = statement.obj,
            context = statement.context,
          )
      }

    for (statement: Statement <- model) {
      val predicate = statement.pred
      if (predicate.stringValue == "http://www.knora.org/ontology/knora-admin#groupDescriptions") {
        statement.obj match {
          case _: StringWithLanguage =>
            ()
          case _: StringLiteralV2 =>
            ()
          case _ =>
            updateGroupDescription(
              statement = statement,
              languageTag = Some(DEFAULT_LANG),
            )
        }
      }

      if (predicate.stringValue == "http://www.knora.org/ontology/knora-admin#groupDescription") {
        statement.obj match {
          case _: StringWithLanguage =>
            updateGroupDescription(statement, None)
          case _: StringLiteralV2 =>
            updateGroupDescription(statement, None)
          case _ =>
            updateGroupDescription(
              statement = statement,
              languageTag = Some(DEFAULT_LANG),
            )
        }
      }
    }

    model.removeStatements(statementsToRemove.toSet)
    model.addStatements(statementsToAdd.toSet)
  }
}
