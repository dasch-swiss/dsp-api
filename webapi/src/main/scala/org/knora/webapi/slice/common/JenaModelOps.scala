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
import scala.jdk.CollectionConverters.IteratorHasAsScala

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.ModelError.IsNoResourceIri
import org.knora.webapi.slice.common.ModelError.ParseError
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

enum ModelError(val msg: String) {
  case ParseError(override val msg: String)                   extends ModelError(msg)
  case IsNoResourceIri(override val msg: String, iri: String) extends ModelError(msg)
}
object ModelError {
  def parseError(ex: Throwable): ParseError = ParseError(ex.getMessage)
  def parseError(msg: String): ParseError   = ParseError(msg)
  def noResourceIri(iri: SmartIri): IsNoResourceIri =
    IsNoResourceIri(s"This is not a resource IRI $iri", iri.toOntologySchema(ApiV2Complex).toIri)
}

/*
 * The KnoraApiModel represents any incoming values model from our v2 API.
 */
final case class KnoraApiValueModel(model: Model, convert: IriConverter) { self =>
  import ResourceOps.*

  def getResourceIri: IO[Option[ModelError], SmartIri] = ZIO.scoped {
    val iter = model.listSubjects()
    ZIO
      .fromOption(iter.asScala.find(_.isURIResource).map(_.getURI))
      .flatMap(iri => convert.asSmartIri(iri).orElseFail(ModelError.parseError(s"Unable to parse $iri")).asSomeError)
      .filterOrElseWith(_.isKnoraResourceIri)(iri => ZIO.fail(ModelError.noResourceIri(iri)).asSomeError)
      .withFinalizer(_ => ZIO.succeed(iter.close()))
  }

  def getResource: IO[Option[ModelError], Resource] = self.getResourceIri.map(_.toIri).map(model.getResource)

  def getResourceClassIri: IO[Option[ModelError], SmartIri] =
    for {
      resource <- self.getResource
      iri      <- ZIO.fromOption(resource.property(RDF.`type`)).map(_.getObject.asResource().getURI)
      smartIri <- convert.asSmartIri(iri).orDie
    } yield smartIri
}

object ResourceOps {
  extension (res: Resource) {
    def property(p: Property): Option[Statement] = Option(res.getProperty(p))
  }
}

object KnoraApiValueModel { self =>

  // available for ease of use in tests
  def fromJsonLd(str: String): ZIO[Scope & IriConverter, ParseError, KnoraApiValueModel] =
    ZIO.service[IriConverter].flatMap(self.fromJsonLd(str, _))

  def fromJsonLd(str: String, converter: IriConverter): ZIO[Scope & IriConverter, ParseError, KnoraApiValueModel] =
    JenaModelOps.fromJsonLd(str).map(KnoraApiValueModel(_, converter))
}

object JenaModelOps { self =>

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
