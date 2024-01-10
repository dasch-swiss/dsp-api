/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*
import zio.nio.file.*
import zio.test.*

import java.io.IOException

import org.knora.webapi.config.Fuseki
import org.knora.webapi.config.Triplestore
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.testcontainers.FusekiTestContainer

object ProjectImportServiceIT extends ZIOSpecDefault {

  private val repositoryName = "knora-test"

  private val storageServiceLayer: Layer[IOException, ProjectExportStorageServiceLive] = ZLayer.fromZIO {
    for {
      exportDirectory <- Files.createTempDirectory(Path(""), None, List.empty)
    } yield ProjectExportStorageServiceLive(exportDirectory)
  }

  private val importServiceTestLayer
    : URLayer[FusekiTestContainer with ProjectExportStorageService, ProjectImportServiceLive] = ZLayer.fromZIO {
    (for {
      exportStorageService <- ZIO.service[ProjectExportStorageService]
      container            <- ZIO.service[FusekiTestContainer]
      dspIngestClient      <- ZIO.service[DspIngestClient]
      config =
        Triplestore(
          dbtype = "tdb2",
          useHttps = false,
          host = container.getHost,
          queryTimeout = java.time.Duration.ofSeconds(5),
          gravsearchTimeout = java.time.Duration.ofSeconds(5),
          autoInit = false,
          fuseki = Fuseki(
            port = container.getFirstMappedPort,
            repositoryName = repositoryName,
            username = "admin",
            password = "test"
          ),
          profileQueries = false
        )
    } yield ProjectImportServiceLive(config, exportStorageService, dspIngestClient))
      .provideSomeLayer[FusekiTestContainer with ProjectExportStorageService](DspIngestClientITMock.layer)
  }

  private val trigContent =
    """
      |@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
      |@prefix owl: <http://www.w3.org/2002/07/owl#> .
      |
      |<http://example.org/graph> {
      |  <http://www.knora.org/ontology/0001/freetest>  rdf:type  owl:Ontology .
      |}
      |""".stripMargin

  def spec: Spec[Any, Throwable] =
    suite("ImportService")(test("should import a trig file into a named graph and the default graph") {
      ZIO.scoped {
        for {
          _ <- FusekiTestContainer.initializeWithDataset(repositoryName)

          filePath <- FileTestUtil.createTempTextFileScoped(trigContent, ".trig")
          _        <- ProjectImportService.importTrigFile(filePath)
          nrResultsInNamedGraph <- ProjectImportService
                                     .querySelect(
                                       """
                                         |SELECT ?subject ?predicate ?object
                                         |FROM NAMED <http://example.org/graph>
                                         |WHERE {
                                         |  GRAPH <http://example.org/graph> {
                                         |    ?subject ?predicate ?object.
                                         |  }
                                         |}
                                         |""".stripMargin
                                     )
                                     .map(_.rewindable.size())
          nrResultsInDefaultGraph <- ProjectImportService
                                       .querySelect(
                                         """
                                           |SELECT ?subject ?predicate ?object
                                           |WHERE {
                                           |  ?subject ?predicate ?object.
                                           |}
                                           |""".stripMargin
                                       )
                                       .map(_.rewindable.size())
          _ <- ZIO.logDebug("loaded")
        } yield assertTrue(nrResultsInNamedGraph == 1, nrResultsInDefaultGraph == 1)
      }
    })
      .provideSomeLayer[FusekiTestContainer with ProjectExportStorageService](importServiceTestLayer)
      .provideSomeLayer[FusekiTestContainer](storageServiceLayer)
      .provideSomeLayerShared(FusekiTestContainer.layer)
}

object FileTestUtil {
  def createTempTextFileScoped(content: String, suffix: String): ZIO[Scope, IOException, Path] = for {
    filePath <- Files.createTempFileScoped(suffix)
    _        <- Files.writeBytes(filePath, Chunk.fromIterable(content.getBytes))
  } yield filePath
}

final case class DspIngestClientITMock() extends DspIngestClient {
  override def exportProject(shortcode: KnoraProject.Shortcode): ZIO[Scope, Throwable, Path] =
    ZIO.succeed(Path("/tmp/test.zip"))
  override def importProject(shortcode: KnoraProject.Shortcode, fileToImport: Path): Task[Path] =
    ZIO.succeed(Path("/tmp/test.zip"))

  override def getAssetInfo(shortcode: KnoraProject.Shortcode, assetId: AssetId): Task[AssetInfoResponse] =
    ZIO.succeed(
      AssetInfoResponse(
        s"$assetId.txt",
        s"$assetId.txt.orig",
        "test.txt",
        "bfd3192ea04d5f42d79836cf3b8fbf17007bab71",
        "17bab70071fbf8b3fc63897d24f5d40ae2913dfb",
        internalMimeType = Some("text/plain"),
        originalMimeType = Some("text/plain")
      )
    )
}
object DspIngestClientITMock {
  val layer = ZLayer.derive[DspIngestClientITMock]
}
