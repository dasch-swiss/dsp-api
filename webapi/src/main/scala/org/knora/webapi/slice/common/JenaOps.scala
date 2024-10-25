/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import org.apache.jena.rdf.model.Literal
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.rdf.model.Statement
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.vocabulary.RDF
import zio.Console
import zio.Scope
import zio.UIO
import zio.ZIO

import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import scala.util.Try

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.slice.common.ModelError.ParseError

object NodeOps {
  extension (node: RDFNode) {
    def getStringLiteral(property: String): Option[String] =
      node.getStringLiteral(ResourceFactory.createProperty(property))
    def getStringLiteral(property: Property): Option[String] =
      node.getLiteral(property).flatMap(lit => Try(lit.getString).toOption)
    def getLiteral(property: Property): Option[Literal] =
      node.getObject(property).flatMap(obj => Try(obj.asLiteral()).toOption)
    def getObject(property: Property): Option[RDFNode] =
      node.getStatement(property).map(_.getObject)
    def getStatement(property: Property): Option[Statement] =
      node.toResourceOption.flatMap(r => Option(r.getProperty(property)))
    def toResourceOption: Option[Resource] = Try(node.asResource()).toOption
    def getDateTimeProperty(property: Property): Either[String, Option[Instant]] =
      node
        .getLiteral(property)
        .map { lit =>
          Right(lit)
            .filterOrElse(
              _.getDatatypeURI == OntologyConstants.Xsd.DateTimeStamp,
              s"Invalid data type (should be xsd:dateTimeStamp) for value: ${lit.getLexicalForm}",
            )
            .map(_.getLexicalForm)
            .flatMap(str =>
              ValuesValidator
                .xsdDateTimeStampToInstant(str)
                .toRight(s"Invalid xsd:dateTimeStamp value: $str")
                .map(Some(_)),
            )
        }
        .fold(Right(None))(identity)
  }
}

object ResourceOps {
  extension (res: Resource) {
    def property(p: Property): Option[Statement] = Option(res.getProperty(p))
    def rdfsType(): Option[String]               = Option(res.getPropertyResourceValue(RDF.`type`)).flatMap(_.uri)
    def uri: Option[String]                      = Option(res.getURI)
  }
}

object StatementOps {
  extension (stmt: Statement) {
    def subjectUri(): Option[String] = Option(stmt.getSubject.getURI)
    def objectUri(): Option[String]  = Try(stmt.getObject.asResource()).toOption.flatMap(r => Option(r.getURI))
  }
}

object ModelOps { self =>

  extension (model: Model) {
    def printTurtle: UIO[Unit] =
      ZIO.scoped {
        for {
          writer <- ZIO.fromAutoCloseable(ZIO.succeed(new StringWriter()))
          _      <- ZIO.attempt(RDFDataMgr.write(writer, model, Lang.TURTLE))
          _      <- Console.printLine(writer.toString)
        } yield ()
      }.logError.ignore
  }

  def fromJsonLd(str: String): ZIO[Scope, ParseError, Model] = from(str, Lang.JSONLD)

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
