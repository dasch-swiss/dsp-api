/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service.maintenance

import zio.IO
import zio.Task
import zio.ZIO
import zio.stream.ZStream

import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.Dimensions
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.ProjectWithBakFiles
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.ProjectsWithBakfilesReport
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.ReportAsset
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.MaintenanceAction
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

final case class TopLeftCorrectionAction[A <: ProjectsWithBakfilesReport](
  knoraProjectService: KnoraProjectService,
  triplestoreService: TriplestoreService,
) extends MaintenanceAction[A] {
  def execute(params: A): Task[Unit] =
    ZStream.fromIterable(params.projects).flatMap(processProject).runDrain

  private def getKnoraProject(project: ProjectWithBakFiles): ZStream[Any, Throwable, KnoraProject] = {
    val getProjectZio: IO[Option[Throwable], KnoraProject] = knoraProjectService
      .findByShortcode(project.id)
      .some
      .tapSomeError { case None => ZIO.logInfo(s"Project ${project.id} not found, skipping.") }
    ZStream.fromZIOOption(getProjectZio)
  }

  private def processSingleAsset(knoraProject: KnoraProject, assetId: ReportAsset): ZStream[Any, Nothing, Unit] =
    ZStream.fromZIOOption(
      fixAsset(knoraProject, assetId)
        // None.type errors are just a sign that the assetId should be ignored. Some.type errors are real errors.
        .tapSomeError { case Some(e) => ZIO.logError(s"Error while processing ${knoraProject.id}, $assetId: $e") }
        // We have logged real errors above, from here on out ignore all errors so that the stream can continue.
        .orElseFail(None),
    )

  private def processProject(project: ProjectWithBakFiles): ZStream[Any, Throwable, Unit] =
    getKnoraProject(project).flatMap { knoraProject =>
      ZStream
        .fromIterable(project.assetIds)
        .flatMapPar(5)(processSingleAsset(knoraProject, _))
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
    asset: ReportAsset,
  ): IO[Option[Throwable], InternalIri] =
    for {
      result <- getDimensionAndStillImageValueIri(project, asset).tapSomeError { case None =>
                  ZIO.logDebug(s"No StillImageFileValue with dimensions found for $asset, skipping.")
                }
      (actualDimensions, iri) = result
      _                      <- ZIO.when(actualDimensions == asset.dimensions)(
             ZIO.logDebug(s"Dimensions for $asset already correct, skipping.") *> ZIO.fail(None),
           )
    } yield iri

  private def getDimensionAndStillImageValueIri(
    project: KnoraProject,
    asset: ReportAsset,
  ): IO[Option[Throwable], (Dimensions, InternalIri)] =
    for {
      result <- triplestoreService.query(checkDimensionsQuery(project, asset.id)).asSomeError
      rowMap <- ZIO.fromOption(result.getFirstRow.map(_.rowMap))
      iri    <- ZIO.fromOption(rowMap.get("valueIri")).map(InternalIri.apply)
      width  <- ZIO.fromOption(rowMap.get("dimX").flatMap(_.toIntOption))
      height <- ZIO.fromOption(rowMap.get("dimY").flatMap(_.toIntOption))
      dim    <- ZIO.fromOption(Dimensions.from(width, height).toOption)
    } yield (dim, iri)

  private def checkDimensionsQuery(project: KnoraProject, assetId: AssetId) = {
    val projectGraph = ProjectService.projectDataNamedGraphV2(project)
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
    stillImageFileValueIri: InternalIri,
  ): IO[Option[Throwable], Unit] =
    triplestoreService.query(transposeUpdate(project, stillImageFileValueIri)).asSomeError

  private def transposeUpdate(project: KnoraProject, stillImageFileValueIri: InternalIri) = {
    val projectGraph = ProjectService.projectDataNamedGraphV2(project)
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
         |""".stripMargin,
    )
  }
}
