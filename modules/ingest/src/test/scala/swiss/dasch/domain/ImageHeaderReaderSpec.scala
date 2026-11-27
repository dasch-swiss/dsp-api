/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.junit.runner.RunWith
import swiss.dasch.test.ImageFixtures
import zio.*
import zio.nio.file.Files
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class ImageHeaderReaderSpec extends ZIOSpecDefault {

  private def readBytes(bytes: Array[Byte]) =
    ZIO.scoped {
      for {
        file <- Files.createTempFileScoped(".orig")
        _    <- Files.writeBytes(file, Chunk.fromArray(bytes))
        dim  <- ImageHeaderReader.read(file)
      } yield dim.map(d => (d.width.value, d.height.value))
    }

  val spec = suite("ImageHeaderReader")(
    test("reads PNG, JPEG and TIFF dimensions") {
      for {
        png  <- readBytes(ImageFixtures.imageIo("png", 7, 5))
        jpeg <- readBytes(ImageFixtures.imageIo("jpeg", 9, 4))
        tiff <- readBytes(ImageFixtures.imageIo("tiff", 3, 11))
      } yield assertTrue(png.contains((7, 5)), jpeg.contains((9, 4)), tiff.contains((3, 11)))
    },
    test("reads a TIFF header that declares more pixels than the file holds") {
      readBytes(ImageFixtures.stripTiff(100000, 100000)).map(dim => assertTrue(dim.contains((100000, 100000))))
    },
    test("reads JP2 and JPX dimensions from ihdr inside jp2h") {
      for {
        jp2 <- readBytes(ImageFixtures.jp2(width = 12, height = 34))
        jpx <- readBytes(ImageFixtures.jp2(width = 56, height = 78, brand = "jpx "))
      } yield assertTrue(jp2.contains((12, 34)), jpx.contains((56, 78)))
    },
    test("reads JP2 width and height as unsigned and keeps them above any limit") {
      readBytes(ImageFixtures.jp2(width = 0xffffffffL, height = 5)).map(dim =>
        assertTrue(dim.contains((Int.MaxValue, 5))),
      )
    },
    test("returns None for deeply nested jp2h boxes, without a stack overflow") {
      val depth = 100000
      val buf   = java.nio.ByteBuffer.allocate(8 * depth)
      (0 until depth).foreach(i => buf.putInt(8 * (depth - i)).put("jp2h".getBytes("US-ASCII")))
      val nested = buf.array()
      readBytes(ImageFixtures.jp2SignatureBox ++ nested).map(dim => assertTrue(dim.isEmpty))
    },
    test("returns None for a truncated file") {
      for {
        png <- readBytes(ImageFixtures.imageIo("png", 7, 5).take(20))
        jp2 <- readBytes(ImageFixtures.jp2(width = 12, height = 34).dropRight(10))
      } yield assertTrue(png.isEmpty, jp2.isEmpty)
    },
    test("returns None for a file that is not an image") {
      readBytes("not an image".getBytes).map(dim => assertTrue(dim.isEmpty))
    },
  )
}
