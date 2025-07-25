/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import com.typesafe.scalalogging.*
import org.apache.pekko
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.client.RequestBuilding
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.testkit.ImplicitSender
import org.apache.pekko.testkit.TestKitBase
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.*

import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.SECONDS

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.Db
import org.knora.webapi.core.TestStartupUtils
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.testservices.TestClientService
import org.knora.webapi.util.TestDataFileUtil

/**
 * This class can be used in End-to-End testing. It starts the DSP stack
 * and provides access to settings and logging.
 */
abstract class E2ESpec
    extends AnyWordSpec
    with TestKitBase
    with TestStartupUtils
    with TriplestoreJsonProtocol
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll
    with RequestBuilding
    with ImplicitSender {

  implicit val runtime: Runtime.Scoped[core.LayersTest.Environment] =
    Unsafe.unsafe(implicit u => Runtime.unsafe.fromLayer(util.Logger.text() >>> core.LayersTest.layer))

  lazy val appConfig: AppConfig                        = UnsafeZioRun.service[AppConfig]
  implicit lazy val system: ActorSystem                = UnsafeZioRun.service[ActorSystem]
  implicit lazy val executionContext: ExecutionContext = system.dispatcher
  lazy val rdfDataObjects                              = List.empty[RdfDataObject]
  val log: Logger                                      = Logger(this.getClass)

  // needed by some tests
  val baseApiUrl: String          = appConfig.knoraApi.internalKnoraApiBaseUrl
  val baseInternalSipiUrl: String = appConfig.sipi.internalBaseUrl

  // the default timeout for all tests
  implicit val timeout: FiniteDuration = FiniteDuration(10, SECONDS)

  final override def beforeAll(): Unit = UnsafeZioRun.runOrThrow(Db.initWithTestData(rdfDataObjects))

  final override def afterAll(): Unit =
    /* Stop ZIO runtime and release resources (e.g., running docker containers) */
    Unsafe.unsafe(implicit u => runtime.unsafe.shutdown())

  protected def singleAwaitingRequest(
    request: HttpRequest,
    timeout: Option[zio.Duration] = None,
    printFailure: Boolean = true,
  ): HttpResponse =
    UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[TestClientService](_.singleAwaitingRequest(request, timeout, printFailure)),
    )

  protected def singleAwaitingRequest(request: HttpRequest, duration: zio.Duration): HttpResponse =
    UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[TestClientService](_.singleAwaitingRequest(request, Some(duration))))

  protected def getResponseAsString(request: HttpRequest): String =
    UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[TestClientService](_.getResponseString(request)))

  protected def checkResponseOK(request: HttpRequest): Unit =
    UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[TestClientService](_.checkResponseOK(request)))

  protected def getResponseAsJsonLD(request: HttpRequest): JsonLDDocument =
    UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[TestClientService](_.getResponseJsonLD(request)))

  protected def responseToJsonLDDocument(httpResponse: HttpResponse): JsonLDDocument = {
    val responseBodyFuture: Future[String] =
      httpResponse.entity.toStrict(FiniteDuration(10L, TimeUnit.SECONDS)).map(_.data.decodeString("UTF-8"))
    val responseBodyStr = Await.result(responseBodyFuture, FiniteDuration(10L, TimeUnit.SECONDS))
    JsonLDUtil.parseJsonLD(responseBodyStr)
  }

  protected def responseToString(httpResponse: HttpResponse): String = {
    val responseBodyFuture: Future[String] =
      httpResponse.entity.toStrict(FiniteDuration(10L, TimeUnit.SECONDS)).map(_.data.decodeString("UTF-8"))
    Await.result(responseBodyFuture, FiniteDuration(10L, TimeUnit.SECONDS))
  }

  protected def doGetRequest(urlPath: String): String = {
    val request                = Get(s"$baseApiUrl$urlPath")
    val response: HttpResponse = singleAwaitingRequest(request)
    responseToString(response)
  }

  def readTestData(folder: String, filename: String): String =
    TestDataFileUtil.readTestData(folder, filename, appConfig.sipi)
}
