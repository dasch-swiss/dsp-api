/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConstants.Projects.*
import swiss.dasch.util.TestUtils
import zio.Chunk
import zio.Scope
import zio.ZIO
import zio.ZLayer
import zio.nio.file.Files
import zio.nio.file.Path
import zio.test.Spec
import zio.test.TestEnvironment
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.io.FileInputStream
import java.security.MessageDigest

import org.knora.bagit.BagIt
import org.knora.bagit.ChecksumAlgorithm

object ProjectServiceSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ProjectService")(
      suite("listAllProjects")(
        test("should list all projects which contain assets in the asset directory") {
          for {
            projects <- ProjectService.listAllProjects()
          } yield assertTrue(projects.map(_.shortcode) == Chunk(existingProject))
        },
      ),
      suite("findProject path")(
        test("should find existing projects which contain at least one non hidden regular file") {
          for {
            project <- ProjectService.findProject(existingProject)
          } yield assertTrue(project.map(_.shortcode).contains(existingProject))
        },
        test("should not find not existing projects") {
          for {
            project <- ProjectService.findProject(nonExistentProject)
          } yield assertTrue(project.isEmpty)
        },
      ),
      suite("findAssetsOfProject path")(
        test("should find asset infos from existing project") {
          for {
            infos <- ProjectService.findAssetInfosOfProject(existingProject).runCollect
          } yield assertTrue(infos.nonEmpty)
        },
        test("should not find non existing projects") {
          for {
            infos <- ProjectService.findAssetInfosOfProject(nonExistentProject).runCollect
          } yield assertTrue(infos.isEmpty)
        },
      ),
      suite("zipping a project")(
        test("given it does not exist, should return None") {
          for {
            zip <- ProjectService.zipProject(nonExistentProject)
          } yield assertTrue(zip.isEmpty)
        },
        test("given it does exists, should zip and return file path") {
          for {
            tempDir <- ZIO.serviceWith[StorageConfig](_.tempPath)
            zip     <- ProjectService.zipProject(existingProject)
          } yield assertTrue(zip.contains(tempDir / "zipped" / "0001.zip"))
        },
      ),
      suite("BagIt export")(
        test("exported zip passes BagIt validation") {
          ZIO.scoped {
            for {
              zipPath <- ProjectService.zipProject(existingProject).someOrFail(new Exception("export returned None"))
              result  <- BagIt.readAndValidateZip(zipPath)
              bag      = result._1
            } yield assertTrue(bag.version == "1.0", bag.encoding == "UTF-8")
          }
        },
        test("payload files under data/ match the expected project directory structure") {
          ZIO.scoped {
            for {
              zipPath          <- ProjectService.zipProject(existingProject).someOrFail(new Exception("export returned None"))
              result           <- BagIt.readAndValidateZip(zipPath)
              bag               = result._1
              bagRoot           = result._2
              dataDir           = bagRoot / "data"
              extractedFiles   <- Files.walk(dataDir).filterZIO(Files.isRegularFile(_)).runCollect
              extractedRelPaths =
                extractedFiles.map(f => dataDir.toFile.toPath.relativize(f.toFile.toPath).toString).sorted
              payloadPaths = bag.payloadFiles.map(_.value).sorted
            } yield assertTrue(
              extractedFiles.nonEmpty,
              extractedRelPaths == Chunk.fromIterable(payloadPaths),
            )
          }
        },
        test("bag-info.txt contains Source-Organization: DaSCH Service Platform") {
          ZIO.scoped {
            for {
              zipPath <- ProjectService.zipProject(existingProject).someOrFail(new Exception("export returned None"))
              result  <- BagIt.readAndValidateZip(zipPath)
              bagInfo  = result._1.bagInfo.get
            } yield assertTrue(bagInfo.sourceOrganization.contains("DaSCH Service Platform"))
          }
        },
        test("bag-info.txt contains External-Identifier matching the project shortcode") {
          ZIO.scoped {
            for {
              zipPath <- ProjectService.zipProject(existingProject).someOrFail(new Exception("export returned None"))
              result  <- BagIt.readAndValidateZip(zipPath)
              bagInfo  = result._1.bagInfo.get
            } yield assertTrue(bagInfo.externalIdentifier.contains(existingProject.value))
          }
        },
        test("bag-info.txt contains Ingest-Export-Version: 1") {
          ZIO.scoped {
            for {
              zipPath <- ProjectService.zipProject(existingProject).someOrFail(new Exception("export returned None"))
              result  <- BagIt.readAndValidateZip(zipPath)
              bagInfo  = result._1.bagInfo.get
            } yield assertTrue(bagInfo.additionalFields.contains("Ingest-Export-Version" -> "1"))
          }
        },
        test("Payload-Oxum matches actual file count and byte total") {
          ZIO.scoped {
            for {
              zipPath    <- ProjectService.zipProject(existingProject).someOrFail(new Exception("export returned None"))
              result     <- BagIt.readAndValidateZip(zipPath)
              bagInfo     = result._1.bagInfo.get
              payloadOxum = bagInfo.payloadOxum.get
              dataDir     = result._2 / "data"
              files      <- Files.walk(dataDir).filterZIO(Files.isRegularFile(_)).runCollect
              totalBytes <- ZIO.foreach(files)(f => Files.size(f)).map(_.sum)
            } yield assertTrue(
              payloadOxum.streamCount == files.size.toLong,
              payloadOxum.octetCount == totalBytes,
            )
          }
        },
        test("SHA-512 checksums in manifest match actual file checksums") {
          ZIO.scoped {
            for {
              zipPath       <- ProjectService.zipProject(existingProject).someOrFail(new Exception("export returned None"))
              result        <- BagIt.readAndValidateZip(zipPath)
              bag            = result._1
              bagRoot        = result._2
              sha512Manifest = bag.manifests.find(_.algorithm == ChecksumAlgorithm.SHA512).get
              results       <- ZIO.foreach(sha512Manifest.entries) { entry =>
                           val filePath = bagRoot / entry.path.value
                           ZIO.attemptBlockingIO {
                             val digest = MessageDigest.getInstance("SHA-512")
                             val is     = new FileInputStream(filePath.toFile)
                             try {
                               val buffer = new Array[Byte](8192)
                               var read   = is.read(buffer)
                               while (read != -1) {
                                 digest.update(buffer, 0, read)
                                 read = is.read(buffer)
                               }
                             } finally is.close()
                             val computed = digest.digest().map(b => String.format("%02x", b)).mkString
                             entry.checksum == computed
                           }
                         }
            } yield assertTrue(results.forall(identity), sha512Manifest.entries.nonEmpty)
          }
        },
      ),
    ).provide(
      AssetInfoServiceLive.layer,
      FileChecksumServiceLive.layer,
      ProjectService.layer,
      ProjectRepositoryLive.layer,
      SpecConfigurations.storageConfigLayer,
      StorageServiceLive.layer,
      TestUtils.testDbLayerWithEmptyDb,
    )
}
