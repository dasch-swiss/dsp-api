/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.twirl

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
case class SparqlTemplateResourceToCreate(
  resourceIri: IRI,
  permissions: String,
  sparqlForValues: String,
  resourceClassIri: IRI,
  resourceLabel: String,
  resourceCreationDate: Instant
)
