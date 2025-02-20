/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service
import cats.syntax.traverse.*
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.*
import zio.IO
import zio.ZIO
import zio.ZLayer
import zio.prelude.Validation

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueV2
import org.knora.webapi.slice.admin.api.model.FilterAndOrder
import org.knora.webapi.slice.admin.api.model.PageAndSize
import org.knora.webapi.slice.admin.api.model.PagedResponse
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.repo.LicenseRepo
import org.knora.webapi.slice.common.repo.rdf.Vocabulary
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

case class LegalInfoService(
  private val licenses: LicenseRepo,
  private val projects: KnoraProjectService,
  private val triplestore: TriplestoreService,
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

  def findAuthorships(
    project: KnoraProject,
    paging: PageAndSize,
    filterAndOrder: FilterAndOrder,
  ): UIO[PagedResponse[Authorship]] = {
    val graph                 = Rdf.iri(ProjectService.projectDataNamedGraphV2(project).value)
    val searchTermQueryString = filterAndOrder.filter.map(Rdf.literalOf).map(_.getQueryString)
    val authorVar             = "author"
    val graphPattern =
      s"""GRAPH ${graph.getQueryString} {
         |  ?fileValue ${Vocabulary.KnoraBase.hasAuthorship.getQueryString} ?$authorVar .
         |  ${searchTermQueryString.fold("")(term => s"FILTER(CONTAINS(LCASE(STR(?$authorVar)), ${term.toLowerCase}))")}
         |}""".stripMargin

    val order = filterAndOrder.order.toQueryString
    val authorshipsQuery =
      s"""
         |SELECT DISTINCT ?$authorVar WHERE {
         |  $graphPattern
         |} ORDER BY $order(?$authorVar) LIMIT ${paging.size} OFFSET ${paging.size * (paging.page - 1)}
         |""".stripMargin
    val runAuthorshipsQuery = for {
      result <- triplestore.query(Select(authorshipsQuery)).map(_.results.bindings)
      authors <-
        ZIO
          .fromEither(result.flatMap(_.rowMap.get(authorVar)).traverse(Authorship.from))
          .mapError(e => InconsistentRepositoryDataException(e))
    } yield authors

    val countVar = "count"
    val countQuery =
      s"""
         |SELECT (COUNT(DISTINCT ?$authorVar) AS ?$countVar) WHERE {
         |  $graphPattern
         |}
         |""".stripMargin
    val runCountQuery = triplestore
      .query(Select(countQuery))
      .map(_.results.bindings)
      .flatMap(result => ZIO.attempt(result.head.rowMap(countVar).toInt))

    for {
      authorsFiber <- runAuthorshipsQuery.fork
      count        <- runCountQuery.orDie
      authors      <- authorsFiber.join.orDie
    } yield PagedResponse.from(authors, count, paging)
  }
}

object LegalInfoService {
  val layer = ZLayer.derive[LegalInfoService]
}
