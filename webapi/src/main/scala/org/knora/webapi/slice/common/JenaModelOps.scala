/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import com.apicatalog.jsonld.document.JsonDocument
import com.apicatalog.jsonld.JsonLd
import com.apicatalog.jsonld.JsonLdOptions
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat

import java.nio.charset.StandardCharsets
import org.knora.webapi.slice.common.JenaModelOps.Errors.ParseError
import zio.*
import zio.json
import zio.json.ast.Json

import java.io.ByteArrayInputStream
import java.io.StringWriter
import zio.json.DecoderOps
import zio.json.ast.JsonCursor.field
import zio.json.ast.JsonCursor.isObject

object JenaModelOps { self =>

  enum Errors {
    case ParseError(msg: String) extends Errors
  }
  object Errors {
    def parseError(ex: Throwable): ParseError = ParseError(ex.getMessage)
  }

  def fromJsonLd(str: String): ZIO[Scope, ParseError, Model] = from(str, Lang.JSONLD)

  private val createModel =
    ZIO.acquireRelease(ZIO.succeed(ModelFactory.createDefaultModel()))(m => ZIO.succeed(m.close()))

  def from(str: String, lang: Lang): ZIO[Scope, Errors.ParseError, Model] =
    for {
      m <- createModel
      _ <- ZIO
             .attempt(RDFDataMgr.read(m, ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8)), lang))
             .mapError(Errors.parseError)
    } yield m

  extension (model: Model) {
    def toJsonLd: UIO[String] = ZIO.scoped {
      for {
        w <- ZIO.fromAutoCloseable(ZIO.succeed(StringWriter()))
        _ <- ZIO.succeed(RDFDataMgr.write(w, model, RDFFormat.JSONLD))
      } yield w.toString
    }
  }
}
