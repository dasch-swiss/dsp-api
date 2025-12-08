/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import net.datafaker.Faker
import sttp.client4.UriContext
import sttp.model.StatusCode
import zio.*
import zio.json.ast.Json
import zio.logging.*
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
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.testservices.TestClientsModule

abstract class E2EZSpec extends ZIOSpec[E2EZSpec.Environment] {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance
  val faker: Faker                 = new Faker()

  private val testLogger: ULayer[Unit] = Runtime.removeDefaultLoggers >>> consoleLogger(
    config = {
      val default = ConsoleLoggerConfig.default
      default.copy(filter = default.filter.withRootLevel(LogLevel.Error))
    },
  )

  override val bootstrap: ULayer[E2EZSpec.Environment] =
    testLogger >>>
      TestContainerLayers.all >+>
      LayersLive.remainingLayer >+>
      ApiModule.layer >+>
      TestClientsModule.layer

  def rdfDataObjects: List[RdfDataObject] = List.empty

  type env = E2EZSpec.Environment  & Scope

  private def prepare = for {
    _ <- Db.initWithTestData(rdfDataObjects)
    _ <- ZIO.serviceWithZIO[CacheManager](_.clearAll())
    _ <- (DspApiServer.startup *> ZIO.never).provideSomeAuto(DspApiServer.layer).forkScoped
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
