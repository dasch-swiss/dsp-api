/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.update.plugins

import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.util.Models
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.{IRI, Model, Statement, Value}
import org.knora.webapi.update.UpdatePlugin
import org.knora.webapi.util.JavaUtil._
import org.knora.webapi.{InconsistentTriplestoreDataException, OntologyConstants}

import scala.collection.JavaConverters._

class UpdatePluginPR1307 extends UpdatePlugin {
    private val valueFactory = SimpleValueFactory.getInstance

    private val TextValueIri: IRI = valueFactory.createIRI(OntologyConstants.KnoraBase.TextValue)
    private val ValueHasStandoffIri: IRI = valueFactory.createIRI(OntologyConstants.KnoraBase.ValueHasStandoff)
    private val StandoffTagHasStartIndexIri : IRI= valueFactory.createIRI(OntologyConstants.KnoraBase.StandoffTagHasStartIndex)
    private val StandoffTagHasStartParentIri: IRI = valueFactory.createIRI(OntologyConstants.KnoraBase.StandoffTagHasStartParent)
    private val StandoffTagHasEndParentIri: IRI = valueFactory.createIRI(OntologyConstants.KnoraBase.StandoffTagHasEndParent)
    private val ValueHasMaxStandoffStartIndexIri: IRI = valueFactory.createIRI(OntologyConstants.KnoraBase.ValueHasMaxStandoffStartIndex)

    case class StandoffRdf(oldIri: IRI, statements: Model) {
        val startIndex: Int = Models.getPropertyLiteral(statements, oldIri, StandoffTagHasStartIndexIri).toOption match {
            case Some(index) => index.intValue
            case None => throw InconsistentTriplestoreDataException(s"$oldIri has no knora-base:standoffTagHasStartIndex")
        }

        lazy val newIri: IRI = {
            val oldSubjStr: String = oldIri.stringValue
            val slashPos: Int = oldSubjStr.lastIndexOf('/')
            valueFactory.createIRI(oldSubjStr.substring(0, slashPos + 1) + startIndex.toString)
        }

        def transform(standoff: Map[IRI, StandoffRdf]): Unit = {
            for (statement: Statement <- statements.asScala) {
                val newStatementObj: Value = if (statement.getPredicate == StandoffTagHasStartParentIri || statement.getPredicate == StandoffTagHasEndParentIri) {
                    val targetTagOldIri: IRI = valueFactory.createIRI(statement.getObject.stringValue)
                    standoff(targetTagOldIri).newIri
                } else {
                    statement.getObject
                }

                statements.remove(
                    oldIri,
                    statement.getPredicate,
                    statement.getObject,
                    statement.getContext
                )

                statements.add(
                    newIri,
                    statement.getPredicate,
                    newStatementObj,
                    statement.getContext
                )
            }
        }
    }

    case class TextValueRdf(iri: IRI, context: IRI, valueHasStandoffStatements: Model, standoff: Map[IRI, StandoffRdf]) {
        def transform(): Unit = {
            for (standoffTag <- standoff.values) {
                standoffTag.transform(standoff)
            }

            if (standoff.nonEmpty) {
                for (statement: Statement <- valueHasStandoffStatements.asScala) {
                    val targetTagOldIri: IRI = valueFactory.createIRI(statement.getObject.stringValue)
                    val targetTagNewIri: IRI = standoff(targetTagOldIri).newIri

                    valueHasStandoffStatements.remove(
                        iri,
                        statement.getPredicate,
                        statement.getObject,
                        statement.getContext
                    )

                    valueHasStandoffStatements.add(
                        iri,
                        statement.getPredicate,
                        targetTagNewIri,
                        statement.getContext
                    )
                }

                valueHasStandoffStatements.add(
                    iri,
                    ValueHasMaxStandoffStartIndexIri,
                    valueFactory.createLiteral(new java.math.BigInteger(standoff.values.map(_.startIndex).max.toString)),
                    context
                )
            }
        }
    }

    override def transform(model: Model): Unit = {
        for (textValue <- collectTextValues(model)) {
            textValue.transform()
        }
    }

    private def collectTextValues(model: Model): Vector[TextValueRdf] = {
        val textValueSubjectsAndContexts: Vector[(IRI, IRI)] = model.filter(null, RDF.TYPE, TextValueIri).asScala.map {
            statement =>
                (valueFactory.createIRI(statement.getSubject.stringValue), valueFactory.createIRI(statement.getContext.stringValue))
        }.toVector

        textValueSubjectsAndContexts.map {
            case (textValueSubj: IRI, textValueContext: IRI) =>
                val textValueStatements: Model = model.filter(textValueSubj, null, null)
                val valueHasStandoffStatements: Model = textValueStatements.filter(null, ValueHasStandoffIri, null)

                val standoffSubjects: Set[IRI] = valueHasStandoffStatements.objects.asScala.map {
                    value => valueFactory.createIRI(value.stringValue)
                }.toSet

                val standoff: Map[IRI, StandoffRdf] = standoffSubjects.map {
                    standoffSubj: IRI =>
                        standoffSubj -> StandoffRdf(
                            oldIri = standoffSubj,
                            statements = model.filter(standoffSubj, null, null)
                        )
                }.toMap

                TextValueRdf(
                    iri = textValueSubj,
                    context = textValueContext,
                    valueHasStandoffStatements = valueHasStandoffStatements,
                    standoff = standoff
                )
        }
    }
}
