/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service
import cats.syntax.traverse.*
import zio.*
import zio.prelude.Validation

import scala.annotation.unused

import dsp.errors.InconsistentRepositoryDataException
import org.knora.sparqlbuilder.Iri
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueV2
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.repo.LicenseRepo
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.api.admin.model.FilterAndOrder
import org.knora.webapi.slice.common.PlaceholderIri
import org.knora.webapi.store.triplestore.api.TriplestoreService

case class LegalInfoService(
  private val licenses: LicenseRepo,
  private val projects: KnoraProjectService,
  private val triplestore: TriplestoreService,
) {

  /**
   * Currently, the project is not used in the implementation and all built in licenses are returned.
   *
   * @param id the Project for which the licenses are retrieved.
   * @return Returns the licenses available in the project.
   */
  def findAvailableLicenses(@unused id: Shortcode): UIO[Set[License]] =
    licenses.findAll().map(_.toSet).orDie

  def findEnabledLicenses(id: Shortcode): UIO[Set[License]] = for {
    enabled  <- projects.findByShortcode(id).orDie.map(_.map(_.enabledLicenses).getOrElse(Set.empty))
    licenses <- ZIO.foreach(enabled)(licenses.findById).orDie
    result    = licenses.flatten
  } yield result

  def findAvailableLicenseByIdAndShortcode(licenseIri: LicenseIri, shortcode: Shortcode): UIO[Option[License]] =
    findAvailableLicenses(shortcode).map(_.find(_.id == licenseIri))

  def enableLicense(license: LicenseIri, project: KnoraProject): IO[String, KnoraProject] =
    AppConfig.features(_.allowPlaceholder).flatMap { allowPlaceholder =>
      if (license == LicenseIri.PLACEHOLDER && !allowPlaceholder)
        ZIO.fail(s"License $license is the placeholder license and is not allowed on this server")
      else
        projects.enableLicense(license, project).orDie
    }

  def disableLicense(license: LicenseIri, project: KnoraProject): UIO[KnoraProject] =
    projects.disableLicense(license, project).orDie

  def validateLegalInfo(fileValue: FileValueV2, id: Shortcode): IO[String, FileValueV2] =
    for {
      licenseValid         <- licenseValidation(fileValue.licenseIri, id)
      copyrightHolderValid <- copyrightHolderValidation(fileValue.copyrightHolder, id)
      _                    <- Validation.validate(licenseValid, copyrightHolderValid).toZIOParallelErrors.mapError(_.mkString(", "))
    } yield fileValue

  // Placeholder rejection lives at the parser layer
  // (`ApiComplexV2JsonLdRequestParser.ensurePlaceholderAllowed` via
  // `FileValueV2.placeholderFields`). This method handles only the
  // project-policy concern: the license must be in the project's enabled set.
  // The sentinel `urn:dasch:placeholder` short-circuits — by the time we get here,
  // the parser-level gate has already approved its use on this deployment.
  private def licenseValidation(
    licenseIri: Option[LicenseIri],
    shortcode: Shortcode,
  ): UIO[Validation[String, Unit]] =
    licenseIri match
      case None                                                    => ZIO.succeed(Validation.unit)
      case Some(iri) if iri.value == PlaceholderIri.instance.value => ZIO.succeed(Validation.unit)
      case Some(iri)                                               =>
        findEnabledLicenses(shortcode).map { licenses =>
          if (licenses.map(_.id).contains(iri)) Validation.unit
          else Validation.fail(s"License $iri is not allowed in project $shortcode")
        }

  // The sentinel `urn:dasch:placeholder` short-circuits for the same reason as
  // `licenseValidation`: deployment-level gating happens at the parser layer.
  private def copyrightHolderValidation(
    copyrightHolder: Option[CopyrightHolder],
    shortcode: Shortcode,
  ): UIO[Validation[String, Unit]] =
    copyrightHolder match
      case None                                                          => ZIO.succeed(Validation.unit)
      case Some(holder) if holder.value == PlaceholderIri.instance.value => ZIO.succeed(Validation.unit)
      case Some(holder)                                                  =>
        projects
          .findByShortcode(shortcode)
          .orDie
          .map {
            case None          => Validation.fail(s"Project $shortcode not found")
            case Some(project) =>
              val holders = project.allowedCopyrightHolders
              if (holders.contains(holder)) { Validation.unit }
              else { Validation.fail(s"Copyright holder $holder is not allowed in project $shortcode") }
          }

  def findAuthorships(
    project: KnoraProject,
    paging: PageAndSize,
    filterAndOrder: FilterAndOrder,
  ): UIO[PagedResponse[Authorship]] = {
    val graph = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)

    val runAuthorshipsQuery = for {
      result  <- triplestore.query(AuthorshipQueries.authorships(graph, paging, filterAndOrder)).map(_.results.bindings)
      authors <-
        ZIO
          .fromEither(result.flatMap(_.rowMap.get(AuthorshipQueries.authorVar.name)).traverse(Authorship.from))
          .mapError(e => InconsistentRepositoryDataException(e))
    } yield authors

    val runCountQuery = triplestore
      .query(AuthorshipQueries.count(graph, filterAndOrder))
      .map(_.results.bindings)
      .flatMap(result => ZIO.attempt(result.head.rowMap(AuthorshipQueries.countVar.name).toInt))

    for {
      authorsFiber <- runAuthorshipsQuery.logError.orDie.fork
      count        <- runCountQuery.logError.orDie
      authors      <- authorsFiber.join
    } yield PagedResponse.from(authors, count, paging)
  }
}

object LegalInfoService {
  val layer = ZLayer.derive[LegalInfoService]
}
