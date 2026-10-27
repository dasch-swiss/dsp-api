/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest

import org.junit.runner.RunWith
import swiss.dasch.config.Configuration.StorageConfig
import zio.*
import zio.http.*
import zio.nio.file.Files
import zio.nio.file.Path as NioPath
import zio.test.*

import java.util.concurrent.atomic.AtomicReference

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.SystemUser
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.InvalidTokenCache
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.infrastructure.JwtServiceLive
import org.knora.webapi.slice.infrastructure.Scope as AuthScope
import org.knora.webapi.slice.ingest.IngestModuleSpec.serve

@RunWith(classOf[DspZTestJUnitRunner])
class IngestServerEndpointsSpec extends ZIOSpecDefault {

  /** The routes of the standalone ingest service, each of which dsp-api serves under `/ingest/`. */
  private val standaloneRoutes = List(
    "GET /info",
    "GET /health",
    "GET /metrics",
    "GET /projects",
    "GET /projects/{shortcode}",
    "GET /projects/{shortcode}/checksumreport",
    "DELETE /projects/{shortcode}/erase",
    "GET /projects/{shortcode}/assets/{assetId}",
    "GET /projects/{shortcode}/assets/{assetId}/original",
    "POST /projects/{shortcode}/assets/ingest/{filename}",
    "POST /projects/{shortcode}/bulk-ingest",
    "POST /projects/{shortcode}/bulk-ingest/finalize",
    "GET /projects/{shortcode}/bulk-ingest/mapping.csv",
    "POST /projects/{shortcode}/export",
    "POST /projects/{shortcode}/import",
    "POST /projects/{shortcode}/bulk-ingest/ingest/{file}",
    "POST /maintenance/{name}",
    "GET /maintenance/needs-top-left-correction",
    "GET /maintenance/was-top-left-correction-applied",
    "POST /report/asset-overview",
  )

  /** Records the thread each log line is written on, in this fiber and in the fibers it forks. */
  private val logThreads: URIO[Scope, AtomicReference[List[(String, String)]]] =
    ZIO.succeed(AtomicReference(List.empty[(String, String)])).tap { seen =>
      ZIO.withLoggerScoped(ZLogger.simple[String, Unit] { msg =>
        seen.updateAndGet((msg, Thread.currentThread().getName) :: _): Unit
      })
    }

  /** The threads `zio` and the fibers it forks resume on. */
  private def resumeThreads[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, Set[String]] = {
    val threads    = AtomicReference(Set.empty[String])
    val supervisor = new Supervisor[Unit] {
      def value(implicit trace: Trace): UIO[Unit] = ZIO.unit
      def onStart[R1, E1, A1](
        environment: ZEnvironment[R1],
        effect: ZIO[R1, E1, A1],
        parent: Option[Fiber.Runtime[Any, Any]],
        fiber: Fiber.Runtime[E1, A1],
      )(implicit unsafe: Unsafe): Unit                                                                        = ()
      def onEnd[R1, E1, A1](value: Exit[E1, A1], fiber: Fiber.Runtime[E1, A1])(implicit unsafe: Unsafe): Unit = ()
      override def onResume[E1, A1](fiber: Fiber.Runtime[E1, A1])(implicit unsafe: Unsafe): Unit              =
        threads.updateAndGet(_ + Thread.currentThread().getName): Unit
    }
    zio.supervised(supervisor).map(_ => threads.get())
  }

  private def adminToken =
    ZIO.serviceWithZIO[JwtService](_.createJwt(UserIri.unsafeFrom(SystemUser.id), AuthScope.admin))

  val spec = suite("IngestServerEndpoints")(
    test("serves every ingest endpoint under /ingest/") {
      for {
        endpoints <- ZIO.serviceWith[IngestServerEndpoints](_.serverEndpoints.map(_.endpoint))
        templates  = endpoints.map(e => e.method.fold("")(_.method + " ") + e.showPathTemplate(showQueryParam = None))
      } yield assertTrue(
        templates.forall(_.matches("[A-Z]+ /ingest/.*")),
        endpoints.forall(IngestServerEndpoints.isIngestRoute),
        templates.sorted == standaloneRoutes.map(_.replace(" /", " /ingest/")).sorted,
      )
    },
    test("runs a bulk-ingest request's forked job on an ingest thread") {
      for {
        storage <- ZIO.service[StorageConfig]
        _       <- Files.createDirectories(NioPath(storage.tempDir) / "import" / "0001")
        token   <- adminToken
        seen    <- logThreads
        request  = Request.post(URL(Path.root / "ingest" / "projects" / "0001" / "bulk-ingest"), Body.empty)
        status  <- serve(request.addHeader(Header.Authorization.Bearer(token.jwtString))).map(_.status)
        started  = ZIO.succeed(seen.get().find(_._1.startsWith("Starting bulk ingest")))
        thread  <- (ZIO.sleep(20.millis) *> started)
                    .repeatUntil(_.isDefined)
                    .timeoutFail("no bulk ingest started")(10.seconds)
                    .map(_.map(_._2))
      } yield assertTrue(status == Status.Accepted, thread.exists(_.startsWith("ingest-")))
    },
    test("runs /ingest/health off the ingest executor") {
      for {
        threads <- resumeThreads(serve(Request.get(URL(Path.root / "ingest" / "health"))))
      } yield assertTrue(threads.nonEmpty, !threads.exists(_.startsWith("ingest-")))
    },
    test("answers /ingest/info and not /info") {
      for {
        ingest <- serve(Request.get(URL(Path.root / "ingest" / "info")))
        root   <- serve(Request.get(URL(Path.root / "info")))
      } yield assertTrue(ingest.status == Status.Ok, root.status == Status.NotFound)
    },
  ).provide(IngestModuleSpec.layer, CacheManager.layer, InvalidTokenCache.layer, JwtServiceLive.layer, Scope.default) @@
    TestAspect.withLiveClock
}
