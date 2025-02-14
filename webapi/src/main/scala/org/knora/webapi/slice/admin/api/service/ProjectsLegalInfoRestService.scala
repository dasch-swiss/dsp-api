/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service
import zio.IO
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import org.knora.webapi.slice.admin.api.AuthorshipAddRequest
import org.knora.webapi.slice.admin.api.AuthorshipReplaceRequest
import org.knora.webapi.slice.admin.api.LicenseDto
import org.knora.webapi.slice.admin.api.PagedResponse
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.LicenseService
import org.knora.webapi.slice.common.api.AuthorizationRestService

final case class ProjectsLegalInfoRestService(
  private val licenses: LicenseService,
  private val projects: KnoraProjectService,
  private val auth: AuthorizationRestService,
) {

  def findByProjectId(shortcode: Shortcode, user: User): IO[ForbiddenException, PagedResponse[LicenseDto]] =
    for {
      _      <- auth.ensureSystemAdminOrProjectAdminByShortcode(user, shortcode)
      result <- licenses.findByProjectShortcode(shortcode)
      page    = PagedResponse.allInOnePage(result.map(LicenseDto.from))
    } yield page

  def findAuthorshipsByProject(shortcode: Shortcode, user: User): Task[List[Authorship]] =
    for {
      project <- auth.ensureSystemAdminOrProjectAdminByShortcode(user, shortcode)
    } yield project.predefinedAuthorships.toList

  def addPredefinedAuthorships(
    shortcode: Shortcode,
    req: AuthorshipAddRequest,
    user: User,
  ): Task[Unit] =
    for {
      project <- auth.ensureSystemAdminOrProjectAdminByShortcode(user, shortcode)
      _       <- projects.addPredefinedAuthorships(project.id, req.data)
    } yield ()

  def replacePredefinedAuthorship(
    shortcode: Shortcode,
    req: AuthorshipReplaceRequest,
    user: User,
  ): Task[Unit] =
    for {
      _       <- auth.ensureSystemAdmin(user)
      project <- projects.findByShortcode(shortcode).someOrFail(NotFoundException(s"Project ${shortcode} not found"))
      _       <- projects.replacePredefinedAuthorship(project.id, req.`old-value`, req.`new-value`)
    } yield ()
}

object ProjectsLegalInfoRestService {
  val layer = ZLayer.derive[ProjectsLegalInfoRestService]
}
