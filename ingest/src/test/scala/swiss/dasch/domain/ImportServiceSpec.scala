/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.knora.bagit.BagIt
import org.knora.bagit.domain.PayloadEntry
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConstants.Projects.*
import swiss.dasch.util.TestUtils
import zio.*
import zio.nio.file.Files
import zio.test.*

import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ImportServiceSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ImportService")(
      test("import fails with ProjectAlreadyExists when project folder has files") {
        for {
          zipPath <- ProjectService.zipProject(existingProject).someOrFail(new Exception("export returned None"))
          result  <- ImportService.importZipFile(existingProject, zipPath).either
        } yield assertTrue(
          result match {
            case Left(_: ProjectAlreadyExists) => true
            case _                             => false
          },
        )
      },
      test("import fails with validation error for corrupted checksum") {
        ZIO.scoped {
          for {
            projectFolder <- StorageService.getProjectFolder(existingProject)
            tmpDir        <- Files.createTempDirectoryScoped(None, List.empty)
            validZip       = tmpDir / "valid.bagit.zip"
            _             <- BagIt.create(
                               List(PayloadEntry.Directory(prefix = "", sourcePath = projectFolder.path)),
                               validZip,
                             )
            corruptedZip   = tmpDir / "corrupted.bagit.zip"
            _             <- ZIO.attemptBlockingIO(corruptZipPayload(validZip.toFile.toPath, corruptedZip.toFile.toPath))
            result        <- ImportService.importZipFile(nonExistentProject, corruptedZip).either
          } yield assertTrue(
            result match {
              case Left(_: BagItValidationFailed) => true
              case _                              => false
            },
          )
        }
      },
      test("import fails with validation error for missing manifest") {
        ZIO.scoped {
          for {
            tmpDir     <- Files.createTempDirectoryScoped(None, List.empty)
            invalidZip  = tmpDir / "no-manifest.zip"
            _          <- ZIO.attemptBlockingIO(createZipWithoutManifest(invalidZip.toFile.toPath))
            result     <- ImportService.importZipFile(nonExistentProject, invalidZip).either
          } yield assertTrue(
            result match {
              case Left(_: BagItValidationFailed) => true
              case _                              => false
            },
          )
        }
      },
      test("round-trip: export, delete, import restores project with correct file structure") {
        for {
          projectFolder    <- StorageService.getProjectFolder(existingProject)
          originalFiles    <- Files.walk(projectFolder.path).filterZIO(Files.isRegularFile(_)).runCollect
          originalRelPaths  = originalFiles.map(f => projectFolder.path.toFile.toPath.relativize(f.toFile.toPath).toString).toSet
          zipPath          <- ProjectService.zipProject(existingProject).someOrFail(new Exception("export returned None"))
          _                <- Files.deleteRecursive(projectFolder.path)
          _                <- ImportService.importZipFile(existingProject, zipPath)
          restoredFiles    <- Files.walk(projectFolder.path).filterZIO(Files.isRegularFile(_)).runCollect
          restoredRelPaths  = restoredFiles.map(f => projectFolder.path.toFile.toPath.relativize(f.toFile.toPath).toString).toSet
        } yield assertTrue(
          restoredRelPaths == originalRelPaths,
          restoredFiles.nonEmpty,
        )
      },
      test("round-trip integrity: imported files match original export checksums") {
        for {
          projectFolder     <- StorageService.getProjectFolder(existingProject)
          originalFiles     <- Files.walk(projectFolder.path).filterZIO(Files.isRegularFile(_)).runCollect
          originalChecksums <- ZIO.foreach(originalFiles) { f =>
                                 val relPath = projectFolder.path.toFile.toPath.relativize(f.toFile.toPath).toString
                                 ZIO.attemptBlockingIO(computeSha512(f.toFile.toPath)).map(relPath -> _)
                               }
          originalMap        = originalChecksums.toMap
          zipPath           <- ProjectService.zipProject(existingProject).someOrFail(new Exception("export returned None"))
          _                 <- Files.deleteRecursive(projectFolder.path)
          _                 <- ImportService.importZipFile(existingProject, zipPath)
          restoredFiles     <- Files.walk(projectFolder.path).filterZIO(Files.isRegularFile(_)).runCollect
          restoredChecksums <- ZIO.foreach(restoredFiles) { f =>
                                 val relPath = projectFolder.path.toFile.toPath.relativize(f.toFile.toPath).toString
                                 ZIO.attemptBlockingIO(computeSha512(f.toFile.toPath)).map(relPath -> _)
                               }
          restoredMap = restoredChecksums.toMap
        } yield assertTrue(
          restoredMap == originalMap,
          restoredMap.nonEmpty,
        )
      },
    ).provide(
      AssetInfoServiceLive.layer,
      FileChecksumServiceLive.layer,
      ImportServiceLive.layer,
      ProjectService.layer,
      ProjectRepositoryLive.layer,
      SpecConfigurations.storageConfigLayer,
      StorageServiceLive.layer,
      TestUtils.testDbLayerWithEmptyDb,
    ) @@ TestAspect.sequential

  private def corruptZipPayload(source: java.nio.file.Path, target: java.nio.file.Path): Unit = {
    val zipFile = new ZipFile(source.toFile)
    val zos     = new ZipOutputStream(new FileOutputStream(target.toFile))
    try {
      val entries = zipFile.entries()
      while (entries.hasMoreElements) {
        val entry    = entries.nextElement()
        val newEntry = new ZipEntry(entry.getName)
        zos.putNextEntry(newEntry)
        val is    = zipFile.getInputStream(entry)
        val bytes = is.readAllBytes()
        is.close()
        if (entry.getName.startsWith("data/") && bytes.nonEmpty && !entry.isDirectory) {
          bytes(0) = ((bytes(0) ^ 0xFF) & 0xFF).toByte
          zos.write(bytes)
        } else {
          zos.write(bytes)
        }
        zos.closeEntry()
      }
    } finally {
      zos.close()
      zipFile.close()
    }
  }

  private def createZipWithoutManifest(target: java.nio.file.Path): Unit = {
    val zos = new ZipOutputStream(new FileOutputStream(target.toFile))
    try {
      zos.putNextEntry(new ZipEntry("bagit.txt"))
      zos.write("BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n".getBytes("UTF-8"))
      zos.closeEntry()
      zos.putNextEntry(new ZipEntry("data/"))
      zos.closeEntry()
      zos.putNextEntry(new ZipEntry("data/test.txt"))
      zos.write("test content".getBytes("UTF-8"))
      zos.closeEntry()
    } finally {
      zos.close()
    }
  }

  private def computeSha512(path: java.nio.file.Path): String = {
    val digest = MessageDigest.getInstance("SHA-512")
    val is     = new FileInputStream(path.toFile)
    try {
      val buffer = new Array[Byte](8192)
      var read   = is.read(buffer)
      while (read != -1) {
        digest.update(buffer, 0, read)
        read = is.read(buffer)
      }
    } finally is.close()
    digest.digest().map(b => String.format("%02x", b)).mkString
  }
}
