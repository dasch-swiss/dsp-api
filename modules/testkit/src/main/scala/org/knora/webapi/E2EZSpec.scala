/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import io.opentelemetry.api
import net.datafaker.Faker
import sttp.client4.UriContext
import sttp.model.StatusCode
import zio.*
import zio.json.ast.Json
import zio.logging.*
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.test.*
import zio.test.Assertion.*

import scala.reflect.ClassTag

import org.knora.webapi.core.Db
import org.knora.webapi.core.DspApiServer
import org.knora.webapi.core.LayersLive
import org.knora.webapi.core.TestContainerLayers
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.slice.api.ApiModule
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.OtelSetup
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.testservices.TestClientsModule

abstract class E2EZSpec extends ZIOSpec[E2EZSpec.Environment] {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance
  val faker: Faker                 = new Faker()

  /**
   * The OpenTelemetry layer the application under test runs with. Defaults to the stdout-exporter setup;
   * instrumentation specs override it (e.g. with an in-memory span exporter) to assert on emitted spans.
   * `bootstrap` is `lazy` so an override that depends on subclass state is initialised in time.
   */
  protected def otelLayer: ULayer[api.OpenTelemetry & Tracing & ContextStorage] = OtelSetup.layer

  override lazy val bootstrap: ULayer[E2EZSpec.Environment] =
    E2EZSpec.testLogger >>>
      TestContainerLayers.all >+>
      LayersLive.remainingLayer(otelLayer) >+>
      ApiModule.layer >+>
      TestClientsModule.layer

  def rdfDataObjects: List[RdfDataObject] = List.empty

  type env = E2EZSpec.Environment & Scope

  private def prepare = for {
    _ <- Db.initWithTestData(rdfDataObjects)
    _ <- ZIO.serviceWithZIO[CacheManager](_.clearAll())
    // Build the server layer into the spec Scope rather than forking `startup *> ZIO.never`:
    // the forked keepalive fiber's interruption tangled with its nested layer scope and hung
    // suite teardown ~60s before reaching the zio-http shutdown.
    built <- DspApiServer.layer.build
    _     <- built.get[DspApiServer].startup()
    // wait max 5 seconds until api is ready
    _ <- TestApiClient
           .getJson[Json](uri"/version")
           .repeatWhile(_.code != StatusCode.Ok)
           .retry(Schedule.fixed(10.milli))
           .timeout(5.seconds)
           .orDie
    _ <- ZIO.logInfo("API is ready, start running tests...")
  } yield ()

  def e2eSpec: Spec[env, Any]

  final override def spec: Spec[env, Any] =
    e2eSpec
      @@ TestAspect.beforeAll(prepare)
      @@ TestAspect.sequential
      @@ TestAspect.withLiveEnvironment
}

object E2EZSpec {

  // zio-test's sbt runner installs every spec's `bootstrap` into a shared runtime, so a per-spec
  // `consoleLogger(...)` layer adds a fresh logger instance for each E2EZSpec subclass in the run,
  // duplicating every log line once per spec class. `FiberRef.currentLoggers` is a Set, so
  // installing this single shared logger instance instead is idempotent.
  private val sharedConsoleLogger: ZLogger[String, Any] = {
    val config = {
      val default = ConsoleLoggerConfig.default
      default.copy(filter = default.filter.withRootLevel(LogLevel.Error))
    }
    Unsafe.unsafe(implicit u => Runtime.default.unsafe.run(makeConsoleLogger(config)).getOrThrowFiberFailure())
  }

  private val testLogger: ULayer[Unit] = Runtime.removeDefaultLoggers >>> Runtime.addLogger(sharedConsoleLogger)

  type Environment =
    // format: off
    LayersLive.Environment &
    ApiModule.Provided &
    TestClientsModule.Provided &
    TestContainerLayers.Environment
    // format: on

  def failsWithMessageEqualTo[A <: Throwable](messsage: String)(implicit
    tag: ClassTag[A],
  ): Assertion[Exit[Any, Any]] =
    fails(isSubtype[A](hasMessage(equalTo(messsage))))

  def failsWithMessageContaining[A <: Throwable](messsage: String)(implicit
    tag: ClassTag[A],
  ): Assertion[Exit[Any, Any]] =
    fails(isSubtype[A](hasMessage(containsString(messsage))))
}
