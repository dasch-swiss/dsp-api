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
import org.knora.webapi.slice.admin.api.CopyrightHolderAddRequest
import org.knora.webapi.slice.admin.api.CopyrightHolderReplaceRequest
import org.knora.webapi.slice.admin.api.LicenseDto
import org.knora.webapi.slice.admin.api.model.PageAndSize
import org.knora.webapi.slice.admin.api.model.PagedResponse
import org.knora.webapi.slice.admin.api.model.Pagination
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
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

  def findAuthorships(user: User)(shortcode: Shortcode, pageAndSize: PageAndSize): Task[PagedResponse[Authorship]] =
    for {
      _          <- auth.ensureProjectMember(user, shortcode)
      authorships = Seq.empty // TODO: Implement query
    } yield slice(authorships, pageAndSize)

  def findLicenses(user: User)(
    shortcode: Shortcode,
    pageAndSize: PageAndSize,
  ): IO[ForbiddenException, PagedResponse[LicenseDto]] =
    for {
      _      <- auth.ensureProjectMember(user, shortcode)
      result <- licenses.findByProjectShortcode(shortcode).map(_.map(LicenseDto.from))
    } yield slice(result, pageAndSize)

  private def slice[A](all: Seq[A], pageAndSize: PageAndSize): PagedResponse[A] =
    val slice = all.slice(pageAndSize.size * (pageAndSize.page - 1), pageAndSize.size * pageAndSize.page)
    PagedResponse(slice, Pagination.from(all.size, pageAndSize))

  def findCopyrightHolders(user: User)(
    shortcode: Shortcode,
    pageAndSize: PageAndSize,
  ): Task[PagedResponse[CopyrightHolder]] =
    for {
      project <- auth.ensureProjectMember(user, shortcode)
    } yield slice(project.predefinedCopyrightHolders.toSeq, pageAndSize)

  def addCopyrightHolders(user: User)(shortcode: Shortcode, req: CopyrightHolderAddRequest): Task[Unit] =
    for {
      project <- auth.ensureSystemAdminOrProjectAdminByShortcode(user, shortcode)
      _       <- projects.addCopyrightHolders(project.id, req.data)
    } yield ()

  def replaceCopyrightHolder(user: User)(
    shortcode: Shortcode,
    req: CopyrightHolderReplaceRequest,
  ): Task[Unit] =
    for {
      _       <- auth.ensureSystemAdmin(user)
      project <- projects.findByShortcode(shortcode).someOrFail(NotFoundException(s"Project ${shortcode} not found"))
      _       <- projects.replaceCopyrightHolder(project.id, req.`old-value`, req.`new-value`)
    } yield ()
}

object ProjectsLegalInfoRestService {
  val layer = ZLayer.derive[ProjectsLegalInfoRestService]
}
