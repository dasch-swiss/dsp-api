/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.cache

import akka.actor
import akka.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import zio._
import zio.logging.backend.SLF4J

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.LayersTest
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.sharedtestdata.SharedTestDataV1

class CacheUtilSpec
    extends TestKit(actor.ActorSystem("CacheUtilSpec"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  type Environment = LayersTest.DefaultTestEnvironmentWithoutSipi

  /**
   * The effect layers from which the App is built.
   */
  lazy val effectLayers = LayersTest.defaultLayersTestWithoutSipi

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  private val bootstrap: ZLayer[
    Any,
    Any,
    Environment
  ] = ZLayer.empty ++ Runtime.removeDefaultLoggers ++ SLF4J.slf4j ++ effectLayers

  /**
   * Create a configured runtime
   */
  val runtime = Unsafe.unsafe { implicit u =>
    Runtime.unsafe
      .fromLayer(bootstrap)
  }

  /**
   * Create router and config by unsafe running them.
   */
  private val appConfig =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for { config <- ZIO.service[AppConfig] } yield config
        )
        .getOrThrowFiberFailure()
    }

  final override def beforeAll(): Unit = {
    CacheUtil.removeAllCaches()
    CacheUtil.createCaches(appConfig.cacheConfigs)
  }

  final override def afterAll(): Unit = {
    CacheUtil.removeAllCaches()
    TestKit.shutdownActorSystem(system)
  }

  private val cacheName = Authenticator.AUTHENTICATION_INVALIDATION_CACHE_NAME
  private val sessionId = java.lang.System.currentTimeMillis().toString

  "Caching" should {

    "allow to set and get the value " in {
      CacheUtil.put(cacheName, sessionId, SharedTestDataV1.rootUser)
      CacheUtil.get(cacheName, sessionId) should be(Some(SharedTestDataV1.rootUser))
    }

    "return none if key is not found " in {
      CacheUtil.get(cacheName, 213.toString) should be(None)
    }

    "allow to delete a set value " in {
      CacheUtil.remove(cacheName, sessionId)
      CacheUtil.get(cacheName, sessionId) should be(None)
    }
  }
}
