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

package org.knora.webapi.responders.v2.search.sparql.gravsearch

import akka.actor.ActorSystem
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.util.SmartIri
import org.knora.webapi.responders.v2.search.sparql._
import org.knora.webapi.responders.v2.search.sparql.gravsearch.GravsearchTypeInspectionUtil.{TypeAnnotationProperties, TypeAnnotationProperty}

import scala.concurrent.Future

/**
  * A [[GravsearchTypeInspector]] that relies on Gravsearch type annotations. There are two kinds of type annotations:
  *
  * 1. For a variable or IRI representing a resource or value, a type annotation is a triple whose subject is the variable
  * or IRI, whose predicate is `rdf:type`, and whose object is `knora-api:Resource`, another `knora-api` type
  * such as `knora-api:date`, or an XSD type such as `xsd:integer`.
  * 1. For a variable or IRI representing a property, a type annotation is a triple whose subject is the variable or
  * property IRI, whose predicate is `knora-api:objectType`, and whose object is an IRI representing the type
  * of object that is required by the property.
  */
class AnnotationReadingGravsearchTypeInspector(nextInspector: Option[GravsearchTypeInspector],
                                               system: ActorSystem) extends GravsearchTypeInspector(nextInspector = nextInspector, system = system) {

    /**
      * Represents a Gravsearch type annotation.
      *
      * @param typeableEntity the entity whose type was annotated.
      * @param annotationProp the annotation property.
      * @param typeIri        the type IRI that was given in the annotation.
      */
    private case class GravsearchTypeAnnotation(typeableEntity: TypeableEntity, annotationProp: TypeAnnotationProperty, typeIri: SmartIri)

    override def inspectTypes(previousResult: IntermediateTypeInspectionResult,
                              whereClause: WhereClause,
                              requestingUser: UserADM): Future[IntermediateTypeInspectionResult] = {
        for {
            // Get all the type annotations.
            typeAnnotations: Seq[GravsearchTypeAnnotation] <- Future {
                QueryTraverser.visitWherePatterns(
                    patterns = whereClause.patterns,
                    whereVisitor = new AnnotationCollectingWhereVisitor(whereClause.querySchema.getOrElse(throw AssertionException(s"WhereClause has no querySchema"))),
                    initialAcc = Vector.empty[GravsearchTypeAnnotation]
                )
            }

            // Collect the information in the type annotations.
            intermediateResult: IntermediateTypeInspectionResult = typeAnnotations.foldLeft(previousResult) {
                case (acc: IntermediateTypeInspectionResult, typeAnnotation: GravsearchTypeAnnotation) =>
                    typeAnnotation.annotationProp match {
                        case TypeAnnotationProperties.RdfType =>
                            acc.addTypes(typeAnnotation.typeableEntity, Set(NonPropertyTypeInfo(typeAnnotation.typeIri)))

                        case TypeAnnotationProperties.ObjectType =>
                            acc.addTypes(typeAnnotation.typeableEntity, Set(PropertyTypeInfo(typeAnnotation.typeIri)))
                    }
            }

            // Pass the intermediate result to the next type inspector in the pipeline.
            lastResult: IntermediateTypeInspectionResult <- runNextInspector(
                intermediateResult = intermediateResult,
                whereClause = whereClause,
                requestingUser = requestingUser
            )
        } yield lastResult
    }

    /**
      * A [[WhereVisitor]] that collects type annotations.
      */
    private class AnnotationCollectingWhereVisitor(querySchema: ApiV2Schema) extends WhereVisitor[Vector[GravsearchTypeAnnotation]] {
        override def visitStatementInWhere(statementPattern: StatementPattern,
                                           acc: Vector[GravsearchTypeAnnotation]): Vector[GravsearchTypeAnnotation] = {
            if (GravsearchTypeInspectionUtil.isAnnotationStatement(statementPattern)) {
                acc :+ annotationStatementToAnnotation(statementPattern, querySchema)
            } else {
                acc
            }
        }

        override def visitFilter(filterPattern: FilterPattern,
                                 acc: Vector[GravsearchTypeAnnotation]): Vector[GravsearchTypeAnnotation] = acc
    }

    /**
      * Given a statement pattern that is known to represent a Gravsearch type annotation, converts it to
      * a [[GravsearchTypeAnnotation]].
      *
      * @param statementPattern the statement pattern.
      * @return an [[GravsearchTypeAnnotation]].
      */
    private def annotationStatementToAnnotation(statementPattern: StatementPattern, querySchema: ApiV2Schema): GravsearchTypeAnnotation = {
        val typeableEntity: TypeableEntity = GravsearchTypeInspectionUtil.toTypeableEntity(statementPattern.subj)

        val annotationPropIri: SmartIri = statementPattern.pred match {
            case IriRef(iri, _) => iri.checkApiV2Schema(querySchema, throw GravsearchException(s"Invalid schema in IRI: $iri"))
            case other => throw AssertionException(s"Not a type annotation predicate: $other")
        }

        val annotationProp: TypeAnnotationProperty =
            TypeAnnotationProperties.fromIri(annotationPropIri).getOrElse(throw AssertionException(s"Not a type annotation predicate: $annotationPropIri"))

        val typeIri: SmartIri = statementPattern.obj match {
            case IriRef(iri, _) => iri.checkApiV2Schema(querySchema, throw GravsearchException(s"Invalid schema in IRI: $iri"))
            case other => throw AssertionException(s"Not a valid type in a type annotation: $other")
        }

        GravsearchTypeAnnotation(
            typeableEntity = typeableEntity,
            annotationProp = annotationProp,
            typeIri = typeIri
        )
    }


}
