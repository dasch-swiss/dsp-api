/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.Logger
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for Knora PR 1921.
 */
class UpgradePluginPR1921(featureFactoryConfig: FeatureFactoryConfig, log: Logger) extends UpgradePlugin {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(featureFactoryConfig)
  // Group descriptions without language attribute get language attribute defined in DEFAULT_LANG
  private val DEFAULT_LANG = "en"

  override def transform(model: RdfModel): Unit = {
    val statementsToRemove: collection.mutable.Set[Statement] = collection.mutable.Set.empty
    val statementsToAdd: collection.mutable.Set[Statement] = collection.mutable.Set.empty

    val newPredicateLabel: IriNode =
      nodeFactory.makeIriNode("http://www.knora.org/ontology/knora-admin#groupDescriptions")

    def updateGroupDescription(statement: Statement, languageTag: Option[String]): Unit =
      languageTag match {
        // the group description did not have a language attribute
        case Some(lang) =>
          val groupDescriptionWithLanguage: RdfLiteral =
            nodeFactory.makeStringWithLanguage(statement.obj.stringValue, lang)

          statementsToRemove += statement

          statementsToAdd += nodeFactory.makeStatement(
            subj = statement.subj,
            pred = newPredicateLabel,
            obj = groupDescriptionWithLanguage,
            context = statement.context
          )

          log.warn(
            s"Updated <${statement.subj}> <${statement.pred}> to <${newPredicateLabel.stringValue}> with <${groupDescriptionWithLanguage}>"
          )

        // the group description did already have a language attribute
        case None =>
          statementsToRemove += statement

          statementsToAdd += nodeFactory.makeStatement(
            subj = statement.subj,
            pred = newPredicateLabel,
            obj = statement.obj,
            context = statement.context
          )
          log.warn(s"Updated <${statement.pred}> to <${newPredicateLabel.stringValue}>")
      }

    for (statement: Statement <- model) {
      statement.pred match {
        case predicate: IriNode =>
          if (predicate.stringValue == "http://www.knora.org/ontology/knora-admin#groupDescriptions") {
            statement.obj match {
              case stringWithLanguage: StringWithLanguage =>
                ()
              case stringWithLanguage: StringLiteralV2 =>
                ()
              case _ =>
                updateGroupDescription(
                  statement = statement,
                  languageTag = Some(DEFAULT_LANG)
                )
            }
          }

          if (predicate.stringValue == "http://www.knora.org/ontology/knora-admin#groupDescription") {
            statement.obj match {
              case stringWithLanguage: StringWithLanguage =>
                updateGroupDescription(statement, None)
              case stringWithLanguage: StringLiteralV2 =>
                updateGroupDescription(statement, None)
              case _ =>
                updateGroupDescription(
                  statement = statement,
                  languageTag = Some(DEFAULT_LANG)
                )
            }
          }
        case _ => ()
      }
    }

    model.removeStatements(statementsToRemove.toSet)
    model.addStatements(statementsToAdd.toSet)
  }
}
