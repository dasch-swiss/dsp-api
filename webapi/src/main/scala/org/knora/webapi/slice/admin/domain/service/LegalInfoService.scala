/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service
import zio.*
import zio.prelude.Validation

import org.knora.webapi.messages.v2.responder.valuemessages.FileValueV2
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.repo.LicenseRepo

case class LegalInfoService(
  private val licenses: LicenseRepo,
  private val projects: KnoraProjectService,
) {

  /**
   * Currently, the project is not used in the implementation and all allowed licenses are returned.
   *
   * @param id the Project for which the licenses are retrieved.
   * @return Returns the licenses available in the project.
   */
  def findLicenses(id: Shortcode): UIO[Chunk[License]] =
    licenses.findAll().orDie

  def validateLegalInfo(fileValue: FileValueV2, id: Shortcode): IO[String, FileValueV2] =
    for {
      licenseValid         <- licenseValidation(fileValue.licenseIri, id)
      copyrightHolderValid <- copyrightHolderValidation(fileValue.copyrightHolder, id)
      _ <- Validation
             .validate(licenseValid, copyrightHolderValid)
             .toZIOParallelErrors
             .mapError(_.mkString(", "))
    } yield fileValue

  private def licenseValidation(
    licenseIri: Option[LicenseIri],
    shortcode: Shortcode,
  ): UIO[Validation[String, Unit]] =
    licenseIri match
      case None => ZIO.succeed(Validation.unit)
      case Some(iri) =>
        findLicenses(shortcode).map { licenses =>
          if (licenses.map(_.id).contains(iri)) { Validation.unit }
          else { Validation.fail(s"License $iri is not allowed in project $shortcode") }
        }

  private def copyrightHolderValidation(
    copyrightHolder: Option[CopyrightHolder],
    shortcode: Shortcode,
  ): UIO[Validation[String, Unit]] =
    copyrightHolder match
      case None => ZIO.succeed(Validation.unit)
      case Some(holder) =>
        projects
          .findByShortcode(shortcode)
          .orDie
          .map {
            case None => Validation.fail(s"Project $shortcode not found")
            case Some(project) =>
              val holders = project.allowedCopyrightHolders
              if (holders.contains(holder)) { Validation.unit }
              else { Validation.fail(s"Copyright holder $holder is not allowed in project $shortcode") }
          }

}

object LegalInfoService {
  val layer = ZLayer.derive[LegalInfoService]
}
