/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.sipi

import akka.http.scaladsl.model._
import org.knora.webapi._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.v2.routing.authenticationmessages._
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import zio._
import zio.http.Client
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import java.nio.file.{Files, Paths}

/**
 * Tests uploading files to Sipi through the /upload_without_transcoding route.
 */
class SipiUploadWithoutTranscodingITSpec
    extends ITKnoraLiveSpec
    with AuthenticationV2JsonProtocol
    with TriplestoreJsonProtocol {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val anythingUserEmail   = SharedTestDataADM.anythingAdminUser.email
  private val incunabulaUserEmail = SharedTestDataADM.incunabulaMemberUser.email
  private val password            = SharedTestDataADM.testPass

  private val rosettaBaseFilename = "1hAvLiMH5Tr-Ds74NVS69Gv"
  private val pathToRosettaOriginal =
    Paths.get("..", s"test_data/test_route/sipi/SipiUploadWithoutTranscodingITSpec/$rosettaBaseFilename.png.org")
  private val pathToRosettaDerivative =
    Paths.get("..", s"test_data/test_route/sipi/SipiUploadWithoutTranscodingITSpec/$rosettaBaseFilename.jp2")
  private val pathToRosettaSidecar =
    Paths.get("..", s"test_data/test_route/sipi/SipiUploadWithoutTranscodingITSpec/$rosettaBaseFilename.info")

  /**
   * Represents the information that Sipi returns about a file that was uploaded.
   *
   * @param filename the file's filename.
   * @param checksum the file's checksum.
   */
  final case class UploadedFile(
    filename: String,
    checksum: String
  )

  implicit lazy val encUploadedFile: JsonEncoder[UploadedFile] = DeriveJsonEncoder.gen[UploadedFile]
  implicit lazy val decUploadedFile: JsonDecoder[UploadedFile] = DeriveJsonDecoder.gen[UploadedFile]

  /**
   * Represents the information that Sipi is returning after the /upload_without_transcoding
   * route is called. The tuple contains the uploaded filename and checksum.
   */
  case class UploadResponse(files: Seq[UploadedFile])

  implicit lazy val encUploadedFiles: JsonEncoder[UploadResponse] = DeriveJsonEncoder.gen[UploadResponse]
  implicit lazy val decUploadedFiles: JsonDecoder[UploadResponse] = DeriveJsonDecoder.gen[UploadResponse]

  "The Knora/Sipi integration" should {
    var loginToken: String = ""

    "log in" in {

      val url = s"$baseApiUrl/v2/authentication"

      val program = for {
        res  <- Client.request(url)
        data <- res.body.asString
        _    <- Console.printLine(data)
      } yield ()

      Unsafe.unsafe { implicit u =>
        Runtime.default.unsafe
          .run(program.provide(Client.default, Scope.default))
          .getOrThrow()
      }

      val params =
        s"""
           |{
           |    "email": "$anythingUserEmail",
           |    "password": "$password"
           |}
                """.stripMargin

      val request                = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK)

    }

    "upload set of files to sipi" in {

      // The files to be uploaded.
      assert(Files.exists(pathToRosettaOriginal), s"File $pathToRosettaOriginal does not exist")
      assert(Files.exists(pathToRosettaDerivative), s"File $pathToRosettaDerivative does not exist")
      assert(Files.exists(pathToRosettaSidecar), s"File $pathToRosettaSidecar does not exist")

    }
  }
}
