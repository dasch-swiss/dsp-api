/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.junit.runner.RunWith
import swiss.dasch.api.SipiClientMock
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.infrastructure.CommandExecutorLive
import swiss.dasch.infrastructure.IngestExecutor
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConstants.Projects.existingProject
import swiss.dasch.test.SpecConstants.Projects.nonExistentProject
import zio.*
import zio.nio.file.Files
import zio.nio.file.Path
import zio.stream.ZStream
import zio.test.Spec
import zio.test.TestAspect
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.io.IOException

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class IngestServiceSpec extends ZIOSpecDefault {

  private def writeFile(dir: Path, name: String): IO[IOException, Path] =
    Files.writeBytes(dir / name, Chunk.fromArray(name.getBytes)).as(dir / name)

  private val onePermitSuite = suite("IngestService with an IngestExecutor of one permit")(
    test("an image ingest, a project export and a project import each complete (no nested permit sections)") {
      ZIO.scoped {
        for {
          executor <- ZIO.service[IngestExecutor]
          _        <- SipiClientMock.setQueryImageDimensions(Dimensions.unsafeFrom(6, 6))
          tempDir  <- StorageService.createTempDirectoryScoped("test", None)
          image    <- writeFile(tempDir, "image.jpg")
          asset    <- executor.run(IngestService.ingestFile(image, existingProject))
          zip      <- executor.run(ProjectService.zipProject(existingProject)).someOrFailException
          _        <- executor.run(ImportService.importZipStream(nonExistentProject, ZStream.fromFile(zip.toFile).orDie))
          imported <- ProjectService.findProject(nonExistentProject)
        } yield assertTrue(asset.metadata.isInstanceOf[StillImageMetadata], imported.isDefined)
      }
    } @@ TestAspect.timeout(30.seconds),
    test("a failing Sipi transcode fails only its own ingest and leaves a concurrent one unaffected") {
      ZIO.scoped {
        for {
          _       <- SipiClientMock.setQueryImageDimensions(Dimensions.unsafeFrom(6, 6))
          _       <- SipiClientMock.failTranscoding(_.filename.toString.endsWith(".tif.orig"))
          tempDir <- StorageService.createTempDirectoryScoped("test", None)
          broken  <- writeFile(tempDir, "broken.tif")
          good    <- writeFile(tempDir, "good.jpg")
          exits   <- IngestService.ingestFile(broken, existingProject).exit <&>
                     IngestService.ingestFile(good, existingProject).exit
          (brokenExit, goodExit) = exits
          goodInfo              <- ZIO.foreach(goodExit.toEither.toOption)(asset => AssetInfoService.findByAssetRef(asset.ref))
        } yield assertTrue(brokenExit.isFailure, goodInfo.flatten.exists(_.originalFilename.value == "good.jpg"))
      }
    } @@ TestAspect.timeout(30.seconds),
  ).provide(
    AssetInfoServiceLive.layer,
    CommandExecutorLive.layer,
    FileChecksumServiceLive.layer,
    ImportServiceLive.layer,
    IngestService.layer,
    MimeTypeGuesser.layer,
    MovingImageService.layer,
    OtherFilesService.layer,
    ProjectService.layer,
    SipiClientMock.layer,
    SpecConfigurations.sipiConfigLayer,
    SpecConfigurations.storageConfigLayer,
    StillImageService.layer,
    StorageServiceLive.layer,
    ZLayer.scoped(IngestExecutor.make(1)),
  )

  val spec: Spec[Any, Any] = onePermitSuite + suite("IngestService")(
    test("should ingest a simple csv file") {
      val shortcode = ProjectShortcode.unsafeFrom("0001")
      ZIO.scoped {
        for {
          // given
          tempDir     <- StorageService.createTempDirectoryScoped("test", None)
          fileToIngest = tempDir / "test.csv"
          _           <- Files.createFile(fileToIngest) *> Files.writeLines(fileToIngest, List("one,two", "1,2"))
          checksum    <- FileChecksumService.createSha256Hash(fileToIngest)
          size        <- SizeInBytes.of(fileToIngest)
          // when
          asset <- IngestService.ingestFile(fileToIngest, shortcode)
          // then
          info              <- AssetInfoService.findByAssetRef(asset.ref).map(_.head)
          assetDir          <- StorageService.getAssetFolder(asset.ref)
          originalFilename   = s"${asset.id}.csv.orig"
          derivativeFilename = s"${asset.id}.csv"
          originalExists    <- Files.exists(assetDir / originalFilename)
          derivativeExists  <- Files.exists(assetDir / derivativeFilename)
        } yield assertTrue(
          asset.belongsToProject == shortcode,
          asset.metadata.originalMimeType.map(_.value).contains("text/csv"),
          asset.metadata.internalMimeType.map(_.value).contains("text/csv"),
          info.originalFilename.toString == fileToIngest.filename.toString,
          info.originalFilename == asset.original.originalFilename,
          info.assetRef == asset.ref,
          info.original.checksum == checksum,
          info.original.size.contains(size),
          info.original.filename.toString == originalFilename,
          info.original.filename == asset.original.internalFilename,
          info.derivative.checksum == checksum,
          info.derivative.size.contains(size),
          info.derivative.filename.toString == derivativeFilename,
          info.derivative.filename == asset.derivative.filename,
          originalExists,
          derivativeExists,
        )
      }
    },
    test("should ingest a svg file") {
      val shortcode = ProjectShortcode.unsafeFrom("0001")
      ZIO.scoped {
        for {
          // given
          tempDir     <- StorageService.createTempDirectoryScoped("test", None)
          fileToIngest = tempDir / "test.svg"
          _           <- Files.createFile(fileToIngest) *>
                 Files.writeLines(fileToIngest, List("""<svg xmlns="http://www.w3.org/2000/svg"/>"""))
          checksum <- FileChecksumService.createSha256Hash(fileToIngest)
          size     <- SizeInBytes.of(fileToIngest)
          // when
          asset <- IngestService.ingestFile(fileToIngest, shortcode)
          // then
          info              <- AssetInfoService.findByAssetRef(asset.ref).map(_.head)
          assetDir          <- StorageService.getAssetFolder(asset.ref)
          originalFilename   = s"${asset.id}.svg.orig"
          derivativeFilename = s"${asset.id}.svg"
          originalExists    <- Files.exists(assetDir / originalFilename)
          derivativeExists  <- Files.exists(assetDir / derivativeFilename)
        } yield assertTrue(
          asset.belongsToProject == shortcode,
          asset.metadata.originalMimeType.map(_.value).contains("image/svg+xml"),
          asset.metadata.internalMimeType.map(_.value).contains("image/svg+xml"),
          info.originalFilename.toString == fileToIngest.filename.toString,
          info.original.checksum == checksum,
          info.original.size.contains(size),
          info.original.filename.toString == originalFilename,
          info.derivative.checksum == checksum,
          info.derivative.size.contains(size),
          info.derivative.filename.toString == derivativeFilename,
          originalExists,
          derivativeExists,
        )
      }
    },
  ).provide(
    AssetInfoServiceLive.layer,
    CommandExecutorLive.layer,
    FileChecksumServiceLive.layer,
    IngestService.layer,
    MimeTypeGuesser.layer,
    MovingImageService.layer,
    OtherFilesService.layer,
    ProjectService.layer,
    SipiClientMock.layer,
    SpecConfigurations.ingestExecutorLayer,
    SpecConfigurations.sipiConfigLayer,
    SpecConfigurations.storageConfigLayer,
    StillImageService.layer,
    StorageServiceLive.layer,
  )
}
