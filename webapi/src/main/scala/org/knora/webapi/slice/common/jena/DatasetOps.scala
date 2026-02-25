/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.jena

import org.apache.jena.query.Dataset
import org.apache.jena.query.DatasetFactory
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import zio.*
import zio.nio.file.Path

object DatasetOps { self =>

  extension (ds: Dataset) {

    def printTrig: UIO[Unit] =
      Console.printLine("/// DS TRIG START ///").ignore *>
        as(Lang.TRIG).flatMap(Console.printLine(_)).logError.ignore *>
        Console.printLine("/// DS TRIG END ///").ignore

    def asTriG: Task[String] = as(Lang.TRIG)

    def as(lang: Lang): Task[String] = ZIO.attempt {
      val out = new java.io.ByteArrayOutputStream()
      RDFDataMgr.write(out, ds, lang)
      out.toString(java.nio.charset.StandardCharsets.UTF_8)
    }

    def defaultModel: Model = ds.getDefaultModel

    def namedModel(uri: Resource): Option[Model] = Option(ds.getNamedModel(uri))
  }

  private val createDataset =
    ZIO.acquireRelease(ZIO.succeed(DatasetFactory.create()))(ds => ZIO.succeed(ds.close()))

  def fromJsonLd(jsonLd: String): ZIO[Scope, String, Dataset] = from(jsonLd, Lang.JSONLD)

  def from(str: String, lang: Lang): ZIO[Scope, String, Dataset] =
    createDataset.flatMap(RdfDataMgr.read(_, str, lang)).mapError(_.getMessage)

  def from(paths: Chunk[Path], lang: Lang): ZIO[Scope, Throwable, Dataset] =
    createDataset.flatMap(RdfDataMgr.read(_, paths, lang))
}
