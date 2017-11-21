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

package org.knora.webapi.util.search

import org.knora.webapi.util.SmartIri

/**
  * Represents the type information that was found concerning a SPARQL entity.
  */
sealed trait SparqlEntityTypeInfo

/**
  * Represents type information about a property.
  *
  * @param objectTypeIri an IRI representing the type of the objects of the property.
  */
case class PropertyTypeInfo(objectTypeIri: SmartIri) extends SparqlEntityTypeInfo

/**
  * Represents type information about a SPARQL entity that's not a property, meaning that it is either a variable
  * or an IRI.
  *
  * @param typeIri an IRI representing the entity's type.
  */
case class NonPropertyTypeInfo(typeIri: SmartIri) extends SparqlEntityTypeInfo

/**
  * Represents a SPARQL entity that we can get type information about.
  */
sealed trait TypeableEntity

/**
  * Represents a SPARQL variable.
  *
  * @param variableName the name of the variable.
  */
case class TypeableVariable(variableName: String) extends TypeableEntity

/**
  * Represents an IRI that we need type information about.
  *
  * @param iri the IRI.
  */
case class TypeableIri(iri: SmartIri) extends TypeableEntity

/**
  * Represents the result of type inspection.
  *
  * @param typedEntities a map of SPARQL entities to the types that were determined for them.
  */
case class TypeInspectionResult(typedEntities: Map[TypeableEntity, SparqlEntityTypeInfo])