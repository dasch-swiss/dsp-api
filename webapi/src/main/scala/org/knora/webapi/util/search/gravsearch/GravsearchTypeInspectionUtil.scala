package org.knora.webapi.util.search.gravsearch

import org.knora.webapi.{AssertionException, GravsearchException, IRI, OntologyConstants}
import org.knora.webapi.util.search._
import scala.collection.mutable

/**
  * Utilities for Gravsearch type inspection.
  */
object GravsearchTypeInspectionUtil {

    /**
      * Represents an intermediate result during type inspection.
      *
      * @param typedEntities   a map of Gravsearch entities to the types that were determined for them.
      * @param untypedEntities a set of Gravsearch entities for which types could not be determined.
      */
    case class IntermediateTypeInspectionResult(typedEntities: Map[TypeableEntity, GravsearchEntityTypeInfo],
                                                untypedEntities: Set[TypeableEntity]) {
        def typedEntitiesToMutableMap: mutable.Map[TypeableEntity, GravsearchEntityTypeInfo] = {
            mutable.Map(typedEntities.toSeq: _*)
        }

        def untypedEntitiesToMutableSet: mutable.Set[TypeableEntity] = {
            mutable.Set(untypedEntities.toSeq: _*)
        }
    }

    object IntermediateTypeInspectionResult {
        def apply(mutableTypedEntities: mutable.Map[TypeableEntity, GravsearchEntityTypeInfo],
                  mutableUntypedEntities: mutable.Set[TypeableEntity]): IntermediateTypeInspectionResult = {
            new IntermediateTypeInspectionResult(
                typedEntities = mutableTypedEntities.toMap,
                untypedEntities = Set(mutableUntypedEntities.toSeq: _*)
            )
        }
    }


    /**
      * An enumeration of the properties that are used in type annotations.
      */
    object TypeAnnotationPropertiesV2 extends Enumeration {

        import Ordering.Tuple2 // scala compiler issue: https://issues.scala-lang.org/browse/SI-8541

        val RDF_TYPE: Value = Value(0, OntologyConstants.Rdf.Type)
        val OBJECT_TYPE: Value = Value(1, OntologyConstants.KnoraApiV2Simple.ObjectType)

        val valueMap: Map[IRI, Value] = values.map(v => (v.toString, v)).toMap
    }

    /**
      * The IRIs of types that are recognised in explicit type annotations.
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
    val ApiV2SimpleNonTypeableIris: Set[IRI] = ApiV2SimpleTypeIris ++ TypeAnnotationPropertiesV2.valueMap.keySet

    /**
      * Given a sequence of query patterns, extracts all the entities (variable names or IRIs) that need type information.
      *
      * @param patterns the patterns to be searched.
      * @return a set of typeable entities.
      */
    def getTypableEntitiesFromPatterns(patterns: Seq[QueryPattern]): Set[TypeableEntity] = {
        patterns.collect {
            case statementPattern: StatementPattern =>
                // Don't look for a type annotation of an IRI that's the object of rdf:type.
                statementPattern.pred match {
                    case iriRef: IriRef if iriRef.iri.toString == OntologyConstants.Rdf.Type => toTypeableEntities(Seq(statementPattern.subj))
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
      * Removes explicit type annotations from a sequence of query patterns.
      *
      * @param patterns the patterns to be filtered.
      * @return the same patterns, minus any type annotations.
      */
    def removeTypeAnnotationsFromPatterns(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
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

            case bindPattern: BindPattern => bindPattern
        }
    }


    /**
      * Determines whether a statement pattern represents an explicit type annotation.
      *
      * @param statementPattern the statement pattern.
      * @return `true` if the statement pattern represents a type annotation.
      */
    def isAnnotationStatement(statementPattern: StatementPattern): Boolean = {
        statementPattern.pred match {
            case IriRef(predIri, _) =>
                TypeAnnotationPropertiesV2.valueMap.get(predIri.toString) match {
                    case Some(TypeAnnotationPropertiesV2.RDF_TYPE) =>
                        isValidTypeInAnnotation(statementPattern.obj)

                    case Some(TypeAnnotationPropertiesV2.OBJECT_TYPE) =>
                        if (!isValidTypeInAnnotation(statementPattern.obj)) {
                            throw GravsearchException(s"Object of ${statementPattern.pred} is not a valid type: ${statementPattern.obj}")
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
            case IriRef(objIri, _) if ApiV2SimpleTypeIris(objIri.toString) => true
            case _ => false
        }
    }
}
