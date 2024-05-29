/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.impl

import spray.json.*
import sttp.capabilities.zio.ZioStreams
import sttp.client3
import sttp.client3.*
import sttp.client3.SttpBackend
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.model.Uri
import zio.*
import zio.json.DecoderOps
import zio.nio.file.Path

import java.net.URI

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.Sipi
import org.knora.webapi.messages.store.sipimessages.*
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.Asset
import org.knora.webapi.slice.admin.domain.service.DspIngestClient
import org.knora.webapi.slice.infrastructure.Jwt
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.store.iiif.api.FileMetadataSipiResponse
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.errors.SipiException
import org.knora.webapi.util.SipiUtil

/**
 * Makes requests to Sipi.
 *
 * @param sipiConfig   The application's configuration
 * @param jwtService         The JWT Service to handle JWT Tokens
 * @param httpClient  The HTTP Client
 */
final case class SipiServiceLive(
  private val sipiConfig: Sipi,
  private val jwtService: JwtService,
  private val sttp: SttpBackend[Task, ZioStreams],
  private val dspIngestClient: DspIngestClient,
) extends SipiService {

  private object SipiRoutes {
    def file(asset: Asset): UIO[URI]           = makeUri(s"${assetBase(asset)}/file")
    def knoraJson(asset: Asset): UIO[URI]      = makeUri(s"${assetBase(asset)}/knora.json")
    private def makeUri(uri: String): UIO[URI] = ZIO.attempt(URI.create(uri)).logError.orDie
    private def assetBase(asset: Asset): String =
      s"${sipiConfig.internalBaseUrl}/${asset.belongsToProject.value}/${asset.internalFilename}"
  }

  /**
   * Asks Sipi for metadata about a file, served from the 'knora.json' route.
   *
   * @param filename the file name
   * @return a [[FileMetadataSipiResponse]] containing the requested metadata.
   */
  override def getFileMetadataFromSipiTemp(filename: String): Task[FileMetadataSipiResponse] =
    for {
      jwt <- jwtService.createJwt(KnoraSystemInstances.Users.SystemUser)
      request = quickRequest
                  .get(uri"${sipiConfig.internalBaseUrl}/tmp/$filename/knora.json")
                  .header("Authorization", s"Bearer ${jwt.jwtString}")
      body <- doSipiRequest(request)
      res <- ZIO
               .fromEither(body.fromJson[FileMetadataSipiResponse])
               .mapError(e => SipiException(s"Invalid response from Sipi: $e, $body"))
    } yield res

  override def getFileMetadataFromDspIngest(shortcode: Shortcode, assetId: AssetId): Task[FileMetadataSipiResponse] =
    for {
      response <- dspIngestClient.getAssetInfo(shortcode, assetId)
    } yield FileMetadataSipiResponse(
      Some(response.originalFilename),
      response.originalMimeType,
      response.internalMimeType.getOrElse("application/octet-stream"),
      response.width,
      response.height,
      None,
      response.duration.map(BigDecimal(_)),
      response.fps.map(BigDecimal(_)),
    )

  /**
   * Asks Sipi to move a file from temporary storage to permanent storage.
   *
   * @param moveTemporaryFileToPermanentStorageRequestV2 the request.
   * @return a [[SuccessResponseV2]].
   */
  def moveTemporaryFileToPermanentStorage(
    moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest,
  ): Task[SuccessResponseV2] = {
    // create the JWT token with the necessary permission
    val jwt = jwtService.createJwt(
      moveTemporaryFileToPermanentStorageRequestV2.requestingUser,
      Map(
        "knora-data" -> JsObject(
          Map(
            "permission" -> JsString("StoreFile"),
            "filename"   -> JsString(moveTemporaryFileToPermanentStorageRequestV2.internalFilename),
            "prefix"     -> JsString(moveTemporaryFileToPermanentStorageRequestV2.prefix),
          ),
        ),
      ),
    )

    val params = Map(
      ("filename" -> moveTemporaryFileToPermanentStorageRequestV2.internalFilename),
      ("prefix"   -> moveTemporaryFileToPermanentStorageRequestV2.prefix),
    )

    for {
      token <- jwt
      url    = uri"${sipiConfig.internalBaseUrl}/${sipiConfig.moveFileRoute}?token=${token.jwtString}"
      _     <- doSipiRequest(quickRequest.post(url).body(params))
    } yield SuccessResponseV2("Moved file to permanent storage.")
  }

  /**
   * Asks Sipi to delete a temporary file.
   *
   * @param deleteTemporaryFileRequestV2 the request.
   * @return a [[SuccessResponseV2]].
   */
  def deleteTemporaryFile(deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest): Task[SuccessResponseV2] = {
    val deleteRequestContent =
      Map(
        "knora-data" -> JsObject(
          Map(
            "permission" -> JsString("DeleteTempFile"),
            "filename"   -> JsString(deleteTemporaryFileRequestV2.internalFilename),
          ),
        ),
      )

    val url: String => Uri = s =>
      uri"${sipiConfig.internalBaseUrl}/${sipiConfig.deleteTempFileRoute}/${deleteTemporaryFileRequestV2.internalFilename}?token=${s}"

    for {
      token <- jwtService.createJwt(deleteTemporaryFileRequestV2.requestingUser, deleteRequestContent)
      _     <- doSipiRequest(quickRequest.delete(url(token.jwtString)))
    } yield SuccessResponseV2("Deleted temporary file.")
  }

  /**
   * Asks Sipi for a text file used internally by Knora.
   *
   * @param textFileRequest the request message.
   */
  def getTextFileRequest(textFileRequest: SipiGetTextFileRequest): Task[SipiGetTextFileResponse] =
    doSipiRequest
      .apply(quickRequest.get(uri"${textFileRequest.fileUrl}"))
      .map(SipiGetTextFileResponse(_))
      .catchAll { ex =>
        val msg =
          s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${ex.getMessage}"
        (ex match {
          case (_: NotFoundException | _: BadRequestException | _: SipiException) => ZIO.die(SipiException(msg))
          case other                                                              => ZIO.logError(msg) *> ZIO.die(SipiException(msg))
        })
      }

  private def doSipiRequest(request: Request[String, Any]): Task[String] =
    sttp
      .send(request)
      .flatMap { response =>
        if (response.isSuccess) {
          ZIO.succeed(response.body)
        } else {
          val sipiErrorMsg = SipiUtil.getSipiErrorMessage(response.body)

          if (response.code.code == 404) {
            ZIO.fail(NotFoundException(sipiErrorMsg))
          } else if (response.isClientError) {
            ZIO.fail(BadRequestException(s"Sipi responded with HTTP status code ${response.code.code}: $sipiErrorMsg"))
          } else {
            ZIO.fail(SipiException(s"Sipi responded with HTTP status code ${response.code.code}: $sipiErrorMsg"))
          }
        }
      }
      .catchAll {
        case badRequestException: BadRequestException => ZIO.fail(badRequestException)
        case notFoundException: NotFoundException     => ZIO.fail(notFoundException)
        case sipiException: SipiException             => ZIO.fail(sipiException)
        case e: Exception                             => ZIO.logError(e.getMessage) *> ZIO.fail(SipiException("Failed to connect to Sipi", e))
      }

  /**
   * Downloads an asset and its knora.json from Sipi.
   *
   * @param asset     The asset to download.
   * @param targetDir The target directory in which the asset should be stored.
   * @param user      The user who is downloading the asset.
   * @return The path to the downloaded asset. If the asset could not be downloaded, [[None]] is returned.
   */
  override def downloadAsset(asset: Asset, targetDir: Path, user: User): Task[Option[Path]] = {
    def executeDownloadRequest(uri: URI, jwt: Jwt, targetFilename: String): ZIO[Any, Throwable, Option[Path]] =
      sttp
        .send(quickRequest.get(uri"$uri").header("Authorization", s"Bearer ${jwt.jwtString}"))
        .filterOrElseWith(_.code.isSuccess)(r => ZIO.fail(new Exception(s"${r.code.code} code from sipi")))
        .flatMap { response =>
          val path = targetDir / targetFilename
          ZIO.writeFile(path.toString, response.body: String).mapError(e => e: java.lang.Throwable).as(path)
        }
        .tapBoth(
          e => ZIO.logWarning(s"Failed downloading $uri: ${e.getMessage}"),
          _ => ZIO.logDebug(s"Downloaded ${targetDir / targetFilename}"),
        )
        .fold(_ => None, Some(_))

    def downloadAsset(asset: Asset, jwt: Jwt): Task[Option[Path]] =
      ZIO.logDebug(s"Downloading ${Asset.logString(asset)}") *>
        SipiRoutes.file(asset).flatMap(executeDownloadRequest(_, jwt, asset.internalFilename))

    def downloadKnoraJson(asset: Asset, jwt: Jwt): Task[Option[Path]] =
      ZIO.logDebug(s"Downloading knora.json for  ${Asset.logString(asset)}") *>
        SipiRoutes.knoraJson(asset).flatMap(executeDownloadRequest(_, jwt, s"${asset.internalFilename}_knora.json"))

    for {
      jwt             <- jwtService.createJwt(user)
      assetDownloaded <- downloadAsset(asset, jwt)
      _               <- downloadKnoraJson(asset, jwt).when(assetDownloaded.isDefined)
    } yield assetDownloaded
  }
}

object SipiServiceLive {
  val layer: URLayer[AppConfig & DspIngestClient & JwtService, SipiServiceLive] =
    HttpClientZioBackend.layer().orDie >+>
      ZLayer.scoped {
        for {
          config          <- ZIO.serviceWith[AppConfig](_.sipi)
          jwtService      <- ZIO.service[JwtService]
          dspIngestClient <- ZIO.service[DspIngestClient]
          sttp            <- ZIO.service[SttpBackend[Task, ZioStreams]]
        } yield SipiServiceLive(config, jwtService, sttp, dspIngestClient)
      }
}
