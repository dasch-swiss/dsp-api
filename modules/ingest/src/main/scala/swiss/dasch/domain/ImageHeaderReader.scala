/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.UIO
import zio.ZIO
import zio.nio.file.Path

import java.io.RandomAccessFile
import javax.imageio.ImageIO
import javax.imageio.stream.FileImageInputStream
import scala.annotation.tailrec
import scala.util.Using

/** Reads an image's width and height from its header, without decoding any pixels. */
object ImageHeaderReader {

  private val jp2Signature = Array[Byte](0, 0, 0, 12, 'j', 'P', ' ', ' ', 0x0d, 0x0a, 0x87.toByte, 0x0a)

  /** `None` when the file is not a readable TIFF, PNG, JPEG, JP2 or JPX header. */
  def read(path: Path): UIO[Option[Dimensions]] =
    ZIO
      .attemptBlocking(readBlocking(path.toFile))
      .tapErrorCause(ZIO.logWarningCause(s"Image header of $path is unreadable, pixel limit not checked", _))
      .orElseSucceed(None)

  private def readBlocking(file: java.io.File): Option[Dimensions] =
    if (isJp2(file))
      Using.resource(new RandomAccessFile(file, "r"))(raf => readJp2(raf, jp2Signature.length, raf.length()))
    else readWithImageIo(file)

  private def isJp2(file: java.io.File): Boolean =
    Using.resource(new RandomAccessFile(file, "r")) { raf =>
      val head = new Array[Byte](jp2Signature.length)
      raf.length() >= head.length && { raf.readFully(head); head.sameElements(jp2Signature) }
    }

  /** ImageIO returns a TIFF LONG width or height as a signed Int; the value is unsigned. */
  private def readWithImageIo(file: java.io.File): Option[Dimensions] =
    Using.resource(new FileImageInputStream(file)) { stream =>
      val readers = ImageIO.getImageReaders(stream)
      Option.when(readers.hasNext)(readers.next()).flatMap { reader =>
        try {
          reader.setInput(stream, true, true)
          dimensions(Integer.toUnsignedLong(reader.getWidth(0)), Integer.toUnsignedLong(reader.getHeight(0)))
        } finally reader.dispose()
      }
    }

  private val jp2h = boxType("jp2h")
  private val ihdr = boxType("ihdr")

  /** `jp2h` precedes the codestream in a valid file, so the walk gives up after a few boxes. */
  private val maxTopLevelBoxes = 16

  private def readJp2(raf: RandomAccessFile, start: Long, end: Long): Option[Dimensions] =
    findBox(raf, start, end, jp2h, maxTopLevelBoxes).flatMap { case (from, to) =>
      findBox(raf, from, to, ihdr, maxTopLevelBoxes).filter { case (f, t) => t - f >= 8 }.flatMap { case (f, _) =>
        raf.seek(f)
        val height = raf.readInt()
        val width  = raf.readInt()
        dimensions(Integer.toUnsignedLong(width), Integer.toUnsignedLong(height))
      }
    }

  /** The content range of the first box of `tpe` among the boxes in `[pos, end)`. */
  @tailrec
  private def findBox(raf: RandomAccessFile, pos: Long, end: Long, tpe: Int, boxesLeft: Int): Option[(Long, Long)] =
    if (boxesLeft == 0 || pos + 8 > end) None
    else {
      raf.seek(pos)
      val lbox       = Integer.toUnsignedLong(raf.readInt())
      val tbox       = raf.readInt()
      val (len, hdr) =
        if (lbox == 1) (raf.readLong(), 16L)
        else if (lbox == 0) (end - pos, 8L)
        else (lbox, 8L)
      if (len < hdr || len > end - pos) None
      else if (tbox == tpe) Some((pos + hdr, pos + len))
      else findBox(raf, pos + len, end, tpe, boxesLeft - 1)
    }

  /**
   * A width or height above `Int.MaxValue` is clamped so the pixel count still exceeds any practical limit; such an
   * image must not fall through to `None`, which skips the check.
   */
  private def dimensions(width: Long, height: Long): Option[Dimensions] =
    Dimensions.from(width.min(Int.MaxValue).toInt, height.min(Int.MaxValue).toInt).toOption

  private def boxType(name: String): Int = name.getBytes("US-ASCII").foldLeft(0)((acc, b) => (acc << 8) | (b & 0xff))
}
