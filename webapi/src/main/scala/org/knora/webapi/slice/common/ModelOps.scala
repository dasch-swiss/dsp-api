/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import org.apache.jena.rdf.model.*
import org.apache.jena.vocabulary.RDF
import zio.*

import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

import dsp.valueobjects.UuidUtil
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.*
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.ValueHasUUID
import org.knora.webapi.messages.OntologyConstants.Xsd
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.ValueContentV2.FileInfo
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.KnoraIris.*
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri as KResourceClassIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri as KResourceIri
import org.knora.webapi.slice.common.ModelError.ParseError
import org.knora.webapi.slice.common.jena.JenaConversions.given
import org.knora.webapi.slice.common.jena.ModelOps
import org.knora.webapi.slice.common.jena.ModelOps.*
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.common.jena.StatementOps.*
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

enum ModelError(val msg: String) {
  case ParseError(override val msg: String)             extends ModelError(msg)
  case InvalidIri(override val msg: String)             extends ModelError(msg)
  case InvalidModel(override val msg: String)           extends ModelError(msg)
  case MissingValueProp(override val msg: String)       extends ModelError(msg)
  case MultipleValueProp(override val msg: String)      extends ModelError(msg)
  case NoRootResourceClassIri(override val msg: String) extends ModelError(msg)
}
object ModelError {
  def parseError(ex: Throwable): ParseError          = ParseError(ex.getMessage)
  def invalidIri(msg: String): InvalidIri            = InvalidIri(msg)
  def invalidIri(e: Throwable): InvalidIri           = InvalidIri(e.getMessage)
  def invalidModel(msg: String): InvalidModel        = InvalidModel(msg)
  def missingValueProp: MissingValueProp             = MissingValueProp("No value property found in root resource")
  def multipleValueProp: MultipleValueProp           = MultipleValueProp("Multiple value properties found in root resource")
  def noRootResourceClassIri: NoRootResourceClassIri = NoRootResourceClassIri("No root resource class IRI found")
}

/*
 * The KnoraApiModel represents any incoming value models from our v2 API.
 */
final case class KnoraApiValueModel(
  resourceIri: ResourceIri,
  resourceClassIri: KResourceClassIri,
  valueNode: KnoraApiValueNode,
) {
  lazy val shortcode: Shortcode = resourceIri.shortcode
}

object KnoraApiValueModel { self =>

  // available for ease of use in tests
  def fromJsonLd(str: String): ZIO[Scope & IriConverter, ModelError, KnoraApiValueModel] =
    ZIO.service[IriConverter].flatMap(self.fromJsonLd(str, _))

  def fromJsonLd(str: String, converter: IriConverter): ZIO[Scope & IriConverter, ModelError, KnoraApiValueModel] =
    for {
      model                  <- ModelOps.fromJsonLd(str)
      resourceAndIri         <- resourceAndIri(model, converter)
      (resource, resourceIri) = resourceAndIri
      resourceClassIri       <- resourceClassIri(resource, converter)
      valueProp              <- valueNode(resource, resourceIri.shortcode, converter)
    } yield KnoraApiValueModel(
      resourceIri,
      resourceClassIri,
      valueProp,
    )

  private def resourceAndIri(model: Model, convert: IriConverter): IO[ModelError, (Resource, ResourceIri)] =
    ZIO.fromEither(model.singleRootResource).mapError(ModelError.invalidModel).flatMap { (r: Resource) =>
      convert
        .asSmartIri(r.uri.getOrElse(""))
        .mapError(ModelError.invalidIri)
        .flatMap(iri => ZIO.fromEither(KResourceIri.from(iri)).mapError(ModelError.invalidIri))
        .map((r, _))
    }

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
        .toList
    }
      .filterOrFail(_.nonEmpty)(ModelError.missingValueProp)
      .filterOrFail(_.size == 1)(ModelError.multipleValueProp)
      .map(_.head)
      .flatMap(s => KnoraApiValueNode.from(s, shortcode, converter))

  private def resourceClassIri(
    rootResource: Resource,
    convert: IriConverter,
  ): IO[ModelError, KResourceClassIri] = ZIO
    .fromOption(rootResource.rdfsType)
    .orElseFail(ModelError.noRootResourceClassIri)
    .flatMap(convert.asSmartIri(_).mapError(ModelError.invalidIri))
    .flatMap(iri => ZIO.fromEither(KResourceClassIri.from(iri)).mapError(ModelError.invalidIri))
}

final case class KnoraApiValueNode(
  node: Resource,
  propertyIri: PropertyIri,
  valueType: SmartIri,
  shortcode: Shortcode,
  convert: IriConverter,
) {

  def getValueIri: IO[ModelError, Option[ValueIri]] =
    ZIO
      .fromOption(node.uri)
      .flatMap(convert.asSmartIri(_).mapError(_.getMessage).asSomeError)
      .flatMap(iri => ZIO.fromEither(ValueIri.from(iri)).asSomeError)
      .unsome
      .mapError(ModelError.invalidIri)

  def getValueHasUuid: Either[String, Option[UUID]] =
    node.objectStringOption(ValueHasUUID).flatMap {
      case Some(str) =>
        UuidUtil.base64Decode(str).map(Some(_)).toEither.left.map(e => s"Invalid UUID '$str': ${e.getMessage}")
      case None => Right(None)
    }

  def getValueCreationDate: Either[String, Option[Instant]] =
    node.objectDataTypeOption(ValueCreationDate, Xsd.DateTimeStamp).flatMap {
      case Some(str) => ValuesValidator.parseXsdDateTimeStamp(str).map(Some(_))
      case None      => Right(None)
    }

  def getHasPermissions: Either[String, Option[String]] =
    node.objectStringOption(OntologyConstants.KnoraApiV2Complex.HasPermissions)

  def getValueContent(fileInfo: Option[FileInfo] = None): ZIO[MessageRelay, String, ValueContentV2] =
    def withFileInfo[T](f: FileInfo => Either[String, T]): IO[String, T] =
      fileInfo match
        case None       => ZIO.fail("FileInfo is missing")
        case Some(info) => ZIO.fromEither(f(info))
    valueType.toString match
      case AudioFileValue              => withFileInfo(AudioFileValueContentV2.from(node, _))
      case ArchiveFileValue            => withFileInfo(ArchiveFileValueContentV2.from(node, _))
      case BooleanValue                => ZIO.fromEither(BooleanValueContentV2.from(node))
      case ColorValue                  => ZIO.fromEither(ColorValueContentV2.from(node))
      case DateValue                   => ZIO.fromEither(DateValueContentV2.from(node))
      case DecimalValue                => ZIO.fromEither(DecimalValueContentV2.from(node))
      case DocumentFileValue           => withFileInfo(DocumentFileValueContentV2.from(node, _))
      case GeomValue                   => ZIO.fromEither(GeomValueContentV2.from(node))
      case GeonameValue                => ZIO.fromEither(GeonameValueContentV2.from(node))
      case IntValue                    => ZIO.fromEither(IntegerValueContentV2.from(node))
      case IntervalValue               => ZIO.fromEither(IntervalValueContentV2.from(node))
      case ListValue                   => HierarchicalListValueContentV2.from(node, convert)
      case LinkValue                   => LinkValueContentV2.from(node, convert)
      case MovingImageFileValue        => withFileInfo(MovingImageFileValueContentV2.from(node, _))
      case StillImageExternalFileValue => ZIO.fromEither(StillImageExternalFileValueContentV2.from(node))
      case StillImageFileValue         => withFileInfo(StillImageFileValueContentV2.from(node, _))
      case TextValue                   => TextValueContentV2.from(node)
      case TextFileValue               => withFileInfo(TextFileValueContentV2.from(node, _))
      case TimeValue                   => ZIO.fromEither(TimeValueContentV2.from(node))
      case UriValue                    => ZIO.fromEither(UriValueContentV2.from(node))
      case _                           => ZIO.fail(s"Unsupported value type: $valueType")
}

object KnoraApiValueNode {
  def from(
    stmt: Statement,
    shortcode: Shortcode,
    convert: IriConverter,
  ): IO[ModelError, KnoraApiValueNode] =
    for {
      propertyIri <- convert
                       .asSmartIri(stmt.predicateUri)
                       .mapError(ModelError.invalidIri)
                       .flatMap(iri => ZIO.fromEither(PropertyIri.from(iri)).mapError(ModelError.invalidIri))
      valueType <- ZIO
                     .fromEither(stmt.objectAsResource().flatMap(_.rdfsType.toRight("No rdf:type found for value.")))
                     .orElseFail(ModelError.invalidIri(s"No value type found for value."))
                     .flatMap(convert.asSmartIri(_).mapError(ModelError.invalidIri))
    } yield KnoraApiValueNode(stmt.getObject.asResource(), propertyIri, valueType, shortcode, convert)
}
