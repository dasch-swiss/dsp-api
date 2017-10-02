/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.util.search.v2

import org.knora.webapi._
import org.knora.webapi.util.search._

/**
  * A [[TypeInspector]] that relies on explicit type annotations in SPARQL. There are two kinds of type annotations:
  *
  * 1. For every variable or IRI representing a resource or value, there must be a triple whose subject is the variable
  *    or IRI, whose predicate is `rdf:type`, and whose object is `knora-api:Resource`, another `knora-api` type
  *    such as `knora-api:date`, or an XSD type such as `xsd:integer`.
  * 1. For every variable or IRI representing a property, there must be a triple whose subject is the variable or
  *    property IRI, whose predicate is `knora-api:objectType`, and whose object is an IRI representing the type
  *    of object that is required by the property.
  *
  * @param apiType specifies which API schema is being used in the search query.
  */
class ExplicitTypeInspectorV2(apiType: ApiV2Schema) extends TypeInspector {

    /**
      * An enumeration of the properties that are used in type annotations.
      */
    private object TypeAnnotationPropertiesV2 extends Enumeration {
        import Ordering.Tuple2 // scala compiler issue: https://issues.scala-lang.org/browse/SI-8541

        val RDF_TYPE: Value = Value(0, OntologyConstants.Rdf.Type)
        val OBJECT_TYPE: Value = Value(1, OntologyConstants.KnoraApiV2Simple.ObjectType)

        val valueMap: Map[IRI, Value] = values.map(v => (v.toString, v)).toMap
    }

    /**
      * Constants used by the explicit type inspector.
      */
    private object TypeInspectionConstantsV2 {
        /**
          * The IRIs of types that are recognised in explicit type annotations.
          */
        val ApiV2SimpleTypeIris: Set[IRI] = Set(
            OntologyConstants.Xsd.Boolean,
            OntologyConstants.Xsd.String,
            OntologyConstants.Xsd.Integer,
            OntologyConstants.Xsd.Decimal,
            OntologyConstants.KnoraApiV2Simple.Date,
            OntologyConstants.KnoraApiV2Simple.Resource,
            OntologyConstants.KnoraApiV2Simple.StillImageFile,
            OntologyConstants.KnoraApiV2Simple.Geom,
            OntologyConstants.KnoraApiV2Simple.Color
        )

        /**
          * IRIs that do not need to be annotated to specify their types.
          */
        val ApiV2SimpleNonTypeableIris: Set[IRI] = ApiV2SimpleTypeIris ++ TypeAnnotationPropertiesV2.valueMap.keySet
    }

    /**
      * Represents an explicit type annotation.
      *
      * @param typeableEntity the entity whose type was annotated.
      * @param annotationProp the annotation property.
      * @param typeIri the type IRI that was given in the annotation.
      */
    private case class ExplicitAnnotationV2Simple(typeableEntity: TypeableEntity, annotationProp: TypeAnnotationPropertiesV2.Value, typeIri: IRI)

    if (apiType != ApiV2Simple) {
        throw NotImplementedException("Type inspection with value objects is not yet implemented")
    }

    def inspectTypes(whereClause: WhereClause): TypeInspectionResult = {
        val maybeTypedEntities = collection.mutable.Map.empty[TypeableEntity, Option[SparqlEntityTypeInfo]]

        // Make a set of all the entities to be typed in the WHERE clause.
        val entitiesToType: Set[TypeableEntity] = getTypableEntitiesFromPatterns(whereClause.patterns)

        // We don't yet have type information about any of the entities, so set each variable's type info to None.
        for (typedEntity <- entitiesToType) {
            maybeTypedEntities.put(typedEntity, None)
        }

        // Get all the explicit type annotations.

        val explicitAnnotations: Seq[ExplicitAnnotationV2Simple] = getExplicitAnnotations(whereClause.patterns)

        // Collect the information in the type annotations.

        for (explicitAnnotation: ExplicitAnnotationV2Simple <- explicitAnnotations) {
            explicitAnnotation.annotationProp match {
                case TypeAnnotationPropertiesV2.RDF_TYPE =>
                    maybeTypedEntities.put(explicitAnnotation.typeableEntity, Some(NonPropertyTypeInfo(explicitAnnotation.typeIri)))

                case TypeAnnotationPropertiesV2.OBJECT_TYPE =>
                    maybeTypedEntities.put(explicitAnnotation.typeableEntity, Some(PropertyTypeInfo(explicitAnnotation.typeIri)))
            }
        }

        // If any entities still don't have types, throw an exception.

        val nonTypedEntities: Vector[TypeableEntity] = maybeTypedEntities.filter {
            case (_, typeInfo) => typeInfo.isEmpty
        }.keys.toVector

        if (nonTypedEntities.nonEmpty) {
            throw SparqlSearchException(s"Types could not be determined for the following SPARQL entities: ${nonTypedEntities.mkString(", ")}")
        }

        val typedEntities: Map[TypeableEntity, SparqlEntityTypeInfo] = maybeTypedEntities.map {
            case (typedEntity, typeInfo) => (typedEntity, typeInfo.get)
        }.toMap

        TypeInspectionResult(typedEntities = typedEntities)
    }

    /**
      * Removes type annotations from a SPARQL WHERE clause.
      *
      * @param whereClause the WHERE clause to be filtered.
      * @return the same WHERE clause, minus any type annotations.
      */
    def removeTypeAnnotations(whereClause: WhereClause): WhereClause = {
        WhereClause(removeTypeAnnotationsFromPatterns(whereClause.patterns))
    }

    /**
      * Removes explicit type annotations from a sequence of query patterns.
      *
      * @param patterns the patterns to be filtered.
      * @return the same patterns, minus any type annotations.
      */
    private def removeTypeAnnotationsFromPatterns(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
        patterns.collect {
            case statementPattern: StatementPattern if !isAnnotationStatement(statementPattern) => statementPattern

            case optionalPattern: OptionalPattern => OptionalPattern(removeTypeAnnotationsFromPatterns(optionalPattern.patterns))

            case filterNotExistsPattern: FilterNotExistsPattern => FilterNotExistsPattern(removeTypeAnnotationsFromPatterns(filterNotExistsPattern.patterns))

            case minusPattern: MinusPattern => MinusPattern(removeTypeAnnotationsFromPatterns(minusPattern.patterns))

            case unionPattern: UnionPattern =>
                val blocksWithoutAnnotations = unionPattern.blocks.map {
                    patterns: Seq[QueryPattern] => removeTypeAnnotationsFromPatterns(patterns)
                }

                UnionPattern(blocksWithoutAnnotations)

            case filterPattern: FilterPattern => filterPattern
        }
    }

    /**
      * Given a sequence of query patterns, gets all the explicit type annotations.
      *
      * @param patterns the query patterns.
      * @return the type annotations found in the query patterns.
      */
    private def getExplicitAnnotations(patterns: Seq[QueryPattern]): Seq[ExplicitAnnotationV2Simple] = {
        patterns.collect {
            case statementPattern: StatementPattern =>
                if (isAnnotationStatement(statementPattern)) {
                    Seq(annotationStatementToExplicitAnnotationV2Simple(statementPattern))
                } else {
                    Seq.empty[ExplicitAnnotationV2Simple]
                }

            case optionalPattern: OptionalPattern => getExplicitAnnotations(optionalPattern.patterns)

            case filterNotExistsPattern: FilterNotExistsPattern => getExplicitAnnotations(filterNotExistsPattern.patterns)

            case minusPattern: MinusPattern => getExplicitAnnotations(minusPattern.patterns)

            case unionPattern: UnionPattern =>
                unionPattern.blocks.flatMap {
                    patterns: Seq[QueryPattern] => getExplicitAnnotations(patterns)
                }
        }.flatten
    }

    /**
      * Given a statement pattern that is known to represent an explicit type annotation, converts it to
      * an [[ExplicitAnnotationV2Simple]].
      *
      * @param statementPattern the statement pattern.
      * @return an [[ExplicitAnnotationV2Simple]].
      */
    private def annotationStatementToExplicitAnnotationV2Simple(statementPattern: StatementPattern): ExplicitAnnotationV2Simple = {
        val typeableEntity = toTypeableEntity(statementPattern.subj)

        val annotationPropIri = statementPattern.pred match {
            case IriRef(iri) => iri
            case other => throw AssertionException(s"Not a type annotation predicate: $other")
        }

        val annotationProp = TypeAnnotationPropertiesV2.valueMap.getOrElse(annotationPropIri, throw AssertionException(s"Not a type annotation predicate: $annotationPropIri"))

        val typeIri = statementPattern.obj match {
            case IriRef(iri) => iri
            case other => throw AssertionException(s"Not a valid type in a type annotation: $other")
        }

        ExplicitAnnotationV2Simple(
            typeableEntity = typeableEntity,
            annotationProp = annotationProp,
            typeIri = typeIri
        )
    }

    /**
      * Determines whether a statement pattern represents an explicit type annotation.
      *
      * @param statementPattern the statement pattern.
      * @return `true` if the statement pattern represents a type annotation.
      */
    private def isAnnotationStatement(statementPattern: StatementPattern): Boolean = {
        statementPattern.pred match {
            case IriRef(predIri) =>
                TypeAnnotationPropertiesV2.valueMap.get(predIri) match {
                    case Some(TypeAnnotationPropertiesV2.RDF_TYPE) =>
                        isValidTypeInAnnotation(statementPattern.obj)

                    case Some(TypeAnnotationPropertiesV2.OBJECT_TYPE) =>
                        if (!isValidTypeInAnnotation(statementPattern.obj)) {
                            throw SparqlSearchException(s"Object of ${statementPattern.pred} is not a valid type: ${statementPattern.obj}")
                        }

                        true

                    case _ => false
                }

            case _ => false
        }
    }

    /**
      * Determines whether an entity represents the IRI of a type that can be used in a type annotation.
      *
      * @param entity the entity to be checked.
      * @return `true` if the entity is an IRI and if it represents a type that can be used in a type annotation.
      */
    def isValidTypeInAnnotation(entity: Entity): Boolean = {
        entity match {
            case IriRef(objIri) if TypeInspectionConstantsV2.ApiV2SimpleTypeIris(objIri) => true
            case _ => false
        }
    }

    /**
      * Given a sequence of query patterns, extracts all the entities (variable names or IRIs) that need type information.
      *
      * @param patterns the patterns to be searched.
      * @return a set of typeable entities.
      */
    private def getTypableEntitiesFromPatterns(patterns: Seq[QueryPattern]): Set[TypeableEntity] = {
        patterns.collect {
            case statementPattern: StatementPattern =>
                // Don't look for a type annotation of an IRI that's the object of rdf:type.
                statementPattern.pred match {
                    case IriRef(OntologyConstants.Rdf.Type) => toTypeableEntities(Seq(statementPattern.subj, statementPattern.pred))
                    case _ => toTypeableEntities(Seq(statementPattern.subj, statementPattern.pred, statementPattern.obj))
                }

            case optionalPattern: OptionalPattern => getTypableEntitiesFromPatterns(optionalPattern.patterns)

            case filterNotExistsPattern: FilterNotExistsPattern => getTypableEntitiesFromPatterns(filterNotExistsPattern.patterns)

            case minusPattern: MinusPattern => getTypableEntitiesFromPatterns(minusPattern.patterns)

            case unionPattern: UnionPattern =>
                unionPattern.blocks.flatMap {
                    patterns: Seq[QueryPattern] => getTypableEntitiesFromPatterns(patterns)
                }.toSet
        }.flatten.toSet
    }

    /**
      * Given a SPARQL entity that is known to need type information, converts it to a [[TypeableEntity]].
      *
      * @param entity a SPARQL entity that is known to need type information.
      * @return a [[TypeableEntity]].
      */
    private def toTypeableEntity(entity: Entity): TypeableEntity = {
        entity match {
            case QueryVariable(variableName) => TypeableVariable(variableName)
            case IriRef(iri) => TypeableIri(iri)
            case _ => throw AssertionException(s"Entity cannot be typed: $entity")
        }
    }

    /**
      * Given a sequence of entities, finds the ones that need type information and returns them as
      * [[TypeableEntity]] objects.
      *
      * @param entities the entities to be checked.
      * @return a sequence of typeable entities.
      */
    private def toTypeableEntities(entities: Seq[Entity]): Set[TypeableEntity] = {
        entities.collect {
            case QueryVariable(variableName) => TypeableVariable(variableName)
            case IriRef(iri) if !TypeInspectionConstantsV2.ApiV2SimpleNonTypeableIris(iri) => TypeableIri(iri)
        }.toSet
    }
}
