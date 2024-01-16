package org.knora.webapi.messages.util.rdf

import dsp.valueobjects.V2
import org.apache.jena.rdf.model.{Model, Resource}
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

  private def getStringLiteralByPropertyTypeSafe(key: String): IO[Option[NotALiteral], String] =
    ZIO
      .succeed(model.createProperty(key))
      .map(p => Option(res.getProperty(p)))
      .flatMap(p => ZIO.attempt(p.map(_.getLiteral)).orElseFail(NotALiteral(key)))
      .map(_.map(_.getString))
      .some

  def getStringLiteralByPropertyTypeSafeWithMapper[A](
    key: String
  )(implicit mapper: String => Either[String, A]): IO[RdfError, Option[A]] =
    getStringLiteralByPropertyTypeSafe(key)
      .flatMap(lit => ZIO.fromEither(mapper(lit)).mapError(msg => Some(ConversionError(msg))))
      .unsome

  def getStringLiteralByPropertyTypeSafeWithMapperOrFail[A](
    key: String
  )(implicit mapper: String => Either[String, A]): IO[RdfError, A] =
    getStringLiteralByPropertyTypeSafeWithMapper[A](key).flatMap {
      case None        => ZIO.fail(LiteralNotPresent(key))
      case Some(value) => ZIO.succeed(value)
    }

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
