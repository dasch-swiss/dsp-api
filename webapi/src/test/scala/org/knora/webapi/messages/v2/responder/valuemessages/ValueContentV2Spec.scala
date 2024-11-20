/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.nio.file.Path
import zio.test.Assertion.failsWithA
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assert
import zio.test.assertTrue

import dsp.errors.AssertionException
import org.knora.webapi.messages.store.sipimessages.DeleteTemporaryFileRequest
import org.knora.webapi.messages.store.sipimessages.MoveTemporaryFileToPermanentStorageRequest
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileRequest
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileResponse
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.routing.v2.AssetIngestState
import org.knora.webapi.routing.v2.AssetIngestState.*
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.Asset
import org.knora.webapi.store.iiif.api.FileMetadataSipiResponse
import org.knora.webapi.store.iiif.api.SipiService

object ValueContentV2Spec extends ZIOSpecDefault {

  private val assetId = AssetId.unsafeFrom("4sAf4AmPeeg-ZjDn3Tot1Zt")

  private val jsonLdObj = JsonLDUtil
    .parseJsonLD(s"{\"http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename\" : \"$assetId.txt\"}")
    .body

  private val expected      = FileMetadataSipiResponse(Some("origName"), None, "text/plain", None, None, None, None, None)
  private val shortcode0001 = KnoraProject.Shortcode.unsafeFrom("0001")

  override def spec: Spec[Any, Option[Throwable]] =
    suite("ValueContentV2.getFileInfo")(
      suite("Given the asset is ingested")(
        test("When getting file metadata with AssetIngested from dsp-ingest, then it should succeed") {
          for {
            ingested <- ValueContentV2.getFileInfo(shortcode0001, jsonLdObj).some
          } yield assertTrue(ingested.metadata == expected)
        },
      ).provide(mockSipi()),
    )

  private def mockSipi() = ZLayer.succeed(new SipiService {
    override def getFileMetadataFromDspIngest(
      shortcode: KnoraProject.Shortcode,
      assetId: AssetId,
    ): Task[FileMetadataSipiResponse] =
      ZIO.succeed(expected)

    // The following are unsupported operations because they are not used in the test
    def moveTemporaryFileToPermanentStorage(
      moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest,
    ): Task[SuccessResponseV2] =
      ZIO.dieMessage("unsupported operation")
    def deleteTemporaryFile(
      deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest,
    ): Task[SuccessResponseV2] =
      ZIO.dieMessage("unsupported operation")
    def getTextFileRequest(textFileRequest: SipiGetTextFileRequest): Task[SipiGetTextFileResponse] =
      ZIO.dieMessage("unsupported operation")
    def downloadAsset(asset: Asset, targetDir: Path, user: User): Task[Option[Path]] =
      ZIO.dieMessage("unsupported operation")
  })
}
