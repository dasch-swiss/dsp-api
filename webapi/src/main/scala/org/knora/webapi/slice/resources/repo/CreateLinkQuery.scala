/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.literalOf
import zio.IO

import java.time.Instant
import java.util.UUID

import dsp.errors.SparqlGenerationException
import dsp.valueobjects.UuidUtil
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.resources.repo.model.SparqlTemplateLinkUpdate

/**
 * Inserts a new link between resources.
 *
 * This query:
 * 1. Deletes the link source's last modification date so it can be updated
 * 2. Inserts a direct link between source and target resources
 * 3. Creates a new LinkValue describing the link
 * 4. Updates the link source's last modification date
 */
object CreateLinkQuery extends QueryBuilderHelper {

  /**
   * Builds a SPARQL UPDATE query to create a link between two resources.
   *
   * @param project        the project that owns the data graph
   * @param resourceIri    the resource that is the source of the link
   * @param linkUpdate     a [[SparqlTemplateLinkUpdate]] specifying the link to create
   * @param newValueUUID   the UUID to be attached to the value
   * @param creationDate   an xsd:dateTimeStamp that will be attached to the link value
   * @param maybeComment   an optional comment on the link
   */
  def build(
    project: Project,
    resourceIri: ResourceIri,
    linkUpdate: SparqlTemplateLinkUpdate,
    newValueUUID: UUID,
    creationDate: Instant,
    maybeComment: Option[String],
  ): IO[SparqlGenerationException, ModifyQuery] =
    for {
      _ <- failIf(!linkUpdate.insertDirectLink, "linkUpdate.insertDirectLink must be true in this SPARQL template")
      _ <- failIf(linkUpdate.directLinkExists, "linkUpdate.directLinkExists must be false in this SPARQL template")
      _ <- failIf(linkUpdate.linkValueExists, "linkUpdate.linkValueExists must be false in this SPARQL template")
    } yield {
      val dataGraph         = graphIri(project)
      val resource          = toRdfIri(resourceIri)
      val linkProperty      = toRdfIri(linkUpdate.linkPropertyIri)
      val linkValueProperty = Rdf.iri(linkUpdate.linkPropertyIri.toInternalSchema.toIri + "Value")
      val linkTarget        = Rdf.iri(linkUpdate.linkTargetIri)
      val newLinkValue      = Rdf.iri(linkUpdate.newLinkValueIri)

      val resourceLastModificationDate = variable("resourceLastModificationDate")
      val nextOrder                    = variable("nextOrder")
      val resourceClass                = variable("resourceClass")

      // DELETE patterns
      val deletePatterns = Seq(
        resource.has(KB.lastModificationDate, resourceLastModificationDate),
      )

      // INSERT patterns
      val baseInsertPatterns = Seq(
        // Update the link source's last modification date
        resource.has(KB.lastModificationDate, toRdfLiteral(creationDate)),
        // Insert a direct link between the source and target resources
        resource.has(linkProperty, linkTarget),
        // Insert a LinkValue describing the link
        newLinkValue.isA(KB.linkValue),
        newLinkValue.has(RDF.SUBJECT, resource),
        newLinkValue.has(RDF.PREDICATE, linkProperty),
        newLinkValue.has(RDF.OBJECT, linkTarget),
        newLinkValue.has(KB.valueHasString, Rdf.literalOfType(linkUpdate.linkTargetIri, XSD.STRING)),
        newLinkValue.has(KB.valueHasRefCount, literalOf(linkUpdate.newReferenceCount)),
        newLinkValue.has(KB.valueHasOrder, nextOrder),
        newLinkValue.has(KB.isDeleted, literalOf(false)),
        newLinkValue.has(KB.valueHasUUID, Rdf.literalOf(UuidUtil.base64Encode(newValueUUID))),
        newLinkValue.has(KB.valueCreationDate, toRdfLiteral(creationDate)),
        newLinkValue.has(KB.attachedToUser, Rdf.iri(linkUpdate.newLinkValueCreator)),
        newLinkValue.has(KB.hasPermissions, Rdf.literalOfType(linkUpdate.newLinkValuePermissions, XSD.STRING)),
        // Attach the new LinkValue to its containing resource
        resource.has(linkValueProperty, newLinkValue),
      )

      val commentInsert =
        maybeComment.map(comment => newLinkValue.has(KB.valueHasComment, Rdf.literalOf(comment))).toSeq

      val insertPatterns = baseInsertPatterns ++ commentInsert

      // WHERE patterns
      val subClassOfPath = zeroOrMore(RDFS.SUBCLASSOF)

      val baseWherePatterns: Seq[GraphPattern] = Seq(
        // Check resource exists, is not deleted, and is a knora-base:Resource
        resource.isA(resourceClass).andHas(KB.isDeleted, literalOf(false)),
        resourceClass.has(subClassOfPath, KB.Resource),
        // Get the link source's last modification date, if it has one
        resource.has(KB.lastModificationDate, resourceLastModificationDate).optional(),
      )

      // If the link target already exists, validate it
      val linkTargetValidationPatterns: Seq[GraphPattern] =
        if (linkUpdate.linkTargetExists) {
          val linkTargetClass     = variable("linkTargetClass")
          val expectedTargetClass = variable("expectedTargetClass")
          val restriction         = variable("restriction")
          Seq(
            // Make sure the link target is a knora-base:Resource
            linkTarget.isA(linkTargetClass),
            linkTargetClass.has(subClassOfPath, KB.Resource),
            // Do nothing if the target resource belongs to the wrong OWL class
            linkProperty.has(KB.objectClassConstraint, expectedTargetClass),
            linkTargetClass.has(subClassOfPath, expectedTargetClass),
            // Do nothing if the target resource doesn't exist or is marked as deleted
            linkTarget.has(KB.isDeleted, literalOf(false)),
            // Do nothing if the source resource's OWL class has no cardinality for the link property
            resourceClass.has(subClassOfPath, restriction),
            restriction.isA(OWL.RESTRICTION),
            restriction.has(OWL.ONPROPERTY, linkProperty),
          )
        } else {
          Seq.empty
        }

      // Subquery for next order value
      val order                       = variable("order")
      val maxOrder                    = variable("maxOrder")
      val otherValue                  = variable("otherLinkValue")
      val orderSubquery: GraphPattern = GraphPatterns
        .select()
        .select(
          Expressions.max(order).as(maxOrder),
          Expressions
            .iff(
              Expressions.bound(maxOrder),
              Expressions.add(maxOrder, literalOf(1)),
              literalOf(0),
            )
            .as(nextOrder),
        )
        .where(
          resource.has(linkValueProperty, otherValue),
          otherValue.has(KB.valueHasOrder, order).andHas(KB.isDeleted, literalOf(false)),
        )

      val wherePatterns = baseWherePatterns ++ linkTargetValidationPatterns :+ orderSubquery

      Queries
        .MODIFY()
        .prefix(RDF.NS, RDFS.NS, OWL.NS, XSD.NS, KB.NS)
        .from(dataGraph)
        .delete(deletePatterns*)
        .into(dataGraph)
        .insert(insertPatterns*)
        .where(wherePatterns*)
    }
}
