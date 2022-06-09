/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.types

import org.knora.webapi.IRI
import dsp.errors.AssertionException
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter

/**
 * Represents an intermediate result during type inspection. This is different from [[GravsearchTypeInspectionResult]]
 * in that an entity can have multiple types, which means that the entity has been used inconsistently.
 *
 * @param entities a map of Gravsearch entities to the types that were determined for them. If an entity
 *                 has more than one type, this means that it has been used with inconsistent types.
 * @param entitiesInferredFromPropertyIris entities whose types were inferred from their use with a property IRI.
 */
case class IntermediateTypeInspectionResult(
  entities: Map[TypeableEntity, Set[GravsearchEntityTypeInfo]],
  entitiesInferredFromPropertyIris: Map[TypeableEntity, Set[GravsearchEntityTypeInfo]] = Map.empty
) {

  /**
   * Adds types for an entity.
   *
   * @param entity               the entity for which types have been found.
   * @param entityTypes          the types to be added.
   * @param inferredFromPropertyIri `true` if any of the types of this entity were inferred from its use with a property IRI.
   * @return a new [[IntermediateTypeInspectionResult]] containing the additional type information.
   */
  def addTypes(
    entity: TypeableEntity,
    entityTypes: Set[GravsearchEntityTypeInfo],
    inferredFromPropertyIri: Boolean = false
  ): IntermediateTypeInspectionResult =
    if (entityTypes.nonEmpty) {
      val newTypes = entities.getOrElse(entity, Set.empty[GravsearchEntityTypeInfo]) ++ entityTypes

      val newEntitiesInferredFromPropertyIris = if (inferredFromPropertyIri && entityTypes.nonEmpty) {
        val newTypesInferredFromPropertyIris =
          entitiesInferredFromPropertyIris.getOrElse(entity, Set.empty[GravsearchEntityTypeInfo]) ++ entityTypes
        entitiesInferredFromPropertyIris + (entity -> newTypesInferredFromPropertyIris)
      } else {
        entitiesInferredFromPropertyIris
      }

      IntermediateTypeInspectionResult(
        entities = entities + (entity -> newTypes),
        entitiesInferredFromPropertyIris = newEntitiesInferredFromPropertyIris
      )
    } else {
      this
    }

  /**
   * removes types of an entity.
   *
   * @param entity       the entity for which types must be removed.
   * @param typeToRemove the type to be removed.
   * @return a new [[IntermediateTypeInspectionResult]] without the specified type information assigned to the entity.
   */
  def removeType(entity: TypeableEntity, typeToRemove: GravsearchEntityTypeInfo): IntermediateTypeInspectionResult = {
    val remainingTypes = entities.getOrElse(entity, Set.empty[GravsearchEntityTypeInfo]) - typeToRemove

    val updatedEntitiesInferredFromProperties =
      if (entitiesInferredFromPropertyIris.exists(aType => aType._1 == entity && aType._2.contains(typeToRemove))) {
        val remainingTypesInferredFromProperty: Set[GravsearchEntityTypeInfo] = entitiesInferredFromPropertyIris
          .getOrElse(entity, Set.empty[GravsearchEntityTypeInfo]) - typeToRemove
        if (remainingTypesInferredFromProperty.nonEmpty) {
          entitiesInferredFromPropertyIris + (entity -> remainingTypesInferredFromProperty)
        } else {
          entitiesInferredFromPropertyIris - entity
        }
      } else {
        entitiesInferredFromPropertyIris
      }

    IntermediateTypeInspectionResult(
      entities = entities + (entity -> remainingTypes),
      entitiesInferredFromPropertyIris = updatedEntitiesInferredFromProperties
    )
  }

  /**
   * Returns the entities for which types have not been found.
   */
  def untypedEntities: Set[TypeableEntity] =
    entities.collect {
      case (entity: TypeableEntity, entityTypes: Set[GravsearchEntityTypeInfo]) if entityTypes.isEmpty => entity
    }.toSet

  /**
   * Returns the entities that have been used with inconsistent types.
   */
  def entitiesWithInconsistentTypes: Map[TypeableEntity, Set[GravsearchEntityTypeInfo]] =
    entities.filter { case (_, entityTypes) =>
      entityTypes.size > 1
    }

  /**
   * Converts this [[IntermediateTypeInspectionResult]] to a [[GravsearchTypeInspectionResult]]. Before calling
   * this method, ensure that `entitiesWithInconsistentTypes` returns an empty map.
   */
  def toFinalResult: GravsearchTypeInspectionResult =
    GravsearchTypeInspectionResult(
      entities = entities.map { case (entity, entityTypes) =>
        if (entityTypes.size == 1) {
          entity -> entityTypes.head
        } else {
          throw AssertionException(s"Cannot generate final type inspection result because of inconsistent types")
        }
      },
      entitiesInferredFromProperties = entitiesInferredFromPropertyIris
    )
}

object IntermediateTypeInspectionResult {

  /**
   * Constructs an [[IntermediateTypeInspectionResult]] for the given set of typeable entities, with built-in
   * types specified (e.g. for `rdfs:label`).
   *
   * @param entities the set of typeable entities found in the WHERE clause of a Gravsearch query.
   */
  def apply(
    entities: Set[TypeableEntity]
  )(implicit stringFormatter: StringFormatter): IntermediateTypeInspectionResult = {
    // Make an IntermediateTypeInspectionResult in which each typeable entity has no types.
    val emptyResult = new IntermediateTypeInspectionResult(
      entities = entities.map(entity => entity -> Set.empty[GravsearchEntityTypeInfo]).toMap
    )

    // Collect the typeable IRIs used.
    val irisUsed: Set[IRI] = entities.collect { case typeableIri: TypeableIri =>
      typeableIri.iri.toString
    }

    // Find the IRIs that represent resource metadata properties, and get their object types.
    val resourceMetadataPropertyTypesUsed: Map[TypeableIri, PropertyTypeInfo] =
      OntologyConstants.ResourceMetadataPropertyAxioms.view
        .filterKeys(irisUsed)
        .toMap
        .map { case (propertyIri: IRI, objectTypeIri: IRI) =>
          val isValue: Boolean = GravsearchTypeInspectionUtil.GravsearchValueTypeIris.contains(objectTypeIri)
          TypeableIri(propertyIri.toSmartIri) -> PropertyTypeInfo(
            objectTypeIri = objectTypeIri.toSmartIri,
            objectIsResourceType = !isValue,
            objectIsValueType = isValue
          )
        }

    // Add those types to the IntermediateTypeInspectionResult.
    resourceMetadataPropertyTypesUsed.foldLeft(emptyResult) {
      case (acc: IntermediateTypeInspectionResult, (entity: TypeableIri, entityType: PropertyTypeInfo)) =>
        acc.addTypes(entity, Set(entityType))
    }
  }
}
