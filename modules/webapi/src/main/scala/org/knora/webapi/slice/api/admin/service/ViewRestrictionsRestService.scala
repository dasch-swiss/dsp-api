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
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsService
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.*
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.ProjectCandidate

/**
 * Application service for the "view restrictions" report (design screen 1h).
 *
 * Authorization: both routes require the requesting user to be **project admin on the target
 * project or system admin** (`auth.authorizeProjectAdminById`), matching the 1h header.
 *
 * Note (AC5): a well-formed but non-existent `projectIri` yields 403, not 404 — deliberately, because
 * `authorizeProjectAdminById` is the shared admin-API authorization gate and every admin route
 * behaves this way (it does not leak project existence to non-admins). Diverging only here would be
 * inconsistent, so we keep the convention.
 */
final case class ViewRestrictionsRestService(
  private val service: ViewRestrictionsService,
  private val auth: AuthorizationRestService,
) {

  /** Step 1: the whole class table. No filter, so nothing to validate beyond authorization. */
  def getClasses(candidate: ProjectCandidate)(projectIri: ProjectIri): Task[ViewRestrictionsClasses] =
    for {
      grant   <- auth.authorizeProjectAdminById(candidate, projectIri)
      classes <- service.classSummaries(grant.project)
    } yield classes

  /** Step 2: one class's value counts. */
  def getValues(candidate: ProjectCandidate)(
    projectIri: ProjectIri,
    resourceClass: String,
    itemType: ValueItemType,
  ): Task[ViewRestrictionsValues] =
    for {
      grant <- auth.authorizeProjectAdminById(candidate, projectIri)
      // `resourceClass` is a class IRI chosen from the step-1 response. Validate it is a well-formed IRI
      // at the boundary: a malformed value is a client error (400), not a 500, and it must never reach the
      // SPARQL builder unvalidated (see the sibling `projectIri`, which is a typed value object).
      _      <- ZIO.fail(BadRequestException.invalidQueryParamValue("resourceClass")).unless(Iri.isIri(resourceClass))
      counts <- service.valueCounts(grant.project, resourceClass, itemType)
    } yield counts

  def getItems(candidate: ProjectCandidate)(
    projectIri: ProjectIri,
    resourceClass: String,
    itemType: ItemType,
    pageAndSize: PageAndSize,
  ): Task[PagedResponse[RestrictedResource]] =
    for {
      grant <- auth.authorizeProjectAdminById(candidate, projectIri)
      // `resourceClass` is a class IRI chosen from the step-1 response. Validate it is a well-formed
      // IRI at the boundary: a malformed value is a client error (400), not a 500, and it must never reach
      // the SPARQL builder unvalidated (see the sibling `projectIri`, which is a typed value object).
      _     <- ZIO.fail(BadRequestException.invalidQueryParamValue("resourceClass")).unless(Iri.isIri(resourceClass))
      items <- service.items(grant.project, resourceClass, itemType, pageAndSize)
    } yield items
}

object ViewRestrictionsRestService {
  val layer = zio.ZLayer.derive[ViewRestrictionsRestService]
}
