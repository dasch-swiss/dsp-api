/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo
import org.eclipse.rdf4j.sparqlbuilder.core.query.ConstructQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries

import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.QueryBuilderHelper

object GetOntologyGraphQuery extends QueryBuilderHelper {
  def build(ontologyIri: OntologyIri): ConstructQuery =
    val (s, p, o)    = spo
    val graphPattern = s.has(p, o)
    Queries.CONSTRUCT(graphPattern).where(graphPattern.from(toRdfIri(ontologyIri)))
}
