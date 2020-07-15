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

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.util.Models
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.{IRI, Model, Statement, Value}
import org.knora.webapi.constances.OntologyConstants
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin
import org.knora.webapi.util.JavaUtil._
import org.knora.webapi.exceptions.InconsistentTriplestoreDataException

import scala.collection.JavaConverters._

/**
 * Transforms a repository for Knora PR 1307.
 */
class UpgradePluginPR1307 extends UpgradePlugin {
    private val valueFactory = SimpleValueFactory.getInstance

    // RDF4J IRI objects representing the IRIs used in this transformation.
    private val TextValueIri: IRI = valueFactory.createIRI(OntologyConstants.KnoraBase.TextValue)
    private val ValueHasStandoffIri: IRI = valueFactory.createIRI(OntologyConstants.KnoraBase.ValueHasStandoff)
    private val StandoffTagHasStartIndexIri: IRI = valueFactory.createIRI(OntologyConstants.KnoraBase.StandoffTagHasStartIndex)
    private val StandoffTagHasStartParentIri: IRI = valueFactory.createIRI(OntologyConstants.KnoraBase.StandoffTagHasStartParent)
    private val StandoffTagHasEndParentIri: IRI = valueFactory.createIRI(OntologyConstants.KnoraBase.StandoffTagHasEndParent)
    private val ValueHasMaxStandoffStartIndexIri: IRI = valueFactory.createIRI(OntologyConstants.KnoraBase.ValueHasMaxStandoffStartIndex)

    /**
     * Represents a standoff tag to be transformed.
     *
     * @param oldIri     the tag's old IRI.
     * @param statements the statements about the tag.
     */
    case class StandoffRdf(oldIri: IRI, statements: Model) {
        /**
         * The value of knora-base:standoffTagHasStartIndex.
         */
        val startIndex: Int = Models.getPropertyLiteral(statements, oldIri, StandoffTagHasStartIndexIri).toOption match {
            case Some(index) => index.intValue
            case None => throw InconsistentTriplestoreDataException(s"$oldIri has no knora-base:standoffTagHasStartIndex")
        }

        /**
         * The tag's new IRI.
         */
        lazy val newIri: IRI = {
            val oldSubjStr: String = oldIri.stringValue
            val slashPos: Int = oldSubjStr.lastIndexOf('/')
            valueFactory.createIRI(oldSubjStr.substring(0, slashPos + 1) + startIndex.toString)
        }

        def transform(model: Model, standoff: Map[IRI, StandoffRdf]): Unit = {
            for (statement: Statement <- statements.asScala.toSet) {
                // Change statements with knora-base:standoffTagHasStartParent and knora-base:standoffTagHasEndParent to point
                // to the new IRIs of those tags.
                val newStatementObj: Value = if (statement.getPredicate == StandoffTagHasStartParentIri || statement.getPredicate == StandoffTagHasEndParentIri) {
                    val targetTagOldIri: IRI = valueFactory.createIRI(statement.getObject.stringValue)
                    standoff(targetTagOldIri).newIri
                } else {
                    statement.getObject
                }

                // Remove each statement that uses this tag's old IRI.
                model.remove(
                    oldIri,
                    statement.getPredicate,
                    statement.getObject,
                    statement.getContext
                )

                // Replace it with a statement that uses this tag's new IRI.
                model.add(
                    newIri,
                    statement.getPredicate,
                    newStatementObj,
                    statement.getContext
                )
            }
        }
    }

    /**
     * Represents a `knora-base:TextValue` to be transformed.
     *
     * @param iri                        the text value's IRI.
     * @param context                    the text value's context.
     * @param valueHasStandoffStatements the statements whose subject is the text value and whose predicate is
     *                                   `knora-base:valueHasStandoff`.
     * @param standoff                   the standoff tags attached to this text value, as a map of old standoff tag IRIs to
     *                                   [[StandoffRdf]] objects.
     */
    case class TextValueRdf(iri: IRI, context: IRI, valueHasStandoffStatements: Model, standoff: Map[IRI, StandoffRdf]) {
        def transform(model: Model): Unit = {
            // Transform the text value's standoff tags.
            for (standoffTag <- standoff.values) {
                standoffTag.transform(model = model, standoff = standoff)
            }

            if (standoff.nonEmpty) {
                for (statement: Statement <- valueHasStandoffStatements.asScala.toSet) {
                    // Replace each statement in valueHasStandoffStatements with one that points to the standoff
                    // tag's new IRI.

                    val targetTagOldIri: IRI = valueFactory.createIRI(statement.getObject.stringValue)
                    val targetTagNewIri: IRI = standoff(targetTagOldIri).newIri

                    model.remove(
                        iri,
                        statement.getPredicate,
                        statement.getObject,
                        statement.getContext
                    )

                    model.add(
                        iri,
                        statement.getPredicate,
                        targetTagNewIri,
                        statement.getContext
                    )
                }

                // Add a statement to the text value with the predicate knora-base:valueHasMaxStandoffStartIndex.
                model.add(
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
            textValue.transform(model)
        }
    }

    /**
     * Collects the text values and standoff tags in the repository.
     */
    private def collectTextValues(model: Model): Vector[TextValueRdf] = {
        // Pairs of text value IRI and text value context.
        val textValueSubjectsAndContexts: Vector[(IRI, IRI)] = model.filter(null, RDF.TYPE, TextValueIri).asScala.map {
            statement =>
                (valueFactory.createIRI(statement.getSubject.stringValue), valueFactory.createIRI(statement.getContext.stringValue))
        }.toVector

        textValueSubjectsAndContexts.map {
            case (textValueSubj: IRI, textValueContext: IRI) =>
                // Get the statements about the text value.
                val textValueStatements: Model = model.filter(textValueSubj, null, null)

                // Get the statements whose subject is the text value and whose predicate is knora-base:valueHasStandoff.
                val valueHasStandoffStatements: Model = textValueStatements.filter(null, ValueHasStandoffIri, null)

                // Get the IRIs of the text value's standoff tags.
                val standoffSubjects: Set[IRI] = valueHasStandoffStatements.objects.asScala.map {
                    value => valueFactory.createIRI(value.stringValue)
                }.toSet

                // Make a map of standoff IRIs to StandoffRdf objects.
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
