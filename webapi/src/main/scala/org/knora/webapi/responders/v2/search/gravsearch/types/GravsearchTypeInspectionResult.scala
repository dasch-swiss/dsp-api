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

package org.knora.webapi.responders.v2.search.gravsearch.types

import org.knora.webapi.responders.v2.search.{Entity, IriRef, QueryVariable}
import org.knora.webapi.util.SmartIri

/**
  * Represents the type information that was found concerning a Gravsearch entity.
  */
sealed trait GravsearchEntityTypeInfo

/**
  * Represents type information about a property.
  *
  * @param objectTypeIri an IRI representing the type of the objects of the property.
  */
case class PropertyTypeInfo(objectTypeIri: SmartIri) extends GravsearchEntityTypeInfo {
    override def toString: String = s"knora-api:objectType ${IriRef(objectTypeIri).toSparql}"
}

/**
  * Represents type information about a SPARQL entity that's not a property, meaning that it is either a variable
  * or an IRI.
  *
  * @param typeIri an IRI representing the entity's type.
  */
case class NonPropertyTypeInfo(typeIri: SmartIri) extends GravsearchEntityTypeInfo {
    override def toString: String = s"rdf:type ${IriRef(typeIri).toSparql}"
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
case class GravsearchTypeInspectionResult(entities: Map[TypeableEntity, GravsearchEntityTypeInfo]) {
    /**
      * Given an [[Entity]], returns its type, if the entity is typeable and its type is available.
      *
      * @param entity the entity whose type is to be checked.
      * @return the entity's type, if available.
      */
    def getTypeOfEntity(entity: Entity): Option[GravsearchEntityTypeInfo] = {
        GravsearchTypeInspectionUtil.maybeTypeableEntity(entity).flatMap(typeableEntity => entities.get(typeableEntity))
    }
}
