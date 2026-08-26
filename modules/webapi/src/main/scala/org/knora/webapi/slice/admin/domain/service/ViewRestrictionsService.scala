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
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.PermissionCountRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.RestrictedObjectRow
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.*

/**
 * Computes the "view restrictions" report for a project.
 *
 * Visibility is resolved with the real permission model: [[PermissionUtilADM.getUserPermissionADM]] is run
 * against a synthetic user for each of the three audiences (see [[ViewRestrictionsService.audienceUser]])
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
) {

  /**
   * Resolves each distinct permission literal against each audience, once.
   *
   * The literals recur across classes (the step-1 query groups by class *and* literal), so classifying
   * per row would repeat the same permission resolution many times over. The set is small either way —
   * literals come from project-level default-permission templates rather than being authored per object —
   * but resolving up front also makes the fold below a pure lookup.
   */
  private def classify(literals: Set[String], projectIri: ProjectIri): Map[(String, Audience), Visibility] =
    (for {
      literal  <- literals
      audience <- Audience.ordered
    } yield (literal, audience) -> visibilityOf(
      PermissionUtilADM.getUserPermissionADM(
        // The creator only matters when it equals the requesting user, which no synthetic audience user
        // can be (see audienceUser) — so the placeholder is equivalent to any real creator here.
        entityCreator = syntheticCreatorPlaceholder,
        entityProject = projectIri.value,
        entityPermissionLiteral = literal,
        requestingUser = audienceUser(audience, projectIri),
      ),
    )).toMap

  /**
   * Folds permission-grouped rows into per-audience counts, and the total object count across every
   * literal.
   *
   * The total is what replaces the separate population query: because the grouped query applies no
   * permission filter, summing a group's counts over all its literals — the fully visible ones included —
   * gives that group's entire population.
   */
  private def foldRows(
    rows: Seq[PermissionCountRow],
    lookup: Map[(String, Audience), Visibility],
  ): (AudienceRestrictionCounts, Int) =
    rows.foldLeft((AudienceRestrictionCounts.zero, 0)) { case ((counts, total), row) =>
      val next = Audience.ordered.foldLeft(counts) { (acc, audience) =>
        lookup.get((row.permissions, audience)) match {
          case Some(Visibility.Hidden) =>
            AudienceRestrictionCounts.add(acc, audience, RestrictionCounts(row.count, 0))
          case Some(Visibility.RestrictedView) =>
            AudienceRestrictionCounts.add(acc, audience, RestrictionCounts(0, row.count))
          // Fully visible is not a restriction, so it contributes to the population only.
          case _ => acc
        }
      }
      (next, total + row.count)
    }

  /**
   * Step 1 of the stepped report: every resource class with its population and its resource-level
   * restrictions, from a single query.
   */
  def classSummaries(projectIri: ProjectIri): Task[ViewRestrictionsClasses] =
    for {
      classes <- repo.projectClasses(projectIri)
      rows    <- repo.resourceCountsByClass(projectIri, classes)
      lookup   = classify(rows.map(_.permissions).toSet, projectIri)
      byClass  = rows.groupBy(_.groupId).collect { case (Some(classIri), rs) => classIri -> foldRows(rs, lookup) }
      // Report every class the project asserts, not just those that produced rows: a class holding no
      // resources at all is still a real row, with a zero population. When no class was discovered the
      // queries fall back to a subClassOf guard, so the row set is the only source of class IRIs.
      reported = (if (classes.iris.nonEmpty) classes.iris else byClass.keys.toSeq).map { classIri =>
                   val (counts, total) = byClass.getOrElse(classIri, (AudienceRestrictionCounts.zero, 0))
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
      classes <- repo.projectClasses(projectIri)
      rows    <- repo.valueCountsForClass(projectIri, resourceClass, ValueItemType.toItemType(itemType), classes)
      lookup   = classify(rows.map(_.permissions).toSet, projectIri)
      counts   = foldRows(rows, lookup)._1
    } yield ViewRestrictionsValues(projectIri.value, resourceClass, itemType, counts)

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

  /**
   * Cap on the summary's concurrent per-(audience, state) count queries.
   *
   * Each of the six can issue two triplestore queries (resources + values); four in flight keeps the
   * latency win without letting one dashboard request saturate the store's connections.
   */
  private[service] val MaxConcurrentCountQueries = 4

  val layer = ZLayer.derive[ViewRestrictionsService]
}
