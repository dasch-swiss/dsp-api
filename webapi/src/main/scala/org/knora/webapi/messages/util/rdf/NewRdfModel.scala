package org.knora.webapi.messages.util.rdf

import dsp.valueobjects.V2
import org.apache.jena.rdf.model.{Literal, Model, Resource}
import org.knora.webapi.messages.util.rdf.Errors.{ConversionError, LiteralNotPresent, NotALiteral, RdfError}
import zio.*

import scala.jdk.CollectionConverters.*

object Errors {
  sealed trait RdfError

  case class LiteralNotPresent(key: String) extends RdfError
  case class NotALiteral(key: String)       extends RdfError
  case class ConversionError(msg: String)   extends RdfError
}

final case class NewRdfResource(private val res: Resource, private val model: Model) {

  def getStringLiteralByProperty(propertyIri: String): Task[String] = ZIO.attempt {
    val property = model.createProperty(propertyIri)
    res.getProperty(property).getLiteral.getString
  }

  private def getLiteral(propertyIri: String): IO[Option[NotALiteral], Literal] =
    ZIO
      .succeed(model.createProperty(propertyIri))
      .flatMap(prop => ZIO.fromOption(Option(res.getProperty(prop))))
      .flatMap(stmt => ZIO.attempt(stmt.getLiteral).orElseFail(NotALiteral(s"$propertyIri")).mapError(Some(_)))

  private def getTypedLiteral[A](literal: Literal, f: Literal => A)(implicit
    tag: Tag[A]
  ): IO[ConversionError, A] =
    ZIO
      .attempt(f(literal))
      .orElseFail(ConversionError(s"$literal is not an ${tag.getClass.getSimpleName}"))

  private def getTypedLiteral[A, B](
    propertyIri: String,
    f: Literal => A,
    mapper: A => Either[String, B]
  )(implicit tag: Tag[A]): IO[RdfError, Option[B]] =
    getLiteral(propertyIri)
      .flatMap(lit => getTypedLiteral(lit, f).mapError(Some(_)))
      .flatMap(a => ZIO.fromEither(mapper(a)).mapError(ConversionError.apply).mapError(Some(_)))
      .unsome

  def getStringLiteralPropertySingleOption[A](
    propertyIri: String
  )(implicit mapper: String => Either[String, A]): IO[RdfError, Option[A]] =
    getTypedLiteral(propertyIri, _.getString, mapper)

  def getStringLiteralPropertyOrFail[A](propertyIri: String)(implicit
    mapper: String => Either[String, A]
  ): IO[RdfError, A] = getStringLiteralPropertySingleOption(propertyIri).someOrFail(LiteralNotPresent(propertyIri))

  def getBooleanLiteralPropertySingleOption[A](propertyIri: String)(implicit
    mapper: Boolean => Either[String, A]
  ): IO[RdfError, Option[A]] =
    getTypedLiteral(propertyIri, _.getBoolean, mapper)

  def getBooleanLiteralPropertyOrFail[A](propertyIri: String)(implicit
    mapper: Boolean => Either[String, A]
  ): IO[RdfError, A] = getBooleanLiteralPropertySingleOption(propertyIri).someOrFail(LiteralNotPresent(propertyIri))

  def getStringLiteralsByProperty(propertyIri: String): Task[List[String]] = ZIO.attempt {
    val property = model.createProperty(propertyIri)
    res.listProperties(property).toList.asScala.toList.map(_.getLiteral.getString)
  }

  def getLangStringLiteralsByProperty(propertyIri: String): Task[List[V2.StringLiteralV2]] = ZIO.attempt {
    val property = model.createProperty(propertyIri)
    res.listProperties(property).toList.asScala.toList.map { stmt =>
      val lang       = stmt.getLiteral.getLanguage
      val langOption = Option.when(lang.nonEmpty)(lang)
      val value      = stmt.getLiteral.getString
      V2.StringLiteralV2(value, langOption)
    }
  }

  def getBooleanLiteralByProperty(propertyIri: String): Task[Boolean] = ZIO.attempt {
    val property = model.createProperty(propertyIri)
    res.getProperty(property).getLiteral.getBoolean
  }

  def getObjectIriByProperty(propertyIri: String): Task[String] = ZIO.attempt {
    val property = model.createProperty(propertyIri)
    res.getProperty(property).getResource.getURI
  }

  def getObjectIrisByProperty(propertyIri: String): Task[List[String]] = ZIO.attempt {
    val property = model.createProperty(propertyIri)
    res.listProperties(property).toList.asScala.toList.map(_.getResource.getURI)
  }

}

final case class NewRdfModel(private val model: Model) {
  def getResource(subjectIri: String): Task[NewRdfResource] = ZIO.attempt {
    val resource = model.getResource(subjectIri)
    NewRdfResource(resource, model)
  }
}
