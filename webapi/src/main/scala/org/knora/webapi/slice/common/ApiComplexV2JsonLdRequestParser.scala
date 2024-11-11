/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common
import org.apache.jena.rdf.model.*
import org.apache.jena.vocabulary.RDF
import zio.*
import zio.ZIO
import zio.ZLayer

import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

import dsp.valueobjects.UuidUtil
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.*
import org.knora.webapi.messages.OntologyConstants.Xsd
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.ValueContentV2.FileInfo
import org.knora.webapi.routing.v2.AssetIngestState
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.KnoraIris.*
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri as KResourceClassIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri as KResourceIri
import org.knora.webapi.slice.common.jena.JenaConversions.given
import org.knora.webapi.slice.common.jena.ModelOps
import org.knora.webapi.slice.common.jena.ModelOps.*
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.common.jena.StatementOps.*
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.iiif.api.SipiService

final case class ApiComplexV2JsonLdRequestParser(
  converter: IriConverter,
  messageRelay: MessageRelay,
  sipiService: SipiService,
) {

  def updateValueV2fromJsonLd(str: String, ingestState: AssetIngestState): IO[String, UpdateValueV2] =
    ZIO.scoped {
      for {
        model                  <- ModelOps.fromJsonLd(str)
        resourceAndIri         <- resourceAndIri(model)
        (resource, resourceIri) = resourceAndIri
        resourceClassIri       <- resourceClassIri(resource)
        valueStatement         <- valueStatement(resource)
        valuePropertyIri       <- valuePropertyIri(valueStatement)
        valueType              <- valueType(valueStatement)
        valueResource           = valueStatement.getObject.asResource()
        valueIri               <- valueIri(valueResource).someOrFail("The value IRI is required")
        newValueVersionIri     <- newValueVersionIri(valueResource, valueIri)
        valueCreationDate      <- ZIO.fromEither(valueCreationDate(valueResource))
        valuePermissions       <- ZIO.fromEither(valuePermissions(valueResource))
        valueFileValueFilename <- ZIO.fromEither(valueFileValueFilename(valueResource))
        valueContent <-
          getValueContent(valueType.toString, valueResource, valueFileValueFilename, resourceIri.shortcode, ingestState)
            .map(Some(_))
            .orElse(ZIO.none)
        updateValue <- valueContent match
                         case Some(valueContentV2) =>
                           ZIO.succeed(
                             UpdateValueContentV2(
                               resourceIri.toString,
                               resourceClassIri.smartIri,
                               valuePropertyIri.smartIri,
                               valueIri.toString,
                               valueContentV2,
                               valuePermissions,
                               valueCreationDate,
                               newValueVersionIri.map(_.smartIri),
                               ingestState,
                             ),
                           )
                         case None =>
                           ZIO
                             .fromOption(valuePermissions)
                             .mapBoth(
                               _ => "No permissions and no value content found",
                               permissions =>
                                 UpdateValuePermissionsV2(
                                   resourceIri.toString,
                                   resourceClassIri.smartIri,
                                   valuePropertyIri.smartIri,
                                   valueIri.toString,
                                   valueType,
                                   permissions,
                                   valueCreationDate,
                                   newValueVersionIri.map(_.smartIri),
                                 ),
                             )
      } yield updateValue
    }

  private def newValueVersionIri(r: Resource, valueIri: ValueIri): IO[String, Option[ValueIri]] =
    ZIO
      .fromEither(r.objectUriOption(NewValueVersionIri))
      .some
      .flatMap(converter.asSmartIri(_).mapError(_.getMessage).asSomeError)
      .flatMap(iri => ZIO.fromEither(ValueIri.from(iri)).asSomeError)
      .filterOrFail(newV => newV != valueIri)(
        Some(s"The IRI of a new value version cannot be the same as the IRI of the current version"),
      )
      .filterOrFail(newV => newV.sameResourceAs(valueIri))(
        Some(
          s"The project shortcode and resource must be equal for the new value version and the current version",
        ),
      )
      .unsome

  def createValueV2FromJsonLd(str: String, ingestState: AssetIngestState): IO[String, CreateValueV2] =
    ZIO.scoped {
      for {
        model                  <- ModelOps.fromJsonLd(str)
        resourceAndIri         <- resourceAndIri(model)
        (resource, resourceIri) = resourceAndIri
        resourceClassIri       <- resourceClassIri(resource)
        valueStatement         <- valueStatement(resource)
        valuePropertyIri       <- valuePropertyIri(valueStatement)
        valueType              <- valueType(valueStatement)
        valueResource           = valueStatement.getObject.asResource()
        valueIri               <- valueIri(valueResource)
        valueUuid              <- ZIO.fromEither(valueHasUuid(valueResource))
        valueCreationDate      <- ZIO.fromEither(valueCreationDate(valueResource))
        valuePermissions       <- ZIO.fromEither(valuePermissions(valueResource))
        valueFileValueFilename <- ZIO.fromEither(valueFileValueFilename(valueResource))
        valueContent <-
          getValueContent(valueType.toString, valueResource, valueFileValueFilename, resourceIri.shortcode, ingestState)
      } yield CreateValueV2(
        resourceIri.toString,
        resourceClassIri.smartIri,
        valuePropertyIri.smartIri,
        valueContent,
        valueIri.map(_.smartIri),
        valueUuid,
        valueCreationDate,
        valuePermissions,
        ingestState,
      )
    }

  private def resourceAndIri(model: Model): IO[String, (Resource, ResourceIri)] =
    ZIO.fromEither(model.singleRootResource).flatMap { (r: Resource) =>
      converter
        .asSmartIri(r.uri.getOrElse(""))
        .mapError(_.getMessage)
        .flatMap(iri => ZIO.fromEither(KResourceIri.from(iri)))
        .map((r, _))
    }

  private def valueStatement(rootResource: Resource): IO[String, Statement] = ZIO
    .succeed(rootResource.listProperties().asScala.filter(_.getPredicate != RDF.`type`).toList)
    .filterOrFail(_.nonEmpty)("No value property found in root resource")
    .filterOrFail(_.size == 1)("Multiple value properties found in root resource")
    .map(_.head)

  private def valuePropertyIri(valueStatement: Statement) =
    converter
      .asSmartIri(valueStatement.predicateUri)
      .mapError(_.getMessage)
      .flatMap(iri => ZIO.fromEither(PropertyIri.from(iri)))

  private def valueType(stmt: Statement) = ZIO
    .fromEither(stmt.objectAsResource().flatMap(_.rdfsType.toRight("No rdf:type found for value.")))
    .orElseFail(s"No value type found for value.")
    .flatMap(converter.asSmartIri(_).mapError(_.getMessage))

  private def valueIri(valueResource: Resource): IO[String, Option[ValueIri]] = ZIO
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

  private def resourceClassIri(rootResource: Resource): IO[String, KResourceClassIri] = ZIO
    .fromOption(rootResource.rdfsType)
    .orElseFail("No root resource class IRI found")
    .flatMap(converter.asSmartIri(_).mapError(_.getMessage))
    .flatMap(iri => ZIO.fromEither(KResourceClassIri.from(iri)))

  private def getValueContent(
    valueType: String,
    valueResource: Resource,
    maybeFileName: Option[String],
    shortcode: Shortcode,
    ingestState: AssetIngestState,
  ): IO[String, ValueContentV2] =
    def withFileInfo[T](fileInfo: Option[FileInfo], f: FileInfo => Either[String, T]): IO[String, T] =
      fileInfo match
        case None       => ZIO.fail("FileInfo is missing")
        case Some(info) => ZIO.fromEither(f(info))
    for {
      i <-
        ValueContentV2
          .fileInfoFromExternal(maybeFileName, ingestState, shortcode)
          .provide(ZLayer.succeed(sipiService))
          .mapError(_.getMessage)
      content <-
        valueType match
          case AudioFileValue              => withFileInfo(i, AudioFileValueContentV2.from(valueResource, _))
          case ArchiveFileValue            => withFileInfo(i, ArchiveFileValueContentV2.from(valueResource, _))
          case BooleanValue                => ZIO.fromEither(BooleanValueContentV2.from(valueResource))
          case ColorValue                  => ZIO.fromEither(ColorValueContentV2.from(valueResource))
          case DateValue                   => ZIO.fromEither(DateValueContentV2.from(valueResource))
          case DecimalValue                => ZIO.fromEither(DecimalValueContentV2.from(valueResource))
          case DocumentFileValue           => withFileInfo(i, DocumentFileValueContentV2.from(valueResource, _))
          case GeomValue                   => ZIO.fromEither(GeomValueContentV2.from(valueResource))
          case GeonameValue                => ZIO.fromEither(GeonameValueContentV2.from(valueResource))
          case IntValue                    => ZIO.fromEither(IntegerValueContentV2.from(valueResource))
          case IntervalValue               => ZIO.fromEither(IntervalValueContentV2.from(valueResource))
          case ListValue                   => HierarchicalListValueContentV2.from(valueResource, converter)
          case LinkValue                   => LinkValueContentV2.from(valueResource, converter)
          case MovingImageFileValue        => withFileInfo(i, MovingImageFileValueContentV2.from(valueResource, _))
          case StillImageExternalFileValue => ZIO.fromEither(StillImageExternalFileValueContentV2.from(valueResource))
          case StillImageFileValue         => withFileInfo(i, StillImageFileValueContentV2.from(valueResource, _))
          case TextValue                   => TextValueContentV2.from(valueResource).provide(ZLayer.succeed(messageRelay))
          case TextFileValue               => withFileInfo(i, TextFileValueContentV2.from(valueResource, _))
          case TimeValue                   => ZIO.fromEither(TimeValueContentV2.from(valueResource))
          case UriValue                    => ZIO.fromEither(UriValueContentV2.from(valueResource))
          case _                           => ZIO.fail(s"Unsupported value type: $valueType")
    } yield content
}

object ApiComplexV2JsonLdRequestParser {
  val layer = ZLayer.derive[ApiComplexV2JsonLdRequestParser]
}
