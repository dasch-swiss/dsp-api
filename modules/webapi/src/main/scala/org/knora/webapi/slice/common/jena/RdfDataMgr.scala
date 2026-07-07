/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.jena

import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import zio.*
import zio.nio.file.Path

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path as JPath

import org.knora.webapi.util.ZScopedJavaIoStreams.fileBufferedOutputStream
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

  /** Loads `path` into a fresh [[Model]]. */
  def loadModel(path: JPath, lang: Lang): Task[Model] =
    read(ModelFactory.createDefaultModel(), path, lang)

  def read(model: Model, path: JPath, lang: Lang): Task[Model] =
    ZIO.scoped(fileInputStream(path).flatMap(read(model, _, lang))).as(model)

  def read(model: Model, is: InputStream, lang: Lang): Task[Model] =
    ZIO.attempt(RDFDataMgr.read(model, is, lang)).as(model)

  def read(model: Model, str: String, lang: Lang): Task[Model] =
    read(model, new ByteArrayInputStream(str.getBytes(java.nio.charset.StandardCharsets.UTF_8)), lang)

  def write(model: Model, path: JPath, lang: Lang): Task[Unit] =
    ZIO.scoped(fileBufferedOutputStream(path).flatMap(write(model, _, lang)))

  def write(model: Model, os: OutputStream, lang: Lang): Task[Unit] =
    ZIO.attempt(RDFDataMgr.write(os, model, lang))
}
