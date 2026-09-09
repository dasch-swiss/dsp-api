/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.IO
import zio.ZIO

import java.time.Instant
import java.util.UUID

import dsp.errors.SparqlGenerationException
import dsp.valueobjects.UuidUtil
import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.resources.repo.model.SparqlTemplateLinkUpdate
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Inserts a new link between resources.
 *
 * This query:
 * 1. Deletes the link source's last modification date so it can be updated
 * 2. Inserts a direct link between source and target resources
 * 3. Creates a new LinkValue describing the link
 * 4. Updates the link source's last modification date
 */
object CreateLinkQuery {

  private def failIf(condition: Boolean, message: String): IO[SparqlGenerationException, Unit] =
    ZIO.fail(SparqlGenerationException(message)).when(condition).unit

  /**
   * Builds a SPARQL UPDATE query to create a link between two resources.
   *
   * @param project        the project that owns the data graph
   * @param resourceIri    the resource that is the source of the link
   * @param linkUpdate     a [[SparqlTemplateLinkUpdate]] specifying the link to create
   * @param newValueUUID   the UUID to be attached to the value
   * @param creationDate   an xsd:dateTimeStamp that will be attached to the link value
   * @param maybeComment   an optional comment on the link
   * @param valueHasOrder  an explicit order for the new LinkValue; when absent, the next
   *                       order is derived from the highest existing order for the property
   */
  def build(
    project: Project,
    resourceIri: ResourceIri,
    linkUpdate: SparqlTemplateLinkUpdate,
    newValueUUID: UUID,
    creationDate: Instant,
    maybeComment: Option[String],
    valueHasOrder: Option[Int] = None,
  ): IO[SparqlGenerationException, Update] =
    for {
      _ <- failIf(!linkUpdate.insertDirectLink, "linkUpdate.insertDirectLink must be true in this SPARQL template")
      _ <- failIf(linkUpdate.directLinkExists, "linkUpdate.directLinkExists must be false in this SPARQL template")
      _ <- failIf(linkUpdate.linkValueExists, "linkUpdate.linkValueExists must be false in this SPARQL template")
    } yield {
      val dataGraph         = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
      val resource          = Iri.unsafeFrom(resourceIri.value)
      val linkProperty      = Iri.unsafeFrom(linkUpdate.linkPropertyIri.toInternalSchema.toIri)
      val linkValueProperty =
        Iri.unsafeFrom(linkUpdate.linkPropertyIri.toInternalSchema.fromLinkPropToLinkValueProp.toIri)
      val linkTarget       = Iri.unsafeFrom(linkUpdate.linkTargetIri)
      val newLinkValue     = Iri.unsafeFrom(linkUpdate.newLinkValueIri.value)
      val newValueCreator  = Iri.unsafeFrom(linkUpdate.newLinkValueCreator)
      val creationDateTime = Literal.dateTime(creationDate)

      val commentInsert =
        maybeComment.whenSome(comment => sparql"$newLinkValue knora-base:valueHasComment ${Literal.string(comment)} .")

      // If the link target already exists, validate it.
      val linkTargetValidation =
        sparql"""|# Make sure the link target is a knora-base:Resource
                 |$linkTarget a ?linkTargetClass .
                 |?linkTargetClass rdfs:subClassOf* knora-base:Resource .
                 |# Do nothing if the target resource belongs to the wrong OWL class
                 |$linkProperty knora-base:objectClassConstraint ?expectedTargetClass .
                 |?linkTargetClass rdfs:subClassOf* ?expectedTargetClass .
                 |# Do nothing if the target resource doesn't exist or is marked as deleted
                 |$linkTarget knora-base:isDeleted false .
                 |# Do nothing if the source resource's OWL class has no cardinality for the link property
                 |?resourceClass rdfs:subClassOf* ?restriction .
                 |?restriction a owl:Restriction .
                 |?restriction owl:onProperty $linkProperty .""".when(linkUpdate.linkTargetExists)

      // Determine the next order: use the explicit value when supplied, otherwise MAX(existing) + 1.
      val orderPattern =
        valueHasOrder.fold(
          sparql"""|{
                   |  SELECT (MAX(?order) AS ?maxOrder) (IF(BOUND(?maxOrder), ?maxOrder + 1, 0) AS ?nextOrder)
                   |  WHERE {
                   |    $resource $linkValueProperty ?otherLinkValue .
                   |    ?otherLinkValue knora-base:valueHasOrder ?order ;
                   |      knora-base:isDeleted false .
                   |  }
                   |}""",
        )(order => sparql"BIND(${Literal.int(order)} AS ?nextOrder)")

      Update(
        sparql"""|PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                 |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |
                 |DELETE {
                 |  GRAPH $dataGraph {
                 |    # Delete the link source's last modification date so we can update it
                 |    $resource knora-base:lastModificationDate ?resourceLastModificationDate .
                 |  }
                 |}
                 |INSERT {
                 |  GRAPH $dataGraph {
                 |    # Update the link source's last modification date
                 |    $resource knora-base:lastModificationDate $creationDateTime .
                 |    # Insert a direct link between the source and target resources
                 |    $resource $linkProperty $linkTarget .
                 |    # Insert a LinkValue describing the link
                 |    $newLinkValue a knora-base:LinkValue .
                 |    $newLinkValue rdf:subject $resource .
                 |    $newLinkValue rdf:predicate $linkProperty .
                 |    $newLinkValue rdf:object $linkTarget .
                 |    $newLinkValue knora-base:valueHasString ${Literal.string(linkUpdate.linkTargetIri)} .
                 |    $newLinkValue knora-base:valueHasRefCount ${Literal.int(linkUpdate.newReferenceCount)} .
                 |    $newLinkValue knora-base:valueHasOrder ?nextOrder .
                 |    $newLinkValue knora-base:isDeleted false .
                 |    $newLinkValue knora-base:valueHasUUID ${Literal.string(UuidUtil.base64Encode(newValueUUID))} .
                 |    $newLinkValue knora-base:valueCreationDate $creationDateTime .
                 |    $newLinkValue knora-base:attachedToUser $newValueCreator .
                 |    $newLinkValue knora-base:hasPermissions ${Literal.string(linkUpdate.newLinkValuePermissions)} .
                 |    # Attach the new LinkValue to its containing resource
                 |    $resource $linkValueProperty $newLinkValue .
                 |    $commentInsert
                 |  }
                 |}
                 |WHERE {
                 |  # Check that the resource exists, is not deleted, and is a knora-base:Resource
                 |  $resource a ?resourceClass ;
                 |    knora-base:isDeleted false .
                 |  ?resourceClass rdfs:subClassOf* knora-base:Resource .
                 |  # Get the link source's last modification date, if it has one, so we can update it
                 |  OPTIONAL { $resource knora-base:lastModificationDate ?resourceLastModificationDate . }
                 |  $linkTargetValidation
                 |  $orderPattern
                 |}""".render,
      )
    }
}
