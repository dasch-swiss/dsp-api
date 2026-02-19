/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.impl

import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import zio.*

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.messages.store.sipimessages.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.service.DspIngestClient
import org.knora.webapi.slice.api.admin.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.infrastructure.TracingHttpClient
import org.knora.webapi.store.iiif.api.FileMetadataSipiResponse
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.errors.SipiException
import org.knora.webapi.util.SipiUtil

final class SipiServiceLive(
  backend: StreamBackend[Task, ZioStreams],
  dspIngestClient: DspIngestClient,
) extends SipiService {

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
        ex match {
          case _: NotFoundException | _: BadRequestException | _: SipiException => ZIO.die(SipiException(msg))
          case _                                                                => ZIO.logError(msg) *> ZIO.die(SipiException(msg))
        }
      }

  private def doSipiRequest(request: Request[String]): Task[String] =
    request
      .send(backend)
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
}

object SipiServiceLive {
  val layer = TracingHttpClient.layer >>> ZLayer.derive[SipiServiceLive]
}
