/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

// #OntologySchema
package org.knora.webapi

/**
  * Indicates the schema that a Knora ontology or ontology entity conforms to.
  */
sealed trait OntologySchema

/**
  * The schema of Knora ontologies and entities that are used in the triplestore.
  */
case object InternalSchema extends OntologySchema

/**
  * The schema of Knora ontologies and entities that are used in API v2.
  */
sealed trait ApiV2Schema extends OntologySchema

/**
  * The simple schema for representing Knora ontologies and entities. This schema represents values as literals
  * when possible.
  */
case object ApiV2Simple extends ApiV2Schema

/**
  * The default (or complex) schema for representing Knora ontologies and entities. This
  * schema always represents values as objects.
  */
case object ApiV2WithValueObjects extends ApiV2Schema
// #OntologySchema
