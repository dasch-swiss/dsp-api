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


package org.knora.webapi.twirl

import java.time.Instant

import org.knora.webapi._

/**
  * Represents a resource to be created with its index, label, IRI, permissions, and SPARQL for creating its values
  *
  * @param resourceIri          the IRI of the resource to be created.
  * @param permissions          the permissions user has for creating the new resource.
  * @param sparqlForValues      the SPARQL for creating the values of the resource.
  * @param resourceClassIri     the type of the resource to be created.
  * @param resourceLabel        the label of the resource.
  * @param resourceCreationDate the creation date that should be attached to the resource.
  */

case class SparqlTemplateResourceToCreate(resourceIri: IRI,
                                          permissions: String,
                                          sparqlForValues: String,
                                          resourceClassIri: IRI,
                                          resourceLabel: String,
                                          resourceCreationDate: Instant)
