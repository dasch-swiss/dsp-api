/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.repo.rdf

import org.apache.jena.rdf.model.*
import org.apache.jena.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.XSD
import zio.*

import java.io.StringReader
import scala.jdk.CollectionConverters.*

import org.knora.webapi.slice.common.repo.rdf.Errors.*
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

object Errors {
  sealed trait RdfError

  final case class ResourceNotPresent(key: String) extends RdfError
  final case class LiteralNotPresent(key: String)  extends RdfError
  final case class ObjectNotPresent(key: String)   extends RdfError
  final case class NotALiteral(key: String)        extends RdfError
  final case class ObjectNotAResource(key: String) extends RdfError
  final case class ConversionError(msg: String)    extends RdfError
  final case class RdfParsingError(msg: String)    extends RdfError
}

final case class LangString(value: String, lang: Option[String])

/**
 * A wrapper around Jena's [[Resource]].
 * Exposes access to the resource's properties.
 * Should be created via [[RdfModel.getResourceOrFail]].
 */
final case class RdfResource(val res: Resource) {

  private val model = res.getModel

  def iri: UIO[InternalIri] = ZIO.succeed(InternalIri(res.getURI))

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
      stmts     = Chunk.fromIterator(stmtIter.asScala)
      literals <- ZIO.attempt(stmts.map(_.getLiteral)).mapBoth(_ => NotALiteral(propertyIri), Chunk.fromIterable)
    } yield literals

  /**
   * Returns the string value for a string literal or fails if the literal is not a string.
   * May be resolved by https://github.com/apache/jena/issues/2248 in the future.
   */
  private def stringFromLiteral(literal: Literal) =
    literal.getDatatypeURI match {
      case "http://www.w3.org/2001/XMLSchema#string" | "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString" =>
        ZIO.succeed(literal.getString)
      case _ => ZIO.fail(ConversionError(s"$literal is not a String"))
    }

  /**
   * See [[stringFromLiteral]].
   */
  private def langStringFromLiteral(literal: Literal) =
    for {
      string <- stringFromLiteral(literal)
      lang    = Option.when(literal.getLanguage.nonEmpty)(literal.getLanguage)
    } yield LangString(string, lang)

  private def booleanFromLiteral(literal: Literal): IO[ConversionError, Boolean] =
    ZIO.attempt(literal.getBoolean).orElseFail(ConversionError(s"$literal is not a Boolean"))

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
    for {
      literal      <- getLiteral(propertyIri).unsome
      string       <- ZIO.foreach(literal)(stringFromLiteral)
      domainObject <- ZIO.foreach(string)(str => ZIO.fromEither(mapper(str)).mapError(ConversionError.apply))
    } yield domainObject

  def getUriLiteral[A](propertyIri: String)(implicit mapper: String => Either[String, A]): IO[RdfError, Option[A]] =
    import org.knora.webapi.slice.common.jena.ResourceOps.*
    import org.knora.webapi.slice.common.jena.JenaConversions.given
    import scala.language.implicitConversions
    for {
      prop         <- ZIO.fromEither(res.objectDataTypeOption(propertyIri, XSD.ANYURI.toString)).mapError(ConversionError.apply)
      domainObject <- ZIO.foreach(prop)(str => ZIO.fromEither(mapper(str)).mapError(ConversionError.apply))
    } yield domainObject

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
    propertyIri: String,
  )(implicit mapper: String => Either[String, A]): IO[RdfError, Chunk[A]] =
    for {
      literals      <- getLiterals(propertyIri)
      strings       <- ZIO.foreach(literals)(stringFromLiteral)
      domainObjects <- ZIO.foreach(strings)(str => ZIO.fromEither(mapper(str)).mapError(ConversionError.apply))
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
    propertyIri: String,
  )(implicit mapper: String => Either[String, A]): IO[RdfError, NonEmptyChunk[A]] =
    for {
      chunk         <- getStringLiterals(propertyIri)
      nonEmptyChunk <- ZIO.fromOption(NonEmptyChunk.fromChunk(chunk)).orElseFail(LiteralNotPresent(propertyIri))
    } yield nonEmptyChunk

  /**
   * Returns the value of a literal with a given predicate IRI as a domain object of type `A`,
   * provided an implicit function `LangString => Either[String, A]`
   * to convert the lang string literal to the domain object.
   * Returns None if the literal is not present.
   *
   * @param propertyIri the IRI of the predicate.
   * @param mapper      the implicit function to convert the lang string literal to the domain object.
   * @tparam A          the type of the domain object.
   * @return            the domain object or None if the literal is not present;
   *                    an [[RdfError]] if the property does not contain a lang string literal or if the conversion fails.
   */
  def getLangStringLiteral[A](
    propertyIri: String,
  )(implicit mapper: LangString => Either[String, A]): IO[RdfError, Option[A]] =
    for {
      literal      <- getLiteral(propertyIri).unsome
      langString   <- ZIO.foreach(literal)(langStringFromLiteral)
      domainObject <- ZIO.foreach(langString)(it => ZIO.fromEither(mapper(it)).mapError(ConversionError.apply))
    } yield domainObject

  /**
   * Returns the value of a literal with a given predicate IRI as a domain object of type `A`,
   * provided an implicit function `LangString => Either[String, A]`
   * to convert the lang string literal to the domain object.
   * Fails if the literal is not present.
   *
   * @param propertyIri the IRI of the predicate.
   * @param mapper      the implicit function to convert the lang string literal to the domain object.
   * @tparam A          the type of the domain object.
   * @return            the domain object or an [[RdfError]] if the literal is not present or if the conversion fails.
   */
  def getLangStringLiteralOrFail[A](
    propertyIri: String,
  )(implicit mapper: LangString => Either[String, A]): IO[RdfError, A] =
    getLangStringLiteral(propertyIri).someOrFail(LiteralNotPresent(propertyIri))

  /**
   * Returns the values of literals with a given predicate IRI as domain objects of type `A`,
   * provided an implicit function `LangString => Either[String, A]`
   * to convert the lang string literals to the domain objects.
   * Returns an empty chunk if no literals are present.
   *
   * @param propertyIri the IRI of the predicate.
   * @param mapper      the implicit function to convert the lang string literals to the domain objects.
   * @tparam A          the type of the domain objects.
   * @return            the domain objects or an [[RdfError]] if the conversion fails.
   */
  def getLangStringLiterals[A](
    propertyIri: String,
  )(implicit mapper: LangString => Either[String, A]): IO[RdfError, Chunk[A]] =
    for {
      literals      <- getLiterals(propertyIri)
      langStrings   <- ZIO.foreach(literals)(langStringFromLiteral)
      domainObjects <- ZIO.foreach(langStrings)(it => ZIO.fromEither(mapper(it)).mapError(ConversionError.apply))
    } yield Chunk.fromIterable(domainObjects)

  /**
   * Returns the values of literals with a given predicate IRI as domain objects of type `A`,
   * provided an implicit function `LangString => Either[String, A]`
   * to convert the lang string literals to the domain objects.
   * Fails if no literals are present.
   *
   * @param propertyIri the IRI of the predicate.
   * @param mapper      the implicit function to convert the lang string literals to the domain objects.
   * @tparam A          the type of the domain objects.
   * @return            the domain objects or an [[RdfError]] if no literals are present or if the conversion fails.
   */
  def getLangStringLiteralsOrFail[A](
    propertyIri: String,
  )(implicit mapper: LangString => Either[String, A]): IO[RdfError, NonEmptyChunk[A]] =
    for {
      chunk         <- getLangStringLiterals(propertyIri)
      nonEmptyChunk <- ZIO.fromOption(NonEmptyChunk.fromChunk(chunk)).orElseFail(LiteralNotPresent(propertyIri))
    } yield nonEmptyChunk

  /**
   * Returns the value of a literal with a given predicate IRI as a domain object of type `A`,
   * provided an implicit function `String => Either[String, A]` to convert the string literal to the domain object.
   * Returns None if the literal is not present.
   *
   * @param propertyIri the IRI of the predicate.
   * @param mapper      the implicit function to convert the string literal to the domain object.
   * @tparam A          the type of the domain object.
   * @return            the domain object or None if the literal is not present;
   *                    an [[RdfError]] if the property does not contain a string literal or if the conversion fails.
   */
  def getBooleanLiteral[A](propertyIri: String)(implicit
    mapper: Boolean => Either[String, A],
  ): IO[RdfError, Option[A]] =
    for {
      literal      <- getLiteral(propertyIri).unsome
      boolean      <- ZIO.foreach(literal)(booleanFromLiteral)
      domainObject <- ZIO.foreach(boolean)(it => ZIO.fromEither(mapper(it))).mapError(ConversionError.apply)
    } yield domainObject

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
  def getBooleanLiteralOrFail[A](propertyIri: String)(implicit
    mapper: Boolean => Either[String, A],
  ): IO[RdfError, A] =
    getBooleanLiteral(propertyIri).someOrFail(LiteralNotPresent(propertyIri))

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
  def getBooleanLiterals[A](propertyIri: String)(implicit
    mapper: Boolean => Either[String, A],
  ): IO[RdfError, Chunk[A]] =
    for {
      literals      <- getLiterals(propertyIri)
      booleans      <- ZIO.foreach(literals)(booleanFromLiteral)
      domainObjects <- ZIO.foreach(booleans)(it => ZIO.fromEither(mapper(it)).mapError(ConversionError.apply))
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
  def getBooleanLiteralsOrFail[A](propertyIri: String)(implicit
    mapper: Boolean => Either[String, A],
  ): IO[RdfError, NonEmptyChunk[A]] =
    for {
      chunk         <- getBooleanLiterals(propertyIri)
      nonEmptyChunk <- ZIO.fromOption(NonEmptyChunk.fromChunk(chunk)).orElseFail(LiteralNotPresent(propertyIri))
    } yield nonEmptyChunk

  /**
   * Returns the IRI of the object of a given predicate IRI.
   *
   * @param propertyIri the IRI of the predicate.
   * @return            the [[InternalIri]] of the object or None if the object is not present.
   */
  def getObjectIri(propertyIri: String): IO[RdfError, Option[InternalIri]] =
    getObjectUri(propertyIri).map(InternalIri.apply).unsome

  /**
   * Returns the IRI of the object of a given predicate IRI.
   * Fails if the object is not present.
   *
   * @param propertyIri the IRI of the predicate.
   * @return            the [[InternalIri]] of the object or an [[RdfError]] if the object is not present.
   */
  def getObjectIriOrFail(propertyIri: String): IO[RdfError, InternalIri] =
    getObjectIri(propertyIri).someOrFail(ObjectNotPresent(propertyIri))

  /**
   * Returns the IRIs of the objects of a given predicate IRI.
   *
   * @param propertyIri the IRI of the predicate.
   * @return            the [[InternalIri]]s of the objects or an [[RdfError]] if the objects are not present.
   */
  def getObjectIris(propertyIri: String): IO[RdfError, Chunk[InternalIri]] =
    getObjectUris(propertyIri).map(_.map(InternalIri.apply))

  def getObjectIrisConvert[A](prop: String)(implicit map: String => Either[String, A]) =
    getObjectIris(prop).flatMap { iris =>
      ZIO.foreach(iris) { iri =>
        ZIO.fromEither(map(iri.value)).mapError(err => ConversionError(s"Unable to parse $iri: $err"))
      }
    }

  /**
   * Returns the IRIs of the objects of a given predicate IRI.
   * Fails if the objects are not present.
   *
   * @param propertyIri the IRI of the predicate.
   * @return            the [[InternalIri]]s of the objects or an [[RdfError]] if the objects are not present.
   */
  def getObjectIrisOrFail(propertyIri: String): IO[RdfError, NonEmptyChunk[InternalIri]] =
    for {
      chunk         <- getObjectIris(propertyIri)
      nonEmptyChunk <- ZIO.fromOption(NonEmptyChunk.fromChunk(chunk)).orElseFail(ObjectNotPresent(propertyIri))
    } yield nonEmptyChunk

  def getSubjectIri: UIO[InternalIri] = ZIO.succeed(InternalIri(res.getURI))
}

/**
 * Wrapper around Jena's [[Model]].
 * Exposes access to resources of the model's graph.
 */
final case class RdfModel private (private val model: Model) {

  /**
   * Returns a [[RdfResource]] for the given subject IRI.
   *
   * @param subjectIri the IRI of the resource.
   * @return           the [[RdfResource]] or None if the resource is not present.
   */
  def getResource(subjectIri: String): UIO[Option[RdfResource]] =
    for {
      resource   <- ZIO.attempt(model.createResource(subjectIri)).orDie
      rdfResource = Option.when(resource.listProperties().hasNext)(RdfResource(resource))
    } yield rdfResource

  /**
   * Returns a [[RdfResource]] for the given subject IRI.
   * Fails if no resource with the given IRI is present in the model.
   *
   * @param subjectIri the IRI of the resource.
   * @return the [[RdfResource]] or an [[RdfError]] if the resource is not present.
   */
  def getResourceOrFail(subjectIri: String): IO[RdfError, RdfResource] =
    getResource(subjectIri).someOrFail(ResourceNotPresent(subjectIri))

  /**
   * Returns a [[RdfResource]] for a given property IRI and value.
   *
   * @param propertyIri the IRI of the predicate.
   * @param value       the value of the object.
   * @return            the [[RdfResource]] or None if the resource is not present.
   */
  def getResourceByPropertyStringValue(propertyIri: String, value: String): UIO[Option[RdfResource]] =
    getResourceByPropertyStringValueOrFail(propertyIri, value).option

  /**
   * Returns a [[RdfResource]] for a given property IRI and value.
   * Fails if no resource with the given property IRI and value is present in the model.
   *
   * @param propertyIri the IRI of the predicate.
   * @param value       the value of the object.
   * @return            the [[RdfResource]] or an [[RdfError]] if the resource is not present.
   */
  def getResourceByPropertyStringValueOrFail(propertyIri: String, value: String): IO[RdfError, RdfResource] = {
    val iter = model.listStatements(null, model.createProperty(propertyIri), value)
    val iri  = Option.when(iter.hasNext)(iter.nextStatement().getSubject.getURI)
    for {
      iri <- ZIO.fromOption(iri).orElseFail(ResourceNotPresent(s"No resource with: $propertyIri -> $value"))
      res <- getResourceOrFail(iri)
    } yield res
  }

  def getResourcesRdfType(objectClass: String): IO[RdfError, Iterator[RdfResource]] = for {
    objClassProp  <- ZIO.attempt(model.createProperty(objectClass)).orDie
    resourcesJIter = model.listResourcesWithProperty(RDF.`type`, objClassProp)
  } yield resourcesJIter.asScala.map(RdfResource.apply)

  def getSubjectResources: UIO[Chunk[RdfResource]] =
    ZIO.succeed(Chunk.fromIterator(model.listSubjects().asScala).map(RdfResource.apply))
}

object RdfModel {

  /**
   * Creates an [[RdfModel]] from a turtle string.
   * @param turtle the turtle string.
   * @return the [[RdfModel]] or a throwable, if parsing the underlying Jena model failed.
   */
  def fromTurtle(turtle: String): IO[RdfParsingError, RdfModel] =
    ZIO.scoped {
      val model = ModelFactory.createDefaultModel()
      for {
        reader <- ZIO.acquireRelease(ZIO.succeed(new StringReader(turtle)))(it => ZIO.succeed(it.close()))
        _ <- ZIO
               .attempt(model.read(reader, null, "TURTLE"))
               .orElseFail(RdfParsingError("Failed to parse the turtle string"))
      } yield RdfModel(model)
    }
}
