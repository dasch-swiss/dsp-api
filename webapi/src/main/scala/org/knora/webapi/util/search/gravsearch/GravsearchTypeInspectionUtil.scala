package org.knora.webapi.util.search.gravsearch

import org.knora.webapi.util.search._
import org.knora.webapi.{AssertionException, GravsearchException, IRI, OntologyConstants}

/**
  * Utilities for Gravsearch type inspection.
  */
object GravsearchTypeInspectionUtil {

    /**
      * Represents an intermediate result during type inspection.
      *
      * @param entities a map of Gravsearch entities to the types that were determined for them. If an entity
      *                 has more that one type, this means that it has been used with inconsistent types.
      */
    case class IntermediateTypeInspectionResult(entities: Map[TypeableEntity, Set[GravsearchEntityTypeInfo]]) {
        /**
          * Adds types for an entity.
          *
          * @param entity      the entity for which types have been found.
          * @param entityTypes the types to be added.
          * @return a new [[IntermediateTypeInspectionResult]] containing the additional type information.
          */
        def addTypes(entity: TypeableEntity, entityTypes: Set[GravsearchEntityTypeInfo]): IntermediateTypeInspectionResult = {
            val newTypes = entities.getOrElse(entity, Set.empty[GravsearchEntityTypeInfo]) ++ entityTypes
            IntermediateTypeInspectionResult(entities = entities + (entity -> newTypes))
        }

        /**
          * Returns the entities for which types have not been found.
          */
        def untypedEntities: Set[TypeableEntity] = {
            entities.collect {
                case (entity, entityTypes) if entityTypes.isEmpty => entity
            }.toSet
        }

        /**
          * Returns the entities that have been used with inconsistent types.
          */
        def entitiesWithInconsistentTypes: Map[TypeableEntity, Set[GravsearchEntityTypeInfo]] = {
            entities.filter {
                case (_, entityTypes) => entityTypes.size > 1
            }
        }

        /**
          * Converts this [[IntermediateTypeInspectionResult]] to a [[GravsearchTypeInspectionResult]]. Before calling
          * this method, ensure that `entitiesWithInconsistentTypes` returns an empty map.
          */
        def toFinalResult: GravsearchTypeInspectionResult = {
            GravsearchTypeInspectionResult(
                entities = entities.map {
                    case (entity, entityTypes) =>
                        if (entityTypes.size == 1) {
                            entity -> entityTypes.head
                        } else {
                            throw AssertionException(s"Cannot generate final type inspection result because of inconsistent types")
                        }
                }
            )
        }
    }

    object IntermediateTypeInspectionResult {
        /**
          * Constructs an [[IntermediateTypeInspectionResult]] for the given set of typeable entities, with no
          * types specified.
          *
          * @param entities the set of typeable entities found in the WHERE clause of a Gravsearch query.
          */
        def apply(entities: Set[TypeableEntity]): IntermediateTypeInspectionResult = {
            new IntermediateTypeInspectionResult(entities = entities.map(entity => entity -> Set.empty[GravsearchEntityTypeInfo]).toMap)
        }
    }


    /**
      * An enumeration of the properties that are used in type annotations.
      */
    object TypeAnnotationPropertiesV2 extends Enumeration {

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
    val ApiV2SimpleNonTypeableIris: Set[IRI] = ApiV2SimpleTypeIris ++ TypeAnnotationPropertiesV2.valueMap.keySet

    /**
      * Given a flattened sequence of query patterns, extracts all the entities (variable names or IRIs) that need type information.
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
        }.flatten.toSet
    }

    /**
      * A [[WhereTransformer]] that returns statements and filters unchanged.
      */
    private class NoOpWhereTransformer extends WhereTransformer {
        override def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[QueryPattern] = Seq(statementPattern)

        override def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)
    }

    /**
      * Flattens all the patterns in a Gravsearch WHERE clause into a single sequence.
      *
      * @param whereClause the WHERE clause.
      * @return a flat sequence of query patterns.
      */
    def flattenPatterns(whereClause: WhereClause): Seq[QueryPattern] = {
        QueryTraverser.transformWherePatterns(
            patterns = whereClause.patterns,
            inputOrderBy = Seq.empty[OrderCriterion],
            whereTransformer = new NoOpWhereTransformer,
            rebuildStructure = false
        )
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
      * Removes explicit type annotations from a Gravsearch WHERE clause.
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
