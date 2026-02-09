/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.IO
import zio.ZIO

import java.time.Instant

import dsp.errors.SparqlGenerationException
import org.knora.webapi.IRI
import org.knora.webapi.messages.twirl.SparqlTemplateLinkUpdate
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB

/**
 * Deletes an existing link between two resources.
 *
 * This query:
 * 1. Deletes the direct link between source and target resources
 * 2. Detaches the current LinkValue from the link source
 * 3. Creates a new version of the LinkValue marked as deleted with reference count 0
 * 4. Updates the link source's last modification date
 */
object DeleteLinkQuery extends QueryBuilderHelper {

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
  ): IO[SparqlGenerationException, ModifyQuery] = {
    // Validate preconditions
    inline def failIf(condition: Boolean, message: String): IO[SparqlGenerationException, Unit] =
      ZIO.fail(SparqlGenerationException(message)).when(condition).unit

    for {
      _ <- failIf(!linkUpdate.deleteDirectLink, "linkUpdate.deleteDirectLink must be true in this SPARQL template")
      _ <- failIf(!linkUpdate.linkValueExists, "linkUpdate.linkValueExists must be true in this SPARQL template")
      _ <- failIf(!linkUpdate.directLinkExists, "linkUpdate.directLinkExists must be true in this SPARQL template")
      _ <- failIf(linkUpdate.newReferenceCount != 0, "linkUpdate.newReferenceCount must be 0 in this SPARQL template")
    } yield {
      val dataGraph                      = Rdf.iri(dataNamedGraph)
      val linkSource                     = Rdf.iri(linkSourceIri)
      val linkProperty                   = toRdfIri(linkUpdate.linkPropertyIri)
      val linkValueProperty              = Rdf.iri(linkUpdate.linkPropertyIri.toInternalSchema.toIri + "Value")
      val linkTarget                     = Rdf.iri(linkUpdate.linkTargetIri)
      val newLinkValue                   = Rdf.iri(linkUpdate.newLinkValueIri)
      val linkSourceClass                = variable("linkSourceClass")
      val currentLinkValue               = variable("currentLinkValue")
      val currentLinkUUID                = variable("currentLinkUUID")
      val linkSourceLastModificationDate = variable("linkSourceLastModificationDate")

      // DELETE patterns
      val deletePatterns = Seq(
        // Delete the link source's last modification date so we can update it
        linkSource.has(KB.lastModificationDate, linkSourceLastModificationDate),
        // Delete the direct link
        linkSource.has(linkProperty, linkTarget),
        // Detach the LinkValue from the link source
        linkSource.has(linkValueProperty, currentLinkValue),
        // Delete the UUID from the current version of the link value
        currentLinkValue.has(KB.valueHasUUID, currentLinkUUID),
      )

      // INSERT patterns - build new LinkValue
      val baseLinkValueInserts = Seq(
        newLinkValue.isA(KB.linkValue),
        newLinkValue.has(RDF.SUBJECT, linkSource),
        newLinkValue.has(RDF.PREDICATE, linkProperty),
        newLinkValue.has(RDF.OBJECT, linkTarget),
        newLinkValue.has(KB.valueHasString, Rdf.literalOfType(linkUpdate.linkTargetIri, XSD.STRING)),
        newLinkValue.has(KB.valueHasRefCount, Rdf.literalOf(linkUpdate.newReferenceCount)),
        newLinkValue.has(KB.valueCreationDate, toRdfLiteral(deletedAt)),
        newLinkValue.has(KB.deleteDate, toRdfLiteral(deletedAt)),
        newLinkValue.has(KB.deletedBy, toRdfIri(userIri)),
        newLinkValue.has(KB.previousValue, currentLinkValue),
        newLinkValue.has(KB.valueHasUUID, currentLinkUUID),
        newLinkValue.has(KB.isDeleted, Rdf.literalOf(true)),
        newLinkValue.has(KB.attachedToUser, Rdf.iri(linkUpdate.newLinkValueCreator)),
        newLinkValue.has(KB.hasPermissions, Rdf.literalOfType(linkUpdate.newLinkValuePermissions, XSD.STRING)),
        // Attach the new LinkValue to its containing resource
        linkSource.has(linkValueProperty, newLinkValue),
        // Update the link source's last modification date
        linkSource.has(KB.lastModificationDate, toRdfLiteral(deletedAt)),
      )

      val commentInsert = maybeComment.map(comment => newLinkValue.has(KB.deleteComment, Rdf.literalOf(comment))).toSeq

      val insertPatterns = baseLinkValueInserts ++ commentInsert

      // WHERE patterns - verify preconditions (flat structure matching original Twirl template)
      val subClassOfPath = zeroOrMore(RDFS.SUBCLASSOF)

      val wherePatterns: Seq[GraphPattern] = Seq(
        // Check link source exists, is not deleted, and is a knora-base:Resource
        linkSource.isA(linkSourceClass).andHas(KB.isDeleted, Rdf.literalOf(false)),
        linkSourceClass.has(subClassOfPath, KB.Resource),
        // Make sure a direct link exists between the two resources
        linkSource.has(linkProperty, linkTarget),
        // Make sure a LinkValue exists describing the direct link with correct reference count
        linkSource.has(linkValueProperty, currentLinkValue),
        currentLinkValue
          .isA(KB.linkValue)
          .andHas(RDF.SUBJECT, linkSource)
          .andHas(RDF.PREDICATE, linkProperty)
          .andHas(RDF.OBJECT, linkTarget)
          .andHas(KB.valueHasRefCount, Rdf.literalOf(linkUpdate.currentReferenceCount))
          .andHas(KB.isDeleted, Rdf.literalOf(false))
          .andHas(KB.valueHasUUID, currentLinkUUID),
        // Get the link source's last modification date, if it has one, so we can update it
        linkSource.has(KB.lastModificationDate, linkSourceLastModificationDate).optional(),
      )

      Queries
        .MODIFY()
        .prefix(RDF.NS, RDFS.NS, XSD.NS, KB.NS)
        .from(dataGraph)
        .delete(deletePatterns*)
        .into(dataGraph)
        .insert(insertPatterns*)
        .where(wherePatterns*)
    }
  }
}
