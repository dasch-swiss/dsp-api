/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import org.apache.jena.rdf.model.*
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.vocabulary.RDF
import zio.*

import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.util.Try

import dsp.valueobjects.UuidUtil
import dsp.valueobjects.UuidUtil.base64Decode
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.CreationDate
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.ValueCreationDate
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.ValueHasUUID
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.ModelError.IsNoResourceIri
import org.knora.webapi.slice.common.ModelError.MoreThanOneRootResource
import org.knora.webapi.slice.common.ModelError.ParseError
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

enum ModelError(val msg: String) {
  case ParseError(override val msg: String)                           extends ModelError(msg)
  case IsNoResourceIri(override val msg: String, iri: String)         extends ModelError(msg)
  case IsNoDataIri(override val msg: String, iri: String)             extends ModelError(msg)
  case InvalidResourceClassIri(override val msg: String, iri: String) extends ModelError(msg)
  case MoreThanOneRootResource(override val msg: String)              extends ModelError(msg)
  case NoRootResource(override val msg: String)                       extends ModelError(msg)
  case ShortCodeMissing(override val msg: String)                     extends ModelError(msg)
  case MissingValueProp(override val msg: String)                     extends ModelError(msg)
  case MultipleValueProp(override val msg: String)                    extends ModelError(msg)
}
object ModelError {
  def parseError(ex: Throwable): ParseError = ParseError(ex.getMessage)
  def noResourceIri(iri: SmartIri): IsNoResourceIri =
    IsNoResourceIri(s"Invalid Knora resource IRI: $iri", iri.toOntologySchema(ApiV2Complex).toIri)
  def noDataIri(iri: SmartIri): IsNoDataIri =
    IsNoDataIri(s"Invalid Knora data IRI: $iri", iri.toOntologySchema(ApiV2Complex).toIri)
  def moreThanOneRootResource: MoreThanOneRootResource = MoreThanOneRootResource("More than one root resource found")
  def noRootResource: NoRootResource                   = NoRootResource("No root resource found")
  def invalidResourceClassIri(iri: SmartIri): InvalidResourceClassIri =
    InvalidResourceClassIri("Invalid resource class IRI", iri.toIri)
  def shortCodeMissing: ShortCodeMissing   = ShortCodeMissing("Project shortcode is missing in resource IRI")
  def missingValueProp: MissingValueProp   = MissingValueProp("No value property found in root resource")
  def multipleValueProp: MultipleValueProp = MultipleValueProp("Multiple value properties found in root resource")
}

/*
 * The KnoraApiModel represents any incoming value models from our v2 API.
 */
final case class KnoraApiValueModel(
  model: Model,
  rootResourceIri: SmartIri,
  rootResource: Resource,
  shortcode: Shortcode,
  valueNode: KnoraApiValueNode,
  private val convert: IriConverter,
) { self =>
  import ResourceOps.*
  import StatementOps.*

  def rootResourceClassIri: IO[Option[ModelError], SmartIri] = ZIO
    .fromOption(rootResource.rdfsType())
    .flatMap(convert.asSmartIri(_).mapError(ModelError.parseError).asSomeError)
    .filterOrElseWith(iri => iri.isKnoraEntityIri && iri.isApiV2ComplexSchema)(iri =>
      ZIO.fail(ModelError.invalidResourceClassIri(iri)).asSomeError,
    )
}

object KnoraApiValueModel { self =>
  import StatementOps.*

  // available for ease of use in tests
  def fromJsonLd(str: String): ZIO[Scope & IriConverter, ModelError, KnoraApiValueModel] =
    ZIO.service[IriConverter].flatMap(self.fromJsonLd(str, _))

  def fromJsonLd(str: String, converter: IriConverter): ZIO[Scope & IriConverter, ModelError, KnoraApiValueModel] =
    for {
      model           <- ModelOps.fromJsonLd(str)
      rootResourceIri <- rootResourceIri(model, converter)
      rootResource     = model.getResource(rootResourceIri.toString)
      shortcode       <- ZIO.fromEither(rootResourceIri.getProjectShortcode).orElseFail(ModelError.shortCodeMissing)
      valueProp       <- valueNode(rootResource, shortcode, converter)
    } yield KnoraApiValueModel(model, rootResourceIri, rootResource, shortcode, valueProp, converter)

  private def rootResourceIri(model: Model, convert: IriConverter): IO[ModelError, SmartIri] =
    val iter    = model.listStatements()
    var objSeen = Set.empty[String]
    var subSeen = Set.empty[String]
    while (iter.hasNext) {
      val stmt = iter.nextStatement()
      val _    = stmt.objectUri().foreach(iri => objSeen += iri)
      val _    = stmt.subjectUri().foreach(iri => subSeen += iri)
    }
    val result: IO[ModelError, SmartIri] = subSeen -- objSeen match {
      case result if result.size == 1 =>
        convert
          .asSmartIri(result.head)
          .mapError(ModelError.parseError)
          .filterOrElseWith(_.isKnoraResourceIri)(iri => ZIO.fail(ModelError.noResourceIri(iri)))
      case result if result.isEmpty => ZIO.fail(ModelError.noRootResource)
      case _                        => ZIO.fail(ModelError.moreThanOneRootResource)
    }
    result

  private def valueNode(
    rootResource: Resource,
    shortcode: Shortcode,
    converter: IriConverter,
  ): IO[ModelError, KnoraApiValueNode] =
    ZIO.succeed {
      rootResource
        .listProperties()
        .asScala
        .filter(_.getPredicate != RDF.`type`)
        .collect(s => KnoraApiValueNode(s.getObject, s.getPredicate, shortcode, converter))
        .toList
    }
      .filterOrFail(_.nonEmpty)(ModelError.missingValueProp)
      .filterOrFail(_.size == 1)(ModelError.multipleValueProp)
      .map(_.head)
}

final case class KnoraApiValueNode(node: RDFNode, belongsTo: Property, shortcode: Shortcode, convert: IriConverter) {
  import NodeOps.*
  import ResourceOps.*
  def getStringLiteral(property: String): Option[String]   = node.getStringLiteral(property)
  def getStringLiteral(property: Property): Option[String] = node.getStringLiteral(property)
  def getNodeSubject: IO[Option[ModelError], SmartIri] =
    ZIO
      .fromOption(node.toResourceOption.flatMap(_.uri))
      .flatMap(convert.asSmartIri(_).mapError(ModelError.parseError).asSomeError)
      .filterOrElseWith(_.isKnoraDataIri)(iri => ZIO.fail(ModelError.noDataIri(iri)).asSomeError)

  def getValueHasUuid: Either[String, Option[UUID]] =
    getStringLiteral(ValueHasUUID)
      .map(str => base64Decode(str).map(Some(_)).toEither.left.map(e => s"Invalid UUID '$str': ${e.getMessage}"))
      .fold(Right(None))(identity)

  def getValueCreationDate: Either[String, Option[Instant]] =
    node.getDateTimeProperty(ResourceFactory.createProperty(ValueCreationDate))

  def getHasPermissions: Option[String] =
    node.getStringLiteral(ResourceFactory.createProperty(OntologyConstants.KnoraApiV2Complex.HasPermissions))
}

object KnoraApiValueNode {}

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
