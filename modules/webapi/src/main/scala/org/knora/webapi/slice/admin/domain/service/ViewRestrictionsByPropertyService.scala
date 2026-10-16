/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*

import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsClassification.ProjectOntologyIndex
import org.knora.webapi.slice.admin.repo.ViewRestrictionsByPropertyRepo
import org.knora.webapi.slice.admin.repo.ViewRestrictionsByPropertyRepo.RestrictedPropertyValueRow
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.api.admin.ViewRestrictionsByPropertyEndpoints.*
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.Audience
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ItemVisibility
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ValueItemType
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.Visibility
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo

/**
 * Computes the view-restrictions report **grouped by property**.
 *
 * A sibling of [[ViewRestrictionsService]], not a mode of it. Both classify permission literals with the
 * shared [[ViewRestrictionsAudience]], which explains why counting by permission literal is sound.
 *
 * `ViewRestrictionsAudienceSpec` pins that equivalence for the drill-down, which resolves each
 * value with its real creator. A divergence between the counts and the drill-down should fail a test rather
 * than quietly change the numbers on one screen.
 *
 * Counts read through [[ViewRestrictionsRowCache.valueRowsOfProject]].
 *
 * One unit only. A property has no resource population of its own, so unlike the class report there is no
 * second unit to keep apart — every figure here counts values of one property, and `totalValues` is the
 * denominator they share.
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
      // The page and its total are independent reads, so they go out together. They are separate
      // transactions rather than a snapshot: a concurrent write between them can leave `totalItems`
      // inconsistent with the rows, exactly as in the class drill-down.
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
                    // A resource asserted as several classes yields one row per type, since this report
                    // joins `?resource a ?resClass` without the most-specific-class filter the class report
                    // needs. Picking the minimum rather than `head` keeps the answer reproducible across
                    // requests; resolving the most specific one would mean importing the class machinery
                    // this report exists without.
                    resourceClassIri = resourceRows.map(_.resourceClassIri).min,
                    // ...and the same multi-typing would repeat every value once per type.
                    values = resourceRows
                      .distinctBy(_.valueIri)
                      .map(r =>
                        RestrictedPropertyValue(
                          valueIri = r.valueIri,
                          isFile = r.isFile,
                          hasComment = r.hasComment,
                          visibility = itemVisibility(r, projectIri),
                        ),
                      ),
                  )
                }
      // Preserve the SPARQL page order (label, then IRI) so paging stays reproducible across requests.
      ordered = grouped.sortBy(r => (r.label, r.resourceIri))
    } yield PagedResponse.from(ordered, total, pageAndSize)

  /**
   * Per-audience visibility of one drill-down value, resolved with its **real** creator.
   *
   * The counts classify a bare literal against a placeholder; here the creator is known, so it is used.
   * Both reach the same answer for these audiences — none of them is ever the creator — which is the
   * equivalence the spec pins.
   */
  private def itemVisibility(row: RestrictedPropertyValueRow, projectIri: ProjectIri): ItemVisibility =
    ItemVisibility(
      anonymous = visibilityFor(row, Audience.Anonymous, projectIri),
      authenticated = visibilityFor(row, Audience.Authenticated, projectIri),
      projectMember = visibilityFor(row, Audience.ProjectMember, projectIri),
    )

  private def visibilityFor(row: RestrictedPropertyValueRow, audience: Audience, projectIri: ProjectIri): Visibility =
    ViewRestrictionsAudience.visibilityOf(
      PermissionUtilADM.getUserPermissionADM(
        entityCreator = row.creator,
        entityProject = projectIri.value,
        entityPermissionLiteral = row.permissions,
        requestingUser = ViewRestrictionsAudience.audienceUser(audience, projectIri),
      ),
    )
}

object ViewRestrictionsByPropertyService {
  val layer = ZLayer.derive[ViewRestrictionsByPropertyService]
}
