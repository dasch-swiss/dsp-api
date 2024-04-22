/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import com.typesafe.scalalogging.Logger
import org.apache.pekko.actor
import org.apache.pekko.testkit.ImplicitSender
import org.apache.pekko.testkit.TestKitBase
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.ZIO
import zio._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.SECONDS

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.AppRouter
import org.knora.webapi.core.AppServer
import org.knora.webapi.core.LayersTest.DefaultTestEnvironmentWithoutSipi
import org.knora.webapi.core.TestStartupUtils
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.util.LogAspect

abstract class CoreSpec
    extends AnyWordSpec
    with TestKitBase
    with TestStartupUtils
    with Matchers
    with BeforeAndAfterAll
    with ImplicitSender {

  /**
   * The `Environment` that we require to exist at startup.
   * Can be overriden in specs that need other implementations.
   */
  type Environment = core.LayersTest.DefaultTestEnvironmentWithoutSipi

  /**
   * The effect layers from which the App is built.
   * Can be overriden in specs that need other implementations.
   */
  lazy val effectLayers = core.LayersTest.integrationTestsWithFusekiTestcontainers()

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  private val bootstrap = util.Logger.text() >>> effectLayers

  // create a configured runtime
  implicit val runtime: Runtime.Scoped[DefaultTestEnvironmentWithoutSipi] = Unsafe.unsafe { implicit u =>
    Runtime.unsafe.fromLayer(bootstrap)
  }

  def getService[R: Tag](implicit runtime: Runtime[R]): R = UnsafeZioRun.runOrThrow(ZIO.service[R])

  def runOrThrowWithService[R: Tag, A](run: R => A)(implicit runtime: Runtime[R]): A =
    UnsafeZioRun.runOrThrow(ZIO.service[R].map(run))

  // An effect for getting stuff out, so that we can pass them
  // to some legacy code
  private val routerAndConfig = for {
    router <- ZIO.service[core.AppRouter]
    config <- ZIO.service[AppConfig]
  } yield (router, config)

  /**
   * Create router and config by unsafe running them.
   */
  private val (router: AppRouter, config: AppConfig) =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          routerAndConfig,
        )
        .getOrThrowFiberFailure()
    }

  implicit lazy val system: actor.ActorSystem          = router.system
  implicit lazy val executionContext: ExecutionContext = system.dispatcher
  lazy val rdfDataObjects                              = List.empty[RdfDataObject]
  val log: Logger                                      = Logger(this.getClass())
  val appActor                                         = router.ref

  // needed by some tests
  val appConfig = config

  // the default timeout for all tests
  implicit val timeout: FiniteDuration = FiniteDuration(10, SECONDS)

  final override def beforeAll(): Unit =
    /* Here we start our app and initialize the repository before each suit runs */
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          (for {
            _ <- AppServer.testWithoutSipi
            _ <- prepareRepository(rdfDataObjects) @@ LogAspect.logSpan("prepare-repo")
          } yield ()),
        )
        .getOrThrow()

    }

  final override def afterAll(): Unit =
    /* Stop ZIO runtime and release resources (e.g., running docker containers) */
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.shutdown()
    }

  protected def createJwtTokenString(user: User) = ZIO.serviceWithZIO[JwtService](_.createJwt(user)).map(_.jwtString)
}
