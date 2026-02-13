/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import org.apache.jena.rdf.model.*
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS
import zio.*
import zio.json.*
import zio.json.ast.*

import java.time.Instant
import java.util.UUID
import scala.collection.immutable.Seq
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

import org.knora.webapi.IRI
import org.knora.webapi.config.Sipi
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.*
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateValueInNewResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.DeleteOrEraseResourceRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.UpdateResourceMetadataRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.ValueContentV2.FileInfo
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.api.v2.mapping.CreateStandoffMappingForm
import org.knora.webapi.slice.common.KnoraIris.*
import org.knora.webapi.slice.common.jena.JenaConversions.given
import org.knora.webapi.slice.common.jena.ModelOps
import org.knora.webapi.slice.common.jena.ModelOps.*
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.common.jena.StatementOps.*
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.store.iiif.api.SipiService

case class CreateMappingRequestV2(label: String, projectIri: ProjectIri, mappingName: String, xml: String)

final case class ApiComplexV2JsonLdRequestParser(
  converter: IriConverter,
  messageRelay: MessageRelay,
  sipiService: SipiService,
  projectService: ProjectService,
  userService: UserService,
  sipiConfig: Sipi,
) {

  /**
   * Every value or resource request MUST only contain a single root rdf resource.
   * The root resource MUST have a rdf:type property that specifies the Knora resource class.
   * The root resource MAY be an uri resource or a blank node resource.
   * The RootResource provides some common methods to access the properties of the root resource.
   */
  private case class RootResource(
    resource: Resource,
    resourceIri: Option[ResourceIri],
    resourceClassIri: ResourceClassIri,
    valueOrder: Map[String, Seq[String]] = Map.empty,
  ) {
    def resourceIriOrFail: IO[String, ResourceIri] =
      ZIO.fromOption(resourceIri).orElseFail("The resource IRI is required")
    def resourceClassSmartIri: SmartIri = resourceClassIri.smartIri

    // accessor methods for various properties of the root resource
    def creationDateOption: IO[String, Option[Instant]]         = ZIO.fromEither(resource.objectInstantOption(CreationDate))
    def deleteCommentOption: IO[String, Option[String]]         = ZIO.fromEither(resource.objectStringOption(DeleteComment))
    def deleteDateOption: IO[String, Option[Instant]]           = ZIO.fromEither(resource.objectInstantOption(DeleteDate))
    def hasPermissionsOption: IO[String, Option[String]]        = ZIO.fromEither(resource.objectStringOption(HasPermissions))
    def lastModificationDateOption: IO[String, Option[Instant]] =
      ZIO.fromEither(resource.objectInstantOption(LastModificationDate))
    def newModificationDateOption: IO[String, Option[Instant]] =
      ZIO.fromEither(resource.objectInstantOption(NewModificationDate))
    def rdfsLabelOption: IO[String, Option[String]] = ZIO.fromEither(resource.objectStringOption(Rdfs.Label))
  }

  private object RootResource {
    def fromJsonLd(str: String): ZIO[Scope, String, RootResource] =
      for {
        model             <- ModelOps.fromJsonLd(str)
        resource          <- ZIO.fromEither(model.singleRootResource)
        resourceIriOption <-
          ZIO
            .foreach(resource.uri)(
              converter
                .asSmartIri(_)
                .mapError(_.getMessage)
                .flatMap(iri => ZIO.fromEither(KnoraIris.ResourceIri.from(iri))),
            )
        resourceClassIri <- resourceClassIri(resource)
      } yield RootResource(resource, resourceIriOption, resourceClassIri, extractJsonArrayOrder(str))

    private def resourceClassIri(r: Resource): IO[String, ResourceClassIri] = ZIO
      .fromOption(r.rdfType)
      .orElseFail("No root resource class IRI found")
      .flatMap(converter.asResourceClassIriApiV2Complex)
  }

  def updateResourceMetadataRequestV2(
    str: String,
    requestingUser: User,
    uuid: UUID,
  ): IO[String, UpdateResourceMetadataRequestV2] = ZIO.scoped {
    for {
      r                    <- RootResource.fromJsonLd(str)
      resourceIri          <- r.resourceIriOrFail
      label                <- r.rdfsLabelOption
      permissions          <- r.hasPermissionsOption
      lastModificationDate <- r.lastModificationDateOption
      newModificationDate  <- r.newModificationDateOption
      _                    <- ZIO
             .fail("No updated resource metadata provided")
             .when(label.isEmpty && permissions.isEmpty && newModificationDate.isEmpty)
    } yield UpdateResourceMetadataRequestV2(
      resourceIri.smartIri.toString,
      r.resourceClassSmartIri,
      lastModificationDate,
      label,
      permissions,
      newModificationDate,
      requestingUser,
      uuid,
    )
  }

  def deleteOrEraseResourceRequestV2(
    str: String,
    requestingUser: User,
    uuid: UUID,
  ): IO[String, DeleteOrEraseResourceRequestV2] = ZIO.scoped {
    for {
      r                    <- RootResource.fromJsonLd(str)
      resourceIri          <- r.resourceIriOrFail
      deleteComment        <- r.deleteCommentOption
      deleteDate           <- r.deleteDateOption
      lastModificationDate <- r.lastModificationDateOption
    } yield DeleteOrEraseResourceRequestV2(
      resourceIri.smartIri.toString,
      r.resourceClassSmartIri,
      deleteComment,
      deleteDate,
      lastModificationDate,
      requestingUser,
      uuid,
    )
  }

  def deleteValueV2FromJsonLd(str: String): IO[String, DeleteValueV2] = ZIO.scoped {
    for {
      r                  <- RootResource.fromJsonLd(str)
      v                  <- ValueResource.from(r)
      resourceIri        <- r.resourceIriOrFail
      valueIri           <- v.valueIriOrFail
      _                  <- ensureSameProject(valueIri, resourceIri)
      valueDeleteDate    <- v.deleteDateOption
      valueDeleteComment <- v.deleteCommentOption
      uuid               <- Random.nextUUID
    } yield DeleteValueV2(
      resourceIri,
      r.resourceClassIri,
      v.propertyIri,
      valueIri,
      v.valueType,
      valueDeleteComment,
      valueDeleteDate,
      uuid,
    )
  }

  private def ensureSameProject(v: ValueIri, r: ResourceIri): IO[String, Unit] =
    ZIO
      .fail(s"Resource IRI and value IRI must reference the same project")
      .when(v.shortcode != r.shortcode)
      .unit

  def eraseValueV2FromJsonLd(str: String): IO[String, EraseValueV2] = ZIO.scoped {
    for {
      r           <- RootResource.fromJsonLd(str)
      v           <- ValueResource.from(r)
      resourceIri <- r.resourceIriOrFail
      valueIri    <- v.valueIriOrFail
      _           <- ensureSameProject(valueIri, resourceIri)
      uuid        <- Random.nextUUID
    } yield EraseValueV2(
      resourceIri,
      r.resourceClassIri,
      v.propertyIri,
      valueIri,
      v.valueType,
      uuid,
    )
  }

  def eraseValueHistoryV2FromJsonLd(str: String): IO[String, EraseValueHistoryV2] = ZIO.scoped {
    for {
      r           <- RootResource.fromJsonLd(str)
      v           <- ValueResource.from(r)
      resourceIri <- r.resourceIriOrFail
      valueIri    <- v.valueIriOrFail
      _           <- ensureSameProject(valueIri, resourceIri)
      uuid        <- Random.nextUUID
    } yield EraseValueHistoryV2(
      resourceIri,
      r.resourceClassIri,
      v.propertyIri,
      valueIri,
      v.valueType,
      uuid,
    )
  }

  private case class ValueResource(
    r: Resource,
    valueIri: Option[ValueIri],
    propertyIri: PropertyIri,
    valueType: SmartIri,
  ) {
    def valueIriOrFail: IO[String, ValueIri] = ZIO.fromOption(valueIri).orElseFail("The value IRI is required")
    def propertySmartIri: SmartIri           = propertyIri.smartIri

    // accessor methods for various properties of the value resource
    def deleteCommentOption: IO[String, Option[String]]        = ZIO.fromEither(r.objectStringOption(DeleteComment))
    def deleteDateOption: IO[String, Option[Instant]]          = ZIO.fromEither(r.objectInstantOption(DeleteDate))
    def fileValueHasFilenameOption: IO[String, Option[String]] =
      ZIO.fromEither(r.objectStringOption(FileValueHasFilename))
    def hasPermissionsOption: IO[String, Option[String]]     = ZIO.fromEither(r.objectStringOption(HasPermissions))
    def newValueVersionIriOption: IO[String, Option[String]] = ZIO.fromEither(r.objectUriOption(NewValueVersionIri))
    def valueCreationDateOption: IO[String, Option[Instant]] = ZIO.fromEither(r.objectInstantOption(ValueCreationDate))
    def valueHasUuidOption: IO[String, Option[UUID]]         = ZIO.fromEither(r.objectUuidOption(ValueHasUUID))
  }

  private object ValueResource {
    def from(r: RootResource): IO[String, ValueResource] = valueStatement(r).flatMap(from)

    def from(stmt: Statement): IO[String, ValueResource] =
      for {
        propertyIri <- valuePropertyIri(stmt)
        r            = stmt.getObject.asResource()
        iriOption   <- valueIri(r)
        valueType   <- valueType(r)
      } yield ValueResource(r, iriOption, propertyIri, valueType)

    private def valueStatement(r: RootResource): IO[String, Statement] =
      ZIO
        .succeed(r.resource.listProperties().asScala.filter(_.getPredicate != RDF.`type`).toList)
        .filterOrFail(_.nonEmpty)("No value property found in root resource")
        .filterOrFail(_.size == 1)("Multiple value properties found in root resource")
        .map(_.head)

    private def valueIri(valueResource: Resource): IO[String, Option[ValueIri]] = ZIO
      .fromOption(valueResource.uri)
      .flatMap(converter.asSmartIri(_).mapError(_.getMessage).asSomeError)
      .flatMap(iri => ZIO.fromEither(ValueIri.from(iri)).asSomeError)
      .unsome

    private def valuePropertyIri(valueStatement: Statement) =
      converter
        .asSmartIri(valueStatement.predicateUri)
        .mapError(_.getMessage)
        .flatMap(iri => ZIO.fromEither(PropertyIri.fromApiV2Complex(iri)))

    private def valueType(resource: Resource) = ZIO
      .fromEither(resource.rdfType.toRight("No rdf:type found for value."))
      .orElseFail(s"No value type found for value.")
      .flatMap(converter.asSmartIri(_).mapError(_.getMessage))

  }

  def createResourceRequestV2(
    str: String,
    requestingUser: User,
    uuid: UUID,
  ): IO[String, CreateResourceRequestV2] = ZIO.scoped {
    for {
      r            <- RootResource.fromJsonLd(str)
      permissions  <- r.hasPermissionsOption
      creationDate <- r.creationDateOption
      label        <- r.rdfsLabelOption.someOrFail("A Resource must have an rdfs:label")
      project      <- attachedToProject(r.resource)
      _            <- ZIO
             .fail("Resource IRI and project IRI must reference the same project")
             .when(r.resourceIri.exists(_.shortcode != project.shortcode))
      attachedToUser <- attachedToUser(r.resource, requestingUser, project.id)
      values         <- extractValues(r.resource, project.shortcode, r.valueOrder)
      createResource  = CreateResourceV2(
                         r.resourceIri.map(_.smartIri),
                         r.resourceClassSmartIri,
                         label,
                         values,
                         project,
                         permissions,
                         creationDate,
                       )
      _ <- checkMimeTypesForFileValueContents(createResource.flatValues)
    } yield CreateResourceRequestV2(createResource, attachedToUser, uuid)
  }

  private def checkMimeTypesForFileValueContents(
    values: Iterable[CreateValueInNewResourceV2],
  ): IO[String, Unit] = {
    def failBadRequest(fvc: FileValueContentV2): IO[String, Unit] = {
      val msg =
        s"File ${fvc.fileValue.internalFilename} has MIME type ${fvc.fileValue.internalMimeType}, " +
          s"which is not supported for ${fvc.getClass.getSimpleName}."
      ZIO.fail(msg)
    }
    ZIO
      .foreach(values) { value =>
        value.valueContent match {
          case fileValueContent: StillImageFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.imageMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case fileValueContent: DocumentFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.documentMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case fileValueContent: ArchiveFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.archiveMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case fileValueContent: TextFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.textMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case fileValueContent: AudioFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.audioMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case fileValueContent: MovingImageFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.videoMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case _ => ZIO.unit
        }
      }
      .unit
  }

  private def extractValues(
    r: Resource,
    shortcode: Shortcode,
    valueOrder: Map[String, Seq[String]],
  ): IO[String, Map[SmartIri, Seq[CreateValueInNewResourceV2]]] =
    val filteredProperties = Seq(
      RDF.`type`.toString,
      Rdfs.Label,
      AttachedToProject,
      AttachedToUser,
      HasPermissions,
      CreationDate,
    )
    val valueStatements = r
      .listProperties()
      .asScala
      .filter(p => !filteredProperties.contains(p.getPredicate.toString))
      .toSeq
    ZIO
      .foreach(valueStatements)(valueStatementAsContent(_, shortcode))
      .map(_.groupMap(_._1.smartIri)(_._2))
      .map(reorderByJsonArrayOrder(_, valueOrder))

  /**
   * Extract the original JSON array order for property values from the raw JSON-LD string.
   * Returns a map from expanded property IRI to ordered Seq of valueAsString values.
   */
  private def extractJsonArrayOrder(rawJson: String): Map[String, Seq[String]] =
    rawJson
      .fromJson[Json.Obj]
      .toOption
      .map { obj =>
        val context: Map[String, String] = obj
          .get("@context")
          .flatMap {
            case Json.Obj(fields) => Some(fields.collect { case (k, Json.Str(v)) => (k, v) }.toMap)
            case _                => None
          }
          .getOrElse(Map.empty)

        obj.fields.collect {
          case (key, Json.Arr(values)) if !key.startsWith("@") =>
            val expandedKey    = expandCompactIri(key, context)
            val orderedStrings = values.collect { case Json.Obj(fields) =>
              fields.collectFirst {
                case (k, Json.Str(v)) if expandCompactIri(k, context) == ValueAsString => v
              }
            }.flatten
            (expandedKey, orderedStrings)
        }.toMap
      }
      .getOrElse(Map.empty)

  private def expandCompactIri(compactIri: String, context: Map[String, String]): String =
    compactIri.indexOf(':') match {
      case -1 => compactIri
      case i  =>
        val prefix = compactIri.substring(0, i)
        context.get(prefix) match {
          case Some(expansion) => expansion + compactIri.substring(i + 1)
          case None            => compactIri
        }
    }

  /**
   * Reorder each property's values to match the original JSON array order.
   * Uses greedy matching by valueHasString to handle duplicates correctly.
   * Values without a match in the JSON order are appended at the end.
   */
  private def reorderByJsonArrayOrder(
    grouped: Map[SmartIri, Seq[CreateValueInNewResourceV2]],
    jsonOrder: Map[String, Seq[String]],
  ): Map[SmartIri, Seq[CreateValueInNewResourceV2]] =
    grouped.map { case (propertyIri, values) =>
      jsonOrder.get(propertyIri.toString) match {
        case Some(orderedStrings) if orderedStrings.nonEmpty =>
          val (reordered, remaining) = orderedStrings.foldLeft((Seq.empty[CreateValueInNewResourceV2], values)) {
            case ((matched, unmatched), expectedStr) =>
              val idx = unmatched.indexWhere(_.valueContent.unescape.valueHasString == expectedStr)
              if (idx >= 0) {
                val (before, after) = unmatched.splitAt(idx)
                (matched :+ after.head, before ++ after.tail)
              } else (matched, unmatched)
          }
          (propertyIri, reordered ++ remaining)
        case _ => (propertyIri, values)
      }
    }

  private def valueStatementAsContent(
    statement: Statement,
    shortcode: Shortcode,
  ): IO[String, (PropertyIri, CreateValueInNewResourceV2)] =
    for {
      v                       <- ValueResource.from(statement)
      cnt                     <- getValueContent(v, shortcode)
      customValueUuid         <- v.valueHasUuidOption
      customValueCreationDate <- v.valueCreationDateOption
      permissions             <- v.hasPermissionsOption
    } yield (
      v.propertyIri,
      CreateValueInNewResourceV2(cnt, v.valueIri.map(_.smartIri), customValueUuid, customValueCreationDate, permissions),
    )

  def attachedToUser(r: Resource, requestingUser: User, projectIri: ProjectIri): IO[String, User] =
    for {
      userStr <- ZIO.fromEither(r.objectUriOption(AttachedToUser))
      userIri <- ZIO.foreach(userStr)(iri => ZIO.fromEither(UserIri.from(iri)))
      user    <- ZIO.foreach(userIri)(iri => checkUser(requestingUser, iri, projectIri))
    } yield user.getOrElse(requestingUser)

  private def checkUser(requestingUser: User, userIri: UserIri, projectIri: ProjectIri): IO[String, User] =
    requestingUser match {
      case _ if requestingUser.id == userIri.value => ZIO.succeed(requestingUser)
      case _
          if !(requestingUser.permissions.isSystemAdmin ||
            requestingUser.permissions.isProjectAdmin(projectIri.value)) =>
        ZIO.fail(
          s"You are logged in as ${requestingUser.username}, but only a system or project administrator can perform an operation as another user",
        )
      case _ => userService.findUserByIri(userIri).orDie.someOrFail(s"User '${userIri.value}' not found")
    }

  def attachedToProject(r: Resource): IO[String, Project] =
    for {
      projectIri <- ZIO.fromEither(r.objectUri(AttachedToProject)).flatMap(iri => ZIO.fromEither(ProjectIri.from(iri)))
      project    <- projectService.findById(projectIri).orDie.someOrFail(s"Project ${projectIri.value} not found")
    } yield project

  private def newValueVersionIri(r: ValueResource, valueIri: ValueIri): IO[String, Option[ValueIri]] =
    r.newValueVersionIriOption.some
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

  def updateValueV2fromJsonLd(json: Json.Obj): IO[String, UpdateValueV2] =
    updateValueV2fromJsonLd(json.toJson)

  def updateValueV2fromJsonLd(str: String): IO[String, UpdateValueV2] =
    ZIO.scoped {
      for {
        r                  <- RootResource.fromJsonLd(str)
        resourceIri        <- r.resourceIriOrFail
        v                  <- ValueResource.from(r)
        valueIri           <- v.valueIriOrFail
        valueCreationDate  <- v.valueCreationDateOption
        valuePermissions   <- v.hasPermissionsOption
        newValueVersionIri <- newValueVersionIri(v, valueIri)
        valueContent       <- getValueContent(v, resourceIri.shortcode).map(Some(_)).orElse(ZIO.none)
        updateValue        <- (valueContent, valuePermissions) match
                         case (Some(valueContentV2), _) =>
                           ZIO.succeed(
                             UpdateValueContentV2(
                               resourceIri.smartIri.toString,
                               r.resourceClassSmartIri,
                               v.propertySmartIri,
                               valueIri.smartIri.toString,
                               valueContentV2,
                               valuePermissions,
                               valueCreationDate,
                               newValueVersionIri.map(_.smartIri),
                             ),
                           )
                         case (_, Some(permissions)) =>
                           ZIO.succeed(
                             UpdateValuePermissionsV2(
                               resourceIri.smartIri.toString,
                               r.resourceClassSmartIri,
                               v.propertySmartIri,
                               valueIri.smartIri.toString,
                               v.valueType,
                               permissions,
                               valueCreationDate,
                               newValueVersionIri.map(_.smartIri),
                             ),
                           )
                         case _ => ZIO.fail("No value content or permissions provided")
      } yield updateValue
    }

  def createValueV2FromJsonLd(json: Json.Obj): IO[String, CreateValueV2] =
    createValueV2FromJsonLd(json.toJson)

  def createValueV2FromJsonLd(str: String): IO[String, CreateValueV2] =
    ZIO.scoped {
      for {
        r                 <- RootResource.fromJsonLd(str)
        resourceIri       <- r.resourceIriOrFail
        v                 <- ValueResource.from(r)
        valueUuid         <- v.valueHasUuidOption
        valueCreationDate <- v.valueCreationDateOption
        valuePermissions  <- v.hasPermissionsOption
        valueContent      <- getValueContent(v, resourceIri.shortcode)
      } yield CreateValueV2(
        resourceIri.smartIri.toString,
        r.resourceClassSmartIri,
        v.propertyIri.smartIri,
        valueContent,
        v.valueIri.map(_.smartIri),
        valueUuid,
        valueCreationDate,
        valuePermissions,
      )
    }

  private def getValueContent(
    v: ValueResource,
    shortcode: Shortcode,
  ): IO[String, ValueContentV2] =
    def withFileInfo[T](v: ValueResource, f: (Resource, FileInfo) => Either[String, T]): IO[String, T] = for {
      maybeFileName <- v.fileValueHasFilenameOption
      fileInfo      <- ValueContentV2
                    .fileInfoFromExternal(maybeFileName, shortcode)
                    .provide(ZLayer.succeed(sipiService))
                    .mapError(_.getMessage)
                    .someOrFail(s"FileInfo from external is missing for $maybeFileName")
      info <- ZIO.fromEither(f(v.r, fileInfo))
    } yield info

    for {
      content <-
        v.valueType.toString match
          case AudioFileValue              => withFileInfo(v, AudioFileValueContentV2.from)
          case ArchiveFileValue            => withFileInfo(v, ArchiveFileValueContentV2.from)
          case BooleanValue                => ZIO.fromEither(BooleanValueContentV2.from(v.r))
          case ColorValue                  => ZIO.fromEither(ColorValueContentV2.from(v.r))
          case DateValue                   => ZIO.fromEither(DateValueContentV2.from(v.r))
          case DecimalValue                => ZIO.fromEither(DecimalValueContentV2.from(v.r))
          case DocumentFileValue           => withFileInfo(v, DocumentFileValueContentV2.from)
          case GeomValue                   => ZIO.fromEither(GeomValueContentV2.from(v.r))
          case GeonameValue                => ZIO.fromEither(GeonameValueContentV2.from(v.r))
          case IntValue                    => ZIO.fromEither(IntegerValueContentV2.from(v.r))
          case IntervalValue               => ZIO.fromEither(IntervalValueContentV2.from(v.r))
          case ListValue                   => HierarchicalListValueContentV2.from(v.r, converter)
          case LinkValue                   => LinkValueContentV2.from(v.r, converter)
          case MovingImageFileValue        => withFileInfo(v, MovingImageFileValueContentV2.from)
          case StillImageExternalFileValue => ZIO.fromEither(StillImageExternalFileValueContentV2.from(v.r))
          case StillImageFileValue         => withFileInfo(v, StillImageFileValueContentV2.from)
          case TextValue                   => TextValueContentV2.from(v.r).provide(ZLayer.succeed(messageRelay))
          case TextFileValue               => withFileInfo(v, TextFileValueContentV2.from)
          case TimeValue                   => ZIO.fromEither(TimeValueContentV2.from(v.r))
          case UriValue                    => ZIO.fromEither(UriValueContentV2.from(v.r))
          case unsupported                 => ZIO.fail(s"Unsupported value type: $unsupported")
    } yield content

  def createMappingRequestMetadataV2(form: CreateStandoffMappingForm): IO[String, CreateMappingRequestV2] =
    def findSingle[A](m: Model, p: Property, mapper: Resource => Property => Either[String, A]): IO[String, A] = {
      m.listSubjectsWithProperty(p).asScala.toList match {
        case Nil      => ZIO.fail(s"No $p found")
        case r :: Nil => ZIO.succeed(r)
        case _        => ZIO.fail(s"Multiple $p found")
      }
    }.map(r => mapper.apply(r)(p)).flatMap(ZIO.fromEither)
    ZIO.scoped {
      for {
        m           <- ModelOps.fromJsonLd(form.json).logError
        label       <- findSingle(m, RDFS.label, _.objectString)
        projectIri  <- findSingle(m, KA.AttachedToProject, r => r.objectUri(_, ProjectIri.from))
        mappingName <- findSingle(m, KA.MappingHasName, _.objectString)
      } yield CreateMappingRequestV2(label, projectIri, mappingName, form.xml)
    }
}

object ApiComplexV2JsonLdRequestParser {
  val layer = ZLayer.derive[ApiComplexV2JsonLdRequestParser]
}
