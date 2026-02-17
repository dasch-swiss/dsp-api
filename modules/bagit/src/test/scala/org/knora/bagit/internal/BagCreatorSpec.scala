/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

import zio.*
import zio.nio.file.Files
import zio.nio.file.Path
import zio.test.*

import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

import org.knora.bagit.ChecksumAlgorithm
import org.knora.bagit.domain.*

object BagCreatorSpec extends ZIOSpecDefault {

  private def createTempSetup: ZIO[Scope, Throwable, (Path, Path, Path)] =
    for {
      tempDir <- Files.createTempDirectoryScoped(Some("bagit-test"), Seq.empty)
      // Create a single file
      fileContent = "Hello, BagIt!"
      filePath    = tempDir / "single-file.txt"
      _          <- Files.writeBytes(filePath, Chunk.fromArray(fileContent.getBytes("UTF-8")))
      // Create a directory with files
      dirPath   = tempDir / "test-dir"
      _        <- Files.createDirectory(dirPath)
      _        <- Files.writeBytes(dirPath / "a.txt", Chunk.fromArray("File A content".getBytes("UTF-8")))
      subDir    = dirPath / "sub"
      _        <- Files.createDirectory(subDir)
      _        <- Files.writeBytes(subDir / "b.txt", Chunk.fromArray("File B content".getBytes("UTF-8")))
      outputZip = tempDir / "test-bag.zip"
    } yield (filePath, dirPath, outputZip)

  private def unzipEntries(zipPath: Path): Task[Map[String, Array[Byte]]] =
    ZIO.attemptBlocking {
      val zis     = new ZipInputStream(new FileInputStream(zipPath.toFile))
      var entries = Map.empty[String, Array[Byte]]
      try {
        var entry = zis.getNextEntry
        while (entry != null) {
          if (!entry.isDirectory) {
            val bytes = zis.readAllBytes()
            entries = entries + (entry.getName -> bytes)
          }
          zis.closeEntry()
          entry = zis.getNextEntry
        }
      } finally zis.close()
      entries
    }

  private def sha256Hex(bytes: Array[Byte]): String = {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(bytes)
    md.digest().map(b => String.format("%02x", b)).mkString
  }

  def spec: Spec[Any, Any] = suite("BagCreatorSpec")(
    test("does not throw NPE when PayloadEntry.Directory points to a non-existent directory") {
      ZIO.scoped {
        for {
          tempDir    <- Files.createTempDirectoryScoped(Some("bagit-nonexist-test"), Seq.empty)
          outputZip   = tempDir / "test.zip"
          nonExistent = tempDir / "does-not-exist"
          result     <- BagCreator
                      .createBag(
                        List(PayloadEntry.Directory("missing", nonExistent)),
                        List(ChecksumAlgorithm.SHA256),
                        Some(BagInfo(sourceOrganization = Some("Test"))),
                        outputZip,
                      )
                      .either
        } yield
          // walkDirectory returns Nil for non-existent dir, so bag is created with zero payload files
          assertTrue(result.isRight)
      }
    },
    test("creates a valid BagIt bag with file and directory entries") {
      ZIO.scoped {
        for {
          setup                         <- createTempSetup
          (filePath, dirPath, outputZip) = setup
          entries                        = List(
                      PayloadEntry.File("single-file.txt", filePath),
                      PayloadEntry.Directory("test-dir", dirPath),
                    )
          result <- BagCreator.createBag(
                      entries,
                      List(ChecksumAlgorithm.SHA256),
                      Some(BagInfo(sourceOrganization = Some("Test Org"))),
                      outputZip,
                    )
          zipEntries <- unzipEntries(result)
        } yield {
          // Verify bagit.txt exists and has correct content
          val bagitTxt = new String(zipEntries("bagit.txt"), "UTF-8")
          assertTrue(bagitTxt.contains("BagIt-Version: 1.0")) &&
          assertTrue(bagitTxt.contains("Tag-File-Character-Encoding: UTF-8")) &&
          // Verify data files exist
          assertTrue(zipEntries.contains("data/single-file.txt")) &&
          assertTrue(zipEntries.contains("data/test-dir/a.txt")) &&
          assertTrue(zipEntries.contains("data/test-dir/sub/b.txt")) &&
          // Verify manifest exists and checksums match
          assertTrue(zipEntries.contains("manifest-sha256.txt")) && {
            val manifestContent = new String(zipEntries("manifest-sha256.txt"), "UTF-8")
            val expectedSingle  = sha256Hex(zipEntries("data/single-file.txt"))
            assertTrue(manifestContent.contains(s"$expectedSingle  data/single-file.txt"))
          } &&
          // Verify bag-info.txt exists
          assertTrue(zipEntries.contains("bag-info.txt")) && {
            val bagInfoContent = new String(zipEntries("bag-info.txt"), "UTF-8")
            assertTrue(bagInfoContent.contains("Source-Organization: Test Org")) &&
            assertTrue(bagInfoContent.contains("Payload-Oxum:")) &&
            assertTrue(bagInfoContent.contains("Bagging-Date:"))
          } &&
          // Verify tag manifest exists
          assertTrue(zipEntries.contains("tagmanifest-sha256.txt")) && {
            val tagManifest = new String(zipEntries("tagmanifest-sha256.txt"), "UTF-8")
            assertTrue(tagManifest.contains("bagit.txt")) &&
            assertTrue(tagManifest.contains("manifest-sha256.txt")) &&
            assertTrue(tagManifest.contains("bag-info.txt"))
          }
        }
      }
    },
  )
}
