/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.RestrictedObjectRow
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.*

/**
 * Computes the "view restrictions" report for a project (design screen 1h).
 *
 * Visibility is resolved with the real permission model: for each restriction-bearing object we run
 * [[PermissionUtilADM.getUserPermissionADM]] against a synthetic user for each of the three audiences
 * (see [[ViewRestrictionsService.audienceUser]]) and map the resulting permission code to a
 * [[Visibility]]. The summary counts hidden items only (a value that is not fully visible); restricted
 * view (code 1) is reported as its own state only for file values, where it is renderable (watermarked /
 * reduced image) — on resources, ordinary values and comments code 1 collapses to Hidden.
 *
 * NOTE (v1, pending the DEV-6777 feasibility spike): counts are computed by scanning the
 * restriction-bearing objects the repo query returns. On very large projects this is unbounded — the
 * large-project guardrail (query timeout / hard cap / "approximate" flag) is deliberately left as a
 * follow-up; see `docs/prd/2026-07-28-view-restrictions-api.md` §7/§9. Property-first grouping and the
 * `restrictedView` state for image file values are also refined once the spike fixes the query shape.
 */
final case class ViewRestrictionsService(
  private val repo: ViewRestrictionsRepo,
) {

  /**
   * A synthetic, non-admin [[User]] standing in for one audience, suitable to feed
   * [[PermissionUtilADM.getUserPermissionADM]] for the given project.
   *
   *   - [[Audience.Anonymous]]     → the built-in anonymous user (resolves as `{ UnknownUser }`).
   *   - [[Audience.Authenticated]] → a known user with no groups in the project (`{ KnownUser }`).
   *   - [[Audience.ProjectMember]] → a known user that is a `ProjectMember` of the project.
   *
   * Must never be a system/project admin, or `getUserPermissionADM` short-circuits to max permission.
   */
  def audienceUser(audience: Audience, projectIri: ProjectIri): User =
    audience match {
      case Audience.Anonymous     => KnoraSystemInstances.Users.AnonymousUser
      case Audience.Authenticated =>
        knownUser(groupsPerProject = Map.empty)
      case Audience.ProjectMember =>
        knownUser(groupsPerProject = Map(projectIri.value -> Seq(KnoraGroupRepo.builtIn.ProjectMember.id.value)))
    }

  private def knownUser(groupsPerProject: Map[IRI, Seq[IRI]]): User =
    User(
      id = "http://rdfh.ch/users/view-restrictions-audience",
      username = "view-restrictions-audience",
      email = "",
      givenName = "",
      familyName = "",
      status = true,
      lang = "en",
      permissions = PermissionsDataADM(groupsPerProject = groupsPerProject),
    )

  /**
   * Map a resolved object-access permission (or the absence of one) to a [[Visibility]].
   *
   * `rvEligible` gates the RestrictedView (code 1) state: restricted view means a watermarked / reduced
   * image and is only renderable for file values. For resources, ordinary values and comments there is no
   * partial-view rendering, so code 1 collapses to [[Visibility.Hidden]] (the object is simply not fully
   * visible to that audience) rather than showing a misleading "restricted view" state.
   */
  def visibilityOf(permission: Option[Permission.ObjectAccess], rvEligible: Boolean): Visibility =
    permission.map(_.code).getOrElse(0) match {
      case 0                                                                   => Visibility.Hidden
      case c if c == Permission.ObjectAccess.RestrictedView.code && rvEligible => Visibility.RestrictedView
      case c if c == Permission.ObjectAccess.RestrictedView.code               => Visibility.Hidden
      case _                                                                   => Visibility.Visible
    }

  private def visibilityFor(row: RestrictedObjectRow, audience: Audience, projectIri: ProjectIri): Visibility =
    visibilityOf(
      PermissionUtilADM.getUserPermissionADM(
        entityCreator = row.creator,
        entityProject = projectIri.value,
        entityPermissionLiteral = row.permissions,
        requestingUser = audienceUser(audience, projectIri),
      ),
      // Only file values can be served in restricted view; RV on anything else means "not fully visible".
      rvEligible = row.itemType == ItemType.File,
    )

  private def itemVisibility(row: RestrictedObjectRow, projectIri: ProjectIri): ItemVisibility =
    ItemVisibility(
      anonymous = visibilityFor(row, Audience.Anonymous, projectIri),
      authenticated = visibilityFor(row, Audience.Authenticated, projectIri),
      projectMember = visibilityFor(row, Audience.ProjectMember, projectIri),
    )

  /** Add 1 to each audience count that sees the item as [[Visibility.Hidden]] (the summary semantics). */
  private def addHidden(acc: AudienceCounts, vis: ItemVisibility): AudienceCounts =
    AudienceCounts(
      anonymous = acc.anonymous + (if (vis.anonymous == Visibility.Hidden) 1 else 0),
      authenticated = acc.authenticated + (if (vis.authenticated == Visibility.Hidden) 1 else 0),
      projectMember = acc.projectMember + (if (vis.projectMember == Visibility.Hidden) 1 else 0),
    )

  private def plus(a: AudienceCounts, b: AudienceCounts): AudienceCounts =
    AudienceCounts(a.anonymous + b.anonymous, a.authenticated + b.authenticated, a.projectMember + b.projectMember)

  def summary(projectIri: ProjectIri, groupBy: GroupBy, itemType: ItemType): Task[ViewRestrictionsSummary] =
    for {
      result     <- repo.findRestrictedObjects(projectIri, groupBy, itemType)
      rows        = result.rows
      approximate = result.capped
      // A comment is a facet of its value (same object, same permissions), so under itemType=All it must
      // not be counted separately from its Value row — only count Comment rows when Comment is the filter.
      countable = rows.filter(r => itemType == ItemType.Comment || r.itemType != ItemType.Comment)
      // Group rows by their grouping key (class IRI, or property IRI in property mode).
      grouped = countable.groupBy(_.groupId)
      groups  = grouped.toSeq.map { case (groupId, groupRows) =>
                 val counts = groupRows.foldLeft(AudienceCounts.zero) { (acc, row) =>
                   addHidden(acc, itemVisibility(row, projectIri))
                 }
                 val head = groupRows.head
                 RestrictionGroup(
                   id = groupId,
                   label = head.groupLabel,
                   ontology = head.ontology,
                   propertyName = head.propertyName,
                   counts = counts,
                 )
               }
                 // Drop groups with no hidden items under the active filter…
                 .filter(g => g.counts.anonymous + g.counts.authenticated + g.counts.projectMember > 0)
                 // …and order by descending anonymous-hidden count (matches the 1h matrix ordering).
                 .sortBy(g => (-g.counts.anonymous, g.label))
      totals = groups.map(_.counts).foldLeft(AudienceCounts.zero)(plus)
    } yield ViewRestrictionsSummary(projectIri.value, groupBy, itemType, groups, totals, approximate)

  def items(
    projectIri: ProjectIri,
    groupBy: GroupBy,
    group: String,
    itemType: ItemType,
    pageAndSize: PageAndSize,
  ): Task[PagedResponse[RestrictedResource]] =
    for {
      result <- repo.findRestrictedObjects(projectIri, groupBy, itemType, group = Some(group))
      rows    = result.rows
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
      ordered   = byResource.sortBy(_.label)
      total     = ordered.size
      pageSlice = ordered.slice(pageAndSize.size * (pageAndSize.page - 1), pageAndSize.size * pageAndSize.page)
    } yield PagedResponse.from(pageSlice, total, pageAndSize)
}

object ViewRestrictionsService {
  val layer = ZLayer.derive[ViewRestrictionsService]
}
