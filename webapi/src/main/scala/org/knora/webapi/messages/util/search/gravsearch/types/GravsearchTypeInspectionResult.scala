/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.types

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.search.{Entity, IriRef, QueryVariable}

/**
 * Represents the type information that was found concerning a Gravsearch entity.
 */
sealed trait GravsearchEntityTypeInfo

/**
 * Represents type information about a property.
 *
 * @param objectTypeIri        an IRI representing the type of the objects of the property.
 * @param objectIsResourceType `true` if the property's object type is a resource type. Property is a link.
 * @param objectIsValueType    `true` if the property's object type is a value type. Property is not a link.
 * @param objectIsStandoffTagType    `true` if the property's object type is a standoff tag type. Property is not a link.
 */
case class PropertyTypeInfo(
  objectTypeIri: SmartIri,
  objectIsResourceType: Boolean = false,
  objectIsValueType: Boolean = false,
  objectIsStandoffTagType: Boolean = false
) extends GravsearchEntityTypeInfo {
  override def toString: String = s"knora-api:objectType ${IriRef(objectTypeIri).toSparql}"

  /**
   * Converts this [[PropertyTypeInfo]] to a [[NonPropertyTypeInfo]].
   */
  def toNonPropertyTypeInfo: NonPropertyTypeInfo = NonPropertyTypeInfo(
    typeIri = objectTypeIri,
    isResourceType = objectIsResourceType,
    isValueType = objectIsValueType,
    isStandoffTagType = objectIsStandoffTagType
  )
}

/**
 * Represents type information about a SPARQL entity that's not a property, meaning that it is either a variable
 * or an IRI.
 *
 * @param typeIri        an IRI representing the entity's type.
 * @param isResourceType `true` if this is a resource type.
 * @param isValueType    `true` if this is a value type.
 * @param isStandoffTagType `true` if this is a standoff tag type.
 */
case class NonPropertyTypeInfo(
  typeIri: SmartIri,
  isResourceType: Boolean = false,
  isValueType: Boolean = false,
  isStandoffTagType: Boolean = false
) extends GravsearchEntityTypeInfo {
  override def toString: String = s"rdf:type ${IriRef(typeIri).toSparql}"

  /**
   * Converts this [[NonPropertyTypeInfo]] to a [[PropertyTypeInfo]].
   */
  def toPropertyTypeInfo: PropertyTypeInfo = PropertyTypeInfo(
    objectTypeIri = typeIri,
    objectIsResourceType = isResourceType,
    objectIsValueType = isValueType,
    objectIsStandoffTagType = isStandoffTagType
  )
}

/**
 * Represents a SPARQL entity that we can get type information about.
 */
sealed trait TypeableEntity

/**
 * Represents a Gravsearch variable.
 *
 * @param variableName the name of the variable.
 */
case class TypeableVariable(variableName: String) extends TypeableEntity {
  override def toString: String = QueryVariable(variableName).toSparql
}

/**
 * Represents an IRI that we need type information about.
 *
 * @param iri the IRI.
 */
case class TypeableIri(iri: SmartIri) extends TypeableEntity {
  override def toString: String = IriRef(iri).toSparql
}

/**
 * Represents the result of type inspection.
 *
 * @param entities a map of Gravsearch entities to the types that were determined for them.
 */
case class GravsearchTypeInspectionResult(
  entities: Map[TypeableEntity, GravsearchEntityTypeInfo],
  entitiesInferredFromProperties: Map[TypeableEntity, Set[GravsearchEntityTypeInfo]] = Map.empty
) {

  /**
   * Given an [[Entity]], returns its type, if the entity is typeable and its type is available.
   *
   * @param entity the entity whose type is to be checked.
   * @return the entity's type, if available.
   */
  def getTypeOfEntity(entity: Entity): Option[GravsearchEntityTypeInfo] =
    GravsearchTypeInspectionUtil.maybeTypeableEntity(entity).flatMap(typeableEntity => entities.get(typeableEntity))
}
