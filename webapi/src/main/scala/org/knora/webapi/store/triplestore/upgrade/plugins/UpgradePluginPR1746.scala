/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for Knora PR 1746.
 */
class UpgradePluginPR1746 extends UpgradePlugin {
  private val dummyString = "FIXME"

  override def transform(model: RdfModel): Unit = {
    val statementsToRemove: collection.mutable.Set[Statement] = collection.mutable.Set.empty
    val statementsToAdd: collection.mutable.Set[Statement]    = collection.mutable.Set.empty

    def replaceEmptyStringWithDummy(statement: Statement, languageTag: Option[String]): Unit = {
      val fixMeString: RdfLiteral = languageTag match {
        case Some(definedLanguageTag) =>
          JenaNodeFactory.makeStringWithLanguage(dummyString, definedLanguageTag)

        case None => JenaNodeFactory.makeStringLiteral(dummyString)
      }

      statementsToRemove += statement

      statementsToAdd += JenaNodeFactory.makeStatement(
        subj = statement.subj,
        pred = statement.pred,
        obj = fixMeString,
        context = statement.context,
      )
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
                    languageTag = None,
                  )
                }

              case stringWithLanguage: StringWithLanguage =>
                replaceEmptyStringWithDummy(
                  statement = statement,
                  languageTag = Some(stringWithLanguage.language),
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
