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

package org.knora.webapi.util.rdf.jenaimpl

import org.apache.jena
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.RdfProcessingException
import org.knora.webapi.feature.Feature
import org.knora.webapi.util.rdf._

import scala.collection.JavaConverters._


sealed trait JenaNode extends RdfNode {
    def node: jena.graph.Node
}

case class JenaBlankNode(node: jena.graph.Node) extends JenaNode with BlankNode {
    override def id: String = node.getBlankNodeId.getLabelString
}

case class JenaIriNode(node: jena.graph.Node) extends JenaNode with IriNode {
    override def iri: IRI = node.getURI
}

case class JenaDatatypeLiteral(node: jena.graph.Node) extends JenaNode with DatatypeLiteral {
    override def value: String = node.getLiteralLexicalForm

    override def datatype: IRI = node.getLiteralDatatypeURI
}

case class JenaStringWithLanguage(node: jena.graph.Node) extends JenaNode with StringWithLanguage {
    override def value: String = node.getLiteralLexicalForm

    override def language: String = node.getLiteralLanguage
}

case class JenaStatement(quad: jena.sparql.core.Quad) extends Statement {
    override def subj: RdfResource = {
        val subj: jena.graph.Node = quad.getSubject

        if (subj.isURI) {
            JenaIriNode(quad.getSubject)
        } else if (subj.isBlank) {
            JenaBlankNode(quad.getSubject)
        } else {
            throw RdfProcessingException(s"Unexpected statement subject: $subj")
        }
    }

    override def pred: IriNode = JenaIriNode(quad.getPredicate)

    override def obj: RdfNode = {
        val obj: jena.graph.Node = quad.getObject

        if (obj.isURI) {
            JenaIriNode(quad.getSubject)
        } else if (obj.isBlank) {
            JenaBlankNode(quad.getSubject)
        } else if (obj.isLiteral) {
            val literal: jena.graph.impl.LiteralLabel = obj.getLiteral

            if (literal.language != "") {
                JenaStringWithLanguage(obj)
            } else {
                JenaDatatypeLiteral(obj)
            }
        } else {
            throw RdfProcessingException(s"Unexpected statement object: $obj")
        }
    }

    override def context: Option[IRI] = {
        Option(quad.getGraph).map(_.getURI)
    }
}

object JenaConversions {

    implicit class ConvertibleJenaNode(val self: RdfNode) extends AnyVal {
        def asJenaNode: jena.graph.Node = {
            self match {
                case jenaRdfNode: JenaNode => jenaRdfNode.node
                case other => throw RdfProcessingException(s"$other is not a Jena node")
            }
        }
    }

    implicit class ConvertibleJenaQuad(val self: Statement) extends AnyVal {
        def asJenaQuad: jena.sparql.core.Quad = {
            self match {
                case jenaStatement: JenaStatement => jenaStatement.quad
                case other => throw RdfProcessingException(s"$other is not a Jena statement")
            }
        }
    }
}

/**
 * Generates Jena nodes representing contexts.
 */
abstract class JenaContextFactory {
    /**
     * Converts a named graph IRI to a [[jena.graph.Node]].
     */
    protected def contextIriToNode(context: IRI): jena.graph.Node = {
        jena.graph.NodeFactory.createURI(context)
    }

    /**
     * Converts an optional named graph IRI to a [[jena.graph.Node]], converting
     * `None` to the IRI of Jena's default graph.
     */
    protected def contextNodeOrDefaultGraph(context: Option[IRI]): jena.graph.Node = {
        context.map(contextIriToNode).getOrElse(jena.sparql.core.Quad.defaultGraphIRI)
    }

    /**
     * Converts an optional named graph IRI to a [[jena.graph.Node]], converting
     * `None` to a wildcard that will match any graph.
     */
    protected def contextNodeOrWildcard(context: Option[IRI]): jena.graph.Node = {
        context.map(contextIriToNode).getOrElse(jena.graph.Node.ANY)
    }
}

/**
 * An implementation of [[RdfModel]] that wraps a [[jena.query.Dataset]].
 *
 * @param dataset the underlying Jena dataset.
 */
class JenaModel(private val dataset: jena.query.Dataset) extends JenaContextFactory with RdfModel with Feature {

    import JenaConversions._

    private val datasetGraph: jena.sparql.core.DatasetGraph = dataset.asDatasetGraph

    override def addStatement(statement: Statement): Unit = {
        datasetGraph.add(statement.asJenaQuad)
    }

    /**
     * Converts an optional [[RdfNode]] to a [[jena.graph.Node]], converting
     * `None` to a wildcard that will match any node.
     */
    private def asJenaNodeOrWildcard(node: Option[RdfNode]): jena.graph.Node = {
        node.map(_.asJenaNode).getOrElse(jena.graph.Node.ANY)
    }

    override def add(subj: RdfResource, pred: IriNode, obj: RdfNode, context: Option[IRI] = None): Unit = {
        datasetGraph.add(
            contextNodeOrDefaultGraph(context),
            subj.asJenaNode,
            pred.asJenaNode,
            obj.asJenaNode
        )
    }

    override def remove(subj: Option[RdfResource], pred: Option[IriNode], obj: Option[RdfNode], context: Option[IRI] = None): Unit = {
        datasetGraph.deleteAny(
            contextNodeOrWildcard(context),
            asJenaNodeOrWildcard(subj),
            asJenaNodeOrWildcard(pred),
            asJenaNodeOrWildcard(obj)
        )
    }

    override def find(subj: Option[RdfResource], pred: Option[IriNode], obj: Option[RdfNode], context: Option[IRI] = None): Set[Statement] = {
        datasetGraph.find(
            contextNodeOrWildcard(context),
            asJenaNodeOrWildcard(subj),
            asJenaNodeOrWildcard(pred),
            asJenaNodeOrWildcard(obj)
        ).asScala.map(JenaStatement).toSet
    }
}

/**
 * An implementation of [[RdfNodeFactory]] that creates Jena node implementation wrappers.
 */
class JenaNodeFactory extends JenaContextFactory with RdfNodeFactory {
    import JenaConversions._

    private val typeMapper = jena.datatypes.TypeMapper.getInstance

    override def makeBlankNode: BlankNode = {
        JenaBlankNode(jena.graph.NodeFactory.createBlankNode)
    }

    override def makeBlankNodeWithID(id: String): BlankNode = {
        JenaBlankNode(jena.graph.NodeFactory.createBlankNode(id))
    }

    override def makeIriNode(iri: IRI): IriNode = {
        JenaIriNode(jena.graph.NodeFactory.createURI(iri))
    }

    override def makeDatatypeLiteral(value: String, datatype: IRI): DatatypeLiteral = {
        JenaDatatypeLiteral(jena.graph.NodeFactory.createLiteral(value, typeMapper.getTypeByName(datatype)))
    }

    override def makeStringWithLanguage(value: String, language: String): StringWithLanguage = {
        JenaStringWithLanguage(jena.graph.NodeFactory.createLiteral(value, language))
    }

    override def makeStatement(subj: RdfResource, pred: IriNode, obj: RdfNode, context: Option[IRI]): Statement = {
        JenaStatement(
            new jena.sparql.core.Quad(
                contextNodeOrDefaultGraph(context),
                subj.asJenaNode,
                pred.asJenaNode,
                obj.asJenaNode
            )
        )
    }

}

/**
 * A factory for creating instances of [[JenaModel]].
 */
object JenaModelFactory {
    def makeEmptyModel: JenaModel = new JenaModel(jena.query.DatasetFactory.create)
}
