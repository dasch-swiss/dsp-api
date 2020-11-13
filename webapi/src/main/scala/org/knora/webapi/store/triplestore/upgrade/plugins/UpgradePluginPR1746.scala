package org.knora.webapi.store.triplestore.upgrade.plugins


import org.eclipse.rdf4j.model.{Literal, Model, Statement}
import org.eclipse.rdf4j.model.impl.{SimpleLiteral, SimpleValueFactory}
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

import scala.collection.JavaConverters._

/**
 * Transforms a repository for Knora PR 1746.
 */
class UpgradePluginPR1746 extends UpgradePlugin {
    private val valueFactory = SimpleValueFactory.getInstance

    override def transform(model: Model): Unit = {
        // change the empty strings to FIXME
        def replaceEmptyStringWithDummy(statement: Statement, languageTag: String) = {
            val fixMeString: Literal = if(languageTag.isEmpty) {
                valueFactory.createLiteral("FIXME")
            } else {
                valueFactory.createLiteral("FIXME", languageTag)
            }

            model.remove(
                statement.getSubject,
                statement.getPredicate,
                statement.getObject,
                statement.getContext
            )

            model.add(
                statement.getSubject,
                statement.getPredicate,
                fixMeString,
                statement.getContext
            )
        }

        for (statement: Statement <- model.asScala.toSet) {
            statement.getObject match {
                case literal: SimpleLiteral =>
                    if (literal.stringValue().isEmpty) {
                        val lang = literal.getLanguage.orElse("")
                        replaceEmptyStringWithDummy(statement, lang)
                    }

                case _ => ()
            }
        }
    }
}
