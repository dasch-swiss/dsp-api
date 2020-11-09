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

package org.knora.webapi.util.rdf

import org.knora.webapi.IRI

/**
 * Represents an RDF subject, predicate, or object.
 */
trait RdfNode

/**
 * Represents an RDF node that can be used as a subject.
 */
trait RdfResource extends RdfNode

/**
 * Represents a blank node.
 */
trait BlankNode extends RdfResource {
    def id: String
}

/**
 * Represents an IRI used as an RDF resource.
 */
trait IriNode extends RdfResource {
    def iri: IRI
}

/**
 * Represents an RDF literal.
 */
trait RdfLiteral extends RdfNode

/**
 * Represents a literal value with a datatype.
 */
trait DatatypeLiteral extends RdfLiteral {
    def value: String

    def datatype: IRI
}

/**
 * Represents a string value with a language tag.
 */
trait StringWithLanguage extends RdfLiteral {
    def value: String

    def language: String
}

/**
 * Represents an RDF statement.
 */
trait Statement {
    def subj: RdfResource

    def pred: IriNode

    def obj: RdfNode

    def context: Option[IRI]
}

/**
 * Represents an RDF model consisting of a default graph and/or one or more named graphs.
 */
trait RdfModel {
    /**
     * Constructs a blank node.
     */
    def makeBlankNode: BlankNode

    /**
     * Constructs a blank node with the specified ID.
     *
     * @param id the blank node ID.
     */
    def makeBlankNodeWithID(id: String): BlankNode

    /**
     * Constructs an IRI node.
     *
     * @param iri the IRI.
     */
    def makeIriNode(iri: IRI): IriNode

    /**
     * Constructs a literal value with a datatype.
     *
     * @param value    the lexical value of the literal.
     * @param datatype the datatype IRI.
     */
    def makeDatatypeLiteral(value: String, datatype: IRI): DatatypeLiteral

    /**
     * Constructs a string with a language tag.
     *
     * @param value    the string.
     * @param language the language tag.
     */
    def makeStringWithLanguage(value: String, language: String): StringWithLanguage

    /**
     * Constructs a statement.
     *
     * @param subj    the subject.
     * @param pred    the predicate.
     * @param obj     the object.
     * @param context the IRI of the named graph, or `None` to use the default graph.
     */
    def makeStatement(subj: RdfResource, pred: IriNode, obj: RdfNode, context: Option[IRI] = None): Statement

    /**
     * Adds a statement to the model.
     *
     * @param statement the statement to be added.
     */
    def addStatement(statement: Statement): Unit

    /**
     * Constructs a statement and adds it to the model.
     *
     * @param subj    the subject.
     * @param pred    the predicate.
     * @param obj     the object.
     * @param context the IRI of a named graph, or `None` to use the default graph.
     */
    def add(subj: RdfResource, pred: IriNode, obj: RdfNode, context: Option[IRI] = None): Unit

    /**
     * Removes statements from the model.
     *
     * @param subj    the subject, or `None` to match any subject.
     * @param pred    the predicate, or `None` to match any predicate.
     * @param obj     the object, or `None` to match any object.
     * @param context the IRI of a named graph, or `None` to match any graph.
     */
    def remove(subj: Option[RdfResource], pred: Option[IriNode], obj: Option[RdfNode], context: Option[IRI] = None): Unit

    /**
     * Returns statements that match a pattern.
     *
     * @param subj    the subject, or `None` to match any subject.
     * @param pred    the predicate, or `None` to match any predicate.
     * @param obj     the object, or `None` to match any object.
     * @param context the IRI of a named graph, or `None` to match any graph.
     * @return the statements matching the pattern.
     */
    def find(subj: Option[RdfResource], pred: Option[IriNode], obj: Option[RdfNode], context: Option[IRI] = None): Set[Statement]
}
