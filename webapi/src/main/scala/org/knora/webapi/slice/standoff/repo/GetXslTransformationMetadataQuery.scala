/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.standoff.repo

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

/**
 * Builds a SELECT query that returns the metadata of a `knora-base:XSLTransformation`
 * resource needed to construct its Sipi file URL: the resource class, the file
 * value IRI, the internal filename and MIME type, and the project IRI.
 *
 * Used by `StandoffMappingService.getXSLTransformation` instead of a full
 * `ReadResourcesService` round-trip — the latter would form a layer cycle
 * through `ConstructResponseUtilV2`.
 */
object GetXslTransformationMetadataQuery extends QueryBuilderHelper {

  val resourceClass: String    = "resourceClass"
  val fileValueIri: String     = "fileValueIri"
  val internalFilename: String = "internalFilename"
  val internalMimeType: String = "internalMimeType"
  val projectIri: String       = "projectIri"

  def build(xslIri: IRI): SelectQuery = {
    val res                 = Rdf.iri(xslIri)
    val resourceClassVar    = variable(resourceClass)
    val fileValueIriVar     = variable(fileValueIri)
    val internalFilenameVar = variable(internalFilename)
    val internalMimeTypeVar = variable(internalMimeType)
    val projectIriVar       = variable(projectIri)
    val hasTextFileValue    = Rdf.iri(OntologyConstants.KnoraBase.HasTextFileValue)
    val rdfType             = Rdf.iri(RDF.TYPE.stringValue)

    Queries
      .SELECT(
        resourceClassVar,
        fileValueIriVar,
        internalFilenameVar,
        internalMimeTypeVar,
        projectIriVar,
      )
      .where(
        res
          .has(rdfType, resourceClassVar)
          .andHas(KnoraBase.attachedToProject, projectIriVar)
          .andHas(hasTextFileValue, fileValueIriVar),
        fileValueIriVar
          .has(KnoraBase.internalFilename, internalFilenameVar)
          .andHas(KnoraBase.internalMimeType, internalMimeTypeVar),
      )
      .prefix(KnoraBase.NS, RDF.NS)
  }
}
