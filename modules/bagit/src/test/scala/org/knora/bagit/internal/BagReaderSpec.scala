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
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import org.knora.bagit.BagItError
import org.knora.bagit.ChecksumAlgorithm
import org.knora.bagit.ExtractionLimits
import org.knora.bagit.domain.*

object BagReaderSpec extends ZIOSpecDefault {

  private def createTestBag: ZIO[Scope, Any, Path] =
    for {
      tempDir  <- Files.createTempDirectoryScoped(Some("bagit-reader-test"), Seq.empty)
      filePath  = tempDir / "test-file.txt"
      _        <- Files.writeBytes(filePath, Chunk.fromArray("Test content".getBytes("UTF-8")))
      outputZip = tempDir / "test.zip"
      _        <- BagCreator.createBag(
             List(PayloadEntry.File("test-file.txt", filePath)),
             List(ChecksumAlgorithm.SHA256),
             Some(BagInfo(sourceOrganization = Some("Test Org"))),
             outputZip,
           )
    } yield outputZip

  private def createMinimalZip(tempDir: Path, entries: Map[String, String]): Task[Path] =
    ZIO.attemptBlocking {
      val zipPath = (tempDir / "minimal.zip").toFile
      val zos     = new ZipOutputStream(new FileOutputStream(zipPath))
      try
        entries.foreach { case (name, content) =>
          zos.putNextEntry(new ZipEntry(name))
          zos.write(content.getBytes("UTF-8"))
          zos.closeEntry()
        }
      finally zos.close()
      Path.fromJava(zipPath.toPath)
    }

  def spec: Spec[Any, Any] = suite("BagReaderSpec")(
    test("reads a bag created by BagCreator") {
      ZIO.scoped {
        for {
          zipPath <- createTestBag
          result  <- BagReader.readFromZip(zipPath)
          (bag, _) = result
        } yield assertTrue(
          bag.version == "1.0",
          bag.encoding == "UTF-8",
          bag.manifests.nonEmpty,
          bag.manifests.head.algorithm == ChecksumAlgorithm.SHA256,
          bag.manifests.head.entries.nonEmpty,
          bag.bagInfo.isDefined,
          bag.bagInfo.get.sourceOrganization.contains("Test Org"),
          bag.payloadFiles.nonEmpty,
        )
      }
    },
    test("returns MissingBagitTxt for bag without bagit.txt") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-missing-test"), Seq.empty)
          zipPath <- createMinimalZip(tempDir, Map("data/file.txt" -> "content"))
          result  <- BagReader.readFromZip(zipPath).either
        } yield assertTrue(result == Left(BagItError.MissingBagitTxt))
      }
    },
    test("returns MissingPayloadManifest for bag without manifests") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-nomanifest-test"), Seq.empty)
          zipPath <- createMinimalZip(
                       tempDir,
                       Map(
                         "bagit.txt"     -> "BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n",
                         "data/file.txt" -> "content",
                       ),
                     )
          result <- BagReader.readFromZip(zipPath).either
        } yield assertTrue(result == Left(BagItError.MissingPayloadManifest))
      }
    },
    test("rejects bagit.txt with version '1.0.1'") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-version-test"), Seq.empty)
          zipPath <- createMinimalZip(
                       tempDir,
                       Map(
                         "bagit.txt"     -> "BagIt-Version: 1.0.1\nTag-File-Character-Encoding: UTF-8\n",
                         "data/file.txt" -> "content",
                       ),
                     )
          result <- BagReader.readFromZip(zipPath).either
        } yield assertTrue(result.isLeft, result.left.exists(_.isInstanceOf[BagItError.InvalidBagitTxt]))
      }
    },
    test("rejects bagit.txt with reversed lines") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-reversed-test"), Seq.empty)
          zipPath <- createMinimalZip(
                       tempDir,
                       Map(
                         "bagit.txt"     -> "Tag-File-Character-Encoding: UTF-8\nBagIt-Version: 1.0\n",
                         "data/file.txt" -> "content",
                       ),
                     )
          result <- BagReader.readFromZip(zipPath).either
        } yield assertTrue(result.isLeft, result.left.exists(_.isInstanceOf[BagItError.InvalidBagitTxt]))
      }
    },
    test("rejects bagit.txt with an extra third line") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-extraline-test"), Seq.empty)
          zipPath <- createMinimalZip(
                       tempDir,
                       Map(
                         "bagit.txt"     -> "BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\nExtra-Line: value\n",
                         "data/file.txt" -> "content",
                       ),
                     )
          result <- BagReader.readFromZip(zipPath).either
        } yield assertTrue(result.isLeft, result.left.exists(_.isInstanceOf[BagItError.InvalidBagitTxt]))
      }
    },
    test("rejects bagit.txt with version '0.97'") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-oldversion-test"), Seq.empty)
          zipPath <- createMinimalZip(
                       tempDir,
                       Map(
                         "bagit.txt"     -> "BagIt-Version: 0.97\nTag-File-Character-Encoding: UTF-8\n",
                         "data/file.txt" -> "content",
                       ),
                     )
          result <- BagReader.readFromZip(zipPath).either
        } yield assertTrue(result.isLeft, result.left.exists(_.isInstanceOf[BagItError.InvalidBagitTxt]))
      }
    },
    test("accepts bagit.txt with CRLF line endings") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-crlf-test"), Seq.empty)
          zipPath <- createMinimalZip(
                       tempDir,
                       Map(
                         "bagit.txt"           -> "BagIt-Version: 1.0\r\nTag-File-Character-Encoding: UTF-8\r\n",
                         "data/file.txt"       -> "content",
                         "manifest-sha256.txt" -> "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  data/file.txt\n",
                       ),
                     )
          result <- BagReader.readFromZip(zipPath).either
        } yield assertTrue(result.isRight)
      }
    },
    test("accepts valid bagit.txt with exactly two correct lines") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-valid-test"), Seq.empty)
          zipPath <- createMinimalZip(
                       tempDir,
                       Map(
                         "bagit.txt"           -> "BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n",
                         "data/file.txt"       -> "content",
                         "manifest-sha256.txt" -> "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  data/file.txt\n",
                       ),
                     )
          result <- BagReader.readFromZip(zipPath).either
        } yield assertTrue(result.isRight)
      }
    },
    test("does not throw NPE when data/ is a regular file instead of a directory") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-data-file-test"), Seq.empty)
          // Create a valid bag zip but with data/ as a regular file instead of a directory
          // We need a manifest entry but data/ won't be a directory, triggering the walkFiles NPE guard
          zipPath <- createMinimalZip(
                       tempDir,
                       Map(
                         "bagit.txt"           -> "BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n",
                         "manifest-sha256.txt" -> "abc123  data/file.txt\n",
                         "data"                -> "this is a file, not a directory",
                       ),
                     )
          result <- BagReader.readFromZip(zipPath).either
        } yield assertTrue(result == Left(BagItError.MissingPayloadDirectory))
      }
    },
    test("extracted temp directory can be fully deleted after read (no leaked file handles)") {
      for {
        extractedDir <- ZIO.scoped {
                          for {
                            zipPath     <- createTestBag
                            result      <- BagReader.readFromZip(zipPath)
                            (_, bagRoot) = result
                          } yield bagRoot
                        }
        // After scope closes, the temp dir should be deletable
        // If file handles are still open, deletion would fail on some OSes
        dirExists <- ZIO.attemptBlocking(extractedDir.toFile.exists())
      } yield
        // Scope cleanup already deleted the temp dir, proving no locked handles
        assertTrue(!dirExists)
    },
    test("rejects zip with single entry exceeding maxSingleEntryBytes") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-bomb-single-test"), Seq.empty)
          // Create a zip with a single file larger than 1KB
          zipPath <- createMinimalZip(
                       tempDir,
                       Map(
                         "bagit.txt"      -> "BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n",
                         "data/large.txt" -> ("x" * 2000),
                       ),
                     )
          result <- BagReader.readFromZip(zipPath, ExtractionLimits(maxSingleEntryBytes = 1024)).either
        } yield assertTrue(result.isLeft, result.left.exists(_.isInstanceOf[BagItError.ExtractionLimitExceeded]))
      }
    },
    test("rejects zip with more entries than maxEntryCount") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-bomb-count-test"), Seq.empty)
          // Create a zip with 8 entries (bagit.txt, data/, data/0..4.txt, manifest) — limit to 5
          entries = Map(
                      "bagit.txt"           -> "BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n",
                      "data/"               -> "",
                      "data/file0.txt"      -> "a",
                      "data/file1.txt"      -> "b",
                      "data/file2.txt"      -> "c",
                      "data/file3.txt"      -> "d",
                      "data/file4.txt"      -> "e",
                      "manifest-sha256.txt" -> "abc  data/file0.txt\n",
                    )
          zipPath <- createMinimalZip(tempDir, entries)
          result  <- BagReader.readFromZip(zipPath, ExtractionLimits(maxEntryCount = 5)).either
        } yield assertTrue(result.isLeft, result.left.exists(_.isInstanceOf[BagItError.ExtractionLimitExceeded]))
      }
    },
    test("rejects zip with total decompressed size exceeding maxTotalBytes") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-bomb-total-test"), Seq.empty)
          // Create a zip with multiple entries whose total size exceeds 2KB
          zipPath <- createMinimalZip(
                       tempDir,
                       Map(
                         "bagit.txt"  -> "BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n",
                         "data/a.txt" -> ("a" * 800),
                         "data/b.txt" -> ("b" * 800),
                         "data/c.txt" -> ("c" * 800),
                       ),
                     )
          result <- BagReader.readFromZip(zipPath, ExtractionLimits(maxTotalBytes = 2048)).either
        } yield assertTrue(result.isLeft, result.left.exists(_.isInstanceOf[BagItError.ExtractionLimitExceeded]))
      }
    },
    test("normal bag within default limits is extracted successfully") {
      ZIO.scoped {
        for {
          zipPath <- createTestBag
          result  <- BagReader.readFromZip(zipPath, ExtractionLimits.default)
          (bag, _) = result
        } yield assertTrue(bag.version == "1.0", bag.manifests.nonEmpty)
      }
    },
    test("rejects bag with manifest file larger than maxTagFileSize") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-large-manifest-test"), Seq.empty)
          // Create a manifest with many entries so it exceeds 100 bytes
          largeManifest =
            (1 to 10)
              .map(i => s"abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890  data/file$i.txt")
              .mkString("\n")
          zipPath <- createMinimalZip(
                       tempDir,
                       Map(
                         "bagit.txt"           -> "BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n",
                         "manifest-sha256.txt" -> largeManifest,
                         "data/file1.txt"      -> "content",
                       ),
                     )
          result <- BagReader.readFromZip(zipPath, maxTagFileSize = 100).either
        } yield assertTrue(result.isLeft, result.left.exists(_.isInstanceOf[BagItError.FileTooLarge]))
      }
    },
    test("rejects bag with bag-info.txt larger than maxTagFileSize") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-large-baginfo-test"), Seq.empty)
          // Create a bag-info.txt that exceeds 100 bytes
          largeBagInfo = s"Source-Organization: ${"A" * 200}\n"
          zipPath     <- createMinimalZip(
                       tempDir,
                       Map(
                         "bagit.txt"           -> "BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n",
                         "manifest-sha256.txt" -> "abcdef1234567890  data/file.txt\n",
                         "bag-info.txt"        -> largeBagInfo,
                         "data/file.txt"       -> "content",
                       ),
                     )
          result <- BagReader.readFromZip(zipPath, maxTagFileSize = 100).either
        } yield assertTrue(result.isLeft, result.left.exists(_.isInstanceOf[BagItError.FileTooLarge]))
      }
    },
    test("bag with normal-sized tag files is read successfully with default limit") {
      ZIO.scoped {
        for {
          zipPath <- createTestBag
          result  <- BagReader.readFromZip(zipPath)
          (bag, _) = result
        } yield assertTrue(bag.version == "1.0", bag.manifests.nonEmpty, bag.bagInfo.isDefined)
      }
    },
    test("handles deeply nested directory (500 levels) without StackOverflowError") {
      // Test that walkDirectory in BagCreator handles deeply nested dirs.
      // macOS PATH_MAX is 1024, so we use the max nesting that fits on disk,
      // then verify the file is found in the produced zip.
      val depth = 450 // single-char dir names stay within OS path limits
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("b"), Seq.empty)
          _       <- ZIO.attemptBlocking {
                 var current = tempDir.toFile
                 for (_ <- 1 to depth) {
                   val child = new java.io.File(current, "d")
                   child.mkdir()
                   current = child
                 }
                 val file = new java.io.File(current, "f.txt")
                 val fos  = new java.io.FileOutputStream(file)
                 try fos.write("deep".getBytes("UTF-8"))
                 finally fos.close()
               }
          outputZip = tempDir / "o.zip"
          nestedDir = tempDir / "d"
          _        <- BagCreator.createBag(
                 List(PayloadEntry.Directory("n", nestedDir)),
                 List(ChecksumAlgorithm.SHA256),
                 None,
                 outputZip,
               )
          // Verify the zip contains the deeply nested file by inspecting zip entries
          zipEntries <- ZIO.attemptBlocking {
                          val zis     = new java.util.zip.ZipInputStream(new FileInputStream(outputZip.toFile))
                          var entries = List.empty[String]
                          try {
                            var entry = zis.getNextEntry
                            while (entry != null) {
                              entries = entry.getName :: entries
                              zis.closeEntry()
                              entry = zis.getNextEntry
                            }
                          } finally zis.close()
                          entries
                        }
          dataEntries = zipEntries.filter(e => e.startsWith("data/") && !e.endsWith("/"))
        } yield assertTrue(dataEntries.size == 1, dataEntries.head.endsWith("f.txt"))
      }
    } @@ TestAspect.timeout(60.seconds),
    test("handles flat directory with 1000 files, all sorted by name") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-flat-test"), Seq.empty)
          flatDir  = tempDir / "flat"
          _       <- Files.createDirectory(flatDir)
          _       <- ZIO.attemptBlocking {
                 for (i <- 1 to 1000) {
                   val file = new java.io.File(flatDir.toFile, f"file-$i%04d.txt")
                   val fos  = new java.io.FileOutputStream(file)
                   try fos.write(s"content $i".getBytes("UTF-8"))
                   finally fos.close()
                 }
               }
          outputZip = tempDir / "flat.zip"
          _        <- BagCreator.createBag(
                 List(PayloadEntry.Directory("", flatDir)),
                 List(ChecksumAlgorithm.SHA256),
                 None,
                 outputZip,
               )
          result  <- BagReader.readFromZip(outputZip)
          (bag, _) = result
          // Verify all 1000 files are present
          // Verify sorted order: payloadFiles should be in alphabetical order
          paths = bag.payloadFiles.map(PayloadPath.value)
        } yield assertTrue(bag.payloadFiles.size == 1000, paths == paths.sorted)
      }
    } @@ TestAspect.timeout(120.seconds),
    test("mixed files and subdirectories returns files in depth-first, files-before-dirs order") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-mixed-test"), Seq.empty)
          mixedDir = tempDir / "mixed"
          _       <- Files.createDirectory(mixedDir)
          _       <- ZIO.attemptBlocking {
                 // Root: a.txt, b.txt, subA/, subB/
                 new java.io.FileOutputStream(new java.io.File(mixedDir.toFile, "a.txt")).close()
                 new java.io.FileOutputStream(new java.io.File(mixedDir.toFile, "b.txt")).close()
                 val subA = new java.io.File(mixedDir.toFile, "subA")
                 subA.mkdir()
                 // subA: c.txt, subA1/
                 new java.io.FileOutputStream(new java.io.File(subA, "c.txt")).close()
                 val subA1 = new java.io.File(subA, "subA1")
                 subA1.mkdir()
                 new java.io.FileOutputStream(new java.io.File(subA1, "d.txt")).close()
                 val subB = new java.io.File(mixedDir.toFile, "subB")
                 subB.mkdir()
                 new java.io.FileOutputStream(new java.io.File(subB, "e.txt")).close()
               }
          outputZip = tempDir / "mixed.zip"
          _        <- BagCreator.createBag(
                 List(PayloadEntry.Directory("", mixedDir)),
                 List(ChecksumAlgorithm.SHA256),
                 None,
                 outputZip,
               )
          result  <- BagReader.readFromZip(outputZip)
          (bag, _) = result
          paths    = bag.payloadFiles.map(PayloadPath.value)
        } yield
          // Expected depth-first, files-before-dirs order:
          // Root files first (sorted): a.txt, b.txt
          // Then subA (first alphabetically): subA/c.txt
          // Then subA/subA1: subA/subA1/d.txt
          // Then subB: subB/e.txt
          assertTrue(
            paths == List("a.txt", "b.txt", "subA/c.txt", "subA/subA1/d.txt", "subB/e.txt"),
          )
      }
    },
  )
}
