/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.impl

import akka.http.javadsl.server.AuthenticationFailedRejection
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfigForTestContainers
import dsp.errors.TriplestoreTimeoutException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.testcontainers.FusekiTestContainer
import zio._
import zio.test.Assertion._
import zio.test._

/**
 * This spec is used to test [[org.knora.webapi.store.triplestore.impl.TriplestoreServiceHttpConnectorImpl]].
 */
object TriplestoreServiceHttpConnectorImplZSpec extends ZIOSpecDefault {

  /**
   * Defines a layer which encompases all dependencies that are needed for
   * running the tests. `bootstrap` overrides the base layer of ZIOApp.
   */
  val testLayer =
    ZLayer.make[TriplestoreService](
      TriplestoreServiceHttpConnectorImpl.layer,
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      FusekiTestContainer.layer
    )

  def spec = suite("TriplestoreServiceHttpConnectorImplSpec")(
    test("successfully simulate a timeout") {
      for {
        result <- TriplestoreService.doSimulateTimeout().exit
      } yield assertTrue(
        result.is(_.die) == TriplestoreTimeoutException(
          "The triplestore took too long to process a request. This can happen because the triplestore needed too much time to search through the data that is currently in the triplestore. Query optimisation may help."
        )
      )
    }
  ).provideLayer(testLayer) @@ TestAspect.sequential
}
