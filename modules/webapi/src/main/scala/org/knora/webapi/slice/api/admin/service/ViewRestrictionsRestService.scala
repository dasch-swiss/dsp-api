/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin.service

import zio.Task
import zio.ZIO

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsService
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.*
import org.knora.webapi.slice.common.api.AuthorizationRestService

/**
 * Application service for the "view restrictions" report (design screen 1h).
 *
 * Authorization: both routes require the requesting user to be **project admin on the target
 * project or system admin** (`auth.ensureSystemAdminOrProjectAdminById`), matching the 1h header.
 *
 * Note (AC5): a well-formed but non-existent `projectIri` yields 403, not 404 — deliberately, because
 * `ensureSystemAdminOrProjectAdminById` is the shared admin-API authorization gate and every admin route
 * behaves this way (it does not leak project existence to non-admins). Diverging only here would be
 * inconsistent, so we keep the convention.
 */
final case class ViewRestrictionsRestService(
  private val service: ViewRestrictionsService,
  private val auth: AuthorizationRestService,
) {

  def getSummary(user: User)(
    projectIri: ProjectIri,
    groupBy: GroupBy,
    itemType: ItemType,
  ): Task[ViewRestrictionsSummary] =
    for {
      _       <- auth.ensureSystemAdminOrProjectAdminById(user, projectIri)
      summary <- service.summary(projectIri, groupBy, itemType)
    } yield summary

  def getItems(user: User)(
    projectIri: ProjectIri,
    groupBy: GroupBy,
    group: String,
    itemType: ItemType,
    pageAndSize: PageAndSize,
  ): Task[PagedResponse[RestrictedResource]] =
    for {
      _ <- auth.ensureSystemAdminOrProjectAdminById(user, projectIri)
      // `group` is a class or property IRI chosen from the summary response. Validate it is a well-formed
      // IRI at the boundary: a malformed value is a client error (400), not a 500, and it must never reach
      // the SPARQL builder unvalidated (see the sibling `projectIri`, which is a typed value object).
      _     <- ZIO.fail(BadRequestException.invalidQueryParamValue("group")).unless(Iri.isIri(group))
      items <- service.items(projectIri, groupBy, group, itemType, pageAndSize)
    } yield items
}

object ViewRestrictionsRestService {
  val layer = zio.ZLayer.derive[ViewRestrictionsRestService]
}
