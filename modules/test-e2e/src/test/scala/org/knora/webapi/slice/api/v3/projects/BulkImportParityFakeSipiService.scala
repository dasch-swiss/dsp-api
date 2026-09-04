/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import zio.*

import dsp.errors.NotFoundException
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.api.admin.model.MaintenanceRequests.AssetId
import org.knora.webapi.store.iiif.api.FileMetadataSipiResponse
import org.knora.webapi.store.iiif.api.SipiService

/**
 * A [[SipiService]] double for the bulk-import parity E2E test. Both the bulk-import path and the
 * v2 create path fetch file-value metadata through `getFileMetadataFromDspIngest`, so serving a
 * fixed per-asset response makes the file-value triples identical across the two runs without any
 * asset bytes or a running dsp-ingest.
 *
 * The keys are the seven internal filenames with their extension stripped: `AssetId` forbids a dot,
 * so both write paths pass the stripped id. The IIIF external image carries its URL and needs no
 * fetch, so it has no entry here. An unknown asset fails with [[NotFoundException]], the error type
 * the transformer maps to a "must be pre-ingested" message.
 */
object BulkImportParityFakeSipiService {

  private def response(
    internalMimeType: String,
    width: Option[Int] = None,
    height: Option[Int] = None,
    numpages: Option[Int] = None,
    duration: Option[BigDecimal] = None,
    fps: Option[BigDecimal] = None,
  ): FileMetadataSipiResponse =
    FileMetadataSipiResponse(
      originalFilename = Some(s"original.$internalMimeType".replace('/', '_')),
      originalMimeType = Some(internalMimeType),
      internalMimeType = internalMimeType,
      width = width,
      height = height,
      numpages = numpages,
      duration = duration,
      fps = fps,
    )

  private val metadataByAssetId: Map[String, FileMetadataSipiResponse] = Map(
    "4u6NKBRcL3P-alABlxHHBJg" -> response("image/jp2", width = Some(100), height = Some(200)),
    "3gV7rSuCp9W-hIjKlMnOpQr" -> response("image/svg+xml", width = Some(100), height = Some(200)),
    "7bQ2mNpXk4R-cDeFgHiJkLm" ->
      response(
        "video/mp4",
        width = Some(100),
        height = Some(200),
        duration = Some(BigDecimal("10.5")),
        fps = Some(BigDecimal("25")),
      ),
    "8cR3nOqYl5S-dEfGhIjKlMn" -> response("audio/mpeg", duration = Some(BigDecimal("10.5"))),
    "9dS4oPrZm6T-eFgHiJkLmNo" -> response("application/pdf", numpages = Some(3)),
    "1eT5pQsAn7U-fGhIjKlMnOp" -> response("application/zip"),
    "2fU6qRtBo8V-gHiJkLmNoPq" -> response("text/plain"),
  )

  private object Impl extends SipiService {

    override def getFileMetadataFromDspIngest(
      shortcode: Shortcode,
      assetId: AssetId,
    ): Task[FileMetadataSipiResponse] =
      ZIO
        .fromOption(metadataByAssetId.get(assetId.value))
        .orElseFail(NotFoundException(s"No fake file metadata for asset '${assetId.value}'"))

    override def getTextFileRequest(fileUrl: String, senderName: String): Task[String] =
      ZIO.fail(NotFoundException(s"getTextFileRequest is not stubbed in the parity test (fileUrl=$fileUrl)"))
  }

  val layer: ULayer[SipiService] = ZLayer.succeed(Impl)
}
