/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.*

/**
 * Resolves a permission literal to a [[Visibility]] for each of the three audiences, using the real
 * permission model: [[PermissionUtilADM.getUserPermissionADM]] is run against a synthetic user per audience
 * (see [[audienceUser]]) and the resulting permission code mapped to a [[Visibility]].
 *
 * That lets counts be computed by the triplestore, grouped by permission literal, instead of scanning
 * objects. It is sound because for these audiences the visibility of an object depends **only** on its
 * `knora-base:hasPermissions` literal:
 *
 *   - `entityProject` is constant per request;
 *   - `requestingUser` is one of three synthetic users, none of them system/project admin (so
 *     `getUserPermissionADM`'s max-permission short-circuit never fires);
 *   - `entityCreator` matters only for whether the requesting user *is* the creator, which a synthetic
 *     audience user never is. So the `Creator` group never applies, a `CR knora-admin:Creator` clause grants
 *     the audiences nothing, and a bare literal can be classified against [[syntheticCreatorPlaceholder]].
 *     The drill-down, resolving each object's real creator, reaches the same answer for the same reason.
 */
object ViewRestrictionsAudience {

  /**
   * Resolves each distinct permission literal against each audience, once.
   *
   * The literals recur across classes, so classifying per row would repeat the same permission resolution
   * many times over. The set is small either way, since literals come from project-level default-permission
   * templates rather than being authored per object. Resolving up front also makes [[ValueCountFold.foldRows]] a pure
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
   * Stands in for `entityCreator` when classifying a bare permission literal: a value no real
   * `attachedToUser` and no [[audienceUser]] id can take.
   */
  private[service] val syntheticCreatorPlaceholder = "urn:view-restrictions:no-such-creator"

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
