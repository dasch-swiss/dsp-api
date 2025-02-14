package org.knora.webapi.slice.admin.api.service
import zio.IO
import zio.ZIO
import zio.ZLayer

import dsp.errors.ForbiddenException
import org.knora.webapi.slice.admin.api.LicenseDto
import org.knora.webapi.slice.admin.api.PagedResponse
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.LicenseService
import org.knora.webapi.slice.common.api.AuthorizationRestService

final case class ProjectsLegalInfoRestService(
  private val licenses: LicenseService,
  private val auth: AuthorizationRestService,
) {
  def findByProjectId(shortcode: Shortcode, user: User): IO[ForbiddenException, PagedResponse[LicenseDto]] =
    for {
      _      <- auth.ensureSystemAdminOrProjectAdminByShortcode(user, shortcode)
      result <- licenses.findByProjectShortcode(shortcode)
      page    = PagedResponse.allInOnePage(result.map(LicenseDto.from))
    } yield page
}

object ProjectsLegalInfoRestService {
  val layer = ZLayer.derive[ProjectsLegalInfoRestService]
}
