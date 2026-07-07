/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import eu.timepit.refined.types.string.NonEmptyString
import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object CreateOntologyQuery extends QueryBuilderHelper {

  def build(
    ontologyIri: OntologyIri,
    projectIri: ProjectIri,
    isShared: Boolean,
    ontologyLabel: String,
    ontologyComment: Option[NonEmptyString],
  ): UIO[(LastModificationDate, Update)] = LastModificationDate.instant.map { lmd =>
    val ontology = toRdfIri(ontologyIri)
    val project  = Rdf.iri(projectIri.value)

    val basePattern = ontology
      .isA(OWL.ONTOLOGY)
      .andHas(KB.attachedToProject, project)
      .andHas(KB.isShared, Rdf.literalOf(isShared))
      .andHas(RDFS.LABEL, Rdf.literalOfType(ontologyLabel, XSD.STRING))

    val withComment = ontologyComment.fold(basePattern) { comment =>
      basePattern.andHas(RDFS.COMMENT, Rdf.literalOfType(comment.value, XSD.STRING))
    }

    val insertPattern = withComment.andHas(KB.lastModificationDate, toRdfLiteral(lmd))

    val existingOntologyType = variable("existingOntologyType")
    val filterNotExists      = GraphPatterns.filterNotExists(ontology.isA(existingOntologyType))

    // Workaround: rdf4j drops FILTER NOT EXISTS when it's the only WHERE pattern.
    // See https://github.com/eclipse-rdf4j/rdf4j/issues/5561
    val insertQuery = Queries
      .MODIFY()
      .prefix(KB.NS, RDF.NS, RDFS.NS, XSD.NS, OWL.NS)
      .insert(insertPattern)
      .into(ontology)
      .getQueryString
      .replaceFirst("WHERE \\{\\s*}", "")
      .strip()

    val sparql = s"$insertQuery\nWHERE { ${filterNotExists.getQueryString} }"
    (lmd, Update(sparql))
  }
}
