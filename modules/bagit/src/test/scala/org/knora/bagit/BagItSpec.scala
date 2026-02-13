/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit

import zio.*
import zio.nio.file.Files
import zio.nio.file.Path
import zio.test.*
import zio.test.Assertion.*

import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

import org.knora.bagit.domain.*

object BagItSpec extends ZIOSpecDefault {

  private def createTestFiles: ZIO[Scope, Throwable, (Path, Path, Path)] =
    for {
      tempDir <- Files.createTempDirectoryScoped(Some("bagit-facade-test"), Seq.empty)
      // Single file
      filePath = tempDir / "readme.txt"
      _       <- Files.writeBytes(filePath, Chunk.fromArray("Read me".getBytes("UTF-8")))
      // Directory with files
      dirPath   = tempDir / "content"
      _        <- Files.createDirectory(dirPath)
      _        <- Files.writeBytes(dirPath / "data.csv", Chunk.fromArray("a,b,c".getBytes("UTF-8")))
      sub       = dirPath / "images"
      _        <- Files.createDirectory(sub)
      _        <- Files.writeBytes(sub / "photo.jpg", Chunk.fromArray("fake-jpg-data".getBytes("UTF-8")))
      outputZip = tempDir / "facade-test.zip"
    } yield (filePath, dirPath, outputZip)

  def spec: Spec[Any, Any] = suite("BagItSpec")(
    test("round-trip: create then readAndValidateZip succeeds") {
      ZIO.scoped {
        for {
          setup                         <- createTestFiles
          (filePath, dirPath, outputZip) = setup
          entries                        = List(
                      PayloadEntry.File("readme.txt", filePath),
                      PayloadEntry.Directory("content", dirPath),
                    )
          _ <- BagIt.create(
                 entries,
                 outputZip,
                 bagInfo = Some(BagInfo(sourceOrganization = Some("DaSCH"))),
               )
          result        <- BagIt.readAndValidateZip(outputZip)
          (bag, bagRoot) = result
        } yield assertTrue(bag.manifests.nonEmpty) && assertTrue(bagRoot.toString.nonEmpty)
      }
    },
    test("round-trip: extracted file tree matches original file tree") {
      ZIO.scoped {
        for {
          setup                         <- createTestFiles
          (filePath, dirPath, outputZip) = setup
          entries                        = List(
                      PayloadEntry.File("readme.txt", filePath),
                      PayloadEntry.Directory("content", dirPath),
                    )
          // Read original file contents before bagging
          originalFiles  <- collectRelativeFiles(dirPath, dirPath)
          originalByFile <- ZIO.foreach(originalFiles.sorted) { rel =>
                              Files.readAllBytes(dirPath / rel).map(bytes => rel -> bytes)
                            }
          originalReadme <- Files.readAllBytes(filePath)
          _              <- BagIt.create(entries, outputZip)
          result         <- BagIt.readAndValidateZip(outputZip)
          (_, bagRoot)    = result
          dataDir         = bagRoot / "data"
          // Read extracted file contents and compare to originals
          extractedReadme <- Files.readAllBytes(dataDir / "readme.txt")
          extractedByFile <- ZIO.foreach(originalFiles.sorted) { rel =>
                               Files.readAllBytes(dataDir / "content" / rel).map(bytes => rel -> bytes)
                             }
          // Collect all extracted files to verify no extra or missing files
          allExtracted <- collectRelativeFiles(dataDir, dataDir)
          expectedPaths = "readme.txt" :: originalFiles.map("content/" + _)
        } yield assertTrue(
          extractedReadme == originalReadme,
          extractedByFile.map(_._1) == originalByFile.map(_._1),
          extractedByFile.zip(originalByFile).forall { case ((_, ext), (_, orig)) => ext == orig },
          allExtracted.sorted == expectedPaths.sorted,
        )
      }
    },
    test("default algorithms: BagIt.create without explicit algorithms produces manifest-sha512.txt") {
      ZIO.scoped {
        for {
          setup                   <- createTestFiles
          (filePath, _, outputZip) = setup
          entries                  = List(PayloadEntry.File("readme.txt", filePath))
          _                       <- BagIt.create(entries, outputZip)
          zipEntryNames           <- listZipEntryNames(outputZip)
        } yield assertTrue(
          zipEntryNames.contains("manifest-sha512.txt"),
          !zipEntryNames.contains("manifest-sha256.txt"),
        )
      }
    },
    test("explicit algorithms: BagIt.create with SHA256 produces manifest-sha256.txt") {
      ZIO.scoped {
        for {
          setup                   <- createTestFiles
          (filePath, _, outputZip) = setup
          entries                  = List(PayloadEntry.File("readme.txt", filePath))
          _                       <- BagIt.create(entries, outputZip, algorithms = List(ChecksumAlgorithm.SHA256))
          zipEntryNames           <- listZipEntryNames(outputZip)
        } yield assertTrue(
          zipEntryNames.contains("manifest-sha256.txt"),
          !zipEntryNames.contains("manifest-sha512.txt"),
        )
      }
    },
    test("legacy MD5 bag: manually created bag with manifest-md5.txt is read and validated successfully") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-md5-test"), Seq.empty)
          // Build a valid bag ZIP manually with an MD5 manifest
          outputZip = tempDir / "md5-bag.zip"
          _        <- ZIO.attemptBlocking {
                 val payloadContent = "Legacy payload".getBytes("UTF-8")
                 val md5Digest      = MessageDigest.getInstance("MD5")
                 md5Digest.update(payloadContent)
                 val md5Hex = md5Digest.digest().map(b => String.format("%02x", b)).mkString

                 val bagitTxt    = "BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n"
                 val manifestMd5 = s"$md5Hex  data/legacy.txt\n"
                 val zos         = new ZipOutputStream(new FileOutputStream(outputZip.toFile))
                 try {
                   zos.putNextEntry(new ZipEntry("bagit.txt"))
                   zos.write(bagitTxt.getBytes("UTF-8"))
                   zos.closeEntry()
                   zos.putNextEntry(new ZipEntry("data/"))
                   zos.closeEntry()
                   zos.putNextEntry(new ZipEntry("data/legacy.txt"))
                   zos.write(payloadContent)
                   zos.closeEntry()
                   zos.putNextEntry(new ZipEntry("manifest-md5.txt"))
                   zos.write(manifestMd5.getBytes("UTF-8"))
                   zos.closeEntry()
                 } finally zos.close()
               }
          _ <- BagIt.readAndValidateZip(outputZip)
        } yield assertCompletes
      }
    },
    test("round-trip: file path containing percent sign survives create and validate") {
      ZIO.scoped {
        for {
          tempDir  <- Files.createTempDirectoryScoped(Some("bagit-pct-test"), Seq.empty)
          filePath  = tempDir / "content.txt"
          _        <- Files.writeBytes(filePath, Chunk.fromArray("percent test".getBytes("UTF-8")))
          outputZip = tempDir / "pct-bag.zip"
          // Use a relativePath that contains a literal percent sign
          entries = List(PayloadEntry.File("100%done.txt", filePath))
          _      <- BagIt.create(entries, outputZip)
          _      <- BagIt.readAndValidateZip(outputZip)
          // Verify the manifest inside the ZIP contains the encoded path
          zipEntryNames   <- listZipEntryNames(outputZip)
          manifestContent <- readZipEntry(outputZip, zipEntryNames.find(_.startsWith("manifest-")).get)
        } yield assertTrue(manifestContent.contains("data/100%25done.txt"))
      }
    },
    test("corruption: tampered file produces ChecksumMismatch") {
      ZIO.scoped {
        for {
          tempDir  <- Files.createTempDirectoryScoped(Some("bagit-corrupt-test"), Seq.empty)
          filePath  = tempDir / "original.txt"
          _        <- Files.writeBytes(filePath, Chunk.fromArray("Original content".getBytes("UTF-8")))
          outputZip = tempDir / "bag.zip"
          _        <- BagIt.create(
                 List(PayloadEntry.File("original.txt", filePath)),
                 outputZip,
               )
          // Tamper: extract, modify a payload file, re-zip
          tamperedZip <- tamperWithBag(outputZip, tempDir)
          exit        <- BagIt.readAndValidateZip(tamperedZip).exit
        } yield assert(exit)(fails(isSubtype[BagItError.ChecksumMismatch](anything)))
      }
    },
  )

  private def listZipEntryNames(zipPath: Path): Task[List[String]] =
    ZIO.attemptBlocking {
      val zis = new ZipInputStream(new FileInputStream(zipPath.toFile))
      try {
        var names = List.empty[String]
        var entry = zis.getNextEntry
        while (entry != null) {
          names = names :+ entry.getName
          zis.closeEntry()
          entry = zis.getNextEntry
        }
        names
      } finally zis.close()
    }

  private def readZipEntry(zipPath: Path, entryName: String): Task[String] =
    ZIO.attemptBlocking {
      val zis = new ZipInputStream(new FileInputStream(zipPath.toFile))
      try {
        var result: Option[String] = None
        var entry                  = zis.getNextEntry
        while (entry != null && result.isEmpty) {
          if (entry.getName == entryName)
            result = Some(new String(zis.readAllBytes(), "UTF-8"))
          else
            zis.closeEntry()
          entry = zis.getNextEntry
        }
        result.getOrElse(throw new RuntimeException(s"Entry $entryName not found in ZIP"))
      } finally zis.close()
    }

  private def tamperWithBag(zipPath: Path, workDir: Path): Task[Path] =
    ZIO.attemptBlocking {
      // Read all entries
      val zis     = new ZipInputStream(new FileInputStream(zipPath.toFile))
      var entries = List.empty[(String, Array[Byte])]
      try {
        var entry = zis.getNextEntry
        while (entry != null) {
          if (!entry.isDirectory) {
            val bytes = zis.readAllBytes()
            entries = entries :+ (entry.getName, bytes)
          }
          zis.closeEntry()
          entry = zis.getNextEntry
        }
      } finally zis.close()

      // Tamper with the payload file
      val tampered = entries.map { case (name, bytes) =>
        if (name == "data/original.txt") (name, "Tampered content".getBytes("UTF-8"))
        else (name, bytes)
      }

      // Write tampered zip
      val tamperedPath = (workDir / "tampered.zip").toFile
      val zos          = new ZipOutputStream(new FileOutputStream(tamperedPath))
      try
        tampered.foreach { case (name, bytes) =>
          zos.putNextEntry(new ZipEntry(name))
          zos.write(bytes)
          zos.closeEntry()
        }
      finally zos.close()

      Path.fromJava(tamperedPath.toPath)
    }

  private def collectRelativeFiles(dir: Path, base: Path): Task[List[String]] =
    Files
      .walk(dir)
      .filterZIO(p => Files.isRegularFile(p))
      .map(p => base.toFile.toPath.relativize(p.toFile.toPath).toString)
      .runCollect
      .map(_.toList)
}
