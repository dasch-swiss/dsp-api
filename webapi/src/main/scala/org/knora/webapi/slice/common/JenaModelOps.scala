/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import zio.IO
import zio.ZIO

import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

import org.knora.webapi.slice.common.JenaModelOps.Errors.ParseError

object JenaModelOps {

  enum Errors {
    case ParseError(msg: String) extends Errors
  }
  object Errors {
    def parseError(ex: IOException): ParseError = ParseError(ex.getMessage)
  }

  def fromJsonLd(str: String): IO[ParseError, Model] = from(str, Lang.JSONLD)

  def from(str: String, lang: Lang): IO[ParseError, Model] = ZIO.attemptBlockingIO {
    val model = ModelFactory.createDefaultModel()
    model.read(new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8)), lang.getName)
    model
  }.mapError(Errors.parseError)
}
