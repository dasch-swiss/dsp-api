/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl.api

import org.apache.pekko.actor.*
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import zio.*
import zio.Scope
import zio.nio.file.Files
import zio.test.*

import scala.concurrent.Await
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future

import org.knora.webapi.slice.shacl.domain.ShaclValidator

object ShaclApiServiceSpec extends ZIOSpecDefault {

  private val shaclApiService = ZIO.serviceWithZIO[ShaclApiService]

  val spec: Spec[TestEnvironment & Scope, Any] = suite("ShaclApiService")(
    test("validate") {
      for {
        data    <- Files.createTempFile("data.ttl", None, Seq.empty)
        shacl   <- Files.createTempFile("shacl.ttl", None, Seq.empty)
        formData = ValidationFormData(data.toFile, shacl.toFile, None, None, None)
        result  <- shaclApiService(_.validate(formData))
      } yield assertTrue(reportConforms(result))
    },
  ).provide(ShaclApiService.layer, ShaclValidator.layer)

  private def reportConforms(result: Source[ByteString, Any]): Boolean = {
    implicit val system: ActorSystem          = ActorSystem.create()
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    implicit val mat: Materializer            = Materializer(system)

    val str: Future[String] = result
      .runWith(Sink.fold(ByteString.empty)(_ ++ _)) // Accumulate ByteString
      .map(_.utf8String)
    val reportStr = Await.result(str, 5.seconds.asScala)
    reportStr.contains("sh:conforms  true")
  }
}
