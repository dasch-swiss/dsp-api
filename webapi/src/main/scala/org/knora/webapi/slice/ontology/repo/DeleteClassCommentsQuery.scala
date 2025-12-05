/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo
import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import zio.*

import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase
import org.knora.webapi.slice.ontology.api.LastModificationDate

object DeleteClassCommentsQuery extends QueryBuilderHelper {

  def build(
    resourceClassIri: ResourceClassIri,
    lastModificationDate: LastModificationDate,
  ): UIO[ModifyQuery] = Clock.instant.map { now =>
    val ontology      = toRdfIri(resourceClassIri.ontologyIri)
    val resourceClass = toRdfIri(resourceClassIri)

    val comments = variable("comments")

    Queries
      .MODIFY()
      .prefix(XSD.NS, OWL.NS, RDFS.NS, KnoraBase.NS, NS(resourceClassIri.ontologyIri))
      .delete(
        resourceClass.has(RDFS.COMMENT, comments),
        ontology.has(KnoraBase.lastModificationDate, toRdfLiteral(lastModificationDate)),
      )
      .from(ontology)
      .insert(ontology.has(KnoraBase.lastModificationDate, toRdfLiteral(now)))
      .into(ontology)
      .where(
        ontology
          .isA(OWL.ONTOLOGY)
          .andHas(KnoraBase.lastModificationDate, toRdfLiteral(lastModificationDate))
          .and(resourceClass.has(RDFS.COMMENT, comments))
          .from(ontology),
      )
  }

}
