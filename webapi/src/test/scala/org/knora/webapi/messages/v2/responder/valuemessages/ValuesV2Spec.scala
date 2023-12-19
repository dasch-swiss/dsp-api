/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import zio.*
import zio.nio.file.Path
import zio.test.Assertion.*
import zio.test.*

import dsp.errors.AssertionException
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages.*
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceRequestV2.AssetIngestState
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.Asset
import org.knora.webapi.store.iiif.api.FileMetadataSipiResponse
import org.knora.webapi.store.iiif.api.SipiService

object ValuesV2Spec extends ZIOSpecDefault {
  private val json =
    """{
      |  "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename" : "filename"
      |  }""".stripMargin

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ValuesV2")(
      test("Expect file to be not ingested and in `tmp` folder") {
        for {
          ingested <- ValueContentV2
                        .getFileInfo("0001", AssetIngestState.AssetIngested, JsonLDUtil.parseJsonLD(json).body)
                        .exit
          temp <- ValueContentV2
                    .getFileInfo("0001", AssetIngestState.AssetInTemp, JsonLDUtil.parseJsonLD(json).body)
                    .exit
        } yield assert(ingested)(fails(isSubtype[AssertionException](anything))) && assert(temp)(succeeds(anything))
      }.provide(sipiServiceAllowMetadataInTemp),
      test("Expect file to be already ingested and in project folder") {
        for {
          ingested <- ValueContentV2
                        .getFileInfo("0001", AssetIngestState.AssetIngested, JsonLDUtil.parseJsonLD(json).body)
                        .exit
          temp <- ValueContentV2
                    .getFileInfo("0001", AssetIngestState.AssetInTemp, JsonLDUtil.parseJsonLD(json).body)
                    .exit
        } yield assert(ingested)(succeeds(anything)) && assert(temp)(fails(isSubtype[AssertionException](anything)))
      }.provide(sipiServiceAllowMetadataInProjectFolder)
    )

  private val response: FileMetadataSipiResponse =
    FileMetadataSipiResponse(None, None, "", None, None, None, None, None)

  private def sipiServiceAllowMetadataInTemp: ULayer[SipiService] = ZLayer.succeed(new SipiService {
    override def getFileMetadataFromTemp(filename: String): Task[FileMetadataSipiResponse] =
      ZIO.succeed(response)
    override def getFileMetadata(filename: String, shortcode: KnoraProject.Shortcode): Task[FileMetadataSipiResponse] =
      ZIO.fail(AssertionException(filename))
    def moveTemporaryFileToPermanentStorage(
      moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest
    ): Task[SuccessResponseV2] = ???
    def deleteTemporaryFile(
      deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest
    ): Task[SuccessResponseV2] = ???
    def getTextFileRequest(textFileRequest: SipiGetTextFileRequest): Task[SipiGetTextFileResponse] = ???
    def getStatus(): Task[IIIFServiceStatusResponse]                                               = ???
    def downloadAsset(asset: Asset, targetDir: Path, user: UserADM): Task[Option[Path]]            = ???
  })

  private def sipiServiceAllowMetadataInProjectFolder: ULayer[SipiService] = ZLayer.succeed(new SipiService {
    override def getFileMetadata(filename: String, shortcode: KnoraProject.Shortcode): Task[FileMetadataSipiResponse] =
      ZIO.succeed(response)
    override def getFileMetadataFromTemp(filename: String): Task[FileMetadataSipiResponse] =
      ZIO.fail(AssertionException(filename))
    def moveTemporaryFileToPermanentStorage(
      moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest
    ): Task[SuccessResponseV2] = ???
    def deleteTemporaryFile(
      deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest
    ): Task[SuccessResponseV2] = ???
    def getTextFileRequest(textFileRequest: SipiGetTextFileRequest): Task[SipiGetTextFileResponse] = ???
    def getStatus(): Task[IIIFServiceStatusResponse]                                               = ???
    def downloadAsset(asset: Asset, targetDir: Path, user: UserADM): Task[Option[Path]]            = ???
  })

}
