/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.jena

import scala.jdk.CollectionConverters.*

import org.apache.jena.rdf.model.*
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import zio.Scope
import zio.UIO
import zio.ZIO

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.knora.webapi.slice.common.ModelError
import org.knora.webapi.slice.common.ModelError.ParseError

object ModelOps { self =>

  extension (model: Model) {
    def printTurtle: UIO[Unit] =
      ZIO.attempt(RDFDataMgr.write(java.lang.System.out, model, Lang.TURTLE)).logError.ignore

    def resourceOption(uri: String): Option[Resource] = Option(model.getResource(uri))
    def resource(uri: String): Either[String, Resource] =
      model.resourceOption(uri).toRight(s"Resource not found '$uri'")

    def statementOption(s: Resource, p: Property): Option[Statement] = Option(model.getProperty(s, p))
    def statement(s: Resource, p: Property): Either[String, Statement] =
      statementOption(s, p).toRight(s"Statement not found '${s.getURI} ${p.getURI} ?o .'")

    def singleRootResource: Either[String, Resource] =
      val objSeen = model.listObjects().asScala.collect { case r: Resource => Option(r.getURI) }.toSet.flatten
      val subSeen = model.listSubjects().asScala.collect { case r: Resource => Option(r.getURI) }.toSet.flatten
      (subSeen -- objSeen) match {
        case iris if iris.size == 1 => model.resource(iris.head)
        case iris if iris.isEmpty   => Left("No root resource found in model")
        case iris                   => Left(s"Multiple root resources found in model: ${iris.mkString(", ")}")
      }
  }

  def fromJsonLd(str: String): ZIO[Scope, ParseError, Model] = from(str, Lang.JSONLD)
  def fromTurtle(str: String): ZIO[Scope, ParseError, Model] = from(str, Lang.TURTLE)

  private val createModel =
    ZIO.acquireRelease(ZIO.succeed(ModelFactory.createDefaultModel()))(m => ZIO.succeed(m.close()))

  def from(str: String, lang: Lang): ZIO[Scope, ParseError, Model] =
    for {
      m <- createModel
      _ <- ZIO
             .attempt(RDFDataMgr.read(m, ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8)), lang))
             .mapError(ModelError.parseError)
    } yield m
}
