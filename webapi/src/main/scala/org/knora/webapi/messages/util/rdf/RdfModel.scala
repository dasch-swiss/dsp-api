/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf

import scala.util.control.Exception.allCatch

import dsp.errors.InvalidRdfException
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants

/**
 * Represents an RDF subject, predicate, or object.
 */
trait RdfNode {

  /**
   * The lexical representation of this node.
   */
  def stringValue: String
}

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

  /**
   * The lexical value of this literal.
   */
  def value: String

  /**
   * The datatype IRI of this literal.
   */
  def datatype: IRI

  /**
   * The boolean value of this literal.
   *
   * @param errorFun a function that throws an exception. It will
   *                 be called if this literal is not a boolean.
   */
  def booleanValue(errorFun: => Nothing): Boolean =
    if (datatype == OntologyConstants.Xsd.Boolean) {
      allCatch.opt(value.toBoolean).getOrElse(errorFun)
    } else {
      errorFun
    }

  /**
   * The integer value of this literal.
   *
   * @param errorFun a function that throws an exception. It will
   *                 be called if this literal is not an integer.
   */
  def integerValue(errorFun: => Nothing): BigInt =
    if (OntologyConstants.Xsd.integerTypes.contains(datatype)) {
      allCatch.opt(BigInt(value)).getOrElse(errorFun)
    } else {
      errorFun
    }

  /**
   * The decimal value of this literal.
   *
   * @param errorFun a function that throws an exception. It will
   *                 be called if this literal is not a decimal.
   */
  def decimalValue(errorFun: => Nothing): BigDecimal =
    if (datatype == OntologyConstants.Xsd.Decimal) {
      allCatch.opt(BigDecimal(value)).getOrElse(errorFun)
    } else {
      errorFun
    }
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

  /**
   * Returns the object of this statement as an [[RdfResource]].
   */
  def getResourceObject: RdfResource =
    obj match {
      case rdfResource: RdfResource => rdfResource
      case _                        => throw InvalidRdfException(s"The object of $pred is not a resource")
    }

  /**
   * Returns the object of this statement as an [[IriNode]].
   */
  def getIriObject: IriNode =
    obj match {
      case iriNode: IriNode => iriNode
      case _                => throw InvalidRdfException(s"The object of $pred is not an IRI")
    }

  /**
   * Returns the object of this statement as a [[BlankNode]].
   */
  def getBlankNodeObject: BlankNode =
    obj match {
      case blankNode: BlankNode => blankNode
      case _                    => throw InvalidRdfException(s"The object of $pred is not a blank node")
    }

  /**
   * Returns the boolean value of the object of this statement.
   */
  def getBooleanObject: Boolean = {
    def invalid: Nothing = throw InvalidRdfException(s"The object of $pred is not a boolean value")

    obj match {
      case datatypeLiteral: DatatypeLiteral => datatypeLiteral.booleanValue(invalid)
      case _                                => invalid
    }
  }

  /**
   * Returns the integer value of the object of this statement.
   */
  def getIntegerObject: BigInt = {
    def invalid: Nothing = throw InvalidRdfException(s"The object of $pred is not an integer")

    obj match {
      case datatypeLiteral: DatatypeLiteral => datatypeLiteral.integerValue(invalid)
      case _                                => invalid
    }
  }

  /**
   * Returns the decimal value of the object of this statement.
   */
  def getDecimalObject: BigDecimal = {
    def invalid: Nothing = throw InvalidRdfException(s"The object of $pred is not a decimal")

    obj match {
      case datatypeLiteral: DatatypeLiteral => datatypeLiteral.decimalValue(invalid)
      case _                                => invalid
    }
  }
}

/**
 * Represents an RDF model consisting of a default graph and/or one or more named graphs.
 * An [[RdfModel]] is mutable, so don't try to modify it while iterating over its statements
 * using an iterator.
 */
trait RdfModel extends Iterable[Statement] {

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
  def addStatements(statements: Set[Statement]): Unit =
    for (statement <- statements) {
      addStatement(statement)
    }

  /**
   * Adds all the statements from another model to this model.
   *
   * @param otherModel another [[RdfModel]].
   */
  def addStatementsFromModel(otherModel: RdfModel): Unit =
    for (statement <- otherModel) {
      addStatement(statement)
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
   * Removes a statement from the model.
   */
  def removeStatement(statement: Statement): Unit

  /**
   * Removes a set of statements from the model.
   *
   * @param statements the statements to remove.
   */
  def removeStatements(statements: Set[Statement]): Unit =
    for (statement <- statements) {
      removeStatement(statement)
    }

  /**
   * Returns statements that match a pattern.
   *
   * @param subj    the subject, or `None` to match any subject.
   * @param pred    the predicate, or `None` to match any predicate.
   * @param obj     the object, or `None` to match any object.
   * @param context the IRI of a named graph, or `None` to match any graph.
   * @return an iterator over the statements that match the pattern.
   */
  def find(
    subj: Option[RdfResource],
    pred: Option[IriNode],
    obj: Option[RdfNode],
    context: Option[IRI] = None
  ): Iterator[Statement]

  /**
   * Checks whether the model contains the specified statement.
   *
   * @param statement the statement.
   * @return `true` if the model contains the statement.
   */
  def contains(statement: Statement): Boolean

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

  /**
   * @return the number of statements in the model.
   */
  def size: Int

  /**
   * Empties this model.
   */
  def clear(): Unit

  /**
   * Returns an [[RdfRepository]] that can be used to query this model.
   */
  def asRepository: RdfRepository

  override def hashCode(): Int = super.hashCode()

  override def equals(obj: Any): Boolean =
    obj match {
      case thatRdfModel: RdfModel => isIsomorphicWith(thatRdfModel)
      case _                      => false
    }
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
  def makeStringLiteral(value: String): DatatypeLiteral =
    makeDatatypeLiteral(value = value, datatype = OntologyConstants.Xsd.String)

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
  def makeBooleanLiteral(value: Boolean): DatatypeLiteral =
    makeDatatypeLiteral(value = value.toString, datatype = OntologyConstants.Xsd.Boolean)

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

/**
 * A factory that creates [[RdfModel]] instances.
 */
trait RdfModelFactory {
  def makeEmptyModel: RdfModel
}

/**
 * Represents a simple in-memory repository based on an [[RdfModel]].
 */
trait RdfRepository {

  /**
   * Does a SPARQL SELECT query.
   *
   * @param selectQuery the query.
   * @return the query result.
   */
  def doSelect(selectQuery: String): SparqlSelectResult

  /**
   * Shuts down this repository. The underlying [[RdfModel]] may not be usable after its
   * [[RdfRepository]] has been shut down.
   */
  def shutDown(): Unit
}
