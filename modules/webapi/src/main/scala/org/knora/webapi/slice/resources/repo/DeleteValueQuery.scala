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
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.resources.repo.model.SparqlTemplateLinkUpdate
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Marks a value as deleted. This query is used for all value types except links.
 *
 * If the value is a TextValue containing standoff markup with resource references,
 * the corresponding LinkValues are updated (decremented) as part of the same query.
 */
object DeleteValueQuery {

  private def failIf(condition: Boolean, message: String): IO[SparqlGenerationException, Unit] =
    ZIO.fail(SparqlGenerationException(message)).when(condition).unit

  /**
   * Builds a SPARQL UPDATE query to mark a value as deleted.
   *
   * @param project            the project the resource belongs to (used to determine the named graph).
   * @param resourceIri       the IRI of the resource containing the value.
   * @param propertyIri       the IRI of the property that points from the resource to the value.
   * @param valueIri          the IRI of the value to be marked as deleted.
   * @param maybeDeleteComment an optional comment explaining why the value is being deleted.
   * @param linkUpdates       a list of [[SparqlTemplateLinkUpdate]] objects describing LinkValues that need to be
   *                          updated for resource references in standoff markup.
   * @param currentTime       an xsd:dateTimeStamp that will be attached to the resources.
   * @param requestingUser    the IRI of the user making the request.
   */
  def build(
    project: Project,
    resourceIri: ResourceIri,
    propertyIri: PropertyIri,
    valueIri: ValueIri,
    maybeDeleteComment: Option[String],
    linkUpdates: Seq[SparqlTemplateLinkUpdate],
    currentTime: Instant,
    requestingUser: UserIri,
  ): IO[SparqlGenerationException, Update] =
    ZIO
      .foreachDiscard(linkUpdates) { lu =>
        for {
          _ <- failIf(lu.insertDirectLink, "linkUpdate.insertDirectLink must be false in this SPARQL template")
          _ <- failIf(!lu.directLinkExists, "linkUpdate.directLinkExists must be true in this SPARQL template")
          _ <- failIf(!lu.linkValueExists, "linkUpdate.linkValueExists must be true in this SPARQL template")
        } yield ()
      }
      .as {
        val dataGraph   = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
        val resource    = Iri.unsafeFrom(resourceIri.value)
        val property    = Iri.unsafeFrom(propertyIri.toInternalSchema.toIri)
        val value       = Iri.unsafeFrom(valueIri.value)
        val user        = Iri.unsafeFrom(requestingUser.value)
        val currentTs   = Literal.dateTime(currentTime)
        val indexed     = linkUpdates.zipWithIndex
        val linkValueOf = (i: Int) => Variable(s"linkValue$i")
        val linkUuidOf  = (i: Int) => Variable(s"linkValueUUID$i")

        Update(
          sparql"""|PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                   |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                   |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                   |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                   |
                   |DELETE {
                   |  GRAPH $dataGraph {
                   |    # Delete the resource's last modification date so we can update it
                   |    $resource knora-base:lastModificationDate ?resourceLastModificationDate .
                   |    $value knora-base:isDeleted false .
                   |    ${
            // Delete the direct link when the standoff reference is gone, and always detach the
            // current LinkValue from the resource plus its UUID (the new version carries it over).
            indexed.map { case (lu, i) =>
              val linkProperty      = Iri.unsafeFrom(lu.linkPropertyIri.toInternalSchema.toIri)
              val linkValueProperty = Iri.unsafeFrom(lu.linkPropertyIri.toInternalSchema.toIri + "Value")
              val linkTarget        = Iri.unsafeFrom(lu.linkTargetIri)
              sparql"""|${sparql"$resource $linkProperty $linkTarget .".when(lu.deleteDirectLink)}
                     |$resource $linkValueProperty ${linkValueOf(i)} .
                     |${linkValueOf(i)} knora-base:valueHasUUID ${linkUuidOf(i)} ."""
            }.joinLines}
                   |  }
                   |}
                   |INSERT {
                   |  GRAPH $dataGraph {
                   |    $value knora-base:isDeleted true ;
                   |      knora-base:deletedBy $user ;
                   |      knora-base:deleteDate $currentTs .
                   |    ${maybeDeleteComment.whenSome(c =>
              sparql"$value knora-base:deleteComment ${Literal.string(c)} .",
            )}
                   |    ${
            // Add a new LinkValue version for each resource reference in standoff markup.
            indexed.map { case (lu, i) =>
              val linkProperty      = Iri.unsafeFrom(lu.linkPropertyIri.toInternalSchema.toIri)
              val linkValueProperty = Iri.unsafeFrom(lu.linkPropertyIri.toInternalSchema.toIri + "Value")
              val linkTarget        = Iri.unsafeFrom(lu.linkTargetIri)
              val newLinkValue      = Iri.unsafeFrom(lu.newLinkValueIri.value)
              val newValueCreator   = Iri.unsafeFrom(lu.newLinkValueCreator)
              sparql"""|$newLinkValue a knora-base:LinkValue .
                     |$newLinkValue rdf:subject $resource .
                     |$newLinkValue rdf:predicate $linkProperty .
                     |$newLinkValue rdf:object $linkTarget .
                     |$newLinkValue knora-base:valueHasString ${Literal.string(lu.linkTargetIri)} .
                     |$newLinkValue knora-base:valueHasRefCount ${Literal.int(lu.newReferenceCount)} .
                     |${
                  if (lu.newReferenceCount == 0)
                    sparql"""|$newLinkValue knora-base:isDeleted true .
                                 |$newLinkValue knora-base:deletedBy $newValueCreator .
                                 |$newLinkValue knora-base:deleteDate $currentTs ."""
                  else sparql"$newLinkValue knora-base:isDeleted false ."
                }
                     |$newLinkValue knora-base:valueCreationDate $currentTs .
                     |$newLinkValue knora-base:attachedToUser $newValueCreator .
                     |$newLinkValue knora-base:hasPermissions ${Literal.string(lu.newLinkValuePermissions)} .
                     |$newLinkValue knora-base:previousValue ${linkValueOf(i)} .
                     |$newLinkValue knora-base:valueHasUUID ${linkUuidOf(i)} .
                     |# Attach the new LinkValue to its containing resource
                     |$resource $linkValueProperty $newLinkValue ."""
            }.joinLines}
                   |    # Update the resource's last modification date
                   |    $resource knora-base:lastModificationDate $currentTs .
                   |  }
                   |}
                   |WHERE {
                   |  $resource $property $value .
                   |  $value a ?valueClass ;
                   |    knora-base:isDeleted false .
                   |  ?valueClass rdfs:subClassOf* knora-base:Value .
                   |  ${
            // Check the state of any LinkValues to be updated for resource references.
            indexed.map { case (lu, i) =>
              val linkProperty      = Iri.unsafeFrom(lu.linkPropertyIri.toInternalSchema.toIri)
              val linkValueProperty = Iri.unsafeFrom(lu.linkPropertyIri.toInternalSchema.toIri + "Value")
              val linkTarget        = Iri.unsafeFrom(lu.linkTargetIri)
              sparql"""|# Make sure the relevant direct link exists between the two resources
                     |$resource $linkProperty $linkTarget .
                     |# Make sure a LinkValue exists describing the direct link with the correct reference count
                     |$resource $linkValueProperty ${linkValueOf(i)} .
                     |${linkValueOf(i)} a knora-base:LinkValue ;
                     |  rdf:subject $resource ;
                     |  rdf:predicate $linkProperty ;
                     |  rdf:object $linkTarget ;
                     |  knora-base:valueHasRefCount ${Literal.int(lu.currentReferenceCount)} ;
                     |  knora-base:isDeleted false ;
                     |  knora-base:valueHasUUID ${linkUuidOf(i)} ."""
            }.joinLines}
                   |  # Get the resource's last modification date, if it has one, so we can update it
                   |  OPTIONAL { $resource knora-base:lastModificationDate ?resourceLastModificationDate . }
                   |}""".render,
        )
      }
}
