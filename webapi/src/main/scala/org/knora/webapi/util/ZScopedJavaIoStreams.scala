/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import zio.*

import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZScopedJavaIoStreams {

  private def release(in: AutoCloseable): UIO[Unit] =
    ZIO.attempt(in.close()).logError("Unable to close AutoCloseable.").ignore

  def bufferedInputStream(in: InputStream): ZIO[Any & Scope, Throwable, InputStream] = {
    def acquire = ZIO.attempt(new BufferedInputStream(in))
    ZIO.acquireRelease(acquire)(release)
  }

  def bufferedOutputStream(in: OutputStream): ZIO[Any & Scope, Throwable, OutputStream] = {
    def acquire = ZIO.attempt(new BufferedOutputStream(in))
    ZIO.acquireRelease(acquire)(release)
  }

  def byteArrayOutputStream(): ZIO[Any & Scope, Throwable, ByteArrayOutputStream] = {
    def acquire                   = ZIO.attempt(new ByteArrayOutputStream())
    def release(os: OutputStream) = ZIO.succeed(os.close())
    ZIO.acquireRelease(acquire)(release)
  }

  def fileInputStream(path: zio.nio.file.Path): ZIO[Any & Scope, Throwable, InputStream] =
    fileInputStream(path.toFile)
  def fileInputStream(file: File): ZIO[Any & Scope, Throwable, InputStream] =
    fileInputStream(file.toPath)
  def fileInputStream(path: Path): ZIO[Any & Scope, Throwable, InputStream] = {
    def acquire = ZIO.attempt(Files.newInputStream(path))
    if (!Files.exists(path)) {
      ZIO.fail(new IllegalArgumentException(s"File ${path.toAbsolutePath} does not exist."))
    } else {
      ZIO.acquireRelease(acquire)(release).flatMap(bufferedInputStream)
    }
  }

  def fileOutputStream(path: zio.nio.file.Path): ZIO[Scope, Throwable, FileOutputStream] = fileOutputStream(path.toFile)
  def fileOutputStream(path: Path): ZIO[Scope, Throwable, FileOutputStream]              = fileOutputStream(path.toFile)
  def fileOutputStream(file: File): ZIO[Scope, Throwable, FileOutputStream] = {
    def acquire = ZIO.attempt(new FileOutputStream(file))
    ZIO.acquireRelease(acquire)(release)
  }

  def zipOutputStream(file: File): ZIO[Scope, Throwable, ZipOutputStream] = {
    def acquire(fos: FileOutputStream) = ZIO.attempt(new ZipOutputStream(fos))
    fileOutputStream(file).flatMap(fos => ZIO.acquireRelease(acquire(fos))(release))
  }

  def zipInputStream(path: nio.file.Path): ZIO[Scope, Throwable, ZipInputStream] = zipInputStream(path.toFile)
  def zipInputStream(file: File): ZIO[Scope, Throwable, ZipInputStream] = {
    def acquire(fis: InputStream) = ZIO.attempt(new ZipInputStream(fis))
    fileInputStream(file).flatMap(fis => ZIO.acquireRelease(acquire(fis))(release))
  }

  /**
   * Opens or creates a file, returning an output stream that may be used to write bytes to the file.
   * Truncates and overwrites an existing file, or create the file if it doesn't initially exist.
   * The resulting stream will be buffered.
   *
   * @param path The path to the file.
   * @return The managed output stream.
   */
  def fileBufferedOutputStream(path: zio.nio.file.Path): ZIO[Any & Scope, Throwable, OutputStream] =
    fileBufferedOutputStream(path.toFile.toPath)
  def fileBufferedOutputStream(path: Path): ZIO[Any & Scope, Throwable, OutputStream] = {
    def acquire = ZIO.attempt(Files.newOutputStream(path))
    ZIO.acquireRelease(acquire)(release).flatMap(os => bufferedOutputStream(os))
  }
}
