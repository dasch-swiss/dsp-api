/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsClassification.ProjectOntologyIndex
import org.knora.webapi.slice.admin.repo.ViewRestrictionsByPropertyRepo
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.api.admin.ViewRestrictionsByPropertyEndpoints.*
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ValueItemType
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo

/**
 * Computes the view-restrictions report **grouped by property**.
 *
 * A sibling of [[ViewRestrictionsService]], not a mode of it. Both classify permission literals with the
 * shared [[ViewRestrictionsAudience]], and counts read through [[ViewRestrictionsRowCache.valueRowsOfProject]].
 *
 * One unit only: every figure counts values of one property, and `totalValues` is the denominator they share.
 */
final case class ViewRestrictionsByPropertyService(
  private val repo: ViewRestrictionsByPropertyRepo,
  private val ontologyRepo: OntologyRepo,
  private val rowCache: ViewRestrictionsRowCache,
) {

  /** Step 1: the project's value properties, from the ontology cache. No counts, and no SPARQL. */
  def properties(projectIri: ProjectIri): Task[ViewRestrictionsProperties] =
    repo
      .projectValueProperties(projectIri)
      .map(rows =>
        ViewRestrictionsProperties(projectIri.value, rows.map(r => RestrictedProperty(r.id, r.label, r.ontology))),
      )

  /**
   * Step 2: one property's value counts and its population, folded from the per-class value rows.
   *
   * Reads through the shared [[ViewRestrictionsRowCache]] and never refreshes it. A resource typed with two
   * classes counts once for each class, so its values can count twice in this total, while the drill-down
   * lists each value once. A property that is not counted (standoff link values, unknown IRIs) yields zero
   * counts without loading any rows.
   */
  def propertyValues(
    projectIri: ProjectIri,
    property: String,
    itemType: ValueItemType,
  ): Task[ViewRestrictionsPropertyValues] =
    for {
      index <- ProjectOntologyIndex.load(ontologyRepo)
      rows  <- if (index.valueProperties.contains(property)) propertyRows(projectIri, property, index)
              else ZIO.succeed(Seq.empty)
      countsTotal <- ValueCountFold.fold(rows, index, itemType, projectIri)
    } yield ViewRestrictionsPropertyValues(
      projectIri = projectIri.value,
      property = property,
      itemType = itemType,
      totalValues = countsTotal._2,
      counts = countsTotal._1,
    )

  private def propertyRows(projectIri: ProjectIri, property: String, index: ProjectOntologyIndex) =
    rowCache.valueRowsOfProject(projectIri, index).map(_.filter(_.propertyIri == property))

  /**
   * Step 3: the resources carrying a restricted user value of the property, paginated.
   *
   * The values are selected by permission literal, classified by [[ViewRestrictionsAudience.restrictedLiterals]]
   * as the counts classify them. A property that is not counted, or has no restricted literal, yields an
   * empty page and runs no query.
   *
   * The literals come from the cached counts, so an object whose permissions changed after the last refresh
   * by `/classes` (at most 60 minutes ago) can be missing until the next refresh. Only properties in the
   * ontology cache are listed, as only they are counted.
   *
   * Each row reports its own resource class. That is the whole point of this report: one property can be
   * used by many classes, so the class belongs to the row and not to the table.
   */
  def propertyItems(
    projectIri: ProjectIri,
    property: String,
    itemType: ValueItemType,
    pageAndSize: PageAndSize,
  ): Task[PagedResponse[RestrictedPropertyResource]] =
    for {
      index      <- ProjectOntologyIndex.load(ontologyRepo)
      restricted <- if (index.valueProperties.contains(property)) restrictedLiterals(projectIri, property, index)
                    else ZIO.succeed(Set.empty[String])
      result <- if (restricted.isEmpty)
                  ZIO.succeed(PagedResponse.from(Seq.empty[RestrictedPropertyResource], 0, pageAndSize))
                else pageOfItems(projectIri, property, itemType, restricted, pageAndSize)
    } yield result

  /** The permission literals of the property's current values that some audience cannot fully see. */
  private def restrictedLiterals(projectIri: ProjectIri, property: String, index: ProjectOntologyIndex) =
    propertyRows(projectIri, property, index).map(rows =>
      ViewRestrictionsAudience.restrictedLiterals(rows.filterNot(_.deleted).map(_.permissions).toSet, projectIri),
    )

  private def pageOfItems(
    projectIri: ProjectIri,
    property: String,
    itemType: ValueItemType,
    restricted: Set[String],
    pageAndSize: PageAndSize,
  ): Task[PagedResponse[RestrictedPropertyResource]] =
    for {
      // Independent reads issued together as separate transactions, not a snapshot.
      totalAndRows <- repo
                        .countRestrictedResources(projectIri, property, itemType, restricted)
                        .zipPar(
                          repo.findRestrictedResources(
                            projectIri,
                            property,
                            itemType,
                            restricted,
                            offset = pageAndSize.size * (pageAndSize.page - 1),
                            limit = pageAndSize.size,
                          ),
                        )
      (total, rows) = totalAndRows
      grouped       = rows.groupBy(_.resourceIri).toSeq.map { case (resourceIri, resourceRows) =>
                  val head = resourceRows.head
                  RestrictedPropertyResource(
                    resourceIri = resourceIri,
                    label = head.resourceLabel,
                    // A resource asserted as several classes yields one row per type: the minimum class IRI
                    // keeps the answer reproducible, and each value is listed once.
                    resourceClassIri = resourceRows.map(_.resourceClassIri).min,
                    values = resourceRows
                      .distinctBy(_.valueIri)
                      .map(r =>
                        RestrictedPropertyValue(
                          valueIri = r.valueIri,
                          isFile = r.isFile,
                          hasComment = r.hasComment,
                          visibility = ViewRestrictionsAudience.itemVisibility(r.creator, r.permissions, projectIri),
                        ),
                      ),
                  )
                }
      // Keeps the SPARQL page order (label, then IRI).
      ordered = grouped.sortBy(r => (r.label, r.resourceIri))
    } yield PagedResponse.from(ordered, total, pageAndSize)
}

object ViewRestrictionsByPropertyService {
  val layer = ZLayer.derive[ViewRestrictionsByPropertyService]
}
