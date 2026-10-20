/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsClassification.ProjectOntologyIndex
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsIriNames.localName
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsIriNames.ontologyName
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.DrillDownBranches
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
 * The two units are never mixed: `/classes` counts whole resources and `/values` counts values inside them.
 *
 * Counts are computed by the triplestore, grouped by permission literal; why that is sound is explained in
 * [[ViewRestrictionsAudience]]. The drill-down is paged in SPARQL with a matching `COUNT`, so its
 * `totalItems` is exact and its page order is stable.
 */
final case class ViewRestrictionsService(
  private val repo: ViewRestrictionsRepo,
  private val rowCache: ViewRestrictionsRowCache,
  private val ontologyRepo: OntologyRepo,
) {

  /**
   * Step 1 of the stepped report: every resource class that has resources, with its population and its
   * resource-level restrictions, from a single query. Refreshes the project's cached counts.
   *
   * Rows of classes outside `index.resourceClasses` (list nodes, ontologies, value classes) are dropped on
   * purpose: they carry `attachedToProject` but are not resources. The result is sorted for a reproducible
   * response.
   */
  def classSummaries(project: KnoraProject): Task[ViewRestrictionsClasses] =
    for {
      index <- ProjectOntologyIndex.load(ontologyRepo)
      // The only invalidation point besides the cache TTL.
      rows    <- rowCache.refreshProject(project)
      counted  = rows.filter(r => index.resourceClasses.contains(r.classIri))
      lookup   = ViewRestrictionsAudience.classify(counted.map(_.permissions).toSet, project.id)
      reported = counted.groupBy(_.classIri).toSeq.map { case (classIri, classRows) =>
                   val permissionRows  = classRows.map(r => PermissionCountRow(r.permissions, r.count))
                   val (counts, total) = ValueCountFold.foldRows(permissionRows, lookup)
                   RestrictedClass(classIri, localName(classIri), Some(ontologyName(classIri)), total, counts)
                 }
    } yield ViewRestrictionsClasses(project.id.value, reported.sortBy(c => (c.label, c.id)))

  /** Step 2 of the stepped report: one class's value-level restrictions, from the project's cached value rows. */
  def valueCounts(
    project: KnoraProject,
    resourceClass: String,
    itemType: ValueItemType,
  ): Task[ViewRestrictionsValues] =
    for {
      index  <- ProjectOntologyIndex.load(ontologyRepo)
      counts <- if (index.resourceClasses.contains(resourceClass))
                  classValueRows(project, resourceClass).map(ValueCountFold.fold(_, itemType, project.id)._1)
                else ZIO.succeed(AudienceRestrictionCounts.zero)
    } yield ViewRestrictionsValues(project.id.value, resourceClass, itemType, counts)

  /**
   * The restricted resources of one class, with their restricted parts nested, one page at a time.
   *
   * The objects are selected by permission literal: the literals of the class's cached count rows are
   * classified by [[ViewRestrictionsAudience.restrictedLiterals]], the classification the counts use, so the
   * drill-down lists what the counts report. Only the cached rows the item type needs are read. When no
   * literal the item type needs is restricted, or the class is not a resource class, the page is empty and
   * no drill-down query runs.
   *
   * The literals come from the cached counts, so an object whose permissions changed after the last refresh
   * by [[classSummaries]] (at most 60 minutes ago) can be missing until the next refresh.
   */
  def items(
    project: KnoraProject,
    resourceClass: String,
    itemType: ItemType,
    pageAndSize: PageAndSize,
  ): Task[PagedResponse[RestrictedResource]] =
    for {
      index    <- ProjectOntologyIndex.load(ontologyRepo)
      branches <- if (index.resourceClasses.contains(resourceClass))
                    drillDownBranches(project, resourceClass, itemType)
                  else ZIO.none
      result <- branches.fold(ZIO.succeed(emptyPage(pageAndSize)))(
                  pageOfItems(project, resourceClass, itemType, pageAndSize, _),
                )
    } yield result

  private def emptyPage(pageAndSize: PageAndSize): PagedResponse[RestrictedResource] =
    PagedResponse.from(Seq.empty[RestrictedResource], 0, pageAndSize)

  private def pageOfItems(
    project: KnoraProject,
    resourceClass: String,
    itemType: ItemType,
    pageAndSize: PageAndSize,
    branches: DrillDownBranches,
  ): Task[PagedResponse[RestrictedResource]] =
    // The page and its total are independent reads, issued together as separate transactions: a concurrent
    // write between them can leave `totalItems` inconsistent with the rows.
    repo
      .countRestrictedResources(project, itemType, resourceClass, branches)
      .zipPar(
        repo.findRestrictedObjects(
          project,
          itemType,
          resourceClass,
          offset = pageAndSize.size * (pageAndSize.page - 1),
          limit = pageAndSize.size,
          branches,
        ),
      )
      .map { case (total, rows) => PagedResponse.from(restrictedResources(rows, project.id), total, pageAndSize) }

  /**
   * One [[RestrictedResource]] per resource of `rows`, with its restricted parts nested under it, in the
   * SPARQL page order (label, then IRI).
   *
   * The resource's own visibility comes from its resource-level row if present, else it is fully visible. A
   * value typed with several file classes yields one row per class and is listed once per item type.
   */
  private def restrictedResources(rows: Seq[RestrictedObjectRow], projectIri: ProjectIri): Seq[RestrictedResource] =
    rows
      .groupBy(_.resourceIri)
      .toSeq
      .map { case (resourceIri, resourceRows) =>
        val restrictedItems = resourceRows
          .filter(_.itemType != ItemType.Resource)
          .distinctBy(r => (r.itemType, r.valueIri))
          .map(r =>
            RestrictedItem(
              `type` = r.itemType,
              propertyIri = r.propertyIri,
              propertyLabel = r.propertyLabel,
              valueIri = r.valueIri,
              visibility = ViewRestrictionsAudience.itemVisibility(r.creator, r.permissions, projectIri),
            ),
          )
        val resourceVisibility = resourceRows
          .find(_.itemType == ItemType.Resource)
          .map(r => ViewRestrictionsAudience.itemVisibility(r.creator, r.permissions, projectIri))
          .getOrElse(ItemVisibility(Visibility.Visible, Visibility.Visible, Visibility.Visible))
        RestrictedResource(
          resourceIri = resourceIri,
          label = resourceRows.head.resourceLabel,
          resourceClassIri = resourceRows.head.resourceClassIri,
          resourceVisibility = resourceVisibility,
          items = restrictedItems,
        )
      }
      .sortBy(r => (r.label, r.resourceIri))

  /** The drill-down branches of one class, reading only the cached rows the item type needs. */
  private def drillDownBranches(
    project: KnoraProject,
    resourceClass: String,
    itemType: ItemType,
  ): Task[Option[DrillDownBranches]] = {
    val resourceLiterals = rowCache
      .resourceCounts(project)
      .map(rows => restricted(rows.filter(_.classIri == resourceClass).map(_.permissions), project.id))
    val valueLiterals =
      classValueRows(project, resourceClass).map(rows => restricted(rows.map(_.permissions), project.id))
    itemType match {
      case ItemType.Resource                                 => resourceLiterals.map(DrillDownBranches.of(itemType, _, Set.empty))
      case ItemType.All                                      => resourceLiterals.zipPar(valueLiterals).map(DrillDownBranches.of(itemType, _, _))
      case ItemType.File | ItemType.Value | ItemType.Comment =>
        valueLiterals.map(DrillDownBranches.of(itemType, Set.empty, _))
    }
  }

  private def classValueRows(project: KnoraProject, resourceClass: String) =
    rowCache.valueCounts(project).map(_.filter(_.classIri == resourceClass))

  private def restricted(permissions: Seq[String], projectIri: ProjectIri): Set[String] =
    ViewRestrictionsAudience.restrictedLiterals(permissions.toSet, projectIri)
}

object ViewRestrictionsService {

  val layer = ZLayer.derive[ViewRestrictionsService]
}
