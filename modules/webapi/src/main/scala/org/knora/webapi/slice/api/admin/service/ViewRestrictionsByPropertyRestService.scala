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
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsByPropertyService
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.api.admin.ViewRestrictionsByPropertyEndpoints.*
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ValueItemType
import org.knora.webapi.slice.common.api.AuthorizationRestService

/**
 * Application service for the property-grouped view-restrictions report.
 *
 * Authorization: all three routes require the requesting user to be **project admin on the target project
 * or system admin**, matching the class-grouped report.
 *
 * As there, a well-formed but non-existent `projectIri` yields 403 rather than 404. That is deliberate and
 * not specific to this report: `ensureSystemAdminOrProjectAdminById` is the shared admin-API authorization
 * gate and does not leak project existence to non-admins.
 */
final case class ViewRestrictionsByPropertyRestService(
  private val service: ViewRestrictionsByPropertyService,
  private val auth: AuthorizationRestService,
) {

  /** Step 1: the property list. No filter, so nothing to validate beyond authorization. */
  def getProperties(user: User)(projectIri: ProjectIri): Task[ViewRestrictionsProperties] =
    for {
      _      <- auth.ensureSystemAdminOrProjectAdminById(user, projectIri)
      result <- service.properties(projectIri)
    } yield result

  /** Step 2: one property's counts. */
  def getPropertyValues(user: User)(
    projectIri: ProjectIri,
    property: String,
    itemType: ValueItemType,
  ): Task[ViewRestrictionsPropertyValues] =
    for {
      _      <- auth.ensureSystemAdminOrProjectAdminById(user, projectIri)
      _      <- validateProperty(property)
      result <- service.propertyValues(projectIri, property, itemType)
    } yield result

  /** The drill-down: resources carrying a restricted value of the property. */
  def getPropertyItems(user: User)(
    projectIri: ProjectIri,
    property: String,
    itemType: ValueItemType,
    pageAndSize: PageAndSize,
  ): Task[PagedResponse[RestrictedPropertyResource]] =
    for {
      _      <- auth.ensureSystemAdminOrProjectAdminById(user, projectIri)
      _      <- validateProperty(property)
      result <- service.propertyItems(projectIri, property, itemType, pageAndSize)
    } yield result

  /**
   * `property` is an IRI chosen from the step-1 response. Validate it at the boundary: a malformed value is
   * a client error (400), not a 500, and it must never reach the SPARQL builder unvalidated — it is bound
   * directly into a triple pattern there.
   */
  private def validateProperty(property: String): Task[Unit] =
    ZIO.fail(BadRequestException.invalidQueryParamValue("property")).unless(Iri.isIri(property)).unit
}

object ViewRestrictionsByPropertyRestService {
  val layer = zio.ZLayer.derive[ViewRestrictionsByPropertyRestService]
}
