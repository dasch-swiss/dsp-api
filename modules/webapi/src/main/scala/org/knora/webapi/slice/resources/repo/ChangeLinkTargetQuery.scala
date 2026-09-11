/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.IO
import zio.Random
import zio.ZIO

import java.time.Instant
import java.util.UUID

import dsp.errors.SparqlGenerationException
import dsp.valueobjects.UuidUtil
import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.resources.repo.model.SparqlTemplateLinkUpdate
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Deletes an existing link between two resources and replaces it with a link to a different target resource.
 *
 * This query:
 * 1. Deletes the current direct link and detaches the current LinkValue
 * 2. Creates a new version of the current LinkValue marked as deleted with reference count 0
 * 3. Inserts a new direct link to the new target resource
 * 4. Creates a new LinkValue for the new link
 * 5. Updates the link source's last modification date
 */
object ChangeLinkTargetQuery {

  private def failIf(condition: Boolean, message: String): IO[SparqlGenerationException, Unit] =
    ZIO.fail(SparqlGenerationException(message)).when(condition).unit

  /**
   * Builds a SPARQL UPDATE query to change a link's target resource.
   *
   * @param project                  the project the resource belongs to (used to determine the named graph)
   * @param linkSourceIri           the resource that is the source of the links
   * @param linkUpdateForCurrentLink a [[SparqlTemplateLinkUpdate]] specifying how to update the current link
   * @param linkUpdateForNewLink    a [[SparqlTemplateLinkUpdate]] specifying how to update the new link
   * @param maybeComment            an optional comment on the new link value
   * @param currentTime             an xsd:dateTimeStamp that will be attached to the resources
   * @param requestingUser          the IRI of the user making the request
   */
  def build(
    project: Project,
    linkSourceIri: ResourceIri,
    linkUpdateForCurrentLink: SparqlTemplateLinkUpdate,
    linkUpdateForNewLink: SparqlTemplateLinkUpdate,
    maybeComment: Option[String],
    currentTime: Instant,
    requestingUser: UserIri,
  ): IO[SparqlGenerationException, (UUID, Update)] =
    for {
      newLinkValueUUID <- Random.nextUUID
      _                <- failIf(
             !linkUpdateForCurrentLink.deleteDirectLink,
             "linkUpdateForCurrentLink.deleteDirectLink must be true in this SPARQL template",
           )
      _ <- failIf(
             !linkUpdateForCurrentLink.linkValueExists,
             "linkUpdateForCurrentLink.linkValueExists must be true in this SPARQL template",
           )
      _ <- failIf(
             linkUpdateForCurrentLink.newReferenceCount != 0,
             "linkUpdateForCurrentLink.newReferenceCount must be 0 in this SPARQL template",
           )
      _ <- failIf(
             !linkUpdateForCurrentLink.directLinkExists,
             "linkUpdateForCurrentLink.directLinkExists must be true in this SPARQL template",
           )
      _ <- failIf(
             linkUpdateForCurrentLink.linkPropertyIri != linkUpdateForNewLink.linkPropertyIri,
             s"linkUpdateForCurrentLink.linkPropertyIri <${linkUpdateForCurrentLink.linkPropertyIri}> must be equal to linkUpdateForNewLink.linkPropertyIri <${linkUpdateForNewLink.linkPropertyIri}>",
           )
      _ <- failIf(
             linkUpdateForNewLink.directLinkExists,
             "linkUpdateForNewLink.directLinkExists must be false in this SPARQL template",
           )
      _ <- failIf(
             linkUpdateForNewLink.linkValueExists,
             "linkUpdateForNewLink.linkValueExists must be false in this SPARQL template",
           )
      _ <- failIf(
             !linkUpdateForNewLink.insertDirectLink,
             "linkUpdateForNewLink.insertDirectLink must be true in this SPARQL template",
           )
    } yield {
      val dataGraph         = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
      val linkSource        = Iri.unsafeFrom(linkSourceIri.value)
      val linkProperty      = Iri.unsafeFrom(linkUpdateForCurrentLink.linkPropertyIri.toInternalSchema.toIri)
      val linkValueProperty =
        Iri.unsafeFrom(linkUpdateForCurrentLink.linkPropertyIri.toInternalSchema.toIri + "Value")
      val linkTargetForCurrentLink   = Iri.unsafeFrom(linkUpdateForCurrentLink.linkTargetIri)
      val linkTargetForNewLink       = Iri.unsafeFrom(linkUpdateForNewLink.linkTargetIri)
      val newLinkValueForCurrentLink = Iri.unsafeFrom(linkUpdateForCurrentLink.newLinkValueIri.value)
      val newLinkValueForNewLink     = Iri.unsafeFrom(linkUpdateForNewLink.newLinkValueIri.value)
      val deletedByUser              = Iri.unsafeFrom(requestingUser.value)
      val creatorOfCurrentLinkValue  = Iri.unsafeFrom(linkUpdateForCurrentLink.newLinkValueCreator)
      val creatorOfNewLinkValue      = Iri.unsafeFrom(linkUpdateForNewLink.newLinkValueCreator)
      val currentTimeLiteral         = Literal.dateTime(currentTime)

      val currentLinkTargetString = Literal.string(linkUpdateForCurrentLink.linkTargetIri)
      val currentLinkRefCount     = Literal.int(linkUpdateForCurrentLink.newReferenceCount)
      val currentLinkPermissions  = Literal.string(linkUpdateForCurrentLink.newLinkValuePermissions)
      val newLinkTargetString     = Literal.string(linkUpdateForNewLink.linkTargetIri)
      val newLinkRefCount         = Literal.int(linkUpdateForNewLink.newReferenceCount)
      val newLinkPermissions      = Literal.string(linkUpdateForNewLink.newLinkValuePermissions)
      val newLinkValueUUIDLiteral = Literal.string(UuidUtil.base64Encode(newLinkValueUUID))
      val currentRefCount         = Literal.int(linkUpdateForCurrentLink.currentReferenceCount)

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
                 |    # Delete the current direct link
                 |    $linkSource $linkProperty $linkTargetForCurrentLink .
                 |    # Detach the current LinkValue from the link source
                 |    $linkSource $linkValueProperty ?currentLinkValueForCurrentLink .
                 |    # Delete the UUID and the permissions from the current version of the link value
                 |    ?currentLinkValueForCurrentLink knora-base:valueHasUUID ?currentLinkUUID .
                 |    ?currentLinkValueForCurrentLink knora-base:hasPermissions ?currentLinkPermissions .
                 |  }
                 |}
                 |INSERT {
                 |  GRAPH $dataGraph {
                 |    # Insert a new version of the current LinkValue, marked as deleted
                 |    $newLinkValueForCurrentLink a knora-base:LinkValue .
                 |    $newLinkValueForCurrentLink rdf:subject $linkSource .
                 |    $newLinkValueForCurrentLink rdf:predicate $linkProperty .
                 |    $newLinkValueForCurrentLink rdf:object $linkTargetForCurrentLink .
                 |    $newLinkValueForCurrentLink knora-base:valueHasString $currentLinkTargetString .
                 |    $newLinkValueForCurrentLink knora-base:valueHasRefCount $currentLinkRefCount .
                 |    $newLinkValueForCurrentLink knora-base:valueCreationDate $currentTimeLiteral .
                 |    $newLinkValueForCurrentLink knora-base:previousValue ?currentLinkValueForCurrentLink .
                 |    $newLinkValueForCurrentLink knora-base:valueHasUUID ?currentLinkUUID .
                 |    $newLinkValueForCurrentLink knora-base:deleteDate $currentTimeLiteral .
                 |    $newLinkValueForCurrentLink knora-base:deletedBy $deletedByUser .
                 |    $newLinkValueForCurrentLink knora-base:isDeleted true .
                 |    $newLinkValueForCurrentLink knora-base:attachedToUser $creatorOfCurrentLinkValue .
                 |    $newLinkValueForCurrentLink knora-base:hasPermissions $currentLinkPermissions .
                 |    $linkSource $linkValueProperty $newLinkValueForCurrentLink .
                 |    # Insert the new direct link
                 |    $linkSource $linkProperty $linkTargetForNewLink .
                 |    # Insert a LinkValue describing the new link
                 |    $newLinkValueForNewLink a knora-base:LinkValue .
                 |    $newLinkValueForNewLink rdf:subject $linkSource .
                 |    $newLinkValueForNewLink rdf:predicate $linkProperty .
                 |    $newLinkValueForNewLink rdf:object $linkTargetForNewLink .
                 |    $newLinkValueForNewLink knora-base:valueHasString $newLinkTargetString .
                 |    ${maybeComment.whenSome(comment =>
            sparql"$newLinkValueForNewLink knora-base:valueHasComment ${Literal.string(comment)} .",
          )}
                 |    $newLinkValueForNewLink knora-base:valueHasRefCount $newLinkRefCount .
                 |    $newLinkValueForNewLink knora-base:valueHasOrder ?order .
                 |    $newLinkValueForNewLink knora-base:isDeleted false .
                 |    $newLinkValueForNewLink knora-base:valueHasUUID $newLinkValueUUIDLiteral .
                 |    $newLinkValueForNewLink knora-base:valueCreationDate $currentTimeLiteral .
                 |    $newLinkValueForNewLink knora-base:attachedToUser $creatorOfNewLinkValue .
                 |    $newLinkValueForNewLink knora-base:hasPermissions $newLinkPermissions .
                 |    # Attach the new LinkValue to its containing resource
                 |    $linkSource $linkValueProperty $newLinkValueForNewLink .
                 |    # Update the link source's last modification date
                 |    $linkSource knora-base:lastModificationDate $currentTimeLiteral .
                 |  }
                 |}
                 |WHERE {
                 |  # Check that the link source is a knora-base:Resource and is not deleted
                 |  $linkSource a ?linkSourceClass .
                 |  ?linkSourceClass rdfs:subClassOf* knora-base:Resource .
                 |  $linkSource knora-base:isDeleted false .
                 |  # Make sure the current direct link exists
                 |  $linkSource $linkProperty $linkTargetForCurrentLink .
                 |  # Make sure a LinkValue exists for the current link with the correct reference count
                 |  $linkSource $linkValueProperty ?currentLinkValueForCurrentLink .
                 |  ?currentLinkValueForCurrentLink a knora-base:LinkValue ;
                 |    rdf:subject $linkSource ;
                 |    rdf:predicate $linkProperty ;
                 |    rdf:object $linkTargetForCurrentLink ;
                 |    knora-base:valueHasRefCount $currentRefCount ;
                 |    knora-base:isDeleted false ;
                 |    knora-base:valueHasUUID ?currentLinkUUID ;
                 |    knora-base:hasPermissions ?currentLinkPermissions .
                 |  # Get the order from the current link value, if it has one
                 |  OPTIONAL { ?currentLinkValueForCurrentLink knora-base:valueHasOrder ?order . }
                 |  # Do nothing if a direct link already exists to the new target
                 |  FILTER NOT EXISTS { $linkSource $linkProperty $linkTargetForNewLink . }
                 |  # Do nothing if an active LinkValue already exists for the new target
                 |  FILTER NOT EXISTS {
                 |    $linkSource $linkValueProperty ?currentLinkValueForNewLink .
                 |    ?currentLinkValueForNewLink a knora-base:LinkValue ;
                 |      rdf:subject $linkSource ;
                 |      rdf:predicate $linkProperty ;
                 |      rdf:object $linkTargetForNewLink ;
                 |      knora-base:isDeleted false .
                 |  }
                 |  # Validate the new target: it exists, is not deleted, is a knora-base:Resource,
                 |  # and satisfies the link property's object class constraint
                 |  $linkTargetForNewLink a ?linkTargetClass ;
                 |    knora-base:isDeleted false .
                 |  ?linkTargetClass rdfs:subClassOf* knora-base:Resource .
                 |  $linkProperty knora-base:objectClassConstraint ?expectedTargetClass .
                 |  ?linkTargetClass rdfs:subClassOf* ?expectedTargetClass .
                 |  # Get the link source's last modification date, if it has one, so we can update it
                 |  OPTIONAL { $linkSource knora-base:lastModificationDate ?linkSourceLastModificationDate . }
                 |}""".render,
      )

      (newLinkValueUUID, query)
    }
}
