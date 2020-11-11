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

package org.knora.webapi.messages.util.rdf

import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants

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
     * Returns an [[RdfNodeFactory]] that can be used create nodes for use with this model.
     */
    def getNodeFactory: RdfNodeFactory

    /**
     * Adds a statement to the model.
     *
     * @param statement the statement to be added.
     */
    def addStatement(statement: Statement): Unit

    /**
     * Adds one or more statements to the model.
     *
     * @param statements the statements to be added.
     */
    def addStatements(statements: Set[Statement]): Unit = {
        for (statement <- statements) {
            addStatement(statement)
        }
    }

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
     * Removes statements that match a pattern.
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

    /**
     * Returns a set of all the subjects in the model.
     */
    def getSubjects: Set[RdfResource]

    /**
     * Adds a namespace declaration to the model.
     *
     * @param prefix the namespace prefix.
     * @param namespace the namespace.
     */
    def setNamespace(prefix: String, namespace: IRI): Unit

    /**
     * Returns the namespace declarations in the model.
     *
     * @return a map of prefixes to namespaces.
     */
    def getNamespaces: Map[String, IRI]

    /**
     * Returns `true` if this model is empty.
     */
    def isEmpty: Boolean

    /**
     * Returns `true` if this model is isomorphic with another RDF model.
     *
     * @param otherRdfModel another [[RdfModel]].
     */
    def isIsomorphicWith(otherRdfModel: RdfModel): Boolean

    /**
     * Returns the IRIs of the named graphs in the model.
     */
    def getContexts: Set[IRI]
}

/**
 * Represents a factory that can create RDF nodes and statements.
 */
trait RdfNodeFactory {
    /**
     * Constructs a blank node with a generated ID.
     *
     * @return a [[BlankNode]].
     */
    def makeBlankNode: BlankNode

    /**
     * Constructs a blank node with the specified ID.
     *
     * @param id the blank node ID.
     * @return a [[BlankNode]].
     */
    def makeBlankNodeWithID(id: String): BlankNode

    /**
     * Constructs an IRI node.
     *
     * @param iri the IRI.
     * @return an [[IriNode]].
     */
    def makeIriNode(iri: IRI): IriNode

    /**
     * Constructs a literal value with a datatype.
     *
     * @param value    the lexical value of the literal.
     * @param datatype the datatype IRI.
     * @return a [[DatatypeLiteral]].
     */
    def makeDatatypeLiteral(value: String, datatype: IRI): DatatypeLiteral

    /**
     * Creates an `xsd:string`.
     *
     * @param value the string value.
     * @return a [[DatatypeLiteral]].
     */
    def makeStringLiteral(value: String): DatatypeLiteral = {
        makeDatatypeLiteral(value = value, datatype = OntologyConstants.Xsd.String)
    }

    /**
     * Constructs a string with a language tag.
     *
     * @param value    the string.
     * @param language the language tag.
     */
    def makeStringWithLanguage(value: String, language: String): StringWithLanguage

    /**
     * Constructs an `xsd:boolean`.
     *
     * @param value the boolean value.
     * @return a [[DatatypeLiteral]].
     */
    def makeBooleanLiteral(value: Boolean): DatatypeLiteral = {
        makeDatatypeLiteral(value = value.toString, datatype = OntologyConstants.Xsd.Boolean)
    }

    /**
     * Constructs a statement.
     *
     * @param subj    the subject.
     * @param pred    the predicate.
     * @param obj     the object.
     * @param context the IRI of the named graph, or `None` to use the default graph.
     */
    def makeStatement(subj: RdfResource, pred: IriNode, obj: RdfNode, context: Option[IRI] = None): Statement
}
