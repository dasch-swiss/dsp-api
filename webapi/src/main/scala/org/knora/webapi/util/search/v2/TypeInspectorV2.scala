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

/**
  * Represents the type information that was found concerning a SPARQL entity.
  */
sealed trait SparqlEntityTypeInfoV2

/**
  * Represents type information about a property.
  *
  * @param objectTypeIri an IRI representing the type of the objects of the property.
  */
case class PropertyTypeInfoV2(objectTypeIri: IRI) extends SparqlEntityTypeInfoV2

/**
  * Represents type information about a SPARQL entity that's not a property, meaning that it is either a variable
  * or an IRI.
  *
  * @param typeIri an IRI representing the entity's type.
  */
case class NonPropertyTypeInfoV2(typeIri: IRI) extends SparqlEntityTypeInfoV2

/**
  * Represents a SPARQL entity that we can get type information about.
  */
sealed trait TypeableEntityV2

/**
  * Represents a SPARQL variable.
  *
  * @param variableName the name of the variable.
  */
case class TypeableVariableV2(variableName: String) extends TypeableEntityV2

/**
  * Represents an IRI that we need type information about.
  *
  * @param iri the IRI.
  */
case class TypeableIriV2(iri: IRI) extends TypeableEntityV2

/**
  * Represents the result of type inspection.
  *
  * @param typedEntities a map of SPARQL entities to the types that were determined for them.
  */
case class TypeInspectionResultV2(typedEntities: Map[TypeableEntityV2, SparqlEntityTypeInfoV2])

/**
  * A trait for classes that can get type information from a parsed SPARQL search query in different ways.
  */
sealed trait TypeInspectorV2 {
    /**
      * Given the WHERE clause from a parsed SPARQL search query, returns information about the types found
      * in the query.
      *
      * TODO: change this method signature so it has a way of getting info about entity IRIs in the API ontologies.
      *
      * @param whereClause the SPARQL WHERE clause.
      * @return information about the types that were found in the query.
      */
    def inspectTypes(whereClause: SimpleWhereClause): TypeInspectionResultV2
}

/**
  * A [[TypeInspectorV2]] that relies on explicit type annotations in SPARQL. There are two kinds of type annotations:
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
class ExplicitTypeInspectorV2(apiType: ApiV2Schema) extends TypeInspectorV2 {

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
    private case class ExplicitAnnotationV2Simple(typeableEntity: TypeableEntityV2, annotationProp: TypeAnnotationPropertiesV2.Value, typeIri: IRI)

    if (apiType != ApiV2Simple) {
        throw NotImplementedException("Type inspection with value objects is not yet implemented")
    }

    def inspectTypes(whereClause: SimpleWhereClause): TypeInspectionResultV2 = {
        val maybeTypedEntities = collection.mutable.Map.empty[TypeableEntityV2, Option[SparqlEntityTypeInfoV2]]

        // Make a set of all the entities to be typed in the WHERE clause.
        val entitiesToType: Set[TypeableEntityV2] = getTypableEntitiesFromPatterns(whereClause.patterns)

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
                    maybeTypedEntities.put(explicitAnnotation.typeableEntity, Some(NonPropertyTypeInfoV2(explicitAnnotation.typeIri)))

                case TypeAnnotationPropertiesV2.OBJECT_TYPE =>
                    maybeTypedEntities.put(explicitAnnotation.typeableEntity, Some(PropertyTypeInfoV2(explicitAnnotation.typeIri)))
            }
        }

        // If any entities still don't have types, throw an exception.

        val nonTypedEntities: Vector[TypeableEntityV2] = maybeTypedEntities.filter {
            case (_, typeInfo) => typeInfo.isEmpty
        }.keys.toVector

        if (nonTypedEntities.nonEmpty) {
            throw SparqlSearchException(s"Types could not be determined for the following SPARQL entities: ${nonTypedEntities.mkString(", ")}")
        }

        val typedEntities: Map[TypeableEntityV2, SparqlEntityTypeInfoV2] = maybeTypedEntities.map {
            case (typedEntity, typeInfo) => (typedEntity, typeInfo.get)
        }.toMap

        TypeInspectionResultV2(typedEntities = typedEntities)
    }

    /**
      * Removes type annotations from a SPARQL WHERE clause.
      *
      * @param whereClause the WHERE clause to be filtered.
      * @return the same WHERE clause, minus any type annotations.
      */
    def removeTypeAnnotations(whereClause: SimpleWhereClause): SimpleWhereClause = {
        SimpleWhereClause(removeTypeAnnotationsFromPatterns(whereClause.patterns))
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
    private def getTypableEntitiesFromPatterns(patterns: Seq[QueryPattern]): Set[TypeableEntityV2] = {
        patterns.collect {
            case statementPattern: StatementPattern =>
                // Don't look for a type annotation of an IRI that's the object of rdf:type.
                statementPattern.pred match {
                    case IriRef(OntologyConstants.Rdf.Type) => toTypeableEntities(Seq(statementPattern.subj, statementPattern.pred))
                    case _ => toTypeableEntities(Seq(statementPattern.subj, statementPattern.pred, statementPattern.obj))
                }

            case optionalPattern: OptionalPattern => getTypableEntitiesFromPatterns(optionalPattern.patterns)

            case unionPattern: UnionPattern =>
                unionPattern.blocks.flatMap {
                    patterns: Seq[QueryPattern] => getTypableEntitiesFromPatterns(patterns)
                }.toSet
        }.flatten.toSet
    }

    /**
      * Given a SPARQL entity that is known to need type information, converts it to a [[TypeableEntityV2]].
      *
      * @param entity a SPARQL entity that is known to need type information.
      * @return a [[TypeableEntityV2]].
      */
    private def toTypeableEntity(entity: Entity): TypeableEntityV2 = {
        entity match {
            case QueryVariable(variableName) => TypeableVariableV2(variableName)
            case IriRef(iri) => TypeableIriV2(iri)
            case _ => throw AssertionException(s"Entity cannot be typed: $entity")
        }
    }

    /**
      * Given a sequence of entities, finds the ones that need type information and returns them as
      * [[TypeableEntityV2]] objects.
      *
      * @param entities the entities to be checked.
      * @return a sequence of typeable entities.
      */
    private def toTypeableEntities(entities: Seq[Entity]): Set[TypeableEntityV2] = {
        entities.collect {
            case QueryVariable(variableName) => TypeableVariableV2(variableName)
            case IriRef(iri) if !TypeInspectionConstantsV2.ApiV2SimpleNonTypeableIris(iri) => TypeableIriV2(iri)
        }.toSet
    }
}
