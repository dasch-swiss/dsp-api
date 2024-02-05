/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.repo.rdf

import org.apache.jena.rdf.model.*
import zio.*

import java.io.StringReader
import scala.jdk.CollectionConverters.*

import org.knora.webapi.slice.common.repo.rdf.Errors.*
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

/*
 * TODO:
 *  - add scaladoc
 *  - write tests
 *    - for rdf model
 *    - for repository
 *  - simplify literal extraction
 */

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

/**
 * A wrapper around Jena's [[Resource]].
 * Exposes access to the resource's properties.
 * Should be created via [[RdfModel.getResource]].
 */
final case class RdfResource(private val res: Resource) {

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

  private def getStringWithValidation(literal: Literal) =
    if (literal.getDatatypeURI == "http://www.w3.org/2001/XMLSchema#string") literal.getString
    else throw new IllegalArgumentException("Not a string literal")

  /**
   * Returns the value of a literal with a given predicate IRI as a domain object of type `A`,
   * provided an implicit function `String => Either[String, A]` to convert the string literal to the domain object.
   *
   * @param propertyIri the IRI of the predicate.
   * @param mapper      the implicit function to convert the string literal to the domain object.
   * @tparam A          the type of the domain object.
   * @return            the domain object or None if the literal is not present;
   *                    an [[RdfError]] if the property does not contain a string literal or if the conversion fails.
   */
  def getStringLiteral[A](propertyIri: String)(implicit mapper: String => Either[String, A]): IO[RdfError, Option[A]] =
    literalAsDomainObject(propertyIri, getStringWithValidation, mapper).unsome

  /**
   * Returns the value of a literal with a given predicate IRI as a domain object of type `A`,
   * provided an implicit function `String => Either[String, A]` to convert the string literal to the domain object.
   * Fails if the literal is not present.
   *
   * @param propertyIri the IRI of the predicate.
   * @param mapper      the implicit function to convert the string literal to the domain object.
   * @tparam A          the type of the domain object.
   * @return            the domain object or an [[RdfError]] if the literal is not present or if the conversion fails.
   */
  def getStringLiteralOrFail[A](propertyIri: String)(implicit mapper: String => Either[String, A]): IO[RdfError, A] =
    getStringLiteral(propertyIri).someOrFail(LiteralNotPresent(propertyIri))

  /**
   * Returns the values of literals with a given predicate IRI as domain objects of type `A`,
   * provided an implicit function `String => Either[String, A]` to convert the string literals to the domain objects.
   * Returns an empty chunk if no literals are present.
   *
   * @param propertyIri the IRI of the predicate.
   * @param mapper      the implicit function to convert the string literals to the domain objects.
   * @tparam A          the type of the domain objects.
   * @return            the domain objects or an [[RdfError]] if the conversion fails.
   */
  def getStringLiterals[A](
    propertyIri: String
  )(implicit mapper: String => Either[String, A]): IO[RdfError, Chunk[A]] =
    for {
      literals <- getLiterals(propertyIri)
      strings <- ZIO.foreach(literals)(it =>
                   ZIO.attempt(getStringWithValidation(it)).orElseFail(ConversionError(s"$it is not a String"))
                 )
      domainObjects <- ZIO.foreach(strings)(str => ZIO.fromEither(mapper(str)).mapError(ConversionError))
    } yield Chunk.fromIterable(domainObjects)

  /**
   * Returns the values of literals with a given predicate IRI as domain objects of type `A`,
   * provided an implicit function `String => Either[String, A]` to convert the string literals to the domain objects.
   * Fails if no literals are present.
   *
   * @param propertyIri the IRI of the predicate.
   * @param mapper      the implicit function to convert the string literals to the domain objects.
   * @tparam A          the type of the domain objects.
   * @return            the domain objects or an [[RdfError]] if no literals are present or if the conversion fails.
   */
  def getStringLiteralsOrFail[A](
    propertyIri: String
  )(implicit mapper: String => Either[String, A]): IO[RdfError, NonEmptyChunk[A]] =
    for {
      chunk         <- getStringLiterals(propertyIri)
      nonEmptyChunk <- ZIO.fromOption(NonEmptyChunk.fromChunk(chunk)).orElseFail(LiteralNotPresent(propertyIri))
    } yield nonEmptyChunk

  def getLangStringLiteral[A](
    propertyIri: String
  )(implicit mapper: LangString => Either[String, A]): IO[RdfError, Option[A]] =
    literalAsDomainObject(
      propertyIri,
      stmt => LangString(stmt.getString, Option.when(stmt.getLanguage.nonEmpty)(stmt.getLanguage)),
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
      stmt => LangString(stmt.getString, Option.when(stmt.getLanguage.nonEmpty)(stmt.getLanguage)),
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
    getObjectUri(propertyIri).map(InternalIri).unsome

  def getObjectIriOrFail(propertyIri: String): IO[RdfError, InternalIri] =
    getObjectIri(propertyIri).someOrFail(ObjectNotPresent(propertyIri))

  def getObjectIris(propertyIri: String): IO[RdfError, Chunk[InternalIri]] =
    getObjectUris(propertyIri).map(_.map(InternalIri))

  def getObjectIrisOrFail(propertyIri: String): IO[RdfError, NonEmptyChunk[InternalIri]] =
    for {
      chunk         <- getObjectIris(propertyIri)
      nonEmptyChunk <- ZIO.fromOption(NonEmptyChunk.fromChunk(chunk)).orElseFail(ObjectNotPresent(propertyIri))
    } yield nonEmptyChunk

}

/**
 * Wrapper around Jena's [[Model]].
 * Exposes access to resources of the model's graph.
 */
final case class RdfModel private (private val model: Model) {

  /**
   * Returns a [[RdfResource]] for the given subject IRI.
   * Fails if no resource with the given IRI is present in the model.
   *
   * @param subjectIri the IRI of the resource.
   * @return the [[RdfResource]] or an [[RdfError]] if the resource is not present.
   */
  def getResource(subjectIri: String): IO[RdfError, RdfResource] =
    for {
      resource <- ZIO.attempt(model.createResource(subjectIri)).orDie
      _        <- ZIO.fail(ResourceNotPresent(subjectIri)).unless(resource.listProperties().hasNext)
    } yield RdfResource(resource)

}
object RdfModel {

  /**
   * Creates an [[RdfModel]] from a turtle string.
   * @param turtle the turtle string.
   * @return the [[RdfModel]] or a throwable, if parsing the underlying Jena model failed.
   */
  def fromTurtle(turtle: String): Task[RdfModel] = ZIO.attempt {
    val model = ModelFactory.createDefaultModel()
    model.read(new StringReader(turtle), null, "TURTLE")
    RdfModel(model)
  }
}
