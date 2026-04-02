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
import org.knora.webapi.messages.twirl.SparqlTemplateLinkUpdate
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.KnoraIris.ValueIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB

/**
 * Marks a value as deleted. This query is used for all value types except links.
 *
 * If the value is a TextValue containing standoff markup with resource references,
 * the corresponding LinkValues are updated (decremented) as part of the same query.
 */
object DeleteValueQuery extends QueryBuilderHelper {

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
  ): IO[SparqlGenerationException, ModifyQuery] =
    ZIO
      .foreach(linkUpdates.zipWithIndex) { case (lu, _) =>
        for {
          _ <- failIf(lu.insertDirectLink, "linkUpdate.insertDirectLink must be false in this SPARQL template")
          _ <- failIf(!lu.directLinkExists, "linkUpdate.directLinkExists must be true in this SPARQL template")
          _ <- failIf(!lu.linkValueExists, "linkUpdate.linkValueExists must be true in this SPARQL template")
        } yield ()
      }
      .as {
        val dataGraph                    = graphIri(project)
        val resource                     = toRdfIri(resourceIri)
        val property                     = toRdfIri(propertyIri)
        val value                        = toRdfIri(valueIri)
        val valueClass                   = variable("valueClass")
        val resourceLastModificationDate = variable("resourceLastModificationDate")
        val currentTimeLiteral           = Rdf.literalOfType(currentTime.toString, XSD.DATETIME)

        // Indexed variables for each link update
        val linkValueVars = linkUpdates.indices.map(i => variable(s"linkValue$i"))
        val linkUUIDVars  = linkUpdates.indices.map(i => variable(s"linkValueUUID$i"))

        // --- DELETE patterns ---
        val deleteBase = Seq(
          // Delete the resource's last modification date so we can update it
          resource.has(KB.lastModificationDate, resourceLastModificationDate),
          value.has(KB.isDeleted, Rdf.literalOf(false)),
        )

        val deleteLinkPatterns = linkUpdates.zipWithIndex.flatMap { case (lu, i) =>
          val linkProperty      = toRdfIri(lu.linkPropertyIri)
          val linkValueProperty = Rdf.iri(lu.linkPropertyIri.toInternalSchema.toIri + "Value")
          val linkTarget        = Rdf.iri(lu.linkTargetIri)

          // Delete direct links for standoff resource references that no longer exist
          val deleteDirectLink =
            if (lu.deleteDirectLink) Seq(resource.has(linkProperty, linkTarget))
            else Seq.empty

          // Detach the current LinkValue from the resource and delete its UUID (the new version will store it)
          val detachLinkValue = Seq(
            resource.has(linkValueProperty, linkValueVars(i)),
            linkValueVars(i).has(KB.valueHasUUID, linkUUIDVars(i)),
          )

          deleteDirectLink ++ detachLinkValue
        }

        val deletePatterns = deleteBase ++ deleteLinkPatterns

        // --- INSERT patterns ---
        val insertBase = Seq(
          value
            .has(KB.isDeleted, Rdf.literalOf(true))
            .andHas(KB.deletedBy, toRdfIri(requestingUser))
            .andHas(KB.deleteDate, currentTimeLiteral),
        )

        val insertComment =
          maybeDeleteComment.map(c => value.has(KB.deleteComment, Rdf.literalOf(c))).toSeq

        // Update LinkValues for resource references in standoff markup
        val insertLinkPatterns = linkUpdates.zipWithIndex.flatMap { case (lu, i) =>
          val linkProperty      = toRdfIri(lu.linkPropertyIri)
          val linkValueProperty = Rdf.iri(lu.linkPropertyIri.toInternalSchema.toIri + "Value")
          val linkTarget        = Rdf.iri(lu.linkTargetIri)
          val newLinkValue      = Rdf.iri(lu.newLinkValueIri)

          val deletedOrNot =
            if (lu.newReferenceCount == 0)
              Seq(
                newLinkValue.has(KB.isDeleted, Rdf.literalOf(true)),
                newLinkValue.has(KB.deletedBy, Rdf.iri(lu.newLinkValueCreator)),
                newLinkValue.has(KB.deleteDate, currentTimeLiteral),
              )
            else
              Seq(newLinkValue.has(KB.isDeleted, Rdf.literalOf(false)))

          // Add a new LinkValue version for the resource reference
          Seq(
            newLinkValue.isA(KB.linkValue),
            newLinkValue.has(RDF.SUBJECT, resource),
            newLinkValue.has(RDF.PREDICATE, linkProperty),
            newLinkValue.has(RDF.OBJECT, linkTarget),
            newLinkValue.has(KB.valueHasString, Rdf.literalOfType(lu.linkTargetIri, XSD.STRING)),
            newLinkValue.has(KB.valueHasRefCount, Rdf.literalOf(lu.newReferenceCount)),
          ) ++ deletedOrNot ++ Seq(
            newLinkValue.has(KB.valueCreationDate, currentTimeLiteral),
            newLinkValue.has(KB.attachedToUser, Rdf.iri(lu.newLinkValueCreator)),
            newLinkValue.has(KB.hasPermissions, Rdf.literalOfType(lu.newLinkValuePermissions, XSD.STRING)),
            newLinkValue.has(KB.previousValue, linkValueVars(i)),
            newLinkValue.has(KB.valueHasUUID, linkUUIDVars(i)),
            // Attach the new LinkValue to its containing resource
            resource.has(linkValueProperty, newLinkValue),
          )
        }

        // Update the resource's last modification date
        val insertLastMod = Seq(resource.has(KB.lastModificationDate, currentTimeLiteral))

        val insertPatterns = insertBase ++ insertComment ++ insertLinkPatterns ++ insertLastMod

        // --- WHERE patterns ---
        val whereBase: Seq[GraphPattern] = Seq(
          resource.has(property, value),
          value
            .isA(valueClass)
            .andHas(KB.isDeleted, Rdf.literalOf(false)),
          valueClass.has(zeroOrMore(RDFS.SUBCLASSOF), KB.Value),
        )

        // Check the state of any LinkValues to be updated for resource references
        val whereLinkPatterns: Seq[GraphPattern] = linkUpdates.zipWithIndex.flatMap { case (lu, i) =>
          val linkProperty      = toRdfIri(lu.linkPropertyIri)
          val linkValueProperty = Rdf.iri(lu.linkPropertyIri.toInternalSchema.toIri + "Value")
          val linkTarget        = Rdf.iri(lu.linkTargetIri)

          Seq(
            // Make sure the relevant direct link exists between the two resources
            resource.has(linkProperty, linkTarget),
            // Make sure a LinkValue exists describing the direct link with the correct reference count
            resource.has(linkValueProperty, linkValueVars(i)),
            linkValueVars(i)
              .isA(KB.linkValue)
              .andHas(RDF.SUBJECT, resource)
              .andHas(RDF.PREDICATE, linkProperty)
              .andHas(RDF.OBJECT, linkTarget)
              .andHas(KB.valueHasRefCount, Rdf.literalOf(lu.currentReferenceCount))
              .andHas(KB.isDeleted, Rdf.literalOf(false))
              .andHas(KB.valueHasUUID, linkUUIDVars(i)),
          )
        }

        // Get the resource's last modification date, if it has one, so we can update it
        val whereOptional: Seq[GraphPattern] = Seq(
          resource.has(KB.lastModificationDate, resourceLastModificationDate).optional(),
        )

        val wherePatterns = whereBase ++ whereLinkPatterns ++ whereOptional

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
