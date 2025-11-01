/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.types

import zio.*

import dsp.errors.AssertionException
import dsp.errors.GravsearchException
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.search.*

/**
 * Utilities for Gravsearch type inspection.
 */
object GravsearchTypeInspectionUtil {

  /**
   * A trait for case objects representing type annotation properties.
   */
  sealed trait TypeAnnotationProperty

  /**
   * Contains instances of [[TypeAnnotationProperty]].
   */
  object TypeAnnotationProperties {

    /**
     * Represents a type annotation that uses `rdf:type`.
     */
    case object RdfType extends TypeAnnotationProperty

    /**
     * Represents a type annotation that uses `knora-api:objectType` (in the simple or complex schema).
     */
    case object ObjectType extends TypeAnnotationProperty

    private val valueMap = Map(
      OntologyConstants.Rdf.Type                     -> RdfType,
      OntologyConstants.KnoraApiV2Simple.ObjectType  -> ObjectType,
      OntologyConstants.KnoraApiV2Complex.ObjectType -> ObjectType,
    )

    /**
     * Converts an IRI to a [[TypeAnnotationProperty]].
     *
     * @param iri the IRI to be converted.
     * @return a [[TypeAnnotationProperty]], or `None` if the IRI does not correspond to a type
     *         annotation property.
     */
    def fromIri(iri: SmartIri): Option[TypeAnnotationProperty] =
      valueMap.get(iri.toString)

    val allTypeAnnotationIris: Set[IRI] = valueMap.keySet.map(_.toString)
  }

  /**
   * The IRIs of value types that Gravsearch type inspectors return.
   */
  val GravsearchValueTypeIris: Set[IRI] = Set(
    OntologyConstants.Xsd.Boolean,
    OntologyConstants.Xsd.String,
    OntologyConstants.Xsd.Integer,
    OntologyConstants.Xsd.Decimal,
    OntologyConstants.Xsd.Uri,
    OntologyConstants.Xsd.DateTimeStamp,
    OntologyConstants.KnoraApiV2Simple.Date,
    OntologyConstants.KnoraApiV2Simple.Geom,
    OntologyConstants.KnoraApiV2Simple.Geoname,
    OntologyConstants.KnoraApiV2Simple.Interval,
    OntologyConstants.KnoraApiV2Simple.Color,
    OntologyConstants.KnoraApiV2Simple.File,
    OntologyConstants.KnoraApiV2Simple.ListNode,
    OntologyConstants.KnoraApiV2Complex.BooleanValue,
    OntologyConstants.KnoraApiV2Complex.TextValue,
    OntologyConstants.KnoraApiV2Complex.IntValue,
    OntologyConstants.KnoraApiV2Complex.DecimalValue,
    OntologyConstants.KnoraApiV2Complex.UriValue,
    OntologyConstants.KnoraApiV2Complex.DateValue,
    OntologyConstants.KnoraApiV2Complex.ListValue,
    OntologyConstants.KnoraApiV2Complex.ListNode,
    OntologyConstants.KnoraApiV2Complex.GeomValue,
    OntologyConstants.KnoraApiV2Complex.GeonameValue,
    OntologyConstants.KnoraApiV2Complex.ColorValue,
    OntologyConstants.KnoraApiV2Complex.IntervalValue,
    OntologyConstants.KnoraApiV2Complex.TimeValue,
    OntologyConstants.KnoraApiV2Complex.FileValue,
  )

  /**
   * The IRIs of non-property types that Gravsearch type inspectors return.
   */
  val GravsearchAnnotationTypeIris: Set[IRI] = GravsearchValueTypeIris ++ Set(
    OntologyConstants.KnoraApiV2Simple.Resource,
    OntologyConstants.KnoraApiV2Complex.Resource,
    OntologyConstants.KnoraApiV2Complex.StandoffTag,
    OntologyConstants.KnoraApiV2Complex.KnoraProject,
  )

  /**
   * IRIs that are used to set Gravsearch options.
   */
  val GravsearchOptionIris: Set[IRI] = Set(
    OntologyConstants.KnoraApiV2Simple.GravsearchOptions,
    OntologyConstants.KnoraApiV2Complex.GravsearchOptions,
    OntologyConstants.KnoraApiV2Simple.UseInference,
    OntologyConstants.KnoraApiV2Complex.UseInference,
  )

  /**
   * IRIs that do not need to be annotated to specify their types.
   */
  val ApiV2NonTypeableIris: Set[IRI] = GravsearchAnnotationTypeIris ++
    TypeAnnotationProperties.allTypeAnnotationIris ++
    GravsearchOptionIris

  /**
   * Given a Gravsearch entity that is known to need type information, converts it to a [[TypeableEntity]].
   *
   * @param entity a Gravsearch entity that is known to need type information.
   * @return a [[TypeableEntity]].
   */
  def toTypeableEntity(entity: Entity): TypeableEntity =
    maybeTypeableEntity(entity) match {
      case Some(typeableEntity) => typeableEntity
      case None                 => throw AssertionException(s"Entity cannot be typed: $entity")
    }

  /**
   * Given a Gravsearch entity, converts it to a [[TypeableEntity]] if possible.
   *
   * @param entity the entity to be converted.
   * @return a [[TypeableEntity]], or `None` if the entity does not need type information.
   */
  def maybeTypeableEntity(entity: Entity): Option[TypeableEntity] =
    entity match {
      case QueryVariable(variableName)                                    => Some(TypeableVariable(variableName))
      case IriRef(iri, _) if !ApiV2NonTypeableIris.contains(iri.toString) => Some(TypeableIri(iri))
      case _                                                              => None
    }

  /**
   * Given a sequence of entities, finds the ones that need type information and returns them as
   * [[TypeableEntity]] objects.
   *
   * @param entities the entities to be checked.
   * @return a sequence of typeable entities.
   */
  def toTypeableEntities(entities: Seq[Entity]): Set[TypeableEntity] =
    entities.flatMap(entity => maybeTypeableEntity(entity)).toSet

  /**
   * Determines whether a statement pattern must represent a Gravsearch type annotation.
   *
   * @param statementPattern the statement pattern.
   * @return `true` if the statement pattern must represent a type annotation.
   */
  def mustBeAnnotationStatement(statementPattern: StatementPattern): Boolean = {
    // Does the statement have rdf:type knora-api:Resource (which is not necessarily a Gravsearch type annotation)?
    def hasRdfTypeKnoraApiResource: Boolean =
      statementPattern.pred match {
        case predIriRef: IriRef =>
          if (predIriRef.iri.toString == OntologyConstants.Rdf.Type) {
            statementPattern.obj match {
              case objIriRef: IriRef =>
                OntologyConstants.KnoraApi.KnoraApiV2ResourceIris.contains(objIriRef.iri.toString)

              case _ => false
            }
          } else {
            false
          }

        case _ => false
      }

    // If the statement can be a type annotation and doesn't have rdf:type knora-api:Resource, return true.
    // Otherwise, return false.
    canBeAnnotationStatement(statementPattern) && !hasRdfTypeKnoraApiResource
  }

  def canBeAnnotationStatement(statementPattern: StatementPattern): Boolean = {

    /**
     * Returns `true` if an entity is an IRI representing a type that is valid for use in a type annotation.
     *
     * @param entity the entity to be checked.
     */
    def isValidTypeInAnnotation(entity: Entity): Boolean =
      entity match {
        case IriRef(objIri, _) if GravsearchAnnotationTypeIris.contains(objIri.toString) => true
        case _                                                                           => false
      }

    statementPattern.pred match {
      case IriRef(predIri, _) =>
        TypeAnnotationProperties.fromIri(predIri) match {
          case Some(TypeAnnotationProperties.RdfType) =>
            // The statement's predicate is rdf:type. Check whether its object is valid in a type
            // annotation. If not, that's not an error, because the object could be specifying
            // a subclass of knora-api:Resource, knora-api:FileValue, etc., in which case this isn't a
            // type annotation.
            isValidTypeInAnnotation(statementPattern.obj)

          case Some(TypeAnnotationProperties.ObjectType) =>
            // The statement's predicate is knora-api:objectType. Check whether its object is valid
            // in a type annotation. If not, that's an error, because knora-api:objectType isn't used
            // for anything other than type annotations in Gravsearch queries.
            if (!isValidTypeInAnnotation(statementPattern.obj)) {
              throw GravsearchException(
                s"Object of ${statementPattern.pred} is not a valid type: ${statementPattern.obj}",
              )
            }

            true

          case _ => false
        }

      case _ => false
    }
  }

  /**
   * Removes Gravsearch type annotations from a WHERE clause.
   *
   * @param whereClause the WHERE clause.
   * @return the same WHERE clause, minus any type annotations.
   */
  def removeTypeAnnotations(whereClause: WhereClause): Task[WhereClause] = {
    val patterns = whereClause.patterns.flatMap(transformPattern)
    ZIO.succeed(whereClause.copy(patterns = patterns))
  }

  private def transformPattern(pattern: QueryPattern): Seq[QueryPattern] =
    pattern match {
      case filterNotExistsPattern: FilterNotExistsPattern =>
        Seq(FilterNotExistsPattern(filterNotExistsPattern.patterns.flatMap(transformPattern)))
      case minusPattern: MinusPattern         => Seq(MinusPattern(minusPattern.patterns.flatMap(transformPattern)))
      case optionalPattern: OptionalPattern   => Seq(OptionalPattern(optionalPattern.patterns.flatMap(transformPattern)))
      case unionPattern: UnionPattern         => Seq(UnionPattern(unionPattern.blocks.map(_.flatMap(transformPattern))))
      case filterPattern: FilterPattern       => Seq(filterPattern)
      case valuesPattern: ValuesPattern       => Seq(valuesPattern)
      case bindPattern: BindPattern           => Seq(bindPattern)
      case statementPattern: StatementPattern =>
        if (mustBeAnnotationStatement(statementPattern)) Seq.empty[QueryPattern]
        else Seq(statementPattern)
    }
}
