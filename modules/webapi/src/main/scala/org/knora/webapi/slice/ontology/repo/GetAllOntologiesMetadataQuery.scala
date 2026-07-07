/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery

import org.knora.webapi.slice.common.QueryBuilderHelper

object GetAllOntologiesMetadataQuery extends QueryBuilderHelper {
  def build: SelectQuery = {
    val (ontologyGraph, ontologyIri, ontologyPred, ontologyObj) =
      (variable("ontologyGraph"), variable("ontologyIri"), variable("ontologyPred"), variable("ontologyObj"))
    Queries
      .SELECT(ontologyGraph, ontologyIri, ontologyPred, ontologyObj)
      .prefix(OWL.NS)
      .where(ontologyIri.isA(OWL.ONTOLOGY).andHas(ontologyPred, ontologyObj).from(ontologyGraph))
  }
}
