/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.Clock
import zio.IO
import zio.ZIO

import java.time.Instant

import dsp.errors.SparqlGenerationException
import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.resources.repo.model.SparqlTemplateLinkUpdate
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Changes the metadata on a LinkValue (e.g. permissions, comment) without changing the link target.
 */
object ChangeLinkMetadataQuery {

  private def failIf(condition: Boolean, message: String): IO[SparqlGenerationException, Unit] =
    ZIO.fail(SparqlGenerationException(message)).when(condition).unit

  /**
   * Builds a SPARQL UPDATE query to change link metadata.
   *
   * @param project          the project the resource belongs to (used to determine the named graph)
   * @param linkSourceIri    the resource that is the source of the link
   * @param linkUpdate       a [[SparqlTemplateLinkUpdate]] specifying how to update the link value
   * @param maybeComment     an optional comment on the new link value
   * @return a tuple of (timestamp used, SPARQL Update query)
   */
  def build(
    project: Project,
    linkSourceIri: ResourceIri,
    linkUpdate: SparqlTemplateLinkUpdate,
    maybeComment: Option[String],
  ): IO[SparqlGenerationException, (Instant, Update)] =
    for {
      _   <- failIf(!linkUpdate.linkValueExists, "linkUpdate.linkValueExists must be true in this SPARQL template")
      _   <- failIf(!linkUpdate.directLinkExists, "linkUpdate.directLinkExists must be true in this SPARQL template")
      now <- Clock.instant
    } yield {
      val dataGraph         = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
      val linkSource        = Iri.unsafeFrom(linkSourceIri.value)
      val linkProperty      = Iri.unsafeFrom(linkUpdate.linkPropertyIri.toInternalSchema.toIri)
      val linkValueProperty = Iri.unsafeFrom(linkUpdate.linkPropertyIri.toInternalSchema.toIri + "Value")
      val linkTarget        = Iri.unsafeFrom(linkUpdate.linkTargetIri)
      val newLinkValue      = Iri.unsafeFrom(linkUpdate.newLinkValueIri.value)
      val newValueCreator   = Iri.unsafeFrom(linkUpdate.newLinkValueCreator)
      val nowLiteral        = Literal.dateTime(now)

      val query = Update(
        sparql"""|PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                 |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |
                 |DELETE {
                 |  GRAPH $dataGraph {
                 |    # Delete the link source's last modification date so we can update it
                 |    $linkSource knora-base:lastModificationDate ?linkSourceLastModificationDate .
                 |    # Detach the current LinkValue from the link source
                 |    $linkSource $linkValueProperty ?currentLinkValue .
                 |    # Delete the UUID from the current version of the link value
                 |    ?currentLinkValue knora-base:valueHasUUID ?currentLinkUUID .
                 |  }
                 |}
                 |INSERT {
                 |  GRAPH $dataGraph {
                 |    # Insert a new version of the LinkValue carrying the changed metadata
                 |    $newLinkValue a knora-base:LinkValue .
                 |    $newLinkValue rdf:subject $linkSource .
                 |    $newLinkValue rdf:predicate $linkProperty .
                 |    $newLinkValue rdf:object $linkTarget .
                 |    $newLinkValue knora-base:valueHasString ${Literal.string(linkUpdate.linkTargetIri)} .
                 |    $newLinkValue knora-base:valueHasRefCount ${Literal.int(linkUpdate.newReferenceCount)} .
                 |    $newLinkValue knora-base:valueCreationDate $nowLiteral .
                 |    $newLinkValue knora-base:previousValue ?currentLinkValue .
                 |    $newLinkValue knora-base:valueHasUUID ?currentLinkUUID .
                 |    $newLinkValue knora-base:isDeleted false .
                 |    ${maybeComment.whenSome(comment =>
            sparql"$newLinkValue knora-base:valueHasComment ${Literal.string(comment)} .",
          )}
                 |    $newLinkValue knora-base:attachedToUser $newValueCreator .
                 |    $newLinkValue knora-base:hasPermissions ${Literal.string(linkUpdate.newLinkValuePermissions)} .
                 |    # Attach the new LinkValue to its containing resource
                 |    $linkSource $linkValueProperty $newLinkValue .
                 |    # Update the link source's last modification date
                 |    $linkSource knora-base:lastModificationDate $nowLiteral .
                 |  }
                 |}
                 |WHERE {
                 |  # Check that the link source exists, is not deleted, and is a knora-base:Resource
                 |  $linkSource a ?linkSourceClass ;
                 |    knora-base:isDeleted false .
                 |  ?linkSourceClass rdfs:subClassOf* knora-base:Resource .
                 |  # Make sure the direct link exists
                 |  $linkSource $linkProperty $linkTarget .
                 |  # Make sure a LinkValue exists describing the direct link with the correct reference count
                 |  $linkSource $linkValueProperty ?currentLinkValue .
                 |  ?currentLinkValue a knora-base:LinkValue ;
                 |    rdf:subject $linkSource ;
                 |    rdf:predicate $linkProperty ;
                 |    rdf:object $linkTarget ;
                 |    knora-base:valueHasRefCount ${Literal.int(linkUpdate.currentReferenceCount)} ;
                 |    knora-base:isDeleted false ;
                 |    knora-base:valueHasUUID ?currentLinkUUID .
                 |  # Get the link source's last modification date, if it has one, so we can update it
                 |  OPTIONAL { $linkSource knora-base:lastModificationDate ?linkSourceLastModificationDate . }
                 |}""".render,
      )

      (now, query)
    }
}
