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
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.CountUnit
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.GroupCountRow
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
 * [[Visibility]]. This is a reporting view of what is stored, not a rendering decision: restricted view
 * (code 1) is reported as its own state for every item type — resources, ordinary values, comments and any
 * file value — so the dashboard reflects the actual permissions in the triplestore.
 *
 * The summary counts both non-visible states, separately: `hidden` (code 0, nothing is served) and
 * `restrictedView` (code 1, a degraded version is served). They are disjoint, so their sum is the number of
 * items an audience cannot fully see. Reporting a single conflated number would hide code-1 items behind a
 * label that says "hidden".
 *
 * Each state is further split by UNIT — restricted whole resources vs restricted values (see
 * [[org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.CountUnit]]). Resources and values are counted by
 * separate queries over separate row sets, so adding them yields a number in no unit at all: one resource
 * with three hidden values would report 4, which can exceed the class's entire resource population. Keeping
 * them apart is what makes `resources` comparable to `totalResources`.
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
    AudienceCounts(
      UnitCounts.plus(a.anonymous, b.anonymous),
      UnitCounts.plus(a.authenticated, b.authenticated),
      UnitCounts.plus(a.projectMember, b.projectMember),
    )

  /**
   * Which of the project's distinct permission literals resolve to `state` for the given audience.
   *
   * This is the step that makes exact counting affordable: the visibility of an object depends only on its
   * permission literal (see the class doc), so classifying the handful of distinct literals is equivalent
   * to classifying every object — and then the triplestore can do the counting.
   */
  private def literalsResolvingTo(
    literals: Set[String],
    state: Visibility,
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
      ) == state
    }

  /** The two states the summary reports, each counted separately. [[Visibility.Visible]] is not restricted. */
  private val countedStates: Seq[Visibility] = Seq(Visibility.Hidden, Visibility.RestrictedView)

  def summary(projectIri: ProjectIri, groupBy: GroupBy, itemType: ItemType): Task[ViewRestrictionsSummary] =
    for {
      // Resolved once and reused by every query below: the project's asserted resource classes, which
      // replace two per-row `rdfs:subClassOf` traversals in the queries themselves.
      classes  <- repo.projectClasses(projectIri)
      literals <- repo.distinctPermissions(projectIri, itemType, groupBy, classes)
      // One count per (audience, state). Both are tiny fixed sets — 3 x 2 — and each classification pass
      // runs over the small distinct-literal set, so this stays cheap regardless of project size.
      //
      // Issued in parallel, together with the class population below: the six counts are independent
      // read-only aggregations over disjoint permission literals, so serialising them just added up their
      // latencies (the summary was ~17 sequential round-trips).
      //
      // Bounded on purpose: each count can itself issue two queries (resources + values), so an unbounded
      // fan-out would put ~13 concurrent queries per request on the triplestore, multiplied across
      // concurrent dashboard users. The cap keeps most of the win while leaving the store backpressure.
      countedAndTotals <-
        ZIO
          .foreachPar(for (a <- Audience.ordered; s <- countedStates) yield (a, s)) { case (audience, state) =>
            val hits = literalsResolvingTo(literals, state, audience, projectIri)
            repo.countByGroup(projectIri, groupBy, itemType, hits, classes).map(rows => (audience, state, rows))
          }
          .withParallelism(ViewRestrictionsService.MaxConcurrentCountQueries)
          // Every resource class the project has, with its population — independent of any restriction and
          // of the itemType filter. In class mode this is the row set itself (see below), so a class is
          // reported with its true size whether or not anything in it is restricted. A property has no
          // resource population of its own, so property mode has no equivalent and reports none.
          .zipPar(
            if (groupBy == GroupBy.ResourceClass) repo.totalResourcesByClass(projectIri, classes)
            else ZIO.succeed(Seq.empty),
          )
      (counted, classTotals) = countedAndTotals
      // groupId -> per-audience counts. Contributions accumulate per unit, so a group's resource count and
      // its value count stay separate all the way to the response.
      byGroup = counted.foldLeft(Map.empty[String, AudienceCounts]) { case (acc, (audience, state, rows)) =>
                  rows.foldLeft(acc) { (inner, row) =>
                    val current = inner.getOrElse(row.groupId, AudienceCounts.zero)
                    inner.updated(row.groupId, plus(current, delta(audience, state, row)))
                  }
                }
      groups =
        if (groupBy == GroupBy.ResourceClass)
          // Class mode: one row per class in the project, most-restricted first and the unrestricted
          // remainder alphabetically after. Driven by classTotals rather than by the restriction rows,
          // because a class with nothing restricted still has a resource count and must still be reported.
          classTotals
            .map(row =>
              restrictionGroup(
                row.groupId,
                groupBy,
                byGroup.getOrElse(row.groupId, AudienceCounts.zero),
                Some(row.count),
              ),
            )
            .sortBy(orderKey)
        else
          // Property mode: only properties that actually carry a restriction — an unrestricted property has
          // no count and no population, so a row for it would be empty in every column.
          byGroup.toSeq.map { case (groupId, counts) => restrictionGroup(groupId, groupBy, counts, None) }
            .filter(g =>
              g.counts.anonymous.anyRestriction + g.counts.authenticated.anyRestriction +
                g.counts.projectMember.anyRestriction > 0,
            )
            .sortBy(orderKey)
      totals = groups.map(_.counts).foldLeft(AudienceCounts.zero)(plus)
      // Counts come from SPARQL aggregation over the whole project, so they are always exact.
    } yield ViewRestrictionsSummary(projectIri.value, groupBy, itemType, groups, totals)

  /**
   * Place `count` in the right audience slot, the right state within it, and the right unit within that.
   *
   * The unit comes from the repo row rather than being inferred here, which is what keeps resource counts
   * and value counts from being added into one meaningless number.
   */
  private def delta(audience: Audience, state: Visibility, row: GroupCountRow): AudienceCounts = {
    val c    = if (state == Visibility.Hidden) RestrictionCounts(row.count, 0) else RestrictionCounts(0, row.count)
    val unit = row.unit match {
      case CountUnit.Resources => UnitCounts(c, RestrictionCounts.zero)
      case CountUnit.Items     => UnitCounts(RestrictionCounts.zero, c)
    }
    audience match {
      case Audience.Anonymous     => AudienceCounts(unit, UnitCounts.zero, UnitCounts.zero)
      case Audience.Authenticated => AudienceCounts(UnitCounts.zero, unit, UnitCounts.zero)
      case Audience.ProjectMember => AudienceCounts(UnitCounts.zero, UnitCounts.zero, unit)
    }
  }

  /**
   * Row ordering key: most-restricted first, by the anonymous audience.
   *
   * Sorts on hidden resources first, then hidden items, then the restricted-view pair, so a class where
   * whole resources are hidden outranks one where only a few fields are — the more serious finding leads.
   */
  private def orderKey(g: RestrictionGroup): (Int, Int, Int, Int, String) =
    (
      -g.counts.anonymous.resources.hidden,
      -g.counts.anonymous.items.hidden,
      -g.counts.anonymous.resources.restrictedView,
      -g.counts.anonymous.items.restrictedView,
      g.label,
    )

  /**
   * Build a summary row. Labels are derived from the grouping IRI, as elsewhere in this v1 report.
   *
   * `totalResources` is supplied by the caller: the class's population in class mode, `None` in property
   * mode, where there is no resource population to report.
   */
  private def restrictionGroup(
    groupId: String,
    groupBy: GroupBy,
    counts: AudienceCounts,
    totalResources: Option[Int],
  ): RestrictionGroup =
    RestrictionGroup(
      id = groupId,
      label = localName(groupId),
      ontology = Some(ontologyName(groupId)),
      propertyName = Option.when(groupBy == GroupBy.Property)(localName(groupId)),
      counts = counts,
      totalResources = totalResources,
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
      classes <- repo.projectClasses(projectIri)
      // The page total and the page itself are independent reads, so they go out together rather than one
      // after the other. They are separate transactions, not a snapshot: a concurrent write between them
      // can leave `totalItems` inconsistent with the rows — as it could before, when they ran in sequence.
      totalAndRows <- repo
                        .countRestrictedResources(projectIri, groupBy, itemType, group, classes)
                        .zipPar(
                          repo.findRestrictedObjects(
                            projectIri,
                            groupBy,
                            itemType,
                            group,
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

  /**
   * Cap on the summary's concurrent per-(audience, state) count queries.
   *
   * Each of the six can issue two triplestore queries (resources + values); four in flight keeps the
   * latency win without letting one dashboard request saturate the store's connections.
   */
  private[service] val MaxConcurrentCountQueries = 4

  val layer = ZLayer.derive[ViewRestrictionsService]
}
