/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.URLayer
import zio.ZIO
import zio.ZLayer
import zio.nio.file.Path
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.config.Fuseki
import org.knora.webapi.config.Triplestore
import org.knora.webapi.testcontainers.FusekiTestContainer

object ImportServiceIT extends ZIOSpecDefault {

  private val importServiceTestLayer: URLayer[FusekiTestContainer, ImportServiceLive] = ZLayer.fromZIO {
    for {
      container <- ZIO.service[FusekiTestContainer]
      config =
        Triplestore(
          dbtype = "mem",
          useHttps = false,
          host = container.host,
          queryTimeout = "Duration.Undefined",
          gravsearchTimeout = "Duration.Undefined",
          autoInit = false,
          fuseki = Fuseki(port = container.port, repositoryName = "knora-test", username = "admin", password = "test"),
          profileQueries = false
        )
    } yield ImportServiceLive(config)
  }
  val triFile = "/Users/christian/git/dasch/dsp-api/webapi/src/it/resources/anything.trig"

  def spec: Spec[Any, Throwable] = suite("Fuseki")(test("test rdf connection") {
    for {
      service             <- ZIO.service[ImportService]
      _                   <- service.createDataset("knora-test")
      _                   <- service.importTrigFile(Path(triFile))
      hasAtLeastOneResult <- ZIO.scoped(service.querySelect("SELECT * WHERE { ?s ?p ?o } LIMIT 1").map(_.hasNext))
      _                   <- ZIO.logDebug("loaded")
    } yield assertTrue(hasAtLeastOneResult)
  })
    .provideSomeLayer[FusekiTestContainer](importServiceTestLayer)
    .provideSomeLayerShared(FusekiTestContainer.layer)
}
