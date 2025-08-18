/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.AugmentedPath.AssetFolder
import zio.*
import zio.json.DecoderOps
import zio.nio.file.Files

import java.nio.file.StandardOpenOption.*

object AssetInfoFileTestHelper {
  val testProject: ProjectShortcode =
    ProjectShortcode.unsafeFrom("0001")
  val testChecksumOriginal: Sha256Hash =
    Sha256Hash.unsafeFrom("fb252a4fb3d90ce4ebc7e123d54a4112398a7994541b11aab5e4230eac01a61c")
  val testChecksumDerivative: Sha256Hash =
    Sha256Hash.unsafeFrom("0ce405c9b183fb0d0a9998e9a49e39c93b699e0f8e2a9ac3496c349e5cea09cc")

  def createInfoFile(
    originalFileExt: String,
    derivativeFileExt: String,
    customJsonProps: Option[String] = None,
    contentsOrig: Option[List[Byte]] = None,
  ): ZIO[StorageService, Throwable, AssetFolder] =
    for {
      assetRef <- AssetRef.makeNew(testProject)
      json = s"""{
                |    ${customJsonProps.map(_ + ",").getOrElse("")}
                |    "internalFilename" : "${assetRef.id}.$derivativeFileExt",
                |    "originalInternalFilename" : "${assetRef.id}.$originalFileExt.orig",
                |    "originalFilename" : "test.$originalFileExt",
                |    "checksumOriginal" : "$testChecksumOriginal",
                |    "checksumDerivative" : "$testChecksumDerivative"
                |}
                |""".stripMargin
      info <- ZIO
                .fromEither(json.fromJson[AssetInfoFileContent])
                .orElseFail(new IllegalArgumentException(s"Invalid AssetInfoFileContent:\n$json"))
      assetDir <- StorageService.getAssetFolder(assetRef).tap(StorageService.createDirectories(_))
      _        <- StorageService.saveJsonFile[AssetInfoFileContent](assetDir / s"${assetRef.id}.info", info)

      _ <- ZIO.whenCase(contentsOrig) { case Some(bytes) =>
             val filename = assetDir / s"${assetRef.id}.txt.orig"
             Files.writeBytes(filename, Chunk.from(bytes), WRITE, CREATE, TRUNCATE_EXISTING)
           }
    } yield assetDir
}
