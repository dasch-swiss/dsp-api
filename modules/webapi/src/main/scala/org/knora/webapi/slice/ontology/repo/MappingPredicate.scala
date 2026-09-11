/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.knora.sparqlbuilder.Iri
import org.knora.webapi.messages.OntologyConstants.Rdfs

/** The predicate an ontology mapping is stored with: `rdfs:subClassOf` for classes, `rdfs:subPropertyOf` for properties. */
enum MappingPredicate(val iri: Iri):
  case SubClassOf    extends MappingPredicate(Iri.unsafeFrom(Rdfs.SubClassOf))
  case SubPropertyOf extends MappingPredicate(Iri.unsafeFrom(Rdfs.SubPropertyOf))
