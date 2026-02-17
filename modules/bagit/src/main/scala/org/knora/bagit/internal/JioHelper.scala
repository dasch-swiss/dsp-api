/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

import zio.*
import zio.nio.file.Path

import java.io.BufferedInputStream
import java.io.File as JFile
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object JioHelper {

  def zipFileInputStream(p: Path): ZIO[Scope, IOException, ZipInputStream] = ZIO
    .fromAutoCloseable(ZIO.attemptBlocking(new ZipInputStream(new FileInputStream(p.toFile))))
    .refineToOrDie[IOException]

  def bufferedFileInputStream(f: JFile): ZIO[Scope, IOException, BufferedInputStream] = ZIO
    .fromAutoCloseable(ZIO.attemptBlocking(new BufferedInputStream(new FileInputStream(f))))
    .refineToOrDie[IOException]

  def fileInputStream(p: Path): ZIO[Scope, IOException, FileInputStream]  = fileInputStream(p.toFile)
  def fileInputStream(f: JFile): ZIO[Scope, IOException, FileInputStream] = ZIO
    .fromAutoCloseable(ZIO.attemptBlocking(new FileInputStream(f)))
    .refineToOrDie[IOException]

  def zipFileOutputStream(p: Path): ZIO[Scope, IOException, ZipOutputStream] = ZIO
    .fromAutoCloseable(ZIO.attemptBlocking(new ZipOutputStream(new FileOutputStream(p.toFile))))
    .refineToOrDie[IOException]

  def fileOutputStream(f: JFile): ZIO[Scope, IOException, FileOutputStream] = ZIO
    .fromAutoCloseable(ZIO.attemptBlocking(new FileOutputStream(f)))
    .refineToOrDie[IOException]

  /**
   * Recursively walks a directory and returns a list of all files (not directories) contained within it,
   * sorted alphabetically by path.
   *
   * @param dir the directory to walk
   * @return a list of all files contained within the directory and its subdirectories, sorted alphabetically by path.
   *         Nil if the provided `dir` is not a directory.
   */
  def walkDirectory(dir: JFile): List[JFile] =
    if (!dir.isDirectory) Nil
    else {
      // Tail-recursive, immutable traversal that preserves ordering semantics:
      // - Directories are visited in alphabetical order
      // - Files within a directory are appended in alphabetical order as they are encountered
      @annotation.tailrec
      def loop(stack: Vector[JFile], acc: List[JFile]): List[JFile] =
        if (stack.isEmpty) acc.reverse
        else {
          val current       = stack.last
          val rest          = stack.init
          val children      = Option(current.listFiles()).map(_.toList).getOrElse(Nil)
          val (dirs, files) = children.partition(_.isDirectory)

          // Append files (sorted) to the accumulator (we prepend here and reverse at the end)
          val newAcc = files.sortBy(_.getName).foldLeft(acc)((a, f) => f :: a)

          // Push directories so that the alphabetically first is processed next
          val dirsToPush = dirs.sortBy(_.getName).reverse

          loop(rest ++ dirsToPush, newAcc)
        }

      loop(Vector(dir), Nil)
    }
}
