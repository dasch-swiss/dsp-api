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
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsClassification.ProjectOntologyIndex
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsClassification.ValueKind
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.PermissionCountRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ValueCountRow
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.*

/**
 * Pure folds from counted rows to per-audience restriction counts, and the permission classification they
 * share.
 *
 * Visibility is resolved with the real permission model: [[PermissionUtilADM.getUserPermissionADM]] is run
 * against a synthetic user for each of the three audiences (see [[audienceUser]]) and the resulting
 * permission code mapped to a [[Visibility]].
 *
 * COUNTING: counts are computed by the triplestore, grouped by permission literal, not by scanning objects
 * here. That is sound because for the three synthetic audiences the visibility of an object depends
 * **only** on its `knora-base:hasPermissions` literal:
 *
 *   - `entityProject` is constant per request;
 *   - `requestingUser` is one of three synthetic users, none of them system/project admin (so
 *     `getUserPermissionADM`'s max-permission short-circuit never fires);
 *   - `entityCreator` matters only when it equals the requesting user, which a synthetic audience user never
 *     is. So the `Creator` group never applies, and a `CR knora-admin:Creator` clause grants the audiences
 *     nothing (the drill-down, resolving the real creator, reaches the same conclusion for the same reason).
 *
 * The handful of distinct literals are classified here, once each, so one query answers all three audiences
 * and both restriction states at once.
 */
object ValueCountFold {

  /**
   * Counts value rows per permission literal, for one item type.
   *
   * Rows of deleted values and rows of properties that are not counted (link targets, metadata, standoff
   * links, unknown properties) are dropped. Returns the counted rows, grouped by literal with zero-count
   * literals omitted, and the property IRIs that are not in the ontologies at all.
   */
  def permissionCounts(
    rows: Seq[ValueCountRow],
    index: ProjectOntologyIndex,
    itemType: ValueItemType,
  ): (Seq[PermissionCountRow], Set[String]) = {
    val live    = rows.filterNot(_.deleted)
    val counted = live.flatMap { r =>
      index.valueProperties.get(r.propertyIri).map(kind => r.permissions -> contribution(kind, r, itemType))
    }
    val perLiteral = counted.groupMapReduce(_._1)(_._2)(_ + _)
    val result     = perLiteral.collect { case (literal, n) if n > 0 => PermissionCountRow(None, literal, n) }.toSeq
    val unknown    = live.map(_.propertyIri).filterNot(index.knownProperties.contains).toSet
    (result, unknown)
  }

  private def contribution(kind: ValueKind, row: ValueCountRow, itemType: ValueItemType): Int =
    itemType match {
      case ValueItemType.All     => row.count
      case ValueItemType.File    => if kind == ValueKind.File then row.count else 0
      case ValueItemType.Value   => if kind != ValueKind.File then row.count else 0
      case ValueItemType.Comment => row.commented
    }

  /**
   * Folds value-count rows into per-audience counts and the total number of counted values.
   *
   * Logs one warning, naming the properties, when rows mention properties the index does not know.
   */
  def fold(
    rows: Seq[ValueCountRow],
    index: ProjectOntologyIndex,
    itemType: ValueItemType,
    projectIri: ProjectIri,
  ): UIO[(AudienceRestrictionCounts, Int)] = {
    val (counted, unknown) = permissionCounts(rows, index, itemType)
    val lookup             = classify(counted.map(_.permissions).toSet, projectIri)
    ZIO
      .logWarning(
        s"View restrictions: ignored values of properties not in the ontology cache:${unknown.toSeq.sorted.mkString(", ")}",
      )
      .when(unknown.nonEmpty)
      .as(foldRows(counted, lookup))
  }

  /**
   * Resolves each distinct permission literal against each audience, once.
   *
   * The literals recur across classes, so classifying per row would repeat the same permission resolution
   * many times over. The set is small either way, since literals come from project-level default-permission
   * templates rather than being authored per object. Resolving up front also makes [[foldRows]] a pure
   * lookup.
   */
  def classify(literals: Set[String], projectIri: ProjectIri): Map[(String, Audience), Visibility] =
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
   * Folds permission-grouped rows into per-audience counts, and the total object count across every
   * literal.
   *
   * Because the grouped query applies no permission filter, summing a group's counts over all its literals,
   * the fully visible ones included, gives that group's entire population.
   */
  def foldRows(
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
   * Stands in for `entityCreator` when classifying a bare permission literal.
   *
   * `getUserPermissionADM` consults the creator for exactly one thing: whether the requesting user *is* the
   * creator, which adds the `knora-admin:Creator` group. This placeholder is deliberately a value no real
   * `attachedToUser` and no [[audienceUser]] id can take, so that branch never fires. That makes the
   * classification a pure function of the permission literal, which is what lets the counts be aggregated
   * in SPARQL.
   *
   * Consequence, and it is the right one for a report: a `CR knora-admin:Creator` clause grants nothing to
   * any of the three audiences, because none of them is the creator of an arbitrary object. That matches
   * how the drill-down resolves the same object with its real creator.
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

  /**
   * Map a resolved object-access permission (or the absence of one) to a [[Visibility]].
   *
   * This is a **reporting** view of what is stored in the triplestore, not a rendering decision: the
   * dashboard shows the admin the actual permission on each object. So permission code 1 is reported as
   * [[Visibility.RestrictedView]] for every item type, whether or not it is meaningfully renderable there.
   */
  def visibilityOf(permission: Option[Permission.ObjectAccess]): Visibility =
    permission.map(_.code).getOrElse(0) match {
      case 0                                                     => Visibility.Hidden
      case c if c == Permission.ObjectAccess.RestrictedView.code => Visibility.RestrictedView
      case _                                                     => Visibility.Visible
    }
}
