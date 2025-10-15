/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.impl

import zio.*
import zio.nio.file.Path

import org.knora.webapi.messages.store.sipimessages.*
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.Asset
import org.knora.webapi.store.iiif.api.FileMetadataSipiResponse
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.errors.SipiException
import org.knora.webapi.store.iiif.impl.SipiServiceMock.SipiMockMethodName
import org.knora.webapi.store.iiif.impl.SipiServiceMock.SipiMockMethodName.*

/**
 * Can be used in place of [[SipiServiceLive]] for tests without an actual Sipi server, by returning hard-coded
 * responses simulating responses from Sipi.
 */
case class SipiServiceMock(ref: Ref[Map[SipiMockMethodName, Task[Object]]]) extends SipiService {

  private def getReturnValue[T](method: SipiMockMethodName): Task[T] =
    ref.get.flatMap(
      _.getOrElse(
        method,
        throw SipiException(s"No response configured for $method"),
      ).map(_.asInstanceOf[T]),
    )

  def setReturnValue[T](method: SipiMockMethodName, returnValue: Task[Object]): Task[Unit] =
    ref.getAndUpdate(_ + (method -> returnValue)).unit

  def assertNoInteraction: UIO[Unit] = {
    val fail = ZIO.fail(SipiException("No interaction expected"))
    ref.set(SipiMockMethodName.values.map(_ -> fail).toMap)
  }

  override def getTextFileRequest(textFileRequest: SipiGetTextFileRequest): Task[SipiGetTextFileResponse] =
    getReturnValue(GetTextFileRequest)

  override def downloadAsset(asset: Asset, targetDir: Path, user: User): Task[Option[Path]] =
    getReturnValue(DownloadAsset)

  override def getFileMetadataFromDspIngest(shortcode: Shortcode, assetId: AssetId): Task[FileMetadataSipiResponse] =
    getReturnValue(GetFileMetadataFromDspIngest)
}

object SipiServiceMock {
  enum SipiMockMethodName:
    case GetFileMetadataFromSipiTemp
    case GetFileMetadataFromDspIngest
    case GetTextFileRequest
    case DownloadAsset

  private val defaultGetFileMetadataFromSipiTempResponse = FileMetadataSipiResponse(
    originalFilename = Some("test2.tiff"),
    originalMimeType = Some("image/tiff"),
    internalMimeType = "image/jp2",
    width = Some(512),
    height = Some(256),
    numpages = None,
    duration = None,
    fps = None,
  )

  val layer: ULayer[SipiServiceMock] = ZLayer.fromZIO(
    Ref
      .make[Map[SipiMockMethodName, Task[Object]]](
        Map(
          GetFileMetadataFromSipiTemp  -> ZIO.succeed(defaultGetFileMetadataFromSipiTempResponse),
          GetFileMetadataFromDspIngest -> ZIO.succeed(defaultGetFileMetadataFromSipiTempResponse),
        ),
      )
      .map(ref => SipiServiceMock(ref)),
  )
}
