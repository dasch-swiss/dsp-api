/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.sipi

import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.v2.routing.authenticationmessages.{AuthenticationV2Serialization, LoginResponse}
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import zio._
import zio.http.model.{Headers, Method}
import zio.http.{Body, Client}
import zio.json.{DecoderOps, DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import zio.stream.ZStream

import java.nio.file.{Files, Paths}
import zio.json.JsonCodec
import zio.json.DeriveJsonCodec
import zio.http.Response
import java.io.File
import zio.http.Request
import zio.http.Path
import zio.http.URL

/**
 * Tests uploading files to Sipi through the /upload_without_transcoding route.
 */
class SipiUploadWithoutTranscodingITSpec
    extends ITKnoraLiveSpec
    with AuthenticationV2Serialization
    with TriplestoreJsonProtocol {

  private val anythingUserEmail = SharedTestDataADM.anythingAdminUser.email
  private val password          = SharedTestDataADM.testPass

  private val rosettaBaseFilename = "1234567890"
  private val pathToRosettaOriginal =
    Paths.get("..", s"test_data/sipi/SipiUploadWithoutTranscodingITSpec/$rosettaBaseFilename.png.orig")
  private val pathToRosettaDerivative =
    Paths.get("..", s"test_data/sipi/SipiUploadWithoutTranscodingITSpec/$rosettaBaseFilename.jp2")
  private val pathToRosettaSidecar =
    Paths.get("..", s"test_data/sipi/SipiUploadWithoutTranscodingITSpec/$rosettaBaseFilename.info")

  /**
   * Represents the information that Sipi returns about a file that was uploaded.
   *
   * @param filename          the file's filename
   * @param checksum          the file's checksum
   * @param checksumAlgorithm the algorithm used to calculate the checksum
   */
  final case class UploadedFile(
    filename: String,
    checksum: String,
    checksumAlgorithm: String
  )

  implicit lazy val codecUploadedFile: JsonCodec[UploadedFile] = DeriveJsonCodec.gen[UploadedFile]

  /**
   * Represents the information that Sipi is returning after the /upload_without_transcoding
   * route is called. The tuple contains the uploaded filename and checksum.
   */
  final case class UploadResponse(files: Seq[UploadedFile])

  implicit lazy val codecUploadResponse: JsonCodec[UploadResponse] = DeriveJsonCodec.gen[UploadResponse]

  "The DSP-API/Sipi integration" should {
    var loginToken: String = ""

    "log in" in {

      val params =
        s"""
           |{
           |    "email": "$anythingUserEmail",
           |    "password": "$password"
           |}
                """.stripMargin
      val url = s"$baseApiUrl/v2/authentication"
      val request =
        Client.request(url, Method.POST, Headers.Header("Content-Type", "application/json"), Body.fromString(params))

      val login = for {
        res          <- request
        tokenOrError <- res.body.asString.map(_.fromJson[LoginResponse].map(_.token))
        token <- tokenOrError match {
                   case Left(error)  => ZIO.dieMessage(error)
                   case Right(token) => ZIO.succeed(token)
                 }
      } yield token

      loginToken = Unsafe.unsafe { implicit u =>
        Runtime.default.unsafe
          .run(login.provide(Client.default, Scope.default))
          .getOrThrow()
      }

      assert(loginToken.nonEmpty)
    }

    "upload set of files to sipi" in {

      // The files to be uploaded.
      assert(Files.exists(pathToRosettaOriginal), s"File $pathToRosettaOriginal does not exist")
      assert(Files.exists(pathToRosettaDerivative), s"File $pathToRosettaDerivative does not exist")
      assert(Files.exists(pathToRosettaSidecar), s"File $pathToRosettaSidecar does not exist")

      val url  = s"$baseInternalSipiUrl/upload_without_transcoding?token=$loginToken"
      val test = "http://127.0.0.1/post"
      val request =
        Client.request(
          test,
          Method.POST,
          Headers.Header("Content-Type", "multipart/form-data"),
          Body.fromStream(ZStream.fromPath(pathToRosettaOriginal).++(ZStream.fromPath(pathToRosettaDerivative)))
        )

      val upload = for {
        res             <- request
        bodyString      <- res.body.asString
        responseOrError <- res.body.asString.map(_.fromJson[UploadResponse])
        lr <- responseOrError match {
                case Left(error) =>
                  Console.printLineError(res) *>
                    Console.printLineError(s"body: $bodyString") *>
                    ZIO.dieMessage(
                      "JSON deserialization error: " + error
                    )
                case Right(response) => ZIO.succeed(response)
              }
      } yield lr

      val uploadResponse: UploadResponse = Unsafe.unsafe { implicit u =>
        Runtime.default.unsafe
          .run(upload.provide(Client.default, Scope.default))
          .getOrThrow()
      }

      assert(uploadResponse.files.nonEmpty)
    }
  }
}
