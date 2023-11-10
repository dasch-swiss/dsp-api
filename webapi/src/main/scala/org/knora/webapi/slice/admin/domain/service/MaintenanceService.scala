/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.IO
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.macros.accessible
import zio.stream.ZStream

import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

@accessible
trait MaintenanceService {
  def fixTopLeftDimensions(report: ProjectsWithBakfilesReport): Task[Unit]
}

final case class MaintenanceServiceLive(
  projectRepo: KnoraProjectRepo,
  triplestoreService: TriplestoreService,
  mapper: PredicateObjectMapper
) extends MaintenanceService {

  override def fixTopLeftDimensions(report: ProjectsWithBakfilesReport): Task[Unit] = {
    def processProject(project: ProjectWithBakFiles): ZStream[Any, Throwable, Unit] =
      getKnoraProject(project).flatMap { knoraProject =>
        ZStream
          .fromIterable(project.assetIds)
          .flatMapPar(5)(processSingleAsset(knoraProject, _))
      }

    def getKnoraProject(project: ProjectWithBakFiles): ZStream[Any, Throwable, KnoraProject] = {
      val getProjectZio: IO[Option[Throwable], KnoraProject] = projectRepo
        .findByShortcode(project.id)
        .some
        .tapSomeError { case None => ZIO.logInfo(s"Project ${project.id} not found, skipping.") }
      ZStream.fromZIOOption(getProjectZio)
    }

    def processSingleAsset(knoraProject: KnoraProject, assetId: ReportAsset): ZStream[Any, Nothing, Unit] =
      ZStream.fromZIOOption(
        fixAsset(knoraProject, assetId)
          // None.type errors are just a sign that the assetId should be ignored. Some.type errors are real errors.
          .tapSomeError { case Some(e) => ZIO.logError(s"Error while processing ${knoraProject.id}, $assetId: $e") }
          // We have logged real errors above, from here on out ignore all errors so that the stream can continue.
          .orElseFail(None)
      )

    ZIO.logInfo(s"Starting fix top left maintenance") *>
      ZStream.fromIterable(report.projects).flatMap(processProject).runDrain *>
      ZIO.logInfo(s"Finished fix top left maintenance")
  }

  private def fixAsset(project: KnoraProject, asset: ReportAsset): IO[Option[Throwable], Unit] =
    for {
      _                      <- ZIO.logInfo(s"Checking asset $asset.")
      stillImageFileValueIri <- checkDimensions(project, asset)
      _                      <- transposeImageDimensions(project, stillImageFileValueIri)
      _                      <- ZIO.logInfo(s"Transposed dimensions for asset $asset.")
    } yield ()

  private def checkDimensions(
    project: KnoraProject,
    asset: ReportAsset
  ): IO[Option[Throwable], InternalIri] =
    for {
      result <- getDimensionAndStillImageValueIri(project, asset).tapSomeError { case None =>
                  ZIO.logDebug(s"No StillImageFileValue with dimensions found for $asset, skipping.")
                }
      (actualDimensions, iri) = result
      _ <- ZIO.when(actualDimensions == asset.dimensions)(
             ZIO.logDebug(s"Dimensions for $asset already correct, skipping.") *> ZIO.fail(None)
           )
    } yield iri

  private def getDimensionAndStillImageValueIri(
    project: KnoraProject,
    asset: ReportAsset
  ): IO[Option[Throwable], (Dimensions, InternalIri)] =
    for {
      result <- triplestoreService.query(checkDimensionsQuery(project, asset.id)).asSomeError
      rowMap <- ZIO.fromOption(result.results.bindings.headOption.map(_.rowMap))
      iri    <- ZIO.fromOption(rowMap.get("valueIri")).map(InternalIri)
      width  <- ZIO.fromOption(rowMap.get("dimX").flatMap(_.toIntOption))
      height <- ZIO.fromOption(rowMap.get("dimY").flatMap(_.toIntOption))
      dim    <- ZIO.fromOption(Dimensions.from(width, height).toOption)
    } yield (dim, iri)

  private def checkDimensionsQuery(project: KnoraProject, assetId: AssetId) = {
    val projectGraph = ProjectADMService.projectDataNamedGraphV2(project)
    Select(s"""
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |
              |SELECT ?valueIri ?dimX ?dimY
              |  FROM <${projectGraph.value}>
              |WHERE {
              |  ?valueIri a knora-base:StillImageFileValue .
              |  ?valueIri knora-base:internalFilename ?filename .
              |  FILTER (strstarts(str(?filename), "$assetId"))
              |  ?valueIri knora-base:dimX ?dimX .
              |  ?valueIri knora-base:dimY ?dimY .
              |}
              |""".stripMargin)
  }

  private def transposeImageDimensions(
    project: KnoraProject,
    stillImageFileValueIri: InternalIri
  ): IO[Option[Throwable], Unit] =
    triplestoreService.query(transposeUpdate(project, stillImageFileValueIri)).asSomeError

  private def transposeUpdate(project: KnoraProject, stillImageFileValueIri: InternalIri) = {
    val projectGraph = ProjectADMService.projectDataNamedGraphV2(project)
    Update(
      s"""
         |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
         |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
         |
         |WITH <${projectGraph.value}>
         |DELETE 
         |{
         |  ?r knora-base:dimX ?oldX .
         |  ?r knora-base:dimY ?oldY .
         |}
         |INSERT
         |{
         |  ?r knora-base:dimX ?oldY .
         |  ?r knora-base:dimY ?oldX .
         |}
         |WHERE 
         |{
         |  BIND (<${stillImageFileValueIri.value}> AS ?r)
         |  ?r knora-base:dimX ?oldX .
         |  ?r knora-base:dimY ?oldY .
         |}
         |""".stripMargin
    )
  }
}

object MaintenanceServiceLive {
  val layer = ZLayer.derive[MaintenanceServiceLive]
}
