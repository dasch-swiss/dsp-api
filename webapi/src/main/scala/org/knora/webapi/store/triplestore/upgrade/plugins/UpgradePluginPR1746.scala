/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.Logger
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for Knora PR 1746.
 */
class UpgradePluginPR1746(featureFactoryConfig: FeatureFactoryConfig, log: Logger) extends UpgradePlugin {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(featureFactoryConfig)

  private val dummyString = "FIXME"

  override def transform(model: RdfModel): Unit = {
    val statementsToRemove: collection.mutable.Set[Statement] = collection.mutable.Set.empty
    val statementsToAdd: collection.mutable.Set[Statement] = collection.mutable.Set.empty

    def replaceEmptyStringWithDummy(statement: Statement, languageTag: Option[String]): Unit = {
      val fixMeString: RdfLiteral = languageTag match {
        case Some(definedLanguageTag) =>
          nodeFactory.makeStringWithLanguage(dummyString, definedLanguageTag)

        case None => nodeFactory.makeStringLiteral(dummyString)
      }

      statementsToRemove += statement

      statementsToAdd += nodeFactory.makeStatement(
        subj = statement.subj,
        pred = statement.pred,
        obj = fixMeString,
        context = statement.context
      )

      log.warn(s"Changed empty object of <${statement.subj}> <${statement.pred}> to FIXME")
    }

    for (statement: Statement <- model) {
      statement.obj match {
        case rdfLiteral: RdfLiteral =>
          if (rdfLiteral.stringValue.isEmpty) {
            rdfLiteral match {
              case datatypeLiteral: DatatypeLiteral =>
                if (datatypeLiteral.datatype == OntologyConstants.Xsd.String) {
                  replaceEmptyStringWithDummy(
                    statement = statement,
                    languageTag = None
                  )
                }

              case stringWithLanguage: StringWithLanguage =>
                replaceEmptyStringWithDummy(
                  statement = statement,
                  languageTag = Some(stringWithLanguage.language)
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
