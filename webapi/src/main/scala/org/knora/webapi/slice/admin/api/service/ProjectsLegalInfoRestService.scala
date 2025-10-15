/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.IO
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.json.JsonCodec

import dsp.errors.NotFoundException
import org.knora.webapi.slice.admin.api.CopyrightHolderAddRequest
import org.knora.webapi.slice.admin.api.CopyrightHolderReplaceRequest
import org.knora.webapi.slice.admin.api.ProjectLicenseDto
import org.knora.webapi.slice.admin.api.model.FilterAndOrder
import org.knora.webapi.slice.admin.api.model.PageAndSize
import org.knora.webapi.slice.admin.api.model.PagedResponse
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.LegalInfoService
import org.knora.webapi.slice.common.api.AuthorizationRestService

final case class ProjectsLegalInfoRestService(
  private val legalInfos: LegalInfoService,
  private val projects: KnoraProjectService,
  private val auth: AuthorizationRestService,
) {

  def findAuthorships(user: User)(
    shortcode: Shortcode,
    pageAndSize: PageAndSize,
    filterAndOrder: FilterAndOrder,
  ): Task[PagedResponse[Authorship]] =
    auth.ensureProjectMember(user, shortcode).flatMap(legalInfos.findAuthorships(_, pageAndSize, filterAndOrder))

  def findLicenses(
    shortcode: Shortcode,
    pageAndSize: PageAndSize,
    filterAndOrder: FilterAndOrder,
    showEnabledOnly: Boolean,
  ): IO[NotFoundException, PagedResponse[ProjectLicenseDto]] = for {
    prj <- projects.findByShortcode(shortcode).orDie.someOrFail(NotFoundException(s"Project $shortcode not found"))
    licenses <- if (showEnabledOnly) { legalInfos.findEnabledLicenses(shortcode) }
                else { legalInfos.findAvailableLicenses(shortcode) }
    licenseDtos = licenses.map(ProjectLicenseDto.from(_, prj)).toSeq
  } yield slice(licenseDtos, pageAndSize, filterAndOrder)

  def findAvailableLicenseByIdAndShortcode(
    shortcode: Shortcode,
    licenseIri: LicenseIri,
  ): IO[NotFoundException, ProjectLicenseDto] = for {
    prj <- projects.findByShortcode(shortcode).orDie.someOrFail(NotFoundException(s"Project $shortcode not found"))
    license <- legalInfos
                 .findAvailableLicenseByIdAndShortcode(licenseIri, shortcode)
                 .someOrFail(NotFoundException(s"License $licenseIri not found in project $shortcode"))
  } yield ProjectLicenseDto.from(license, prj)

  private def slice[A: JsonCodec](
    all: Seq[A],
    pageAndSize: PageAndSize,
    filterAndOrder: FilterAndOrder,
  )(using o: Ordering[A]): PagedResponse[A] =
    val filtered = all
      .filter(a => filterAndOrder.filter.forall(_.toLowerCase.r.findFirstIn(a.toString.toLowerCase).isDefined))
      .sorted(filterAndOrder.ordering[A])
    val slice = filtered.slice(pageAndSize.size * (pageAndSize.page - 1), pageAndSize.size * pageAndSize.page)
    PagedResponse.from(slice, filtered.size, pageAndSize)

  def enableLicense(user: User)(shortcode: Shortcode, licenseIri: LicenseIri): Task[Unit] =
    for {
      project <- auth.ensureSystemAdminOrProjectAdminByShortcode(user, shortcode)
      _       <- legalInfos.enableLicense(licenseIri, project)
    } yield ()

  def disableLicense(user: User)(shortcode: Shortcode, licenseIri: LicenseIri): Task[Unit] =
    for {
      project <- auth.ensureSystemAdminOrProjectAdminByShortcode(user, shortcode)
      _       <- legalInfos.disableLicense(licenseIri, project)
    } yield ()

  def findCopyrightHolders(user: User)(
    shortcode: Shortcode,
    pageAndSize: PageAndSize,
    filterAndOrder: FilterAndOrder,
  ): Task[PagedResponse[CopyrightHolder]] =
    for {
      project <- auth.ensureProjectMember(user, shortcode)
    } yield slice(project.allowedCopyrightHolders.toSeq, pageAndSize, filterAndOrder)

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
      project <- projects.findByShortcode(shortcode).someOrFail(NotFoundException(s"Project $shortcode not found"))
      _       <- projects.replaceCopyrightHolder(project.id, req.`old-value`, req.`new-value`)
    } yield ()
}

object ProjectsLegalInfoRestService {
  val layer = ZLayer.derive[ProjectsLegalInfoRestService]
}
