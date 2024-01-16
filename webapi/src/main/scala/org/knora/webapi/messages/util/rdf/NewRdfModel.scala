/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf

import org.apache.jena.rdf.model.Literal
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.Resource
import zio.*

import java.io.StringReader
import scala.jdk.CollectionConverters.*

import org.knora.webapi.messages.util.rdf.Errors.ConversionError
import org.knora.webapi.messages.util.rdf.Errors.LiteralNotPresent
import org.knora.webapi.messages.util.rdf.Errors.NotALiteral
import org.knora.webapi.messages.util.rdf.Errors.ObjectNotAResource
import org.knora.webapi.messages.util.rdf.Errors.ObjectNotPresent
import org.knora.webapi.messages.util.rdf.Errors.RdfError
import org.knora.webapi.messages.util.rdf.Errors.ResourceNotPresent
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

object Errors {
  sealed trait RdfError

  final case class ResourceNotPresent(key: String) extends RdfError
  final case class LiteralNotPresent(key: String)  extends RdfError
  final case class ObjectNotPresent(key: String)   extends RdfError
  final case class NotALiteral(key: String)        extends RdfError
  final case class ObjectNotAResource(key: String) extends RdfError
  final case class ConversionError(msg: String)    extends RdfError
}

final case class LangString(value: String, lang: Option[String])

final case class NewRdfResource(private val res: Resource) {

  private val model = res.getModel

  private def property(iri: String): UIO[Property] = ZIO.succeed(model.createProperty(iri))

  private def getObjectUri(propertyIri: String): IO[Option[ObjectNotAResource], String] =
    for {
      prop <- property(propertyIri)
      stmt <- ZIO.fromOption(Option(res.getProperty(prop)))
      obj  <- ZIO.attempt(stmt.getResource).orElseFail(Some(ObjectNotAResource(propertyIri)))
      uri  <- ZIO.fromOption(Option(obj.getURI))
    } yield uri

  private def getObjectUris(propertyIri: String): IO[ObjectNotAResource, Chunk[String]] =
    for {
      prop       <- property(propertyIri)
      stmtIter    = res.listProperties(prop)
      stmts       = Chunk.fromIterable(stmtIter.toList.asScala.toList)
      objects    <- ZIO.foreach(stmts)(stmt => ZIO.attempt(stmt.getResource).orElseFail(ObjectNotAResource(propertyIri)))
      uriOptions <- ZIO.foreach(objects)(obj => ZIO.fromOption(Option(obj.getURI)).unsome)
      uris        = uriOptions.flatten
    } yield uris

  private def getLiteral(propertyIri: String): IO[Option[NotALiteral], Literal] =
    for {
      prop <- property(propertyIri)
      stmt <- ZIO.fromOption(Option(res.getProperty(prop)))
      lit  <- ZIO.attempt(stmt.getLiteral).orElseFail(Some(NotALiteral(propertyIri)))
    } yield lit

  private def getLiterals(propertyIri: String): IO[NotALiteral, Chunk[Literal]] =
    for {
      prop     <- property(propertyIri)
      stmtIter  = res.listProperties(prop)
      stmts     = Chunk.fromIterable(stmtIter.toList.asScala.toList)
      literals <- ZIO.attempt(stmts.map(_.getLiteral)).mapBoth(_ => NotALiteral(propertyIri), Chunk.fromIterable)
    } yield literals

  private def toTypedLiteral[A](literal: Literal, f: Literal => A)(implicit
    tag: Tag[A]
  ): IO[ConversionError, A] =
    ZIO
      .attempt(f(literal))
      .orElseFail(ConversionError(s"$literal is not an ${tag.getClass.getSimpleName}"))

  private def literalAsDomainObject[A, B](
    propertyIri: String,
    f: Literal => A,
    mapper: A => Either[String, B]
  )(implicit tag: Tag[A]): IO[Option[RdfError], B] =
    for {
      literal      <- getLiteral(propertyIri)
      typedLiteral <- toTypedLiteral(literal, f).mapError(Some(_))
      domainObject <- ZIO.fromEither(mapper(typedLiteral)).mapError(err => Some(ConversionError(err)))
    } yield domainObject

  private def literalsAsDomainObjects[A, B](
    propertyIri: String,
    f: Literal => A,
    mapper: A => Either[String, B]
  )(implicit tag: Tag[A]): IO[RdfError, Chunk[B]] =
    for {
      literals      <- getLiterals(propertyIri)
      typedLiterals <- ZIO.foreach(literals)(toTypedLiteral(_, f))
      domainObjects <- ZIO.foreach(typedLiterals)(a => ZIO.fromEither(mapper(a)).mapError(ConversionError))
    } yield Chunk.fromIterable(domainObjects)

  def getStringLiteral[A](propertyIri: String)(implicit mapper: String => Either[String, A]): IO[RdfError, Option[A]] =
    literalAsDomainObject(propertyIri, _.getString, mapper).unsome

  def getStringLiteralOrFail[A](propertyIri: String)(implicit mapper: String => Either[String, A]): IO[RdfError, A] =
    getStringLiteral(propertyIri).someOrFail(LiteralNotPresent(propertyIri))

  def getStringLiterals[A](
    propertyIri: String
  )(implicit mapper: String => Either[String, A], ordering: Ordering[A]): IO[RdfError, Chunk[A]] =
    literalsAsDomainObjects(propertyIri, _.getString, mapper).map(_.sorted)

  def getStringLiteralsOrFail[A](
    propertyIri: String
  )(implicit mapper: String => Either[String, A], ordering: Ordering[A]): IO[RdfError, NonEmptyChunk[A]] =
    for {
      chunk         <- getStringLiterals(propertyIri)
      nonEmptyChunk <- ZIO.fromOption(NonEmptyChunk.fromChunk(chunk)).orElseFail(LiteralNotPresent(propertyIri))
    } yield nonEmptyChunk

  def getLangStringLiteral[A](
    propertyIri: String
  )(implicit mapper: LangString => Either[String, A]): IO[RdfError, Option[A]] =
    literalAsDomainObject(
      propertyIri,
      stmt => LangString(stmt.getString, Option(stmt.getLanguage)),
      mapper
    ).unsome

  def getLangStringLiteralOrFail[A](
    propertyIri: String
  )(implicit mapper: LangString => Either[String, A]): IO[RdfError, A] =
    getLangStringLiteral(propertyIri).someOrFail(LiteralNotPresent(propertyIri))

  def getLangStringLiterals[A](
    propertyIri: String
  )(implicit mapper: LangString => Either[String, A]): IO[RdfError, Chunk[A]] =
    literalsAsDomainObjects(
      propertyIri,
      stmt => LangString(stmt.getString, Option(stmt.getLanguage)),
      mapper
    )

  def getLangStringLiteralsOrFail[A](
    propertyIri: String
  )(implicit mapper: LangString => Either[String, A]): IO[RdfError, NonEmptyChunk[A]] =
    for {
      chunk         <- getLangStringLiterals(propertyIri)
      nonEmptyChunk <- ZIO.fromOption(NonEmptyChunk.fromChunk(chunk)).orElseFail(LiteralNotPresent(propertyIri))
    } yield nonEmptyChunk

  def getBooleanLiteral[A](propertyIri: String)(implicit
    mapper: Boolean => Either[String, A]
  ): IO[RdfError, Option[A]] =
    literalAsDomainObject(propertyIri, _.getBoolean, mapper).unsome

  def getBooleanLiteralOrFail[A](propertyIri: String)(implicit
    mapper: Boolean => Either[String, A]
  ): IO[RdfError, A] =
    getBooleanLiteral(propertyIri).someOrFail(LiteralNotPresent(propertyIri))

  def getBooleanLiterals[A](propertyIri: String)(implicit
    mapper: Boolean => Either[String, A]
  ): IO[RdfError, Chunk[A]] =
    literalsAsDomainObjects(propertyIri, _.getBoolean, mapper)

  def getBooleanLiteralsOrFail[A](propertyIri: String)(implicit
    mapper: Boolean => Either[String, A]
  ): IO[RdfError, NonEmptyChunk[A]] =
    for {
      chunk         <- getBooleanLiterals(propertyIri)
      nonEmptyChunk <- ZIO.fromOption(NonEmptyChunk.fromChunk(chunk)).orElseFail(LiteralNotPresent(propertyIri))
    } yield nonEmptyChunk

  def getObjectIri(propertyIri: String): IO[RdfError, Option[InternalIri]] =
    for {
      prop <- property(propertyIri)
      iri  <- getObjectUri(propertyIri).map(InternalIri).unsome
    } yield iri

  def getObjectIriOrFail(propertyIri: String): IO[RdfError, InternalIri] =
    getObjectIri(propertyIri).someOrFail(ObjectNotPresent(propertyIri))

  def getObjectIris(propertyIri: String): IO[RdfError, Chunk[InternalIri]] =
    for {
      prop <- property(propertyIri)
      iris <- getObjectUris(propertyIri).map(_.map(InternalIri))
    } yield iris

  def getObjectIrisOrFail(propertyIri: String): IO[RdfError, NonEmptyChunk[InternalIri]] =
    for {
      chunk         <- getObjectIris(propertyIri)
      nonEmptyChunk <- ZIO.fromOption(NonEmptyChunk.fromChunk(chunk)).orElseFail(ObjectNotPresent(propertyIri))
    } yield nonEmptyChunk

}

final case class NewRdfModel private (private val model: Model) {
  def getResource(subjectIri: String): IO[RdfError, NewRdfResource] = ZIO.attempt {
    val resource = model.createResource(subjectIri)
    NewRdfResource(resource)
    // TODO: this will probably not fail even if the resource is not present (should check if it is empty)
  }.orElseFail(ResourceNotPresent(subjectIri))

}
object NewRdfModel {
  def fromTurtle(turtle: String): Task[NewRdfModel] = ZIO.attempt {
    val model = ModelFactory.createDefaultModel()
    model.read(new StringReader(turtle), null, "TURTLE")
    NewRdfModel(model)
  }
}
