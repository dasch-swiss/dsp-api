/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import sttp.client4.Response
import sttp.client4.UriContext
import sttp.model.StatusCode
import zio.*
import zio.http.*
import zio.json.ast.Json
import zio.test.*
import zio.test.Assertion.*

import scala.reflect.ClassTag

import org.knora.webapi.core.Db
import org.knora.webapi.core.DspApiServer
import org.knora.webapi.core.LayersTest
import org.knora.webapi.core.TestStartupUtils
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.testservices.TestApiClient

abstract class E2EZSpec extends ZIOSpecDefault with TestStartupUtils {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testLayers = org.knora.webapi.util.Logger.testSafe() >>> LayersTest.layer

  def rdfDataObjects: List[RdfDataObject] = List.empty

  type env = LayersTest.Environment with Client with Scope

  private def prepare = for {
    _ <- Db.initWithTestData(rdfDataObjects)
    _ <- (DspApiServer.startup *> ZIO.never).provideSomeAuto(DspApiServer.layer).fork
    // wait max 5 seconds until api is ready
    _ <- TestApiClient
           .getJson[Json](uri"/version")
           .repeatWhile(_.code != StatusCode.Ok)
           .retry(Schedule.exponential(10.milli))
           .timeout(5.seconds)
           .orDie
    _ <- ZIO.logInfo("API is ready, start running tests...")
  } yield ()

  def e2eSpec: Spec[env, Any]

  final override def spec = (
    e2eSpec
      @@ TestAspect.beforeAll(prepare)
      @@ TestAspect.sequential
  ).provideShared(testLayers, Client.default, Scope.default)
    @@ TestAspect.withLiveEnvironment
}

object E2EZSpec {
  def failsWithMessageEqualTo[A <: Throwable](messsage: String)(implicit
    tag: ClassTag[A],
  ): Assertion[Exit[Any, Any]] =
    fails(isSubtype[A](hasMessage(equalTo(messsage))))

  def failsWithMessageContaining[A <: Throwable](messsage: String)(implicit
    tag: ClassTag[A],
  ): Assertion[Exit[Any, Any]] =
    fails(isSubtype[A](hasMessage(containsString(messsage))))
}
