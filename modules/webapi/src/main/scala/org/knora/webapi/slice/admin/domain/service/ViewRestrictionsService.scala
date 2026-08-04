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
 * [[Visibility]]. This is a reporting view of what is stored, not a rendering decision: the summary counts
 * hidden items only (code 0), while restricted view (code 1) is reported as its own state for every item
 * type — resources, ordinary values, comments and any file value — so the dashboard reflects the actual
 * permissions in the triplestore.
 *
 * COUNTING: the summary counts are computed by the triplestore, not by scanning objects here. That is sound
 * because for the three synthetic audiences the visibility of an object depends **only** on its
 * `knora-base:hasPermissions` literal:
 *
 *   - `entityProject` is constant per request;
 *   - `requestingUser` is one of three synthetic users, none of them system/project admin (so
 *     `getUserPermissionADM`'s max-permission short-circuit never fires);
 *   - `entityCreator` matters only when it equals the requesting user, which a synthetic audience user never
 *     is — so the `Creator` group never applies, and a `CR knora-admin:Creator` clause grants the audiences
 *     nothing (the drill-down, resolving the real creator, reaches the same conclusion for the same reason).
 *
 * So we ask the repo for the project's *distinct* permission literals (a small set — literals come from
 * project-level default-permission templates, not per-object authoring), classify those few with the real
 * permission model, and let SPARQL `COUNT` the objects whose literal is in the hidden subset. Counts are
 * therefore exact at any project size. The drill-down is paged in SPARQL with a matching `COUNT`, so its
 * `totalItems` is exact and its page order is stable.
 */
final case class ViewRestrictionsService(
  private val repo: ViewRestrictionsRepo,
) {

  /**
   * Stands in for `entityCreator` when classifying a bare permission literal.
   *
   * `getUserPermissionADM` consults the creator for exactly one thing: whether the requesting user *is* the
   * creator, which adds the `knora-admin:Creator` group. This placeholder is deliberately a value no real
   * `attachedToUser` and no [[audienceUser]] id can take, so that branch never fires — making the
   * classification a pure function of the permission literal, which is what lets the counts be aggregated
   * in SPARQL. `ViewRestrictionsServiceSpec` pins the equivalence.
   *
   * Consequence, and it is the right one for a report: a `CR knora-admin:Creator` clause grants nothing to
   * any of the three audiences, because none of them is the creator of an arbitrary object. That matches
   * how the drill-down resolves the same object with its real creator, since a synthetic audience user is
   * never the creator either.
   */
  private val syntheticCreatorPlaceholder = "urn:view-restrictions:no-such-creator"

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
   * This is a **reporting** view of what is stored in the triplestore, not a rendering decision: the
   * dashboard shows the admin the actual permission on each object. So permission code 1 is reported as
   * [[Visibility.RestrictedView]] for every item type — resources, ordinary values, comments and any file
   * value (restricted view can be set on any object, whether or not it is meaningfully renderable there).
   */
  def visibilityOf(permission: Option[Permission.ObjectAccess]): Visibility =
    permission.map(_.code).getOrElse(0) match {
      case 0                                                     => Visibility.Hidden
      case c if c == Permission.ObjectAccess.RestrictedView.code => Visibility.RestrictedView
      case _                                                     => Visibility.Visible
    }

  private def visibilityFor(row: RestrictedObjectRow, audience: Audience, projectIri: ProjectIri): Visibility =
    visibilityOf(
      PermissionUtilADM.getUserPermissionADM(
        entityCreator = row.creator,
        entityProject = projectIri.value,
        entityPermissionLiteral = row.permissions,
        requestingUser = audienceUser(audience, projectIri),
      ),
    )

  private def itemVisibility(row: RestrictedObjectRow, projectIri: ProjectIri): ItemVisibility =
    ItemVisibility(
      anonymous = visibilityFor(row, Audience.Anonymous, projectIri),
      authenticated = visibilityFor(row, Audience.Authenticated, projectIri),
      projectMember = visibilityFor(row, Audience.ProjectMember, projectIri),
    )

  private def plus(a: AudienceCounts, b: AudienceCounts): AudienceCounts =
    AudienceCounts(a.anonymous + b.anonymous, a.authenticated + b.authenticated, a.projectMember + b.projectMember)

  /**
   * Which of the project's distinct permission literals are [[Visibility.Hidden]] for the given audience.
   *
   * This is the step that makes exact counting affordable: the visibility of an object depends only on its
   * permission literal (see the class doc), so classifying the handful of distinct literals is equivalent
   * to classifying every object — and then the triplestore can do the counting.
   */
  private def hiddenLiteralsFor(
    literals: Set[String],
    audience: Audience,
    projectIri: ProjectIri,
  ): Set[String] =
    literals.filter { literal =>
      visibilityOf(
        PermissionUtilADM.getUserPermissionADM(
          // The creator only matters when it equals the requesting user, which no synthetic audience user
          // can be (see audienceUser) — so any placeholder is equivalent here.
          entityCreator = syntheticCreatorPlaceholder,
          entityProject = projectIri.value,
          entityPermissionLiteral = literal,
          requestingUser = audienceUser(audience, projectIri),
        ),
      ) == Visibility.Hidden
    }

  def summary(projectIri: ProjectIri, groupBy: GroupBy, itemType: ItemType): Task[ViewRestrictionsSummary] =
    for {
      literals <- repo.distinctPermissions(projectIri, itemType, groupBy)
      // Classify the small distinct-literal set once per audience, then let the triplestore count.
      hiddenPerAudience  = Audience.ordered.map(a => a -> hiddenLiteralsFor(literals, a, projectIri)).toMap
      countsPerAudience <- ZIO.foreach(Audience.ordered) { audience =>
                             repo
                               .countByGroup(projectIri, groupBy, itemType, hiddenPerAudience(audience))
                               .map(rows => audience -> rows)
                           }
      // groupId -> per-audience counts, summing the resource and value contributions of the same group.
      byGroup = countsPerAudience.foldLeft(Map.empty[String, AudienceCounts]) { case (acc, (audience, rows)) =>
                  rows.foldLeft(acc) { (inner, row) =>
                    val current = inner.getOrElse(row.groupId, AudienceCounts.zero)
                    val delta   = audience match {
                      case Audience.Anonymous     => AudienceCounts(row.count, 0, 0)
                      case Audience.Authenticated => AudienceCounts(0, row.count, 0)
                      case Audience.ProjectMember => AudienceCounts(0, 0, row.count)
                    }
                    inner.updated(row.groupId, plus(current, delta))
                  }
                }
      groups = byGroup.toSeq.map { case (groupId, counts) => restrictionGroup(groupId, groupBy, counts) }
                 // Drop groups with no hidden items under the active filter…
                 .filter(g => g.counts.anonymous + g.counts.authenticated + g.counts.projectMember > 0)
                 // …and order by descending anonymous-hidden count (matches the 1h matrix ordering).
                 .sortBy(g => (-g.counts.anonymous, g.label))
      totals = groups.map(_.counts).foldLeft(AudienceCounts.zero)(plus)
      // Counts come from SPARQL aggregation over the whole project, so they are always exact.
    } yield ViewRestrictionsSummary(projectIri.value, groupBy, itemType, groups, totals)

  /** Build a summary row. Labels are derived from the grouping IRI, as elsewhere in this v1 report. */
  private def restrictionGroup(groupId: String, groupBy: GroupBy, counts: AudienceCounts): RestrictionGroup =
    RestrictionGroup(
      id = groupId,
      label = localName(groupId),
      ontology = Some(ontologyName(groupId)),
      propertyName = Option.when(groupBy == GroupBy.Property)(localName(groupId)),
      counts = counts,
    )

  private def localName(iri: String): String    = iri.split(Array('#', '/')).lastOption.getOrElse(iri)
  private def ontologyName(iri: String): String = {
    val beforeHash = iri.split('#').headOption.getOrElse(iri)
    beforeHash.split('/').lastOption.getOrElse(beforeHash)
  }

  def items(
    projectIri: ProjectIri,
    groupBy: GroupBy,
    group: String,
    itemType: ItemType,
    pageAndSize: PageAndSize,
  ): Task[PagedResponse[RestrictedResource]] =
    for {
      // Paging happens in SPARQL: `rows` are already exactly the resources of the requested page, and
      // `total` is an exact COUNT over the whole result set — no scan cap, no Scala-side slicing.
      total <- repo.countRestrictedResources(projectIri, groupBy, itemType, group)
      rows  <- repo.findRestrictedObjects(
                projectIri,
                groupBy,
                itemType,
                group,
                offset = pageAndSize.size * (pageAndSize.page - 1),
                limit = pageAndSize.size,
              )
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
