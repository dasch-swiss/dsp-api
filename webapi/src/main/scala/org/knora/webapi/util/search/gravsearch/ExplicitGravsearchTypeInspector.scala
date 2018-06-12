/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.util.search.gravsearch

import akka.actor.ActorSystem
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.util.SmartIri
import org.knora.webapi.util.search._
import org.knora.webapi.util.search.gravsearch.GravsearchTypeInspectionUtil.{IntermediateTypeInspectionResult, TypeAnnotationPropertiesV2}

import scala.concurrent.{ExecutionContext, Future}

/**
  * A [[GravsearchTypeInspector]] that relies on explicit type annotations in Gravsearch. There are two kinds of type annotations:
  *
  * 1. For a variable or IRI representing a resource or value, a type annotation is a triple whose subject is the variable
  * or IRI, whose predicate is `rdf:type`, and whose object is `knora-api:Resource`, another `knora-api` type
  * such as `knora-api:date`, or an XSD type such as `xsd:integer`.
  * 1. For a variable or IRI representing a property, a type annotation is a triple whose subject is the variable or
  * property IRI, whose predicate is `knora-api:objectType`, and whose object is an IRI representing the type
  * of object that is required by the property.
  */
class ExplicitGravsearchTypeInspector(nextInspector: Option[GravsearchTypeInspector],
                                      system: ActorSystem)
                                     (implicit executionContext: ExecutionContext) extends GravsearchTypeInspector(nextInspector = nextInspector, system = system) {

    /**
      * Represents an explicit type annotation.
      *
      * @param typeableEntity the entity whose type was annotated.
      * @param annotationProp the annotation property.
      * @param typeIri        the type IRI that was given in the annotation.
      */
    private case class ExplicitAnnotationV2Simple(typeableEntity: TypeableEntity, annotationProp: TypeAnnotationPropertiesV2.Value, typeIri: SmartIri)

    override def inspectTypes(previousResult: IntermediateTypeInspectionResult,
                              whereClause: WhereClause,
                              requestingUser: UserADM): Future[IntermediateTypeInspectionResult] = {
        for {
            // Get all the explicit type annotations.
            explicitAnnotations: Seq[ExplicitAnnotationV2Simple] <- Future(getExplicitAnnotations(whereClause))

            // Collect the information in the type annotations.

            intermediateResult = explicitAnnotations.foldLeft(previousResult) {
                case (acc, explicitAnnotation) => explicitAnnotation.annotationProp match {
                    case TypeAnnotationPropertiesV2.RDF_TYPE =>
                        acc.addTypes(explicitAnnotation.typeableEntity, Set(NonPropertyTypeInfo(explicitAnnotation.typeIri)))

                    case TypeAnnotationPropertiesV2.OBJECT_TYPE =>
                        acc.addTypes(explicitAnnotation.typeableEntity, Set(PropertyTypeInfo(explicitAnnotation.typeIri)))
                }
            }

            // Pass the intermediate result to the next type inspector in the pipeline.
            lastResult <- runNextInspector(
                intermediateResult = intermediateResult,
                whereClause = whereClause,
                requestingUser = requestingUser
            )
        } yield lastResult
    }

    /**
      * A [[WhereVisitor]] that collects type annotations.
      */
    private class AnnotationCollectingWhereVisitor extends WhereVisitor[Vector[ExplicitAnnotationV2Simple]] {
        override def visitStatementInWhere(statementPattern: StatementPattern,
                                           acc: Vector[ExplicitAnnotationV2Simple]): Vector[ExplicitAnnotationV2Simple] = {
            if (GravsearchTypeInspectionUtil.isAnnotationStatement(statementPattern)) {
                acc :+ annotationStatementToExplicitAnnotationV2Simple(statementPattern)
            } else {
                acc
            }
        }

        override def visitFilter(filterPattern: FilterPattern,
                                 acc: Vector[ExplicitAnnotationV2Simple]): Vector[ExplicitAnnotationV2Simple] = acc
    }

    /**
      * Given a WHERE clause, gets all the explicit type annotations.
      *
      * @param whereClause the WHERE clause.
      * @return the type annotations found in the query patterns.
      */
    private def getExplicitAnnotations(whereClause: WhereClause): Seq[ExplicitAnnotationV2Simple] = {
        QueryTraverser.visitWherePatterns(
            patterns = whereClause.patterns,
            whereVisitor = new AnnotationCollectingWhereVisitor,
            initialAcc = Vector.empty[ExplicitAnnotationV2Simple]
        )
    }

    /**
      * Given a statement pattern that is known to represent an explicit type annotation, converts it to
      * an [[ExplicitAnnotationV2Simple]].
      *
      * @param statementPattern the statement pattern.
      * @return an [[ExplicitAnnotationV2Simple]].
      */
    private def annotationStatementToExplicitAnnotationV2Simple(statementPattern: StatementPattern): ExplicitAnnotationV2Simple = {
        val typeableEntity = GravsearchTypeInspectionUtil.toTypeableEntity(statementPattern.subj)

        val annotationPropIri = statementPattern.pred match {
            case IriRef(iri, _) => iri
            case other => throw AssertionException(s"Not a type annotation predicate: $other")
        }

        val annotationProp = TypeAnnotationPropertiesV2.valueMap.getOrElse(annotationPropIri.toString, throw AssertionException(s"Not a type annotation predicate: $annotationPropIri"))

        val typeIri = statementPattern.obj match {
            case IriRef(iri, _) => iri
            case other => throw AssertionException(s"Not a valid type in a type annotation: $other")
        }

        ExplicitAnnotationV2Simple(
            typeableEntity = typeableEntity,
            annotationProp = annotationProp,
            typeIri = typeIri
        )
    }


}
