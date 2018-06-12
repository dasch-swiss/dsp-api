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

import org.knora.webapi.util.search._
import org.knora.webapi.{AssertionException, GravsearchException, IRI, OntologyConstants}

/**
  * Utilities for Gravsearch type inspection.
  */
object GravsearchTypeInspectionUtil {

    /**
      * An enumeration of the properties that are used in type annotations.
      */
    object TypeAnnotationProperties extends Enumeration {

        import Ordering.Tuple2 // scala compiler issue: https://issues.scala-lang.org/browse/SI-8541

        // TODO: support OntologyConstants.KnoraApiV2WithValueObjects.ObjectType as well.

        val RDF_TYPE: Value = Value(0, OntologyConstants.Rdf.Type)
        val OBJECT_TYPE: Value = Value(1, OntologyConstants.KnoraApiV2Simple.ObjectType)

        val valueMap: Map[IRI, Value] = values.map(v => (v.toString, v)).toMap
    }

    /**
      * The IRIs of non-property types that Gravsearch type inspectors return.
      */
    val ApiV2SimpleTypeIris: Set[IRI] = Set(
        OntologyConstants.Xsd.Boolean,
        OntologyConstants.Xsd.String,
        OntologyConstants.Xsd.Integer,
        OntologyConstants.Xsd.Decimal,
        OntologyConstants.Xsd.Uri,
        OntologyConstants.KnoraApiV2Simple.Resource,
        OntologyConstants.KnoraApiV2Simple.Date,
        OntologyConstants.KnoraApiV2Simple.Geom,
        OntologyConstants.KnoraApiV2Simple.Geoname,
        OntologyConstants.KnoraApiV2Simple.Interval,
        OntologyConstants.KnoraApiV2Simple.Color,
        OntologyConstants.KnoraApiV2Simple.File
    )

    /**
      * IRIs that do not need to be annotated to specify their types.
      */
    val ApiV2SimpleNonTypeableIris: Set[IRI] = ApiV2SimpleTypeIris ++ TypeAnnotationProperties.valueMap.keySet

    /**
      * Given a Gravsearch entity that is known to need type information, converts it to a [[TypeableEntity]].
      *
      * @param entity a Gravsearch entity that is known to need type information.
      * @return a [[TypeableEntity]].
      */
    def toTypeableEntity(entity: Entity): TypeableEntity = {
        maybeTypeableEntity(entity) match {
            case Some(typeableEntity) => typeableEntity
            case None => throw AssertionException(s"Entity cannot be typed: $entity")
        }
    }

    /**
      * Given a Gravsearch entity, converts it to a [[TypeableEntity]] if possible.
      *
      * @param entity the entity to be converted.
      * @return a [[TypeableEntity]], or `None` if the entity does not need type information.
      */
    def maybeTypeableEntity(entity: Entity): Option[TypeableEntity] = {
        entity match {
            case QueryVariable(variableName) => Some(TypeableVariable(variableName))
            case IriRef(iri, _) if !ApiV2SimpleNonTypeableIris.contains(iri.toString) => Some(TypeableIri(iri))
            case _ => None
        }
    }

    /**
      * Given a sequence of entities, finds the ones that need type information and returns them as
      * [[TypeableEntity]] objects.
      *
      * @param entities the entities to be checked.
      * @return a sequence of typeable entities.
      */
    def toTypeableEntities(entities: Seq[Entity]): Set[TypeableEntity] = {
        entities.flatMap(entity => maybeTypeableEntity(entity)).toSet
    }

    /**
      * A [[WhereTransformer]] for removing Gravsearch type annotations from a WHERE clause.
      */
    private class AnnotationRemovingWhereTransformer extends WhereTransformer {
        override def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[QueryPattern] = {
            if (!isAnnotationStatement(statementPattern)) {
                Seq(statementPattern)
            } else {
                Seq.empty[QueryPattern]
            }
        }

        override def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)
    }

    /**
      * Removes Gravsearch type annotations from a WHERE clause.
      *
      * @param whereClause the WHERE clause.
      * @return the same WHERE clause, minus any type annotations.
      */
    def removeTypeAnnotations(whereClause: WhereClause): WhereClause = {
        whereClause.copy(
            patterns = QueryTraverser.transformWherePatterns(
                patterns = whereClause.patterns,
                inputOrderBy = Seq.empty[OrderCriterion],
                whereTransformer = new AnnotationRemovingWhereTransformer
            )
        )
    }

    /**
      * Determines whether a statement pattern represents a Gravsearch type annotation.
      *
      * @param statementPattern the statement pattern.
      * @return `true` if the statement pattern represents a type annotation.
      */
    def isAnnotationStatement(statementPattern: StatementPattern): Boolean = {
        /**
          * Returns `true` if an entity is an IRI representing a type that is valid for use in a type annotation.
          *
          * @param entity the entity to be checked.
          */
        def isValidTypeInAnnotation(entity: Entity): Boolean = {
            entity match {
                case IriRef(objIri, _) if ApiV2SimpleTypeIris(objIri.toString) => true
                case _ => false
            }
        }

        statementPattern.pred match {
            case IriRef(predIri, _) =>
                TypeAnnotationProperties.valueMap.get(predIri.toString) match {
                    case Some(TypeAnnotationProperties.RDF_TYPE) =>
                        // The statement's predicate is rdf:type. Check whether its object is valid in a type
                        // annotation. If not, that's not an error, because the object could be specifying
                        // a subclass of knora-api:Resource, in which case this isn't a type annotation.
                        isValidTypeInAnnotation(statementPattern.obj)

                    case Some(TypeAnnotationProperties.OBJECT_TYPE) =>
                        // The statement's predicate is knora-api:objectType. Check whether its object is valid
                        // in a type annotation. If not, that's an error, because knora-api:objectType isn't used
                        // for anything other than type annotations in Gravsearch queries.
                        if (!isValidTypeInAnnotation(statementPattern.obj)) {
                            throw GravsearchException(s"Object of ${statementPattern.pred} is not a valid type: ${statementPattern.obj}")
                        }

                        true

                    case _ => false
                }

            case _ => false
        }
    }
}
