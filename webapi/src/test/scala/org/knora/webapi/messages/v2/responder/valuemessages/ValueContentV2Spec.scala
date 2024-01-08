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
import org.knora.webapi.messages.store.sipimessages.IIIFServiceStatusResponse
import org.knora.webapi.messages.store.sipimessages.MoveTemporaryFileToPermanentStorageRequest
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileRequest
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileResponse
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceRequestV2.AssetIngestState
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceRequestV2.AssetIngestState.AssetInTemp
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceRequestV2.AssetIngestState.AssetIngested
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

  private val expected = FileMetadataSipiResponse(Some("origName"), None, "text/plain", None, None, None, None, None)

  override def spec: Spec[Any, Throwable] =
    suite("ValueContentV2.getFileInfo")(
      suite("Given the asset is present in the tmp folder of Sipi")(
        test("When getting file metadata with AssetInTemp from Sipi, then it should succeed") {
          for {
            temp <- ValueContentV2.getFileInfo("0001", AssetInTemp, jsonLdObj)
          } yield assertTrue(temp.metadata == expected)
        },
        test("When getting file metadata with AssetIngested from dsp-ingest, then it should fail") {
          for {
            exit <- ValueContentV2.getFileInfo("0001", AssetIngested, jsonLdObj).exit
          } yield assert(exit)(failsWithA[AssertionException])
        }
      ).provide(mockSipi(AssetInTemp)),
      suite("Given the asset is ingested")(
        test("When getting file metadata with AssetInTemp from Sipi, then it should fail") {
          for {
            exit <- ValueContentV2.getFileInfo("0001", AssetInTemp, jsonLdObj).exit
          } yield assert(exit)(failsWithA[AssertionException])
        },
        test("When getting file metadata with AssetIngested from dsp-ingest, then it should succeed") {
          for {
            ingested <- ValueContentV2.getFileInfo("0001", AssetIngested, jsonLdObj)
          } yield assertTrue(ingested.metadata == expected)
        }
      ).provide(mockSipi(AssetIngested))
    )

  private def mockSipi(flag: AssetIngestState) = ZLayer.succeed(new SipiService {

    override def getFileMetadataFromSipiTemp(filename: String): Task[FileMetadataSipiResponse] =
      if (flag == AssetInTemp) { ZIO.succeed(expected) }
      else { ZIO.fail(AssertionException("fail")) }

    override def getFileMetadataFromDspIngest(
      shortcode: KnoraProject.Shortcode,
      assetId: AssetId
    ): Task[FileMetadataSipiResponse] =
      if (flag == AssetIngested) { ZIO.succeed(expected) }
      else { ZIO.fail(AssertionException("fail")) }

    // The following are unsupported operations because they are not used in the test
    def moveTemporaryFileToPermanentStorage(
      moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest
    ): Task[SuccessResponseV2] =
      ZIO.dieMessage("unsupported operation")
    def deleteTemporaryFile(
      deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest
    ): Task[SuccessResponseV2] =
      ZIO.dieMessage("unsupported operation")
    def getTextFileRequest(textFileRequest: SipiGetTextFileRequest): Task[SipiGetTextFileResponse] =
      ZIO.dieMessage("unsupported operation")
    def getStatus(): Task[IIIFServiceStatusResponse] =
      ZIO.dieMessage("unsupported operation")
    def downloadAsset(asset: Asset, targetDir: Path, user: User): Task[Option[Path]] =
      ZIO.dieMessage("unsupported operation")
  })
}
