/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.fair.ports.FairLicence
import org.knora.webapi.slice.fair.ports.FairProject
import org.knora.webapi.slice.fair.ports.FairProjects

/**
 * Serves project facts and the built-in licence catalogue to the FAIR landing pages.
 */
final case class FairProjectsLive(projectService: KnoraProjectService) extends FairProjects {

  override def findByShortcode(shortcode: Shortcode): Task[Option[FairProject]] =
    projectService
      .findByShortcode(shortcode)
      .map(_.map { p =>
        FairProject(
          shortcode = p.shortcode,
          shortname = p.shortname.value,
          longname = p.longname.map(_.value),
          dataLicenseIri = p.dataLicense.map(_.value),
          defaultDataAuthorship = p.defaultDataAuthorship.map(_.value),
        )
      })

  override def findLicence(licenceIri: String): Task[Option[FairLicence]] =
    ZIO.succeed(FairProjectsLive.toFairLicence(licenceIri))
}

object FairProjectsLive {

  // Resolved against the full built-in catalogue, not the licence repository (which hides the placeholder) and not a
  // project's enabled licences: the page must describe whatever licence a value carries.
  def toFairLicence(licenceIri: String): Option[FairLicence] =
    LicenseIri.from(licenceIri).toOption.flatMap(id => License.BUILT_IN.find(_.id == id)).map { license =>
      license.id match {
        case LicenseIri.PLACEHOLDER                                                  => FairLicence.Placeholder
        case LicenseIri.UNKNOWN | LicenseIri.AI_GENERATED | LicenseIri.PUBLIC_DOMAIN =>
          FairLicence.Declaration(license.labelEn)
        case _ => FairLicence.Licence(license.uri.toString, license.labelEn)
      }
    }

  val layer = ZLayer.derive[FairProjectsLive]
}
