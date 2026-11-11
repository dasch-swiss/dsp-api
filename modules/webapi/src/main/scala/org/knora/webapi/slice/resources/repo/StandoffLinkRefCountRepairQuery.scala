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
import org.knora.webapi.IRI
import org.knora.webapi.slice.resources.repo.model.SparqlTemplateLinkUpdate
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Applies one repair of the standoff link reference count invariant, scoped to one project data graph. The link
 * value and direct link triples mirror those of [[CreateLinkQuery]] and [[ChangeLinkMetadataQuery]], with these
 * differences: the source's `lastModificationDate` is left unchanged, because the repair is maintenance and not a
 * content change, and one current link value per (source, target) pair is assumed, as the check query assumes.
 *
 * Every update is guarded by the state the repair was planned against: the source is a live resource and the stored
 * state is the planned one. If that state changed in the meantime, the update matches nothing and writes nothing;
 * [[isApplied]] tells the two outcomes apart.
 *
 * The caller must hold the per-resource lock of the source (`IriLocker`). Under the lock the stored-count guard
 * suffices: a write that changes the expected count of a pair also changes its stored count.
 */
object StandoffLinkRefCountRepairQuery {

  private def failIf(condition: Boolean, message: String): IO[SparqlGenerationException, Unit] =
    ZIO.fail(SparqlGenerationException(message)).when(condition).unit

  /**
   * Builds the update of one repair.
   *
   * - The link value exists: a new version of it carries the new count (deleted, with deletion date and deleter, at
   *   0), keeps the UUID and points to the current version through `previousValue`. It replaces the current version
   *   on the source. The current version must be attached to the source, not deleted and carry the planned count.
   * - No link value exists and the new count is above 0: a new link value is created. The source must have no
   *   current link value for the target.
   * - No link value exists and the new count is 0: only the direct link is deleted, under the same guard.
   *
   * In every case the source must exist, not be deleted and be a `knora-base:Resource`.
   *
   * @param dataNamedGraph the named graph in which the project stores its data
   * @param linkSourceIri  the resource that is the source of the link
   * @param linkUpdate     the planned change
   * @param creationDate   the creation date of the new link value, and the deletion date when the new count is 0
   * @param newValueUUID   the UUID of a newly created link value; unused when the link value exists
   */
  def build(
    dataNamedGraph: IRI,
    linkSourceIri: IRI,
    linkUpdate: SparqlTemplateLinkUpdate,
    creationDate: Instant,
    newValueUUID: UUID,
  ): IO[SparqlGenerationException, Update] =
    for {
      _ <- failIf(
             !linkUpdate.linkValueExists && linkUpdate.newReferenceCount == 0 && !linkUpdate.deleteDirectLink,
             "linkUpdate has nothing to write: no link value, count 0 and no direct link to delete",
           )
    } yield {
      val dataGraph         = Iri.unsafeFrom(dataNamedGraph)
      val source            = Iri.unsafeFrom(linkSourceIri)
      val linkProperty      = Iri.unsafeFrom(linkUpdate.linkPropertyIri.toInternalSchema.toIri)
      val linkValueProperty = Iri.unsafeFrom(linkUpdate.linkPropertyIri.toInternalSchema.toIri + "Value")
      val target            = Iri.unsafeFrom(linkUpdate.linkTargetIri)
      val newLinkValue      = Iri.unsafeFrom(linkUpdate.newLinkValueIri.value)
      val creator           = Iri.unsafeFrom(linkUpdate.newLinkValueCreator)
      val created           = Literal.dateTime(creationDate)
      val newCount          = Literal.int(linkUpdate.newReferenceCount)
      val isDeleted         = linkUpdate.newReferenceCount == 0

      val deletion: Fragment =
        sparql"""|knora-base:deleteDate $created ;
                 |knora-base:deletedBy $creator ;""".when(isDeleted)

      def linkValueTriples(history: Fragment, uuid: Fragment): Fragment =
        sparql"""|$newLinkValue a knora-base:LinkValue ;
                 |  rdf:subject $source ;
                 |  rdf:predicate $linkProperty ;
                 |  rdf:object $target ;
                 |  knora-base:valueHasString ${Literal.string(linkUpdate.linkTargetIri)} ;
                 |  knora-base:valueHasRefCount $newCount ;
                 |  knora-base:valueCreationDate $created ;
                 |  $history
                 |  $uuid
                 |  knora-base:isDeleted ${Literal.bool(isDeleted)} ;
                 |  knora-base:attachedToUser $creator ;
                 |  knora-base:hasPermissions ${Literal.string(linkUpdate.newLinkValuePermissions)} .
                 |$source $linkValueProperty $newLinkValue ."""

      val replacedLinkValue: Fragment =
        linkValueTriples(
          history = sparql"""|$deletion
                             |knora-base:previousValue ?currentLinkValue ;""",
          uuid = sparql"knora-base:valueHasUUID ?currentLinkUUID ;",
        )

      val createdLinkValue: Fragment =
        linkValueTriples(
          history = Fragment.empty,
          uuid = sparql"knora-base:valueHasUUID ${Literal.string(UuidUtil.base64Encode(newValueUUID))} ;",
        )

      val directLinkToInsert: Fragment = sparql"$source $linkProperty $target .".when(linkUpdate.insertDirectLink)
      val directLinkToDelete: Fragment = sparql"$source $linkProperty $target .".when(linkUpdate.deleteDirectLink)

      val sourceInDataGraph: Fragment =
        sparql"""|$source a ?sourceClass ;
                 |  knora-base:isDeleted false ."""

      val sourceIsResource: Fragment = sparql"?sourceClass rdfs:subClassOf* knora-base:Resource ."

      val sourceGuard: Fragment =
        sparql"""|GRAPH $dataGraph {
                 |  $sourceInDataGraph
                 |}
                 |$sourceIsResource"""

      val noCurrentLinkValue: Fragment =
        sparql"""|FILTER NOT EXISTS {
                 |  GRAPH $dataGraph {
                 |    $source $linkValueProperty ?currentLinkValue .
                 |    ?currentLinkValue knora-base:isDeleted false ;
                 |      rdf:object $target .
                 |  }
                 |}"""

      val body: Fragment =
        if (linkUpdate.linkValueExists)
          sparql"""|DELETE {
                   |  GRAPH $dataGraph {
                   |    $source $linkValueProperty ?currentLinkValue .
                   |    ?currentLinkValue knora-base:valueHasUUID ?currentLinkUUID .
                   |    $directLinkToDelete
                   |  }
                   |}
                   |INSERT {
                   |  GRAPH $dataGraph {
                   |    $replacedLinkValue
                   |    $directLinkToInsert
                   |  }
                   |}
                   |WHERE {
                   |  GRAPH $dataGraph {
                   |    $sourceInDataGraph
                   |    $source $linkValueProperty ?currentLinkValue .
                   |    ?currentLinkValue a knora-base:LinkValue ;
                   |      rdf:object $target ;
                   |      knora-base:valueHasRefCount ${Literal.int(linkUpdate.currentReferenceCount)} ;
                   |      knora-base:isDeleted false ;
                   |      knora-base:valueHasUUID ?currentLinkUUID .
                   |  }
                   |  $sourceIsResource
                   |}"""
        else if (linkUpdate.newReferenceCount > 0)
          sparql"""|INSERT {
                   |  GRAPH $dataGraph {
                   |    $createdLinkValue
                   |    $directLinkToInsert
                   |  }
                   |}
                   |WHERE {
                   |  $sourceGuard
                   |  $noCurrentLinkValue
                   |}"""
        else
          sparql"""|DELETE {
                   |  GRAPH $dataGraph {
                   |    $directLinkToDelete
                   |  }
                   |}
                   |WHERE {
                   |  $sourceGuard
                   |  $noCurrentLinkValue
                   |}"""

      Update(
        sparql"""|PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |
                 |$body""".render,
        SparqlTimeout.Maintenance,
      )
    }

  /**
   * Asks whether the update built from the same arguments took effect: a repair that writes a link value took effect
   * once the new link value exists, a repair that only deletes the direct link once the direct link is gone. The
   * latter is also true when the direct link was already gone, which is the desired state.
   */
  def isApplied(dataNamedGraph: IRI, linkSourceIri: IRI, linkUpdate: SparqlTemplateLinkUpdate): Ask = {
    val dataGraph    = Iri.unsafeFrom(dataNamedGraph)
    val newLinkValue = Iri.unsafeFrom(linkUpdate.newLinkValueIri.value)
    val source       = Iri.unsafeFrom(linkSourceIri)
    val linkProperty = Iri.unsafeFrom(linkUpdate.linkPropertyIri.toInternalSchema.toIri)
    val target       = Iri.unsafeFrom(linkUpdate.linkTargetIri)
    if (linkUpdate.linkValueExists || linkUpdate.newReferenceCount > 0)
      Ask(sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                   |ASK { GRAPH $dataGraph { $newLinkValue a knora-base:LinkValue . } }""".render)
    else
      Ask(sparql"ASK { FILTER NOT EXISTS { GRAPH $dataGraph { $source $linkProperty $target . } } }".render)
  }
}
