/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.impl

import sttp.capabilities.zio.ZioStreams
import sttp.client3
import sttp.client3.*
import sttp.client3.SttpBackend
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.*
import zio.nio.file.Path

import java.net.URI

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.config.Sipi
import org.knora.webapi.messages.store.sipimessages.*
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.Asset
import org.knora.webapi.slice.admin.domain.service.DspIngestClient
import org.knora.webapi.slice.infrastructure.Jwt
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.security.ScopeResolver
import org.knora.webapi.store.iiif.api.FileMetadataSipiResponse
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.errors.SipiException
import org.knora.webapi.util.SipiUtil

final case class SipiServiceLive(
  private val sipiConfig: Sipi,
  private val jwtService: JwtService,
  private val scopeResolver: ScopeResolver,
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
   * Asks Sipi for a text file used internally by Knora.
   *
   * @param textFileRequest the request message.
   */
  def getTextFileRequest(textFileRequest: SipiGetTextFileRequest): Task[SipiGetTextFileResponse] =
    doSipiRequest
      .apply(quickRequest.get(uri"${textFileRequest.fileUrl}"))
      .map(SipiGetTextFileResponse.apply)
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

          if (
            response.code.code == 404 || (response.code.code == 500 && sipiErrorMsg.contains("file_does_not_exist"))
          ) {
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
      scope           <- scopeResolver.resolve(user)
      jwt             <- jwtService.createJwt(user.userIri, scope)
      assetDownloaded <- downloadAsset(asset, jwt)
      _               <- downloadKnoraJson(asset, jwt).when(assetDownloaded.isDefined)
    } yield assetDownloaded
  }
}

object SipiServiceLive {
  val layer: URLayer[Sipi & DspIngestClient & JwtService & ScopeResolver, SipiServiceLive] =
    HttpClientZioBackend.layer().orDie >>> ZLayer.derive[SipiServiceLive]
}
