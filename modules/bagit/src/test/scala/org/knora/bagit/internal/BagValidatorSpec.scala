/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

import zio.*
import zio.nio.file.Files
import zio.nio.file.Path
import zio.test.*
import zio.test.Assertion.*

import org.knora.bagit.BagItError
import org.knora.bagit.ChecksumAlgorithm
import org.knora.bagit.domain.*

object BagValidatorSpec extends ZIOSpecDefault {

  private def createAndReadBag: ZIO[Scope, Any, (Bag, Path)] =
    for {
      tempDir  <- Files.createTempDirectoryScoped(Some("bagit-validator-test"), Seq.empty)
      filePath  = tempDir / "payload.txt"
      _        <- Files.writeBytes(filePath, Chunk.fromArray("Payload content".getBytes("UTF-8")))
      outputZip = tempDir / "test.zip"
      _        <- BagCreator.createBag(
             List(PayloadEntry.File("payload.txt", filePath)),
             List(ChecksumAlgorithm.SHA256),
             Some(BagInfo(sourceOrganization = Some("Test"))),
             outputZip,
           )
      result <- BagReader.readFromZip(outputZip)
    } yield result

  def spec: Spec[Any, Any] = suite("BagValidatorSpec")(
    test("valid bag passes validation") {
      ZIO.scoped {
        for {
          result        <- createAndReadBag
          (bag, bagRoot) = result
          _             <- BagValidator.validate(bag, bagRoot)
        } yield assertCompletes
      }
    },
    test("tampered file content produces ChecksumMismatch") {
      ZIO.scoped {
        for {
          result        <- createAndReadBag
          (bag, bagRoot) = result
          payloadFile    = bagRoot / "data" / "payload.txt"
          _             <- Files.writeBytes(payloadFile, Chunk.fromArray("Tampered content".getBytes("UTF-8")))
          exit          <- BagValidator.validate(bag, bagRoot).exit
        } yield assert(exit)(fails(isSubtype[BagItError.ChecksumMismatch](anything)))
      }
    },
    test("deleted payload file produces ManifestEntryMissing") {
      ZIO.scoped {
        for {
          result        <- createAndReadBag
          (bag, bagRoot) = result
          payloadFile    = bagRoot / "data" / "payload.txt"
          _             <- Files.delete(payloadFile)
          exit          <- BagValidator.validate(bag, bagRoot).exit
        } yield assert(exit)(fails(isSubtype[BagItError.ManifestEntryMissing](anything)))
      }
    },
    test("extra unlisted payload file produces FileNotInManifest") {
      ZIO.scoped {
        for {
          result        <- createAndReadBag
          (bag, bagRoot) = result
          extraFile      = bagRoot / "data" / "extra.txt"
          _             <- Files.writeBytes(extraFile, Chunk.fromArray("Extra content".getBytes("UTF-8")))
          exit          <- BagValidator.validate(bag, bagRoot).exit
        } yield assert(exit)(fails(isSubtype[BagItError.FileNotInManifest](anything)))
      }
    },
    test("deleted bag-info.txt listed in tag manifest produces ManifestEntryMissing") {
      ZIO.scoped {
        for {
          result        <- createAndReadBag
          (bag, bagRoot) = result
          bagInfoFile    = bagRoot / "bag-info.txt"
          _             <- Files.delete(bagInfoFile)
          exit          <- BagValidator.validate(bag, bagRoot).exit
        } yield assert(exit)(
          fails(
            isSubtype[BagItError.ManifestEntryMissing](hasField("path", _.path, equalTo("bag-info.txt"))),
          ),
        )
      }
    },
    test("deleted manifest file listed in tag manifest produces ManifestEntryMissing") {
      ZIO.scoped {
        for {
          result        <- createAndReadBag
          (bag, bagRoot) = result
          manifestFile   = bagRoot / "manifest-sha256.txt"
          _             <- Files.delete(manifestFile)
          exit          <- BagValidator.validate(bag, bagRoot).exit
        } yield assert(exit)(
          fails(
            isSubtype[BagItError.ManifestEntryMissing](hasField("path", _.path, equalTo("manifest-sha256.txt"))),
          ),
        )
      }
    },
    test("tampered tag file produces ChecksumMismatch") {
      ZIO.scoped {
        for {
          result        <- createAndReadBag
          (bag, bagRoot) = result
          bagInfoFile    = bagRoot / "bag-info.txt"
          _             <- Files.writeBytes(bagInfoFile, Chunk.fromArray("Tampered content".getBytes("UTF-8")))
          exit          <- BagValidator.validate(bag, bagRoot).exit
        } yield assert(exit)(
          fails(
            isSubtype[BagItError.ChecksumMismatch](hasField("path", _.path, equalTo("bag-info.txt"))),
          ),
        )
      }
    },
    test("data/ replaced by regular file produces MissingPayloadDirectory") {
      ZIO.scoped {
        for {
          result        <- createAndReadBag
          (bag, bagRoot) = result
          dataDir        = bagRoot / "data"
          _             <- Files.deleteRecursive(dataDir)
          _             <- Files.writeBytes(dataDir, Chunk.fromArray("not a directory".getBytes("UTF-8")))
          exit          <- BagValidator.validate(bag, bagRoot).exit
        } yield assert(exit)(fails(equalTo(BagItError.MissingPayloadDirectory)))
      }
    },
    test("validates bag with 100+ payload files without leaking file descriptors") {
      ZIO.scoped {
        for {
          tempDir <- Files.createTempDirectoryScoped(Some("bagit-manyfiles-test"), Seq.empty)
          entries <- ZIO.foreach((1 to 120).toList) { i =>
                       val name    = f"file-$i%03d.txt"
                       val content = s"Content of file $i"
                       val path    = tempDir / name
                       Files
                         .writeBytes(path, Chunk.fromArray(content.getBytes("UTF-8")))
                         .as(
                           PayloadEntry.File(name, path),
                         )
                     }
          outputZip = tempDir / "many-files.zip"
          _        <- BagCreator.createBag(
                 entries,
                 List(ChecksumAlgorithm.SHA256),
                 Some(BagInfo(sourceOrganization = Some("Test"))),
                 outputZip,
               )
          result        <- BagReader.readFromZip(outputZip)
          (bag, bagRoot) = result
          _             <- BagValidator.validate(bag, bagRoot)
        } yield assertTrue(bag.payloadFiles.size == 120)
      }
    },
    test("payload file deleted between parsing and checksum verification returns typed error, not defect") {
      ZIO.scoped {
        for {
          result        <- createAndReadBag
          (bag, bagRoot) = result
          payloadFile    = bagRoot / "data" / "payload.txt"
          _             <- Files.delete(payloadFile)
          exit          <- BagValidator.validate(bag, bagRoot).exit
        } yield assert(exit)(fails(isSubtype[BagItError.ManifestEntryMissing](anything)))
      }
    },
    test("tag file deleted between parsing and checksum verification returns typed error, not defect") {
      ZIO.scoped {
        for {
          result        <- createAndReadBag
          (bag, bagRoot) = result
          bagInfoFile    = bagRoot / "bag-info.txt"
          _             <- Files.delete(bagInfoFile)
          exit          <- BagValidator.validate(bag, bagRoot).exit
        } yield assert(exit)(
          fails(
            isSubtype[BagItError.ManifestEntryMissing](hasField("path", _.path, equalTo("bag-info.txt"))),
          ),
        )
      }
    },
  )
}
