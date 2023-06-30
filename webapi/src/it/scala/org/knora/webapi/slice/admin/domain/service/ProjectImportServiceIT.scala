/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import dsp.valueobjects.Project
import org.knora.webapi.config.{Fuseki, Triplestore}
import org.knora.webapi.testcontainers.FusekiTestContainer
import zio._
import zio.nio.file._
import zio.test._

import java.io.IOException

object ProjectImportServiceIT extends ZIOSpecDefault {

  private val repositoryName = "knora-test"

  private val storageServiceLayer: Layer[IOException, ProjectExportStorageServiceLive] = ZLayer.fromZIO {
    for {
      exportDirectory <- Files.createTempDirectory(Path(""), None, List.empty)
    } yield ProjectExportStorageServiceLive(exportDirectory)
  }

  private val dspIngestClientLayer: ULayer[DspIngestClient] = ZLayer.succeed {
    new DspIngestClient {
      override def exportProject(shortCode: Project.ShortCode): ZIO[Scope, Throwable, Path] =
        ZIO.succeed(Path("unused"))
      override def importProject(shortCode: Project.ShortCode, fileToImport: Path): Task[Path] =
        ZIO.succeed(Path("unused"))
    }
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
          queryTimeout = "Duration.Undefined",
          gravsearchTimeout = "Duration.Undefined",
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
      .provideSomeLayer[FusekiTestContainer with ProjectExportStorageService](dspIngestClientLayer)
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
