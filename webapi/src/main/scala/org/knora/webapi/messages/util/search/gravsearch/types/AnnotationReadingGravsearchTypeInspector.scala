/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.types

import zio.Task
import zio.ZIO

import dsp.errors.AssertionException
import dsp.errors.GravsearchException
import org.knora.webapi._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.search._
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionUtil.TypeAnnotationProperties
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionUtil.TypeAnnotationProperty

/**
 * A inspector that relies on Gravsearch type annotations. There are two kinds of type annotations:
 *
 * 1. For a variable or IRI representing a resource or value, a type annotation is a triple whose subject is the variable
 * or IRI, whose predicate is `rdf:type`, and whose object is `knora-api:Resource`, another `knora-api` type
 * such as `knora-api:date`, or an XSD type such as `xsd:integer`.
 * 2. For a variable or IRI representing a property, a type annotation is a triple whose subject is the variable or
 * property IRI, whose predicate is `knora-api:objectType`, and whose object is an IRI representing the type
 * of object that is required by the property.
 */
final case class AnnotationReadingGravsearchTypeInspector(queryTraverser: QueryTraverser) {

  /**
   * Represents a Gravsearch type annotation.
   *
   * @param typeableEntity the entity whose type was annotated.
   * @param annotationProp the annotation property.
   * @param typeIri        the type IRI that was given in the annotation.
   * @param isResource     the given type IRI is that of a resource.
   * @param isValue        the given type IRI is that of a value.
   */
  private case class GravsearchTypeAnnotation(
    typeableEntity: TypeableEntity,
    annotationProp: TypeAnnotationProperty,
    typeIri: SmartIri,
    isResource: Boolean = false,
    isValue: Boolean = false
  )

  /**
   * Given the WHERE clause from a parsed Gravsearch query, returns information about the types found in the query.
   *
   * @param previousResult the result of previous type inspection.
   * @param whereClause    the Gravsearch WHERE clause.
   * @param requestingUser the requesting user.
   * @return the result returned by the pipeline.
   */
  def inspectTypes(
    previousResult: IntermediateTypeInspectionResult,
    whereClause: WhereClause,
    requestingUser: UserADM
  ): Task[IntermediateTypeInspectionResult] =
    for {
      // Get all the type annotations.
      querySchema <-
        ZIO.fromOption(whereClause.querySchema).orElseFail(AssertionException(s"WhereClause has no querySchema"))
      typeAnnotations <- ZIO.attempt {
                           queryTraverser.visitWherePatterns(
                             patterns = whereClause.patterns,
                             whereVisitor = new AnnotationCollectingWhereVisitor(querySchema),
                             initialAcc = Vector.empty[GravsearchTypeAnnotation]
                           )
                         }

      // Collect the information in the type annotations.
      result: IntermediateTypeInspectionResult =
        typeAnnotations.foldLeft(previousResult) {
          case (acc: IntermediateTypeInspectionResult, typeAnnotation: GravsearchTypeAnnotation) =>
            typeAnnotation.annotationProp match {
              case TypeAnnotationProperties.RdfType =>
                val isResource =
                  OntologyConstants.KnoraApi.KnoraApiV2ResourceIris.contains(typeAnnotation.typeIri.toString)
                val isValue =
                  GravsearchTypeInspectionUtil.GravsearchValueTypeIris.contains(typeAnnotation.typeIri.toString)
                acc.addTypes(
                  typeAnnotation.typeableEntity,
                  Set(NonPropertyTypeInfo(typeAnnotation.typeIri, isResourceType = isResource, isValueType = isValue))
                )

              case TypeAnnotationProperties.ObjectType =>
                val isResource =
                  OntologyConstants.KnoraApi.KnoraApiV2ResourceIris.contains(typeAnnotation.typeIri.toString)
                val isValue =
                  GravsearchTypeInspectionUtil.GravsearchValueTypeIris.contains(typeAnnotation.typeIri.toString)
                acc.addTypes(
                  typeAnnotation.typeableEntity,
                  Set(
                    PropertyTypeInfo(
                      typeAnnotation.typeIri,
                      objectIsResourceType = isResource,
                      objectIsValueType = isValue
                    )
                  )
                )
            }
        }
    } yield result

  /**
   * A [[WhereVisitor]] that collects type annotations.
   */
  private class AnnotationCollectingWhereVisitor(querySchema: ApiV2Schema)
      extends WhereVisitor[Vector[GravsearchTypeAnnotation]] {
    override def visitStatementInWhere(
      statementPattern: StatementPattern,
      acc: Vector[GravsearchTypeAnnotation]
    ): Vector[GravsearchTypeAnnotation] =
      if (GravsearchTypeInspectionUtil.canBeAnnotationStatement(statementPattern)) {
        acc :+ annotationStatementToAnnotation(statementPattern, querySchema)
      } else {
        acc
      }

    override def visitFilter(
      filterPattern: FilterPattern,
      acc: Vector[GravsearchTypeAnnotation]
    ): Vector[GravsearchTypeAnnotation] = acc
  }

  /**
   * Given a statement pattern that is known to represent a Gravsearch type annotation, converts it to
   * a [[GravsearchTypeAnnotation]].
   *
   * @param statementPattern the statement pattern.
   * @return an [[GravsearchTypeAnnotation]].
   */
  private def annotationStatementToAnnotation(
    statementPattern: StatementPattern,
    querySchema: ApiV2Schema
  ): GravsearchTypeAnnotation = {
    val typeableEntity: TypeableEntity = GravsearchTypeInspectionUtil.toTypeableEntity(statementPattern.subj)

    val annotationPropIri: SmartIri = statementPattern.pred match {
      case IriRef(iri, _) =>
        if (iri.isApiV2Schema(querySchema)) iri
        else throw GravsearchException(s"Invalid schema in IRI: $iri")
      case other => throw AssertionException(s"Not a type annotation predicate: $other")
    }

    val annotationProp: TypeAnnotationProperty =
      TypeAnnotationProperties
        .fromIri(annotationPropIri)
        .getOrElse(throw AssertionException(s"Not a type annotation predicate: $annotationPropIri"))

    val typeIri: SmartIri = statementPattern.obj match {
      case IriRef(iri, _) =>
        if (iri.isApiV2Schema(querySchema)) iri
        else throw GravsearchException(s"Invalid schema in IRI: $iri")
      case other => throw AssertionException(s"Not a valid type in a type annotation: $other")
    }

    GravsearchTypeAnnotation(
      typeableEntity = typeableEntity,
      annotationProp = annotationProp,
      typeIri = typeIri
    )
  }

}
