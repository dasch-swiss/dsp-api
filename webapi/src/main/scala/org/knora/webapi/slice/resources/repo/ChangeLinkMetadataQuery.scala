/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.Clock
import zio.IO

import java.time.Instant

import dsp.errors.SparqlGenerationException
import org.knora.webapi.messages.twirl.SparqlTemplateLinkUpdate
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Changes the metadata on a LinkValue (e.g. permissions, comment) without changing the link target.
 */
object ChangeLinkMetadataQuery extends QueryBuilderHelper {

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
      val dataGraph                      = graphIri(project)
      val linkSource                     = toRdfIri(linkSourceIri)
      val linkProperty                   = toRdfIri(linkUpdate.linkPropertyIri)
      val linkValueProperty              = Rdf.iri(linkUpdate.linkPropertyIri.toInternalSchema.toIri + "Value")
      val linkTarget                     = Rdf.iri(linkUpdate.linkTargetIri)
      val newLinkValue                   = Rdf.iri(linkUpdate.newLinkValueIri)
      val linkSourceClass                = variable("linkSourceClass")
      val currentLinkValue               = variable("currentLinkValue")
      val currentLinkUUID                = variable("currentLinkUUID")
      val linkSourceLastModificationDate = variable("linkSourceLastModificationDate")
      val currentTimeLiteral             = Rdf.literalOfType(now.toString, XSD.DATETIME)

      // DELETE patterns
      val deletePatterns = Seq(
        linkSource.has(KB.lastModificationDate, linkSourceLastModificationDate),
        linkSource.has(linkValueProperty, currentLinkValue),
        currentLinkValue.has(KB.valueHasUUID, currentLinkUUID),
      )

      // INSERT patterns — new version of the LinkValue
      val insertNewLinkValueBase = Seq(
        newLinkValue.isA(KB.linkValue),
        newLinkValue.has(RDF.SUBJECT, linkSource),
        newLinkValue.has(RDF.PREDICATE, linkProperty),
        newLinkValue.has(RDF.OBJECT, linkTarget),
        newLinkValue.has(KB.valueHasString, Rdf.literalOfType(linkUpdate.linkTargetIri, XSD.STRING)),
        newLinkValue.has(KB.valueHasRefCount, Rdf.literalOf(linkUpdate.newReferenceCount)),
        newLinkValue.has(KB.valueCreationDate, currentTimeLiteral),
        newLinkValue.has(KB.previousValue, currentLinkValue),
        newLinkValue.has(KB.valueHasUUID, currentLinkUUID),
        newLinkValue.has(KB.isDeleted, Rdf.literalOf(false)),
      )

      val insertComment =
        maybeComment.map(c => newLinkValue.has(KB.valueHasComment, Rdf.literalOf(c))).toSeq

      val insertNewLinkValueRest = Seq(
        newLinkValue.has(KB.attachedToUser, Rdf.iri(linkUpdate.newLinkValueCreator)),
        newLinkValue.has(KB.hasPermissions, Rdf.literalOfType(linkUpdate.newLinkValuePermissions, XSD.STRING)),
      )

      // Attach new LinkValue to source + update last modification date
      val insertAttachAndLastMod = Seq(
        linkSource.has(linkValueProperty, newLinkValue),
        linkSource.has(KB.lastModificationDate, currentTimeLiteral),
      )

      val insertPatterns = insertNewLinkValueBase ++ insertComment ++ insertNewLinkValueRest ++ insertAttachAndLastMod

      // WHERE patterns
      val subClassOfPath = zeroOrMore(RDFS.SUBCLASSOF)

      val wherePatterns = Seq(
        // Check link source is a Resource and not deleted
        linkSource.isA(linkSourceClass).andHas(KB.isDeleted, Rdf.literalOf(false)),
        linkSourceClass.has(subClassOfPath, KB.Resource),
        // Make sure the direct link exists
        linkSource.has(linkProperty, linkTarget),
        // Make sure a LinkValue exists with the correct reference count
        linkSource.has(linkValueProperty, currentLinkValue),
        currentLinkValue
          .isA(KB.linkValue)
          .andHas(RDF.SUBJECT, linkSource)
          .andHas(RDF.PREDICATE, linkProperty)
          .andHas(RDF.OBJECT, linkTarget)
          .andHas(KB.valueHasRefCount, Rdf.literalOf(linkUpdate.currentReferenceCount))
          .andHas(KB.isDeleted, Rdf.literalOf(false))
          .andHas(KB.valueHasUUID, currentLinkUUID),
        // Get the link source's last modification date, if it has one
        linkSource.has(KB.lastModificationDate, linkSourceLastModificationDate).optional(),
      )

      val query = Update(
        Queries
          .MODIFY()
          .prefix(RDF.NS, RDFS.NS, XSD.NS, KB.NS)
          .from(dataGraph)
          .delete(deletePatterns*)
          .into(dataGraph)
          .insert(insertPatterns*)
          .where(wherePatterns*),
      )

      (now, query)
    }
}
