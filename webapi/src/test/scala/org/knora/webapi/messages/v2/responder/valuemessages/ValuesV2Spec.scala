/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import zio.Scope
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.nio.file.Path
import zio.test.Spec
import zio.test.TestEnvironment
import zio.test.ZIOSpecDefault
import zio.test.*

import dsp.errors.AssertionException
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages.DeleteTemporaryFileRequest
import org.knora.webapi.messages.store.sipimessages.IIIFServiceStatusResponse
import org.knora.webapi.messages.store.sipimessages.MoveTemporaryFileToPermanentStorageRequest
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileRequest
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileResponse
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceRequestV2.AssetIngestState
import org.knora.webapi.slice.admin.domain.service.Asset
import org.knora.webapi.store.iiif.api.FileMetadataSipiResponse
import org.knora.webapi.store.iiif.api.SipiService

object ValuesV2Spec extends ZIOSpecDefault {
  private val json =
    """{
      |  "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename" : "filename"
      |  }""".stripMargin

  private val response: FileMetadataSipiResponse =
    FileMetadataSipiResponse(None, None, "", None, None, None, None, None)

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ValuesV2")(
      test("Expect file to be not ingested and in `tmp` folder") {
        for {
          info <- ValueContentV2
                    .getFileInfo(
                      "0001",
                      AssetIngestState.AssetInTemp,
                      JsonLDUtil.parseJsonLD(json).body,
                      KnoraSystemInstances.Users.SystemUser
                    )
                    .provide(ZLayer.succeed(new SipiService {
                      override def getFileMetadata(filePath: String, requestingUser: UserADM)
                        : Task[FileMetadataSipiResponse] =
                        if (filePath.startsWith("/tmp")) ZIO.succeed(response)
                        else ZIO.fail(AssertionException(filePath))

                      override def moveTemporaryFileToPermanentStorage(
                        moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest
                      ): Task[SuccessResponseV2] = ???
                      override def deleteTemporaryFile(
                        deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest
                      ): Task[SuccessResponseV2] = ???
                      override def getTextFileRequest(
                        textFileRequest: SipiGetTextFileRequest
                      ): Task[SipiGetTextFileResponse] = ???
                      override def getStatus(): Task[IIIFServiceStatusResponse]                                    = ???
                      override def downloadAsset(asset: Asset, targetDir: Path, user: UserADM): Task[Option[Path]] = ???
                    }))
        } yield assertTrue(info.metadata == response)
      },
      test("Expect file to be already ingested and in project folder") {
        for {
          info <- ValueContentV2
                    .getFileInfo(
                      "0001",
                      AssetIngestState.AssetIngested,
                      JsonLDUtil.parseJsonLD(json).body,
                      KnoraSystemInstances.Users.SystemUser
                    )
                    .provide(ZLayer.succeed(new SipiService {
                      override def getFileMetadata(filePath: String, requestingUser: UserADM)
                        : Task[FileMetadataSipiResponse] =
                        if (filePath.startsWith("/0001")) ZIO.succeed(response)
                        else ZIO.fail(AssertionException(filePath))

                      override def moveTemporaryFileToPermanentStorage(
                        moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest
                      ): Task[SuccessResponseV2] = ???
                      override def deleteTemporaryFile(
                        deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest
                      ): Task[SuccessResponseV2] = ???
                      override def getTextFileRequest(
                        textFileRequest: SipiGetTextFileRequest
                      ): Task[SipiGetTextFileResponse] = ???
                      override def getStatus(): Task[IIIFServiceStatusResponse]                                    = ???
                      override def downloadAsset(asset: Asset, targetDir: Path, user: UserADM): Task[Option[Path]] = ???
                    }))
        } yield assertTrue(info.metadata == response)
      }
    )
}
