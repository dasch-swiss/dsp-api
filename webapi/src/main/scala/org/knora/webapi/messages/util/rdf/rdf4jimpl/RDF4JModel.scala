/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf.rdf4jimpl

import org.eclipse.rdf4j
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.RdfProcessingException
import org.knora.webapi.feature.Feature
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.util.JavaUtil._

import scala.jdk.CollectionConverters._
import scala.collection.mutable.ArrayBuffer

sealed trait RDF4JNode extends RdfNode {
  def rdf4jValue: rdf4j.model.Value

  override def stringValue: String = rdf4jValue.stringValue
}

sealed trait RDF4JResource extends RDF4JNode with RdfResource {
  def resource: rdf4j.model.Resource

  override def rdf4jValue: rdf4j.model.Value = resource
}

object RDF4JResource {
  def fromRDF4J(resource: rdf4j.model.Resource): RDF4JResource =
    resource match {
      case iri: rdf4j.model.IRI         => RDF4JIriNode(iri)
      case blankNode: rdf4j.model.BNode => RDF4JBlankNode(blankNode)
      case other                        => throw RdfProcessingException(s"Unexpected resource: $other")
    }
}

case class RDF4JBlankNode(resource: rdf4j.model.BNode) extends RDF4JResource with BlankNode {
  override def id: String = resource.getID
}

case class RDF4JIriNode(resource: rdf4j.model.IRI) extends RDF4JResource with IriNode {
  override def iri: IRI = resource.stringValue
}

sealed trait RDF4JLiteral extends RDF4JNode with RdfLiteral {
  def literal: rdf4j.model.Literal

  override def rdf4jValue: rdf4j.model.Value = literal
}

case class RDF4JDatatypeLiteral(literal: rdf4j.model.Literal) extends RDF4JLiteral with DatatypeLiteral {
  override def value: String = literal.getLabel

  override def datatype: IRI = literal.getDatatype.stringValue
}

case class RDF4JStringWithLanguage(literal: rdf4j.model.Literal) extends RDF4JLiteral with StringWithLanguage {
  override def value: String = literal.getLabel

  override def language: String =
    literal.getLanguage.toOption.getOrElse(throw RdfProcessingException(s"Literal $literal has no language tag"))
}

case class RDF4JStatement(statement: rdf4j.model.Statement) extends Statement {
  override def subj: RdfResource =
    statement.getSubject match {
      case resource: rdf4j.model.Resource => RDF4JResource.fromRDF4J(resource)
      case other                          => throw RdfProcessingException(s"Unexpected statement subject: $other")
    }

  override def pred: IriNode = RDF4JIriNode(statement.getPredicate)

  override def obj: RdfNode =
    statement.getObject match {
      case resource: rdf4j.model.Resource => RDF4JResource.fromRDF4J(resource)

      case literal: rdf4j.model.Literal =>
        if (literal.getLanguage.toOption.isDefined) {
          RDF4JStringWithLanguage(literal)
        } else {
          RDF4JDatatypeLiteral(literal)
        }

      case other => throw RdfProcessingException(s"Unexpected statement object: $other")
    }

  override def context: Option[IRI] = Option(statement.getContext).map(_.stringValue)
}

/**
 * Provides extension methods for converting between Knora RDF API classes and RDF4J classes
 * (see [[https://docs.scala-lang.org/overviews/core/value-classes.html#extension-methods Extension Methods]]).
 */
object RDF4JConversions {

  implicit class ConvertibleRDF4JResource(val self: RdfResource) extends AnyVal {
    def asRDF4JResource: rdf4j.model.Resource =
      self match {
        case rdf4jResource: RDF4JResource => rdf4jResource.resource
        case other                        => throw RdfProcessingException(s"$other is not a RDF4J resource")
      }
  }

  implicit class ConvertibleRDF4JIri(val self: IriNode) extends AnyVal {
    def asRDF4JIri: rdf4j.model.IRI =
      self match {
        case rdf4jIriNode: RDF4JIriNode => rdf4jIriNode.resource
        case other                      => throw RdfProcessingException(s"$other is not an RDF4J IRI")
      }
  }

  implicit class ConvertibleRDF4JValue(val self: RdfNode) extends AnyVal {
    def asRDF4JValue: rdf4j.model.Value =
      self match {
        case rdf4jResource: RDF4JNode => rdf4jResource.rdf4jValue
        case other                    => throw RdfProcessingException(s"$other is not an RDF4J value")
      }
  }

  implicit class ConvertibleRDF4JStatement(val self: Statement) extends AnyVal {
    def asRDF4JStatement: rdf4j.model.Statement =
      self match {
        case rdf4JStatement: RDF4JStatement => rdf4JStatement.statement
        case other                          => throw RdfProcessingException(s"$other is not an RDF4J statement")
      }
  }

  implicit class ConvertibleRDF4JModel(val self: RdfModel) extends AnyVal {
    def asRDF4JModel: rdf4j.model.Model =
      self match {
        case rdf4JModel: RDF4JModel => rdf4JModel.getModel
        case other                  => throw RdfProcessingException(s"${other.getClass.getName} is not an RDF4J model")
      }
  }

}

/**
 * An implementation of [[RdfModel]] that wraps an [[rdf4j.model.Model]].
 *
 * @param model the underlying RDF4J model.
 */
class RDF4JModel(private val model: rdf4j.model.Model, private val nodeFactory: RDF4JNodeFactory)
    extends RdfModel
    with Feature {

  import RDF4JConversions._

  private val valueFactory: rdf4j.model.ValueFactory = rdf4j.model.impl.SimpleValueFactory.getInstance

  private class StatementIterator(rdf4jIterator: java.util.Iterator[rdf4j.model.Statement])
      extends Iterator[Statement] {
    override def hasNext: Boolean = rdf4jIterator.hasNext

    override def next(): Statement = RDF4JStatement(rdf4jIterator.next())
  }

  /**
   * Returns the underlying [[rdf4j.model.Model]].
   */
  def getModel: rdf4j.model.Model = model

  override def getNodeFactory: RdfNodeFactory = nodeFactory

  override def addStatement(statement: Statement): Unit =
    model.add(statement.asRDF4JStatement)

  override def add(subj: RdfResource, pred: IriNode, obj: RdfNode, context: Option[IRI] = None): Unit =
    context match {
      case Some(definedContext) =>
        model.add(
          subj.asRDF4JResource,
          pred.asRDF4JIri,
          obj.asRDF4JValue,
          valueFactory.createIRI(definedContext)
        )

      case None =>
        model.add(
          subj.asRDF4JResource,
          pred.asRDF4JIri,
          obj.asRDF4JValue
        )
    }

  override def remove(
    subj: Option[RdfResource],
    pred: Option[IriNode],
    obj: Option[RdfNode],
    context: Option[IRI] = None
  ): Unit =
    context match {
      case Some(definedContext) =>
        model.remove(
          subj.map(_.asRDF4JResource).orNull,
          pred.map(_.asRDF4JIri).orNull,
          obj.map(_.asRDF4JValue).orNull,
          valueFactory.createIRI(definedContext)
        )

      case None =>
        model.remove(
          subj.map(_.asRDF4JResource).orNull,
          pred.map(_.asRDF4JIri).orNull,
          obj.map(_.asRDF4JValue).orNull
        )
    }

  override def removeStatement(statement: Statement): Unit =
    model.remove(
      statement.subj.asRDF4JResource,
      statement.pred.asRDF4JIri,
      statement.obj.asRDF4JValue,
      statement.context.map(definedContext => valueFactory.createIRI(definedContext)).orNull
    )

  override def find(
    subj: Option[RdfResource],
    pred: Option[IriNode],
    obj: Option[RdfNode],
    context: Option[IRI] = None
  ): Iterator[Statement] = {
    val filteredModel: rdf4j.model.Model = context match {
      case Some(definedContext) =>
        model.filter(
          subj.map(_.asRDF4JResource).orNull,
          pred.map(_.asRDF4JIri).orNull,
          obj.map(_.asRDF4JValue).orNull,
          valueFactory.createIRI(definedContext)
        )

      case None =>
        model.filter(
          subj.map(_.asRDF4JResource).orNull,
          pred.map(_.asRDF4JIri).orNull,
          obj.map(_.asRDF4JValue).orNull
        )
    }

    new StatementIterator(filteredModel.iterator)
  }

  override def contains(statement: Statement): Boolean =
    model.contains(
      statement.subj.asRDF4JResource,
      statement.pred.asRDF4JIri,
      statement.obj.asRDF4JValue,
      statement.context.map(definedContext => valueFactory.createIRI(definedContext)).orNull
    )

  override def setNamespace(prefix: String, namespace: IRI): Unit =
    model.setNamespace(new rdf4j.model.impl.SimpleNamespace(prefix, namespace))

  override def getNamespaces: Map[String, IRI] =
    model.getNamespaces.asScala.map { namespace: rdf4j.model.Namespace =>
      namespace.getPrefix -> namespace.getName
    }.toMap

  override def isEmpty: Boolean = model.isEmpty

  override def getSubjects: Set[RdfResource] =
    model.subjects.asScala.toSet.map(resource => RDF4JResource.fromRDF4J(resource))

  override def isIsomorphicWith(otherRdfModel: RdfModel): Boolean =
    model == otherRdfModel.asRDF4JModel

  override def getContexts: Set[IRI] =
    model.contexts.asScala.toSet.filter(_ != null).map { context: rdf4j.model.Resource =>
      context.stringValue
    }

  override def asRepository: RdfRepository =
    new RDF4JRepository(model)

  override def size: Int = model.size

  override def iterator: Iterator[Statement] =
    new StatementIterator(model.iterator)

  override def clear(): Unit =
    model.remove(null, null, null)
}

/**
 * An implementation of [[RdfNodeFactory]] that creates RDF4J node implementation wrappers.
 */
class RDF4JNodeFactory extends RdfNodeFactory {

  import RDF4JConversions._

  private val valueFactory: rdf4j.model.ValueFactory = rdf4j.model.impl.SimpleValueFactory.getInstance

  override def makeBlankNode: BlankNode =
    RDF4JBlankNode(valueFactory.createBNode)

  override def makeBlankNodeWithID(id: String): BlankNode =
    RDF4JBlankNode(valueFactory.createBNode(id))

  override def makeIriNode(iri: IRI): IriNode =
    RDF4JIriNode(valueFactory.createIRI(iri))

  override def makeDatatypeLiteral(value: String, datatype: IRI): DatatypeLiteral =
    RDF4JDatatypeLiteral(valueFactory.createLiteral(value, valueFactory.createIRI(datatype)))

  override def makeStringWithLanguage(value: String, language: String): StringWithLanguage =
    RDF4JStringWithLanguage(valueFactory.createLiteral(value, language))

  override def makeStatement(subj: RdfResource, pred: IriNode, obj: RdfNode, context: Option[IRI]): Statement = {
    val statement: rdf4j.model.Statement = context match {
      case Some(definedContext) =>
        valueFactory.createStatement(
          subj.asRDF4JResource,
          pred.asRDF4JIri,
          obj.asRDF4JValue,
          valueFactory.createIRI(definedContext)
        )

      case None =>
        valueFactory.createStatement(
          subj.asRDF4JResource,
          pred.asRDF4JIri,
          obj.asRDF4JValue
        )
    }

    RDF4JStatement(statement)
  }
}

/**
 * A factory for creating instances of [[RDF4JModel]].
 */
class RDF4JModelFactory(private val nodeFactory: RDF4JNodeFactory) extends RdfModelFactory {
  override def makeEmptyModel: RDF4JModel = new RDF4JModel(
    model = new rdf4j.model.impl.LinkedHashModel,
    nodeFactory = nodeFactory
  )
}

/**
 * An [[RdfRepository]] that wraps an [[rdf4j.model.Model]] in an [[rdf4j.repository.sail.SailRepository]].
 *
 * @param model the model to be queried.
 */
class RDF4JRepository(model: rdf4j.model.Model) extends RdfRepository {
  // Construct an in-memory SailRepository containing the model.
  val repository = new rdf4j.repository.sail.SailRepository(new rdf4j.sail.memory.MemoryStore())
  repository.init()
  val connection: rdf4j.repository.sail.SailRepositoryConnection = repository.getConnection
  connection.add(model)
  connection.close()

  override def doSelect(selectQuery: String): SparqlSelectResult = {
    // Run the query.

    val connection = repository.getConnection
    val tupleQuery: rdf4j.query.TupleQuery = connection.prepareTupleQuery(selectQuery)
    val tupleQueryResult: rdf4j.query.TupleQueryResult = tupleQuery.evaluate

    // Convert the query result to a SparqlSelectResponse.

    val header = SparqlSelectResultHeader(tupleQueryResult.getBindingNames.asScala.toSeq)
    val rowBuffer = ArrayBuffer.empty[VariableResultsRow]

    while (tupleQueryResult.hasNext) {
      val bindings: Iterable[rdf4j.query.Binding] = tupleQueryResult.next.asScala

      val rowMap: Map[String, String] = bindings.map { binding =>
        binding.getName -> binding.getValue.stringValue
      }.toMap

      rowBuffer.append(
        VariableResultsRow(
          new ErrorHandlingMap[String, String](
            rowMap,
            { key: String =>
              s"No value found for SPARQL query variable '$key' in query result row"
            }
          )
        )
      )
    }

    tupleQueryResult.close()
    connection.close()

    SparqlSelectResult(
      head = header,
      results = SparqlSelectResultBody(bindings = rowBuffer.toSeq)
    )
  }

  override def shutDown(): Unit =
    repository.shutDown()
}
