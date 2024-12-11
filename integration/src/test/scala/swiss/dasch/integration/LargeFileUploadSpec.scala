/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.integration

import sttp.capabilities.zio.ZioStreams
import sttp.client3
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.client3.{SttpBackend, basicRequest}
import sttp.model.{Header, Uri}
import swiss.dasch.integration.testcontainers.{DspIngestTestContainer, SharedVolumes}
import zio.*
import zio.nio.file.Files
import zio.stream.ZStream
import zio.test.*

object LargeFileUploadSpec extends ZIOSpecDefault {

  private val client = ZIO.serviceWithZIO[TestIngestClient]

  val filename = s"test.txt"

  extension (num: Long) {
    def KB: Long = num * 1024
    def MB: Long = num.KB * 1024
    def GB: Long = num.MB * 1024

    def pretty: String = {
      val (value, unit) =
        if (num < 1.KB) (num, "B")
        else if (num < 1.MB) (num / 1.KB, "KB")
        else if (num < 1.GB) (num / (1.MB), "MB")
        else (num / (1.GB), "GB")
      s"$value$unit"
    }
  }

  private val contentLength = 1.MB
  val spec = suite("Large Files")(
    test(
      "uploading a large file should work " +
        "given the file is larger than the available memory of the container " +
        "/projects/:shortcode/bulk-ingest/ingest/:filename",
    ) {
      for {
        _        <- client(_.info).tap(Console.printLine(_))
        response <- client(_.uploadRandomFile(contentLength, filename))
        _        <- Console.printLine(s"upload response: $response")
        tmpFile  <- ZIO.serviceWith[SharedVolumes.Temp](_.asPath / "import" / "0001" / filename)
        size     <- Files.size(tmpFile)
        _        <- Console.printLine(s"file size: ${size.pretty}")
      } yield assertTrue(size >= contentLength)
    },
  ).provide(
    TestIngestClient.layer,
    DspIngestTestContainer.layer(300.MB),
    SharedVolumes.layer,
    HttpClientZioBackend.layer(),
  ) @@ TestAspect.withLiveRandom @@ TestAspect.withLiveClock @@ TestAspect.timeout(40.minutes)
}

final case class TestIngestClient(backend: SttpBackend[Task, ZioStreams], container: DspIngestTestContainer) {
  private val baseUrl = {
    val urlAsString = s"http://localhost:" + container.getMappedPort(3340)
    Uri.parse(urlAsString).getOrElse(throw new IllegalArgumentException(s"Invalid URL: $urlAsString"))
  }

  def info: ZIO[Any, Throwable, String] =
    basicRequest.get(baseUrl.withPath("info")).send(backend).map(_.body.fold(identity, identity))

  def uploadRandomFile(contentLength: Long, filename: String): Task[String] =
    ZIO.scoped {
      val randomByte   = ZIO.random.flatMap(_.nextPrintableChar.map(_.toByte))
      val randomStream = ZStream.fromZIO(randomByte).repeat(Schedule.forever).take(contentLength)
      basicRequest
        .post(baseUrl.withPath(Seq("projects", "0001", "bulk-ingest", "ingest", filename)))
        .streamBody(ZioStreams)(randomStream)
        .header(Header.authorization("Bearer", "token"))
        .readTimeout(3.minutes.asScala)
        .send(backend)
        .flatMap(response => ZIO.fromEither(response.body).mapError(new Exception(_)))
    }
}

object TestIngestClient {
  val layer = ZLayer.derive[TestIngestClient]
}
