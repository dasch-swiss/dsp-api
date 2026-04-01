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
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.IO
import zio.ZIO

import java.time.Instant
import java.util.UUID

import dsp.errors.SparqlGenerationException
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.twirl.SparqlTemplateLinkUpdate
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB

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
object ChangeLinkTargetQuery extends QueryBuilderHelper {

  /**
   * Builds a SPARQL UPDATE query to change a link's target resource.
   *
   * @param project                  the project the resource belongs to (used to determine the named graph)
   * @param linkSourceIri           the resource that is the source of the links
   * @param linkUpdateForCurrentLink a [[SparqlTemplateLinkUpdate]] specifying how to update the current link
   * @param linkUpdateForNewLink    a [[SparqlTemplateLinkUpdate]] specifying how to update the new link
   * @param newLinkValueUUID        the UUID for the new link value
   * @param maybeComment            an optional comment on the new link value
   * @param currentTime             an xsd:dateTimeStamp that will be attached to the resources
   * @param requestingUser          the IRI of the user making the request
   */
  def build(
    project: Project,
    linkSourceIri: ResourceIri,
    linkUpdateForCurrentLink: SparqlTemplateLinkUpdate,
    linkUpdateForNewLink: SparqlTemplateLinkUpdate,
    newLinkValueUUID: UUID,
    maybeComment: Option[String],
    currentTime: Instant,
    requestingUser: UserIri,
  ): IO[SparqlGenerationException, ModifyQuery] = {
    inline def failIf(condition: Boolean, message: String): IO[SparqlGenerationException, Unit] =
      ZIO.fail(SparqlGenerationException(message)).when(condition).unit

    for {
      _ <- failIf(
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
      val dataGraph                      = graphIri(project)
      val linkSource                     = toRdfIri(linkSourceIri)
      val linkProperty                   = toRdfIri(linkUpdateForCurrentLink.linkPropertyIri)
      val linkValueProperty              = Rdf.iri(linkUpdateForCurrentLink.linkPropertyIri.toInternalSchema.toIri + "Value")
      val linkTargetForCurrentLink       = Rdf.iri(linkUpdateForCurrentLink.linkTargetIri)
      val linkTargetForNewLink           = Rdf.iri(linkUpdateForNewLink.linkTargetIri)
      val newLinkValueForCurrentLink     = Rdf.iri(linkUpdateForCurrentLink.newLinkValueIri)
      val newLinkValueForNewLink         = Rdf.iri(linkUpdateForNewLink.newLinkValueIri)
      val linkSourceClass                = variable("linkSourceClass")
      val currentLinkValueForCurrentLink = variable("currentLinkValueForCurrentLink")
      val currentLinkUUID                = variable("currentLinkUUID")
      val currentLinkPermissions         = variable("currentLinkPermissions")
      val order                          = variable("order")
      val linkSourceLastModificationDate = variable("linkSourceLastModificationDate")
      val linkTargetClass                = variable("linkTargetClass")
      val expectedTargetClass            = variable("expectedTargetClass")
      val currentLinkValueForNewLink     = variable("currentLinkValueForNewLink")
      val currentTimeLiteral             = Rdf.literalOfType(currentTime.toString, XSD.DATETIME)

      // DELETE patterns
      val deletePatterns = Seq(
        linkSource.has(KB.lastModificationDate, linkSourceLastModificationDate),
        linkSource.has(linkProperty, linkTargetForCurrentLink),
        linkSource.has(linkValueProperty, currentLinkValueForCurrentLink),
        currentLinkValueForCurrentLink.has(KB.valueHasUUID, currentLinkUUID),
        currentLinkValueForCurrentLink.has(KB.hasPermissions, currentLinkPermissions),
      )

      // INSERT patterns — new version of LinkValue for the current (deleted) link
      val insertCurrentLinkValue = Seq(
        newLinkValueForCurrentLink.isA(KB.linkValue),
        newLinkValueForCurrentLink.has(RDF.SUBJECT, linkSource),
        newLinkValueForCurrentLink.has(RDF.PREDICATE, linkProperty),
        newLinkValueForCurrentLink.has(RDF.OBJECT, linkTargetForCurrentLink),
        newLinkValueForCurrentLink
          .has(KB.valueHasString, Rdf.literalOfType(linkUpdateForCurrentLink.linkTargetIri, XSD.STRING)),
        newLinkValueForCurrentLink
          .has(KB.valueHasRefCount, Rdf.literalOf(linkUpdateForCurrentLink.newReferenceCount)),
        newLinkValueForCurrentLink.has(KB.valueCreationDate, currentTimeLiteral),
        newLinkValueForCurrentLink.has(KB.previousValue, currentLinkValueForCurrentLink),
        newLinkValueForCurrentLink.has(KB.valueHasUUID, currentLinkUUID),
        newLinkValueForCurrentLink.has(KB.deleteDate, currentTimeLiteral),
        newLinkValueForCurrentLink.has(KB.deletedBy, toRdfIri(requestingUser)),
        newLinkValueForCurrentLink.has(KB.isDeleted, Rdf.literalOf(true)),
        newLinkValueForCurrentLink.has(KB.attachedToUser, Rdf.iri(linkUpdateForCurrentLink.newLinkValueCreator)),
        newLinkValueForCurrentLink
          .has(KB.hasPermissions, Rdf.literalOfType(linkUpdateForCurrentLink.newLinkValuePermissions, XSD.STRING)),
        linkSource.has(linkValueProperty, newLinkValueForCurrentLink),
      )

      // INSERT patterns — new direct link
      val insertNewDirectLink = Seq(
        linkSource.has(linkProperty, linkTargetForNewLink),
      )

      // INSERT patterns — new LinkValue for the new link
      val insertNewLinkValueBase = Seq(
        newLinkValueForNewLink.isA(KB.linkValue),
        newLinkValueForNewLink.has(RDF.SUBJECT, linkSource),
        newLinkValueForNewLink.has(RDF.PREDICATE, linkProperty),
        newLinkValueForNewLink.has(RDF.OBJECT, linkTargetForNewLink),
        newLinkValueForNewLink
          .has(KB.valueHasString, Rdf.literalOfType(linkUpdateForNewLink.linkTargetIri, XSD.STRING)),
      )

      val insertComment =
        maybeComment.map(c => newLinkValueForNewLink.has(KB.valueHasComment, Rdf.literalOf(c))).toSeq

      val insertNewLinkValueRest = Seq(
        newLinkValueForNewLink.has(KB.valueHasRefCount, Rdf.literalOf(linkUpdateForNewLink.newReferenceCount)),
        newLinkValueForNewLink.has(KB.valueHasOrder, order),
        newLinkValueForNewLink.has(KB.isDeleted, Rdf.literalOf(false)),
        newLinkValueForNewLink.has(KB.valueHasUUID, Rdf.literalOf(UuidUtil.base64Encode(newLinkValueUUID))),
        newLinkValueForNewLink.has(KB.valueCreationDate, currentTimeLiteral),
        newLinkValueForNewLink.has(KB.attachedToUser, Rdf.iri(linkUpdateForNewLink.newLinkValueCreator)),
        newLinkValueForNewLink
          .has(KB.hasPermissions, Rdf.literalOfType(linkUpdateForNewLink.newLinkValuePermissions, XSD.STRING)),
        linkSource.has(linkValueProperty, newLinkValueForNewLink),
      )

      val insertLastMod = Seq(
        linkSource.has(KB.lastModificationDate, currentTimeLiteral),
      )

      val insertPatterns =
        insertCurrentLinkValue ++ insertNewDirectLink ++ insertNewLinkValueBase ++ insertComment ++ insertNewLinkValueRest ++ insertLastMod

      // WHERE patterns
      val subClassOfPath = zeroOrMore(RDFS.SUBCLASSOF)

      val wherePatterns: Seq[GraphPattern] = Seq(
        // Check link source is a Resource and not deleted
        linkSource.isA(linkSourceClass),
        linkSourceClass.has(subClassOfPath, KB.Resource),
        linkSource.has(KB.isDeleted, Rdf.literalOf(false)),
        // Make sure the current direct link exists
        linkSource.has(linkProperty, linkTargetForCurrentLink),
        // Make sure a LinkValue exists for the current link with the correct reference count
        linkSource.has(linkValueProperty, currentLinkValueForCurrentLink),
        currentLinkValueForCurrentLink
          .isA(KB.linkValue)
          .andHas(RDF.SUBJECT, linkSource)
          .andHas(RDF.PREDICATE, linkProperty)
          .andHas(RDF.OBJECT, linkTargetForCurrentLink)
          .andHas(KB.valueHasRefCount, Rdf.literalOf(linkUpdateForCurrentLink.currentReferenceCount))
          .andHas(KB.isDeleted, Rdf.literalOf(false))
          .andHas(KB.valueHasUUID, currentLinkUUID)
          .andHas(KB.hasPermissions, currentLinkPermissions),
        // Optional: get the order from the current link value
        currentLinkValueForCurrentLink.has(KB.valueHasOrder, order).optional(),
        // Do nothing if a direct link already exists to the new target
        GraphPatterns.filterNotExists(linkSource.has(linkProperty, linkTargetForNewLink)),
        // Do nothing if an active LinkValue already exists for the new target
        GraphPatterns.filterNotExists(
          linkSource
            .has(linkValueProperty, currentLinkValueForNewLink)
            .and(
              currentLinkValueForNewLink
                .isA(KB.linkValue)
                .andHas(RDF.SUBJECT, linkSource)
                .andHas(RDF.PREDICATE, linkProperty)
                .andHas(RDF.OBJECT, linkTargetForNewLink)
                .andHas(KB.isDeleted, Rdf.literalOf(false)),
            ),
        ),
        // Validate new target: exists, not deleted, is a Resource, and satisfies the class constraint
        linkTargetForNewLink.isA(linkTargetClass).andHas(KB.isDeleted, Rdf.literalOf(false)),
        linkTargetClass.has(subClassOfPath, KB.Resource),
        linkProperty.has(KB.objectClassConstraint, expectedTargetClass),
        linkTargetClass.has(subClassOfPath, expectedTargetClass),
        // Get the link source's last modification date, if it has one
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
