/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest

import org.junit.runner.RunWith
import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.StatusCode
import sttp.model.Uri
import zio.*
import zio.nio.file.Files
import zio.stream.ZStream
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.sharedtestdata.SharedTestDataADM.anythingAdminUser
import org.knora.webapi.slice.ingest.config.StorageConfig
import org.knora.webapi.testservices.TestApiClient

@RunWith(classOf[DspZTestJUnitRunner])
class IngestFileUploadE2ESpec extends E2EZSpec {

  private val filename      = "test.txt"
  private val contentLength = 1024L * 1024L

  private def uploadRandomFile(token: String) =
    for {
      uri <- ZIO.serviceWith[KnoraApi](api =>
               Uri.unsafeParse(s"${api.externalKnoraApiBaseUrl}/ingest/projects/0001/bulk-ingest/ingest/$filename"),
             )
      random    = ZStream.repeatZIO(Random.nextPrintableChar.map(_.toByte)).take(contentLength)
      response <- ZIO.scoped(
                    HttpClientZioBackend
                      .scoped()
                      .flatMap(
                        basicRequest
                          .post(uri)
                          .streamBody(ZioStreams)(random)
                          .contentLength(contentLength)
                          .auth
                          .bearer(token)
                          .response(asStringAlways)
                          .send(_),
                      ),
                  )
    } yield response

  val e2eSpec = suite("IngestFileUploadE2ESpec")(
    test("uploading a supported file to /ingest/projects/:shortcode/bulk-ingest/ingest/:filename should work") {
      for {
        token    <- ZIO.serviceWithZIO[TestApiClient](_.jwtFor(anythingAdminUser))
        response <- uploadRandomFile(token)
        tmpFile  <- ZIO.serviceWith[StorageConfig](_.tempPath / "import" / "0001" / filename)
        size     <- Files.size(tmpFile)
      } yield assertTrue(response.code == StatusCode.Ok, response.body == """{"status":"ok"}""", size >= contentLength)
    },
  ) @@ TestAspect.withLiveRandom
}
