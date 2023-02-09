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
import zio.http.{Body, Client, Path}
import zio.json.{DecoderOps, DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import zio.stream.ZStream

import java.nio.file.{Files, Paths}
import java.io.File

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
  final case class UploadResponse(files: Seq[UploadedFile])

  implicit lazy val encUploadedFiles: JsonEncoder[UploadResponse] = DeriveJsonEncoder.gen[UploadResponse]
  implicit lazy val decUploadedFiles: JsonDecoder[UploadResponse] = DeriveJsonDecoder.gen[UploadResponse]

  "The Knora/Sipi integration" should {
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

      val url = s"$baseInternalSipiUrl/upload_without_transcoding?token=$loginToken"
      val request =
        Client.request(
          url,
          Method.POST,
          Headers.Header("Content-Type", "application/json"),
          Body.fromStream(ZStream.fromPath(pathToRosettaSidecar))
        )

      val upload = for {
        res             <- request
        responseOrError <- res.body.asString.map(_.fromJson[UploadResponse])
        lr <- responseOrError match {
                case Left(error) =>
                  Console.printLineError(res) *>
                    Console.printLineError(error) *>
                    ZIO.dieMessage(
                      "JSON deserialization error: " + error
                    )
                case Right(response) => ZIO.succeed(response)
              }
      } yield lr

      val loginResponse = Unsafe.unsafe { implicit u =>
        Runtime.default.unsafe
          .run(upload.provide(Client.default, Scope.default))
          .getOrThrow()
      }

      assert(loginResponse.files.nonEmpty)
    }
  }
}
