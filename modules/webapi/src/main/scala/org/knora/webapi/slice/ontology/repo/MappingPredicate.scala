/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDFS

enum MappingPredicate(val iri: IRI):
  case SubClassOf    extends MappingPredicate(RDFS.SUBCLASSOF)
  case SubPropertyOf extends MappingPredicate(RDFS.SUBPROPERTYOF)
