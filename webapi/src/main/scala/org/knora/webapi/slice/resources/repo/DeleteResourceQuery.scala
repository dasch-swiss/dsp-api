/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import java.time.Instant

import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB

object DeleteResourceQuery extends QueryBuilderHelper {

  def build(
    dataNamedGraph: String,
    resourceIri: String,
    maybeDeleteComment: Option[String],
    currentTime: Instant,
    requestingUser: String,
  ): ModifyQuery = {
    val dataGraph                    = Rdf.iri(dataNamedGraph)
    val resource                     = Rdf.iri(resourceIri)
    val resourceClass                = variable("resourceClass")
    val resourceLastModificationDate = variable("resourceLastModificationDate")
    val currentTimeLiteral           = Rdf.literalOfType(currentTime.toString, XSD.DATETIME)

    // DELETE patterns
    val deletePatterns = List(
      resource.has(KB.lastModificationDate, resourceLastModificationDate),
      resource.has(KB.isDeleted, Rdf.literalOf(false)),
    )

    // INSERT patterns
    val insertBase: TriplePattern = resource
      .has(KB.isDeleted, Rdf.literalOf(true))
      .andHas(KB.deletedBy, Rdf.iri(requestingUser))
      .andHas(KB.deleteDate, currentTimeLiteral)

    val insertComment: List[TriplePattern] =
      maybeDeleteComment.map(c => resource.has(KB.deleteComment, Rdf.literalOf(c))).toList

    val insertLastMod: TriplePattern = resource.has(KB.lastModificationDate, currentTimeLiteral)

    val insertPatterns = insertBase :: insertComment ::: List(insertLastMod)

    // WHERE patterns
    val typeCheck = resource
      .has(RDF.TYPE, resourceClass)
      .andHas(KB.isDeleted, Rdf.literalOf(false))
      .and(resourceClass.has(zeroOrMore(RDFS.SUBCLASSOF), KB.Resource))

    val optionalLastMod = resource.has(KB.lastModificationDate, resourceLastModificationDate).optional()

    Queries
      .MODIFY()
      .prefix(RDF.NS, RDFS.NS, XSD.NS, KB.NS)
      .from(dataGraph)
      .delete(deletePatterns*)
      .into(dataGraph)
      .insert(insertPatterns*)
      .where(typeCheck, optionalLastMod)
  }
}
