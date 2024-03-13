/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
