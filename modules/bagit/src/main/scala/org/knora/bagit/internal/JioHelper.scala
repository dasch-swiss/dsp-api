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
    .fromAutoCloseable(ZIO.attemptBlocking(new ZipInputStream((new FileInputStream(p.toFile)))))
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
}
