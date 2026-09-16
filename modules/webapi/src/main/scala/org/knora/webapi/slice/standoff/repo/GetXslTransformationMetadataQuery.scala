/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.standoff.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.IRI
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

/**
 * Builds a SELECT query that returns the metadata of a `knora-base:XSLTransformation`
 * resource needed to construct its Sipi file URL: the resource class, the file
 * value IRI, the internal filename and MIME type, and the project IRI.
 *
 * Used by `StandoffMappingService.getXSLTransformation` instead of a full
 * `ReadResourcesService` round-trip — the latter would form a layer cycle
 * through `ConstructResponseUtilV2`.
 */
object GetXslTransformationMetadataQuery {

  val resourceClass: String    = "resourceClass"
  val fileValueIri: String     = "fileValueIri"
  val internalFilename: String = "internalFilename"
  val internalMimeType: String = "internalMimeType"
  val projectIri: String       = "projectIri"

  def build(xslIri: IRI): Select = {
    val res                 = Iri.unsafeFrom(xslIri)
    val resourceClassVar    = Variable(resourceClass)
    val fileValueIriVar     = Variable(fileValueIri)
    val internalFilenameVar = Variable(internalFilename)
    val internalMimeTypeVar = Variable(internalMimeType)
    val projectIriVar       = Variable(projectIri)

    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |
               |SELECT $resourceClassVar $fileValueIriVar $internalFilenameVar $internalMimeTypeVar $projectIriVar
               |WHERE {
               |  $res rdf:type $resourceClassVar ;
               |    knora-base:attachedToProject $projectIriVar ;
               |    knora-base:hasTextFileValue $fileValueIriVar .
               |  $fileValueIriVar knora-base:internalFilename $internalFilenameVar ;
               |    knora-base:internalMimeType $internalMimeTypeVar .
               |}""".render,
    )
  }
}
