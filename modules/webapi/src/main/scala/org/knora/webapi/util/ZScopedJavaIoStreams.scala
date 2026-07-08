/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import zio.*
import zio.nio.file.Path

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File as JFile
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Path as JPath
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZScopedJavaIoStreams {

  def byteArrayOutputStream(): ZIO[Scope, Throwable, ByteArrayOutputStream] =
    ZIO.fromAutoCloseable(ZIO.attempt(new ByteArrayOutputStream()))

  def fileInputStream(path: Path): ZIO[Scope, IOException, FileInputStream]  = fileInputStream(path.toFile)
  def fileInputStream(file: JPath): ZIO[Scope, IOException, FileInputStream] = fileInputStream(file.toFile)
  def fileInputStream(file: JFile): ZIO[Scope, IOException, FileInputStream] =
    ZIO.fromAutoCloseable(
      ZIO
        .attemptBlocking(new FileInputStream(file))
        .refineToOrDie[IOException],
    )

  def fileOutputStream(path: Path): ZIO[Scope, IOException, FileOutputStream]  = fileOutputStream(path.toFile)
  def fileOutputStream(path: JPath): ZIO[Scope, IOException, FileOutputStream] = fileOutputStream(path.toFile)
  def fileOutputStream(file: JFile): ZIO[Scope, IOException, FileOutputStream] =
    ZIO.fromAutoCloseable(
      ZIO
        .attemptBlocking(new FileOutputStream(file))
        .refineToOrDie[IOException],
    )

  def zipOutputStream(file: JFile): ZIO[Scope, IOException, ZipOutputStream] =
    ZIO.fromAutoCloseable(
      ZIO
        .attemptBlocking(new ZipOutputStream(new FileOutputStream(file)))
        .refineToOrDie[IOException],
    )

  def zipInputStream(path: Path): ZIO[Scope, IOException, ZipInputStream]  = zipInputStream(path.toFile)
  def zipInputStream(path: JPath): ZIO[Scope, IOException, ZipInputStream] = zipInputStream(path.toFile)
  def zipInputStream(file: JFile): ZIO[Scope, IOException, ZipInputStream] =
    ZIO.fromAutoCloseable(
      ZIO
        .attemptBlocking(new ZipInputStream(new FileInputStream(file)))
        .refineToOrDie[IOException],
    )

  /**
   * Opens or creates a file, returning an output stream that may be used to write bytes to the file.
   * Truncates and overwrites an existing file, or create the file if it doesn't initially exist.
   * The resulting stream will be buffered.
   *
   * @param path The path to the file.
   * @return The managed output stream.
   */
  def fileBufferedOutputStream(path: Path): ZIO[Scope, IOException, BufferedOutputStream] =
    fileBufferedOutputStream(path.toFile)
  def fileBufferedOutputStream(path: JPath): ZIO[Scope, IOException, BufferedOutputStream] =
    fileBufferedOutputStream(path.toFile)
  def fileBufferedOutputStream(file: JFile): ZIO[Scope, IOException, BufferedOutputStream] =
    ZIO.fromAutoCloseable(
      ZIO
        .attemptBlocking(new BufferedOutputStream(new FileOutputStream(file)))
        .refineToOrDie[IOException],
    )
}
