/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.jena

import org.apache.jena.query.Dataset
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import zio.*
import zio.nio.file.Path

import java.io.ByteArrayInputStream
import java.io.InputStream

import org.knora.webapi.util.ZScopedJavaIoStreams.fileInputStream

object RdfDataMgr {

  def read(ds: Dataset, paths: Iterable[Path], lang: Lang): Task[Dataset] =
    ZIO.foreachDiscard(paths)(read(ds, _, lang)).as(ds)

  def read(ds: Dataset, path: Path, lang: Lang): Task[Dataset] =
    ZIO.scoped(fileInputStream(path).flatMap(read(ds, _, lang))).as(ds)

  def read(ds: Dataset, is: InputStream, lang: Lang): Task[Dataset] =
    ZIO.attempt(RDFDataMgr.read(ds, is, lang)).as(ds)

  def read(ds: Dataset, str: String, lang: Lang): Task[Dataset] =
    read(ds, new ByteArrayInputStream(str.getBytes(java.nio.charset.StandardCharsets.UTF_8)), lang)
}
