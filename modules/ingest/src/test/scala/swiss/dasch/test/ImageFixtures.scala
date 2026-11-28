/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.test

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

object ImageFixtures {

  def imageIo(format: String, width: Int, height: Int): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    if (!ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out))
      throw new IllegalStateException(s"no ImageIO writer for $format")
    out.toByteArray
  }

  /** A bilevel strip TIFF whose header declares `width` x `height` with one byte of pixel data. */
  def stripTiff(width: Int, height: Int): Array[Byte] = {
    val entries = List(
      (256, 4, width),  // ImageWidth, LONG
      (257, 4, height), // ImageLength, LONG
      (258, 3, 1),      // BitsPerSample, SHORT
      (259, 3, 1),      // Compression none
      (262, 3, 0),      // PhotometricInterpretation WhiteIsZero
      (273, 4, 0),      // StripOffsets, patched below
      (277, 3, 1),      // SamplesPerPixel
      (278, 4, height), // RowsPerStrip
      (279, 4, 1),      // StripByteCounts
    )
    val ifdSize = 2 + entries.size * 12 + 4
    val data    = 8 + ifdSize
    val buf     = ByteBuffer.allocate(data + 1).order(ByteOrder.LITTLE_ENDIAN)
    buf.put('I'.toByte).put('I'.toByte).putShort(42.toShort).putInt(8)
    buf.putShort(entries.size.toShort)
    entries.foreach { case (tag, kind, value) =>
      buf.putShort(tag.toShort).putShort(kind.toShort).putInt(1)
      if (kind == 3) buf.putShort(value.toShort).putShort(0.toShort)
      else buf.putInt(if (tag == 273) data else value)
    }
    buf.putInt(0)
    buf.put(0.toByte)
    buf.array()
  }

  def box(tpe: String, content: Array[Byte]): Array[Byte] =
    ByteBuffer
      .allocate(8 + content.length)
      .putInt(8 + content.length)
      .put(tpe.getBytes("US-ASCII"))
      .put(content)
      .array()

  val jp2SignatureBox: Array[Byte] = box("jP  ", Array[Byte](0x0d, 0x0a, 0x87.toByte, 0x0a))

  /** A JP2 box sequence (signature, ftyp, jp2h with ihdr) without a codestream; width and height are unsigned. */
  def jp2(width: Long, height: Long, brand: String = "jp2 "): Array[Byte] = {
    val ftyp = box("ftyp", (brand + "\u0000\u0000\u0000\u0000" + brand).getBytes("ISO-8859-1"))
    val ihdr = box(
      "ihdr",
      ByteBuffer
        .allocate(14)
        .putInt(height.toInt)
        .putInt(width.toInt)
        .putShort(3.toShort)
        .put(7.toByte)
        .put(7.toByte)
        .array(),
    )
    val colr = box("colr", Array[Byte](1, 0, 0, 0, 0, 0, 16))
    jp2SignatureBox ++ ftyp ++ box("jp2h", colr ++ ihdr)
  }
}
