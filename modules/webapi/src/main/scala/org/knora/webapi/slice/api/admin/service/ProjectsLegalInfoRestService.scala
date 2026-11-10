/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin.service

import zio.IO
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.LegalInfoService
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.api.admin.CopyrightHolderAddRequest
import org.knora.webapi.slice.api.admin.CopyrightHolderReplaceRequest
import org.knora.webapi.slice.api.admin.ProjectLicenseDto
import org.knora.webapi.slice.api.admin.ResourceSideLegalInfo
import org.knora.webapi.slice.api.admin.model.FilterAndOrder
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.ProjectCandidate

final case class ProjectsLegalInfoRestService(
  private val legalInfos: LegalInfoService,
  private val projects: KnoraProjectService,
  private val auth: AuthorizationRestService,
) {

  def findAuthorships(candidate: ProjectCandidate)(
    shortcode: Shortcode,
    pageAndSize: PageAndSize,
    filterAndOrder: FilterAndOrder,
  ): Task[PagedResponse[Authorship]] =
    auth
      .authorizeProjectMemberByShortcode(candidate, shortcode)
      .flatMap(grant => legalInfos.findAuthorships(grant.project, pageAndSize, filterAndOrder))

  def findLicenses(
    shortcode: Shortcode,
    pageAndSize: PageAndSize,
    filterAndOrder: FilterAndOrder,
    showEnabledOnly: Boolean,
  ): IO[NotFoundException, PagedResponse[ProjectLicenseDto]] = for {
    prj      <- projects.findByShortcode(shortcode).orDie.someOrFail(NotFoundException(s"Project $shortcode not found"))
    licenses <- if (showEnabledOnly) { legalInfos.findEnabledLicenses(shortcode) }
                else { legalInfos.findAvailableLicenses(shortcode) }
    licenseDtos = licenses.map(ProjectLicenseDto.from(_, prj)).toSeq
  } yield slice(licenseDtos, pageAndSize, filterAndOrder)

  def findAvailableLicenseByIdAndShortcode(
    shortcode: Shortcode,
    licenseIri: LicenseIri,
  ): IO[NotFoundException, ProjectLicenseDto] = for {
    prj     <- projects.findByShortcode(shortcode).orDie.someOrFail(NotFoundException(s"Project $shortcode not found"))
    license <- legalInfos
                 .findAvailableLicenseByIdAndShortcode(licenseIri, shortcode)
                 .someOrFail(NotFoundException(s"License $licenseIri not found in project $shortcode"))
  } yield ProjectLicenseDto.from(license, prj)

  private def slice[A](
    all: Seq[A],
    pageAndSize: PageAndSize,
    filterAndOrder: FilterAndOrder,
  )(using o: Ordering[A]): PagedResponse[A] =
    val filtered = all
      .filter(a => filterAndOrder.filter.forall(f => a.toString.toLowerCase.contains(f.toLowerCase)))
      .sorted(using filterAndOrder.ordering[A])
    val slice = filtered.slice(pageAndSize.size * (pageAndSize.page - 1), pageAndSize.size * pageAndSize.page)
    PagedResponse.from(slice, filtered.size, pageAndSize)

  def enableLicense(candidate: ProjectCandidate)(shortcode: Shortcode, licenseIri: LicenseIri): Task[Unit] =
    for {
      grant <- auth.authorizeProjectAdminByShortcode(candidate, shortcode)
      _     <- legalInfos.enableLicense(licenseIri, grant.project).mapError(BadRequestException.apply)
    } yield ()

  def disableLicense(candidate: ProjectCandidate)(shortcode: Shortcode, licenseIri: LicenseIri): Task[Unit] =
    for {
      grant <- auth.authorizeProjectAdminByShortcode(candidate, shortcode)
      _     <- legalInfos.disableLicense(licenseIri, grant.project)
    } yield ()

  def findCopyrightHolders(candidate: ProjectCandidate)(
    shortcode: Shortcode,
    pageAndSize: PageAndSize,
    filterAndOrder: FilterAndOrder,
  ): Task[PagedResponse[CopyrightHolder]] =
    for {
      grant <- auth.authorizeProjectMemberByShortcode(candidate, shortcode)
    } yield slice(grant.project.allowedCopyrightHolders.toSeq, pageAndSize, filterAndOrder)

  def addCopyrightHolders(
    candidate: ProjectCandidate,
  )(shortcode: Shortcode, req: CopyrightHolderAddRequest): Task[Unit] =
    for {
      grant <- auth.authorizeProjectAdminByShortcode(candidate, shortcode)
      _     <- projects.addCopyrightHolders(grant.project.id, req.data)
    } yield ()

  def replaceCopyrightHolder(
    shortcode: Shortcode,
    req: CopyrightHolderReplaceRequest,
  ): Task[Unit] =
    for {
      project <- projects.findByShortcode(shortcode).someOrFail(NotFoundException(s"Project $shortcode not found"))
      _       <- projects.replaceCopyrightHolder(project.id, req.`old-value`, req.`new-value`)
    } yield ()

  def setResourceSideLegalInfo(candidate: ProjectCandidate)(
    shortcode: Shortcode,
    req: ResourceSideLegalInfo,
  ): Task[ResourceSideLegalInfo] =
    for {
      grant <- auth.authorizeProjectAdminByShortcode(candidate, shortcode)
      _     <- ZIO
             .fail(
               BadRequestException(
                 "Invalid data license: only Creative Commons licenses are allowed " +
                   s"(${LicenseIri.CC_LICENSES.map(_.value).toList.sorted.mkString(", ")}).",
               ),
             )
             .when(req.dataLicense.exists(license => !LicenseIri.CC_LICENSES.contains(license)))
      updated <- projects.setResourceSideLegalInfo(
                   grant.project.id,
                   req.dataLicense,
                   req.dataCopyrightHolder,
                   req.defaultDataAuthorship,
                 )
    } yield ResourceSideLegalInfo(updated.dataLicense, updated.dataCopyrightHolder, updated.defaultDataAuthorship)
}

object ProjectsLegalInfoRestService {
  val layer = ZLayer.derive[ProjectsLegalInfoRestService]
}
