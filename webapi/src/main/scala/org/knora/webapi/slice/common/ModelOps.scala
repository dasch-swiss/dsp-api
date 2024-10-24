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
import java.nio.charset.StandardCharsets
import scala.util.Try
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.ModelError.IsNoResourceIri
import org.knora.webapi.slice.common.ModelError.MoreThanOneRootResource
import org.knora.webapi.slice.common.ModelError.ParseError
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

enum ModelError(val msg: String) {
  case ParseError(override val msg: String)                           extends ModelError(msg)
  case IsNoResourceIri(override val msg: String, iri: String)         extends ModelError(msg)
  case InvalidResourceClassIri(override val msg: String, iri: String) extends ModelError(msg)
  case MoreThanOneRootResource(override val msg: String)              extends ModelError(msg)
  case NoRootResource(override val msg: String)                       extends ModelError(msg)
}
object ModelError {
  def parseError(ex: Throwable): ParseError = ParseError(ex.getMessage)
  def noResourceIri(iri: SmartIri): IsNoResourceIri =
    IsNoResourceIri(s"This is not a resource IRI $iri", iri.toOntologySchema(ApiV2Complex).toIri)
  def moreThanOneRootResource: MoreThanOneRootResource = MoreThanOneRootResource("More than one root resource found")
  def noRootResource: NoRootResource                   = NoRootResource("No root resource found")
  def invalidResourceClassIri(iri: SmartIri): InvalidResourceClassIri =
    InvalidResourceClassIri("Invalid resource class IRI", iri.toIri)
}

/*
 * The KnoraApiModel represents any incoming value models from our v2 API.
 */
final case class KnoraApiValueModel(model: Model, rootResourceIri: SmartIri, convert: IriConverter) { self =>
  import ResourceOps.*
  import StatementOps.*

  def rootResource: Resource = model.getResource(rootResourceIri.toString)

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
      model <- ModelOps.fromJsonLd(str)
      root  <- getRootResourceIri(model, converter)
    } yield KnoraApiValueModel(model, root, converter)

  private def getRootResourceIri(model: Model, convert: IriConverter): IO[ModelError, SmartIri] =
    val iter    = model.listStatements()
    var objSeen = Set.empty[String]
    var subSeen = Set.empty[String]
    while (iter.hasNext) {
      val stmt = iter.nextStatement()
      val _    = stmt.objectUri().foreach(iri => objSeen += iri)
      val _    = stmt.subjectUri().foreach(iri => subSeen += iri)
    }
    val result: IO[ModelError, SmartIri] = (subSeen -- objSeen) match {
      case result if result.size == 1 =>
        convert
          .asSmartIri(result.head)
          .mapError(ModelError.parseError)
          .filterOrElseWith(_.isKnoraResourceIri)(iri => ZIO.fail(ModelError.noResourceIri(iri)))
      case result if result.isEmpty => ZIO.fail(ModelError.noRootResource)
      case _                        => ZIO.fail(ModelError.moreThanOneRootResource)
    }
    result
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
