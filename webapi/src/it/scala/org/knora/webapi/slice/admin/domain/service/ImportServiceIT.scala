/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Chunk
import zio.Scope
import zio.URLayer
import zio.ZIO
import zio.ZLayer
import zio.nio.file.Files
import zio.nio.file.Path
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import java.io.IOException

import org.knora.webapi.config.Fuseki
import org.knora.webapi.config.Triplestore
import org.knora.webapi.testcontainers.FusekiTestContainer

object ImportServiceIT extends ZIOSpecDefault {

  private val repositoryName = "knora-test"

  private val importServiceTestLayer: URLayer[FusekiTestContainer, ImportServiceLive] = ZLayer.fromZIO {
    for {
      container <- ZIO.service[FusekiTestContainer]
      config =
        Triplestore(
          dbtype = "tdb2",
          useHttps = false,
          host = container.host,
          queryTimeout = "Duration.Undefined",
          gravsearchTimeout = "Duration.Undefined",
          autoInit = false,
          fuseki =
            Fuseki(port = container.port, repositoryName = repositoryName, username = "admin", password = "test"),
          profileQueries = false
        )
    } yield ImportServiceLive(config)
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

  def spec: Spec[Any, Throwable] = suite("Fuseki")(test("test rdf connection") {
    ZIO.scoped {
      for {
        service  <- ZIO.service[ImportService]
        _        <- service.configureFuseki()
        _        <- service.createDataset()

        filePath <- FileTestUtil.createTextFile(trigContent, ".trig")
        _        <- service.importTrigFile(filePath)
        hasAtLeastOneResultInNamedGraph <- service
                                             .querySelect(
                                               """
                                                 |SELECT ?subject ?predicate ?object
                                                 |FROM NAMED <http://example.org/graph>
                                                 |WHERE {
                                                 |  GRAPH <http://example.org/graph> {
                                                 |    ?subject ?predicate ?object.
                                                 |  }
                                                 |}
                                                 |LIMIT 1
                                                 |""".stripMargin
                                             )
                                             .map(_.hasNext)
        hasAtLeastOneResultInDefaultGraph <- service
                                               .querySelect(
                                                 """
                                                   |SELECT ?subject ?predicate ?object
                                                   |WHERE {
                                                   |  ?subject ?predicate ?object.
                                                   |}
                                                   |LIMIT 1
                                                   |""".stripMargin
                                               )
                                               .map(_.hasNext)
        _ <- ZIO.logDebug("loaded")
      } yield assertTrue(hasAtLeastOneResultInNamedGraph, hasAtLeastOneResultInDefaultGraph)
    }
  })
    .provideSomeLayer[FusekiTestContainer](importServiceTestLayer)
    .provideSomeLayerShared(FusekiTestContainer.layer)
}

object FileTestUtil {
  def createTextFile(content: String, suffix: String): ZIO[Scope, IOException, Path] = for {
    filePath <- Files.createTempFileScoped(suffix)
    _        <- Files.writeBytes(filePath, Chunk.fromIterable(content.getBytes))
  } yield filePath
}
