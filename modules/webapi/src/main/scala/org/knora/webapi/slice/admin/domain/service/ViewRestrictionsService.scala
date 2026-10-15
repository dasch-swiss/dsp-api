/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*

import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsClassification.ProjectOntologyIndex
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.PermissionCountRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.RestrictedObjectRow
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.*
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo

/**
 * Computes the "view restrictions" report for a project.
 *
 * Visibility is resolved with the real permission model: [[PermissionUtilADM.getUserPermissionADM]] is run
 * against a synthetic user for each of the three audiences (see [[ViewRestrictionsAudience.audienceUser]])
 * and the resulting permission code mapped to a [[Visibility]]. This is a reporting view of what is stored,
 * not a rendering decision: restricted view (code 1) is reported as its own state for every item type, so
 * the dashboard reflects the actual permissions in the triplestore.
 *
 * Both non-visible states are counted, separately: `hidden` (code 0, nothing is served) and
 * `restrictedView` (code 1, a degraded version is served). They are disjoint, so their sum is the number of
 * items an audience cannot fully see. Conflating them would hide code-1 items behind a label saying
 * "hidden".
 *
 * The two UNITS are never mixed either. `/classes` counts whole restricted resources and `/values` counts
 * restricted values inside them; adding them gives a number in no unit at all, since one resource holding
 * three hidden values is 1 resource and 3 values. Keeping them apart is what makes the resource figure
 * comparable to `totalResources`. Each endpoint answers in one unit, so nothing here can mix them.
 *
 * COUNTING: counts are computed by the triplestore, not by scanning objects here. That is sound because for
 * the three synthetic audiences the visibility of an object depends **only** on its
 * `knora-base:hasPermissions` literal:
 *
 *   - `entityProject` is constant per request;
 *   - `requestingUser` is one of three synthetic users, none of them system/project admin (so
 *     `getUserPermissionADM`'s max-permission short-circuit never fires);
 *   - `entityCreator` matters only when it equals the requesting user, which a synthetic audience user never
 *     is — so the `Creator` group never applies, and a `CR knora-admin:Creator` clause grants the audiences
 *     nothing (the drill-down, resolving the real creator, reaches the same conclusion for the same reason).
 *
 * So the repo groups its counts by permission literal and the handful of distinct literals are classified
 * here with the real permission model — one query answers all three audiences and both states at once.
 * Counts are exact at any project size. The drill-down is paged in SPARQL with a matching `COUNT`, so its
 * `totalItems` is exact and its page order is stable.
 */
final case class ViewRestrictionsService(
  private val repo: ViewRestrictionsRepo,
  private val ontologyRepo: OntologyRepo,
) {

  /**
   * Step 1 of the stepped report: every resource class with its population and its resource-level
   * restrictions, from a single query.
   */
  def classSummaries(projectIri: ProjectIri): Task[ViewRestrictionsClasses] =
    for {
      index   <- ProjectOntologyIndex.load(ontologyRepo)
      rows    <- repo.resourceCounts(projectIri)
      counted  = rows.filter(r => index.resourceClasses.contains(r.classIri))
      lookup   = ViewRestrictionsAudience.classify(counted.map(_.permissions).toSet, projectIri)
      reported = counted.groupBy(_.classIri).toSeq.map { case (classIri, classRows) =>
                   val permissionRows  = classRows.map(r => PermissionCountRow(Some(classIri), r.permissions, r.count))
                   val (counts, total) = ValueCountFold.foldRows(permissionRows, lookup)
                   RestrictedClass(classIri, localName(classIri), Some(ontologyName(classIri)), total, counts)
                 }
      // Ordering is no longer part of the contract — the frontend renders rows as they arrive — but a
      // stable order keeps the response reproducible and the tests readable.
    } yield ViewRestrictionsClasses(projectIri.value, reported.sortBy(c => (c.label, c.id)))

  /** Step 2 of the stepped report: one class's value-level restrictions, from a single query. */
  def valueCounts(
    projectIri: ProjectIri,
    resourceClass: String,
    itemType: ValueItemType,
  ): Task[ViewRestrictionsValues] =
    for {
      index  <- ProjectOntologyIndex.load(ontologyRepo)
      rows   <- repo.valueCounts(projectIri, resourceClass)
      counts <- ValueCountFold.fold(rows, index, itemType, projectIri).map(_._1)
    } yield ViewRestrictionsValues(projectIri.value, resourceClass, itemType, counts)

  private def visibilityFor(row: RestrictedObjectRow, audience: Audience, projectIri: ProjectIri): Visibility =
    ViewRestrictionsAudience.visibilityOf(
      PermissionUtilADM.getUserPermissionADM(
        entityCreator = row.creator,
        entityProject = projectIri.value,
        entityPermissionLiteral = row.permissions,
        requestingUser = ViewRestrictionsAudience.audienceUser(audience, projectIri),
      ),
    )

  private def itemVisibility(row: RestrictedObjectRow, projectIri: ProjectIri): ItemVisibility =
    ItemVisibility(
      anonymous = visibilityFor(row, Audience.Anonymous, projectIri),
      authenticated = visibilityFor(row, Audience.Authenticated, projectIri),
      projectMember = visibilityFor(row, Audience.ProjectMember, projectIri),
    )

  private def localName(iri: String): String    = iri.split(Array('#', '/')).lastOption.getOrElse(iri)
  private def ontologyName(iri: String): String = {
    val beforeHash = iri.split('#').headOption.getOrElse(iri)
    beforeHash.split('/').lastOption.getOrElse(beforeHash)
  }

  def items(
    projectIri: ProjectIri,
    resourceClass: String,
    itemType: ItemType,
    pageAndSize: PageAndSize,
  ): Task[PagedResponse[RestrictedResource]] =
    for {
      // Paging happens in SPARQL: `rows` are already exactly the resources of the requested page, and
      // `total` is an exact COUNT over the whole result set — no scan cap, no Scala-side slicing.
      classes <- repo.projectClasses(projectIri)
      // The page total and the page itself are independent reads, so they go out together rather than one
      // after the other. They are separate transactions, not a snapshot: a concurrent write between them
      // can leave `totalItems` inconsistent with the rows — as it could before, when they ran in sequence.
      totalAndRows <- repo
                        .countRestrictedResources(projectIri, itemType, resourceClass, classes)
                        .zipPar(
                          repo.findRestrictedObjects(
                            projectIri,
                            itemType,
                            resourceClass,
                            offset = pageAndSize.size * (pageAndSize.page - 1),
                            limit = pageAndSize.size,
                            classes,
                          ),
                        )
      (total, rows) = totalAndRows
      // Assemble one RestrictedResource per affected resource, with its restricted parts nested under it.
      byResource = rows.groupBy(_.resourceIri).toSeq.map { case (resourceIri, resourceRows) =>
                     val head            = resourceRows.head
                     val restrictedItems = resourceRows.collect {
                       case r if r.itemType != ItemType.Resource =>
                         RestrictedItem(
                           `type` = r.itemType,
                           propertyIri = r.propertyIri,
                           propertyLabel = r.propertyLabel,
                           valueIri = r.valueIri,
                           visibility = itemVisibility(r, projectIri),
                         )
                     }
                     // The resource's own visibility comes from the resource-level row if present, else fully visible.
                     val resourceVis = resourceRows
                       .find(_.itemType == ItemType.Resource)
                       .map(itemVisibility(_, projectIri))
                       .getOrElse(ItemVisibility(Visibility.Visible, Visibility.Visible, Visibility.Visible))
                     RestrictedResource(
                       resourceIri = resourceIri,
                       label = head.resourceLabel,
                       resourceClassIri = head.resourceClassIri,
                       resourceVisibility = resourceVis,
                       items = restrictedItems,
                     )
                   }
      // Preserve the SPARQL page order (label, then IRI) so paging stays reproducible across requests.
      ordered = byResource.sortBy(r => (r.label, r.resourceIri))
    } yield PagedResponse.from(ordered, total, pageAndSize)
}

object ViewRestrictionsService {

  val layer = ZLayer.derive[ViewRestrictionsService]
}
