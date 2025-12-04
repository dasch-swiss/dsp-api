/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries

import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.QueryBuilderHelper

object DeleteOntologyQuery extends QueryBuilderHelper {
  def build(ontologyIri: OntologyIri): ModifyQuery =
    val (s, p, o)     = spo
    val ontologyGraph = toRdfIri(ontologyIri)
    Queries.MODIFY().delete(s.has(p, o)).from(ontologyGraph).where(s.has(p, o).from(ontologyGraph))
}
