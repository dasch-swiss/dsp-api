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
import org.knora.webapi.slice.common.jena.JenaConversions.given
import org.knora.webapi.slice.common.jena.ModelOps
import org.knora.webapi.slice.common.jena.ModelOps.*
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.common.jena.StatementOps.*
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

object ModelError {
  def invalidIri(e: Throwable): String = e.getMessage
  def missingValueProp: String         = "No value property found in root resource"
  def multipleValueProp: String        = "Multiple value properties found in root resource"
  def noRootResourceClassIri: String   = "No root resource class IRI found"
}

final case class KnoraApiCreateValueModel(
  resourceIri: ResourceIri,
  resourceClassIri: KResourceClassIri,
  valuePropertyIri: PropertyIri,
  valueType: SmartIri,
  valueIri: Option[ValueIri],
  valueUuid: Option[UUID],
  valueCreationDate: Option[Instant],
  valuePermissions: Option[String],
  valueFileValueFilename: Option[String],
  private val valueResource: Resource,
  private val converter: IriConverter,
) {
  lazy val shortcode: Shortcode = resourceIri.shortcode

  def getValueContent(fileInfo: Option[FileInfo] = None): ZIO[MessageRelay, String, ValueContentV2] =
    def withFileInfo[T](f: FileInfo => Either[String, T]): IO[String, T] =
      fileInfo match
        case None       => ZIO.fail("FileInfo is missing")
        case Some(info) => ZIO.fromEither(f(info))
    valueType.toString match
      case AudioFileValue              => withFileInfo(AudioFileValueContentV2.from(valueResource, _))
      case ArchiveFileValue            => withFileInfo(ArchiveFileValueContentV2.from(valueResource, _))
      case BooleanValue                => ZIO.fromEither(BooleanValueContentV2.from(valueResource))
      case ColorValue                  => ZIO.fromEither(ColorValueContentV2.from(valueResource))
      case DateValue                   => ZIO.fromEither(DateValueContentV2.from(valueResource))
      case DecimalValue                => ZIO.fromEither(DecimalValueContentV2.from(valueResource))
      case DocumentFileValue           => withFileInfo(DocumentFileValueContentV2.from(valueResource, _))
      case GeomValue                   => ZIO.fromEither(GeomValueContentV2.from(valueResource))
      case GeonameValue                => ZIO.fromEither(GeonameValueContentV2.from(valueResource))
      case IntValue                    => ZIO.fromEither(IntegerValueContentV2.from(valueResource))
      case IntervalValue               => ZIO.fromEither(IntervalValueContentV2.from(valueResource))
      case ListValue                   => HierarchicalListValueContentV2.from(valueResource, converter)
      case LinkValue                   => LinkValueContentV2.from(valueResource, converter)
      case MovingImageFileValue        => withFileInfo(MovingImageFileValueContentV2.from(valueResource, _))
      case StillImageExternalFileValue => ZIO.fromEither(StillImageExternalFileValueContentV2.from(valueResource))
      case StillImageFileValue         => withFileInfo(StillImageFileValueContentV2.from(valueResource, _))
      case TextValue                   => TextValueContentV2.from(valueResource)
      case TextFileValue               => withFileInfo(TextFileValueContentV2.from(valueResource, _))
      case TimeValue                   => ZIO.fromEither(TimeValueContentV2.from(valueResource))
      case UriValue                    => ZIO.fromEither(UriValueContentV2.from(valueResource))
      case _                           => ZIO.fail(s"Unsupported value type: $valueType")
}

object KnoraApiCreateValueModel { self =>

  // available for ease of use in tests
  def fromJsonLd(str: String): ZIO[Scope & IriConverter, String, KnoraApiCreateValueModel] =
    ZIO.service[IriConverter].flatMap(self.fromJsonLd(str, _))

  def fromJsonLd(str: String, converter: IriConverter): ZIO[Scope & IriConverter, String, KnoraApiCreateValueModel] =
    for {
      model                  <- ModelOps.fromJsonLd(str)
      resourceAndIri         <- resourceAndIri(model, converter)
      (resource, resourceIri) = resourceAndIri
      resourceClassIri       <- resourceClassIri(resource, converter)
      valueStatement         <- valueStatement(resource)
      propertyIri            <- valuePropertyIri(converter, valueStatement)
      valueType              <- valueType(valueStatement, converter)
      valueResource           = valueStatement.getObject.asResource()
      valueIri               <- valueIri(valueResource, converter)
      valueUuid              <- ZIO.fromEither(valueHasUuid(valueResource))
      valueCreationDate      <- ZIO.fromEither(valueCreationDate(valueResource))
      valuePermissions       <- ZIO.fromEither(valuePermissions(valueResource))
      valueFileValueFilename <- ZIO.fromEither(valueFileValueFilename(valueResource))
    } yield KnoraApiCreateValueModel(
      resourceIri,
      resourceClassIri,
      propertyIri,
      valueType,
      valueIri,
      valueUuid,
      valueCreationDate,
      valuePermissions,
      valueFileValueFilename,
      valueResource,
      converter,
    )

  private def resourceAndIri(model: Model, convert: IriConverter): IO[String, (Resource, ResourceIri)] =
    ZIO.fromEither(model.singleRootResource).flatMap { (r: Resource) =>
      convert
        .asSmartIri(r.uri.getOrElse(""))
        .mapError(ModelError.invalidIri)
        .flatMap(iri => ZIO.fromEither(KResourceIri.from(iri)))
        .map((r, _))
    }

  private def valueStatement(rootResource: Resource): IO[String, Statement] = ZIO
    .succeed(rootResource.listProperties().asScala.filter(_.getPredicate != RDF.`type`).toList)
    .filterOrFail(_.nonEmpty)(ModelError.missingValueProp)
    .filterOrFail(_.size == 1)(ModelError.multipleValueProp)
    .map(_.head)

  private def valuePropertyIri(converter: IriConverter, valueStatement: Statement) =
    converter
      .asSmartIri(valueStatement.predicateUri)
      .mapError(ModelError.invalidIri)
      .flatMap(iri => ZIO.fromEither(PropertyIri.from(iri)))

  private def valueType(stmt: Statement, converter: IriConverter) = ZIO
    .fromEither(stmt.objectAsResource().flatMap(_.rdfsType.toRight("No rdf:type found for value.")))
    .orElseFail(s"No value type found for value.")
    .flatMap(converter.asSmartIri(_).mapError(ModelError.invalidIri))

  private def valueIri(valueResource: Resource, converter: IriConverter): IO[String, Option[ValueIri]] = ZIO
    .fromOption(valueResource.uri)
    .flatMap(converter.asSmartIri(_).mapError(_.getMessage).asSomeError)
    .flatMap(iri => ZIO.fromEither(ValueIri.from(iri)).asSomeError)
    .unsome

  private def valueHasUuid(valueResource: Resource): Either[String, Option[UUID]] =
    valueResource.objectStringOption(ValueHasUUID).flatMap {
      case Some(str) =>
        UuidUtil.base64Decode(str).map(Some(_)).toEither.left.map(e => s"Invalid UUID '$str': ${e.getMessage}")
      case None => Right(None)
    }

  private def valueCreationDate(valueResource: Resource): Either[String, Option[Instant]] =
    valueResource.objectDataTypeOption(ValueCreationDate, Xsd.DateTimeStamp).flatMap {
      case Some(str) => ValuesValidator.parseXsdDateTimeStamp(str).map(Some(_))
      case None      => Right(None)
    }

  private def valuePermissions(valueResource: Resource): Either[String, Option[String]] =
    valueResource.objectStringOption(HasPermissions)

  private def valueFileValueFilename(valueResource: Resource): Either[String, Option[String]] =
    valueResource.objectStringOption(FileValueHasFilename)

  private def resourceClassIri(rootResource: Resource, convert: IriConverter): IO[String, KResourceClassIri] = ZIO
    .fromOption(rootResource.rdfsType)
    .orElseFail(ModelError.noRootResourceClassIri)
    .flatMap(convert.asSmartIri(_).mapError(ModelError.invalidIri))
    .flatMap(iri => ZIO.fromEither(KResourceClassIri.from(iri)))
}
