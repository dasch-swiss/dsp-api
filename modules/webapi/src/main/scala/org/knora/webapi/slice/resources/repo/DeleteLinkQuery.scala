/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.IO
import zio.ZIO

import java.time.Instant

import dsp.errors.SparqlGenerationException
import org.knora.sparqlbuilder.*
import org.knora.webapi.IRI
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.resources.repo.model.SparqlTemplateLinkUpdate
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Deletes an existing link between two resources.
 *
 * This query:
 * 1. Deletes the direct link between source and target resources
 * 2. Detaches the current LinkValue from the link source
 * 3. Creates a new version of the LinkValue marked as deleted with reference count 0
 * 4. Updates the link source's last modification date
 */
object DeleteLinkQuery {

  private def failIf(condition: Boolean, message: String): IO[SparqlGenerationException, Unit] =
    ZIO.fail(SparqlGenerationException(message)).when(condition).unit

  /**
   * Builds a SPARQL UPDATE query to delete a link between two resources.
   *
   * @param dataNamedGraph the named graph in which the project stores its data
   * @param linkSourceIri  the resource that is the source of the link
   * @param linkUpdate     a [[SparqlTemplateLinkUpdate]] specifying how to update the link
   * @param maybeComment   an optional comment explaining why the link is being deleted
   * @param deletedAt    an xsd:dateTimeStamp that will be attached to the resources
   * @param userIri the IRI of the user making the request
   */
  def build(
    dataNamedGraph: IRI,
    linkSourceIri: IRI,
    linkUpdate: SparqlTemplateLinkUpdate,
    maybeComment: Option[String],
    deletedAt: Instant,
    userIri: UserIri,
  ): IO[SparqlGenerationException, Update] =
    for {
      _ <- failIf(!linkUpdate.deleteDirectLink, "linkUpdate.deleteDirectLink must be true in this SPARQL template")
      _ <- failIf(!linkUpdate.linkValueExists, "linkUpdate.linkValueExists must be true in this SPARQL template")
      _ <- failIf(!linkUpdate.directLinkExists, "linkUpdate.directLinkExists must be true in this SPARQL template")
      _ <- failIf(linkUpdate.newReferenceCount != 0, "linkUpdate.newReferenceCount must be 0 in this SPARQL template")
    } yield {
      val dataGraph         = Iri.unsafeFrom(dataNamedGraph)
      val linkSource        = Iri.unsafeFrom(linkSourceIri)
      val linkProperty      = Iri.unsafeFrom(linkUpdate.linkPropertyIri.toInternalSchema.toIri)
      val linkValueProperty = Iri.unsafeFrom(linkUpdate.linkPropertyIri.toInternalSchema.toIri + "Value")
      val linkTarget        = Iri.unsafeFrom(linkUpdate.linkTargetIri)
      val newLinkValue      = Iri.unsafeFrom(linkUpdate.newLinkValueIri.value)
      val deletedByUser     = Iri.unsafeFrom(userIri.value)
      val newValueCreator   = Iri.unsafeFrom(linkUpdate.newLinkValueCreator)
      val deletionDate      = Literal.dateTime(deletedAt)

      val commentInsert =
        maybeComment.whenSome(comment => sparql"$newLinkValue knora-base:deleteComment ${Literal.string(comment)} .")

      Update(
        sparql"""|PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                 |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |
                 |DELETE {
                 |  GRAPH $dataGraph {
                 |    # Delete the link source's last modification date so we can update it
                 |    $linkSource knora-base:lastModificationDate ?linkSourceLastModificationDate .
                 |    # Delete the direct link
                 |    $linkSource $linkProperty $linkTarget .
                 |    # Detach the LinkValue from the link source
                 |    $linkSource $linkValueProperty ?currentLinkValue .
                 |    # Delete the UUID from the current version of the link value
                 |    ?currentLinkValue knora-base:valueHasUUID ?currentLinkUUID .
                 |  }
                 |}
                 |INSERT {
                 |  GRAPH $dataGraph {
                 |    $newLinkValue a knora-base:LinkValue ;
                 |      rdf:subject $linkSource ;
                 |      rdf:predicate $linkProperty ;
                 |      rdf:object $linkTarget ;
                 |      knora-base:valueHasString ${Literal.string(linkUpdate.linkTargetIri)} ;
                 |      knora-base:valueHasRefCount ${Literal.int(linkUpdate.newReferenceCount)} ;
                 |      knora-base:valueCreationDate $deletionDate ;
                 |      knora-base:deleteDate $deletionDate ;
                 |      knora-base:deletedBy $deletedByUser ;
                 |      knora-base:previousValue ?currentLinkValue ;
                 |      knora-base:valueHasUUID ?currentLinkUUID ;
                 |      knora-base:isDeleted true ;
                 |      knora-base:attachedToUser $newValueCreator ;
                 |      knora-base:hasPermissions ${Literal.string(linkUpdate.newLinkValuePermissions)} .
                 |    # Attach the new LinkValue to its containing resource
                 |    $linkSource $linkValueProperty $newLinkValue .
                 |    # Update the link source's last modification date
                 |    $linkSource knora-base:lastModificationDate $deletionDate .
                 |    $commentInsert
                 |  }
                 |}
                 |WHERE {
                 |  # Check that the link source exists, is not deleted, and is a knora-base:Resource
                 |  $linkSource a ?linkSourceClass ;
                 |    knora-base:isDeleted false .
                 |  ?linkSourceClass rdfs:subClassOf* knora-base:Resource .
                 |  # Make sure a direct link exists between the two resources
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
    }
}
