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
import org.knora.webapi.slice.admin.repo.ViewRestrictionsByPropertyRepo
import org.knora.webapi.slice.admin.repo.ViewRestrictionsByPropertyRepo.RestrictedPropertyValueRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.PermissionCountRow
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.api.admin.ViewRestrictionsByPropertyEndpoints.*
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.Audience
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.AudienceRestrictionCounts
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ItemVisibility
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.RestrictionCounts
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ValueItemType
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.Visibility

/**
 * Computes the view-restrictions report **grouped by property**.
 *
 * A sibling of [[ViewRestrictionsService]], not a mode of it. The permission machinery below —
 * [[audienceUser]], [[visibilityOf]], [[syntheticCreatorPlaceholder]] and the fold — is deliberately
 * duplicated rather than shared, because the alternative is the `groupBy` seam this split exists to remove.
 *
 * That duplication has a cost worth naming: the soundness argument is subtle, so it must hold in two
 * places. For the three synthetic audiences an object's visibility depends **only** on its
 * `knora-base:hasPermissions` literal, because
 *
 *   - `entityProject` is constant per request;
 *   - `requestingUser` is one of three synthetic users, none of them system/project admin, so
 *     `getUserPermissionADM`'s max-permission short-circuit never fires;
 *   - `entityCreator` matters only when it equals the requesting user, which a synthetic audience user
 *     never is.
 *
 * `ViewRestrictionsByPropertyServiceSpec` pins that equivalence for *this* service, as
 * `ViewRestrictionsServiceSpec` does for the class one. A divergence between the two should fail a test
 * rather than quietly change the numbers on one screen.
 *
 * One unit only. A property has no resource population of its own, so unlike the class report there is no
 * second unit to keep apart — every figure here counts values of one property, and `totalValues` is the
 * denominator they share.
 */
final case class ViewRestrictionsByPropertyService(
  private val repo: ViewRestrictionsByPropertyRepo,
) {

  /** See the class doc: a value no synthetic audience user can take, so the `Creator` branch never fires. */
  private val syntheticCreatorPlaceholder = "urn:view-restrictions-by-property:no-such-creator"

  /**
   * A synthetic, non-admin [[User]] standing in for one audience.
   *
   * Must never be a system/project admin, or `getUserPermissionADM` short-circuits to max permission.
   */
  def audienceUser(audience: Audience, projectIri: ProjectIri): User =
    audience match {
      case Audience.Anonymous     => KnoraSystemInstances.Users.AnonymousUser
      case Audience.Authenticated => knownUser(groupsPerProject = Map.empty)
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

  /** Maps a resolved object-access permission to a reported [[Visibility]]. */
  def visibilityOf(permission: Option[Permission.ObjectAccess]): Visibility =
    permission.map(_.code).getOrElse(0) match {
      case 0                                                     => Visibility.Hidden
      case c if c == Permission.ObjectAccess.RestrictedView.code => Visibility.RestrictedView
      case _                                                     => Visibility.Visible
    }

  /** Resolves each distinct permission literal against each audience, once, so the fold is a lookup. */
  private def classify(literals: Set[String], projectIri: ProjectIri): Map[(String, Audience), Visibility] =
    (for {
      literal  <- literals
      audience <- Audience.ordered
    } yield (literal, audience) -> visibilityOf(
      PermissionUtilADM.getUserPermissionADM(
        entityCreator = syntheticCreatorPlaceholder,
        entityProject = projectIri.value,
        entityPermissionLiteral = literal,
        requestingUser = audienceUser(audience, projectIri),
      ),
    )).toMap

  /**
   * Folds permission-grouped rows into per-audience counts plus the property's whole value population.
   *
   * The total is what makes a separate population query unnecessary: the grouped query applies no
   * permission filter, so summing over every literal — the fully visible ones included — is the population.
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
          // Fully visible is not a restriction; it contributes to the population only.
          case _ => acc
        }
      }
      (next, total + row.count)
    }

  /** Step 1: the project's value properties, from the ontology cache. No counts, and no SPARQL. */
  def properties(projectIri: ProjectIri): Task[ViewRestrictionsProperties] =
    repo
      .projectValueProperties(projectIri)
      .map(rows =>
        ViewRestrictionsProperties(projectIri.value, rows.map(r => RestrictedProperty(r.id, r.label, r.ontology))),
      )

  /** Step 2: one property's value counts and its population, from a single grouped query. */
  def propertyValues(
    projectIri: ProjectIri,
    property: String,
    itemType: ValueItemType,
  ): Task[ViewRestrictionsPropertyValues] =
    for {
      rows       <- repo.valueCountsForProperty(projectIri, property, itemType)
      lookup      = classify(rows.map(_.permissions).toSet, projectIri)
      countsTotal = foldRows(rows, lookup)
    } yield ViewRestrictionsPropertyValues(
      projectIri = projectIri.value,
      property = property,
      itemType = itemType,
      totalValues = countsTotal._2,
      counts = countsTotal._1,
    )

  /**
   * Step 3: the resources carrying a restricted value of the property, paginated.
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
      // The page and its total are independent reads, so they go out together. They are separate
      // transactions rather than a snapshot: a concurrent write between them can leave `totalItems`
      // inconsistent with the rows, exactly as in the class drill-down.
      totalAndRows <- repo
                        .countRestrictedResources(projectIri, property, itemType)
                        .zipPar(
                          repo.findRestrictedResources(
                            projectIri,
                            property,
                            itemType,
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
                    resourceClassIri = head.resourceClassIri,
                    values = resourceRows.map(r =>
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
    visibilityOf(
      PermissionUtilADM.getUserPermissionADM(
        entityCreator = row.creator,
        entityProject = projectIri.value,
        entityPermissionLiteral = row.permissions,
        requestingUser = audienceUser(audience, projectIri),
      ),
    )
}

object ViewRestrictionsByPropertyService {
  val layer = ZLayer.derive[ViewRestrictionsByPropertyService]
}
