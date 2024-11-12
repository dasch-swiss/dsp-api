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
import scala.collection.immutable.Seq
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

import dsp.valueobjects.UuidUtil
import org.knora.webapi.IRI
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.*
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.OntologyConstants.Xsd
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.ValuesValidator.parseXsdDateTimeStamp
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateValueInNewResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.DeleteOrEraseResourceRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.UpdateResourceMetadataRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.ValueContentV2.FileInfo
import org.knora.webapi.routing.v2.AssetIngestState
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.common.KnoraIris.*
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri as KResourceClassIri
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
  projectService: ProjectService,
  userService: UserService,
) {

  /**
   * Every value or resource request MUST only contain a single root rdf resource.
   * The root resource MUST have a rdf:type property that specifies the Knora resource class.
   * The root resource MAY be an uri resource or a blank node resource.
   * The ResourceAccessors trait provides some common methods to access the properties of the root resource.
   */
  private trait ResourceAccessors {
    def resource: Resource

    def resourceClassIri: ResourceClassIri
    def resourceClassSmartIri: SmartIri = resourceClassIri.smartIri

    // accessor methods for various properties of the root resource
    def deleteComment: IO[String, Option[String]]               = ZIO.fromEither(resource.objectStringOption(DeleteComment))
    def deleteDate: IO[String, Option[Instant]]                 = instantOption(resource, DeleteDate)
    def hasPermissionsOption: IO[String, Option[String]]        = ZIO.fromEither(resource.objectStringOption(HasPermissions))
    def creationDate: IO[String, Option[Instant]]               = instantOption(resource, CreationDate)
    def lastModificationDateOption: IO[String, Option[Instant]] = instantOption(resource, LastModificationDate)
    def newModificationDateOption: IO[String, Option[Instant]]  = instantOption(resource, NewModificationDate)
    def rdfsLabelOption: IO[String, Option[String]]             = ZIO.fromEither(resource.objectStringOption(Rdfs.Label))
  }

  private case class RootUriResource(resource: Resource, resourceIri: ResourceIri, resourceClassIri: ResourceClassIri)
      extends ResourceAccessors {
    def resourceIriStr: String = resourceIri.smartIri.toIri
    def shortcode: Shortcode   = resourceIri.shortcode
  }
  private object RootUriResource {
    def fromJsonLd(str: String): ZIO[Scope, String, RootUriResource] =
      for {
        r   <- RootResource.fromJsonLd(str)
        iri <- ZIO.fromOption(r.resourceIri).orElseFail("No resource IRI found")
      } yield RootUriResource(r.resource, iri, r.resourceClassIri)
  }

  private case class RootResource(
    resource: Resource,
    resourceIri: Option[ResourceIri],
    resourceClassIri: ResourceClassIri,
  ) extends ResourceAccessors
  private object RootResource {
    def fromJsonLd(str: String): ZIO[Scope, String, RootResource] =
      for {
        model    <- ModelOps.fromJsonLd(str)
        resource <- ZIO.fromEither(model.singleRootResource)
        resourceIriOption <-
          ZIO
            .foreach(resource.uri)(
              converter.asSmartIri(_).mapError(_.getMessage).flatMap(iri => ZIO.fromEither(KResourceIri.from(iri))),
            )
        resourceClassIri <- resourceClassIri(resource)
      } yield RootResource(resource, resourceIriOption, resourceClassIri)

    private def resourceClassIri(r: Resource): IO[String, ResourceClassIri] = ZIO
      .fromOption(r.rdfsType)
      .orElseFail("No root resource class IRI found")
      .flatMap(converter.asSmartIri(_).mapError(_.getMessage))
      .flatMap(iri => ZIO.fromEither(KResourceClassIri.fromApiV2Complex(iri)))
  }

  def updateResourceMetadataRequestV2(
    str: String,
    requestingUser: User,
    uuid: UUID,
  ): IO[String, UpdateResourceMetadataRequestV2] = ZIO.scoped {
    for {
      r                    <- RootUriResource.fromJsonLd(str)
      label                <- r.rdfsLabelOption
      permissions          <- r.hasPermissionsOption
      lastModificationDate <- r.lastModificationDateOption
      newModificationDate  <- r.newModificationDateOption
      _ <- ZIO
             .fail("No updated resource metadata provided")
             .when(label.isEmpty && permissions.isEmpty && newModificationDate.isEmpty)
    } yield UpdateResourceMetadataRequestV2(
      r.resourceIriStr,
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
      r                    <- RootUriResource.fromJsonLd(str)
      deleteComment        <- r.deleteComment
      deleteDate           <- r.deleteDate
      lastModificationDate <- r.lastModificationDateOption
    } yield DeleteOrEraseResourceRequestV2(
      r.resourceIriStr,
      r.resourceClassSmartIri,
      deleteComment,
      deleteDate,
      lastModificationDate,
      false,
      requestingUser,
      uuid,
    )
  }

  private def instantOption(r: Resource, p: Property) =
    ZIO.fromEither(r.objectDataTypeOption(p, Xsd.DateTimeStamp)).flatMap { option =>
      ZIO.foreach(option)(dateStr => ZIO.fromEither(parseXsdDateTimeStamp(dateStr)))
    }

  def deleteValueV2FromJsonLd(str: String): IO[String, DeleteValueV2] = ZIO.scoped {
    for {
      r                  <- RootUriResource.fromJsonLd(str)
      valueStatement     <- valueStatement(r.resource)
      valueResource       = valueStatement.getObject.asResource()
      valueIri           <- valueIri(valueResource).someOrFail("The value IRI is required")
      valueTypeIri       <- valueType(valueResource)
      propertyIri        <- valuePropertyIri(valueStatement)
      valueDeleteDate    <- instantOption(valueResource, DeleteDate)
      valueDeleteComment <- ZIO.fromEither(valueResource.objectStringOption(DeleteComment))
    } yield DeleteValueV2(
      r.resourceIriStr,
      r.resourceClassSmartIri,
      propertyIri.smartIri,
      valueIri.smartIri.toIri,
      valueTypeIri,
      valueDeleteComment,
      valueDeleteDate,
    )
  }

  def createResourceRequestV2(
    str: String,
    ingestState: AssetIngestState,
    requestingUser: User,
    uuid: UUID,
  ): IO[String, CreateResourceRequestV2] = ZIO.scoped {
    for {
      r            <- RootResource.fromJsonLd(str)
      permissions  <- r.hasPermissionsOption
      creationDate <- r.creationDate
      label        <- r.rdfsLabelOption.someOrFail("A Resource must have an rdfs:label")
      project      <- attachedToProject(r.resource)
      _ <- ZIO
             .fail("Resource IRI and project IRI must reference the same project")
             .when(r.resourceIri.exists(_.shortcode != project.getShortcode))
      attachedToUser <- attachedToUser(r.resource, requestingUser, project.projectIri)
      values         <- extractValues(r.resource, project.getShortcode, ingestState)
    } yield CreateResourceRequestV2(
      CreateResourceV2(
        r.resourceIri.map(_.smartIri),
        r.resourceClassSmartIri,
        label,
        values,
        project,
        permissions,
        creationDate,
      ),
      attachedToUser,
      uuid,
      ingestState,
    )
  }

  private def extractValues(
    r: Resource,
    shortcode: Shortcode,
    ingestState: AssetIngestState,
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
      .foreach(valueStatements)(valueStatementAsContent(_, shortcode, ingestState))
      .map(_.groupMap(_._1.smartIri)(_._2))

  private def valueStatementAsContent(
    statement: Statement,
    shortcode: Shortcode,
    ingestState: AssetIngestState,
  ): IO[String, (PropertyIri, CreateValueInNewResourceV2)] =
    val valueResource = statement.getObject.asResource()
    for {
      typ                     <- ZIO.fromEither(valueResource.rdfsType.toRight("No rdf:type found for value."))
      filename                <- ZIO.fromEither(valueFileValueFilename(valueResource))
      cnt                     <- getValueContent(typ, valueResource, filename, shortcode, ingestState)
      propertyIri             <- valuePropertyIri(statement)
      customValueIri          <- valueIri(valueResource)
      customValueUuid         <- ZIO.fromEither(valueHasUuid(valueResource))
      customValueCreationDate <- instantOption(valueResource, ValueCreationDate)
      permissions             <- ZIO.fromEither(valuePermissions(valueResource))
    } yield (
      propertyIri,
      CreateValueInNewResourceV2(
        cnt,
        customValueIri.map(_.smartIri),
        customValueUuid,
        customValueCreationDate,
        permissions,
      ),
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
          s"You are logged in as ${requestingUser.username}, but only a system administrator or project administrator can perform an operation as another user",
        )
      case _ => userService.findUserByIri(userIri).orDie.someOrFail(s"User '${userIri.value}' not found")
    }

  def attachedToProject(r: Resource): IO[String, Project] =
    for {
      projectIri <- ZIO.fromEither(r.objectUri(AttachedToProject)).flatMap(iri => ZIO.fromEither(ProjectIri.from(iri)))
      project    <- projectService.findById(projectIri).orDie.someOrFail(s"Project ${projectIri.value} not found")
    } yield project

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

  def updateValueV2fromJsonLd(str: String, ingestState: AssetIngestState): IO[String, UpdateValueV2] =
    ZIO.scoped {
      for {
        r                      <- RootUriResource.fromJsonLd(str)
        valueStatement         <- valueStatement(r.resource)
        valuePropertyIri       <- valuePropertyIri(valueStatement)
        valueResource           = valueStatement.getObject.asResource()
        valueType              <- valueType(valueResource)
        valueIri               <- valueIri(valueResource).someOrFail("The value IRI is required")
        newValueVersionIri     <- newValueVersionIri(valueResource, valueIri)
        valueCreationDate      <- instantOption(valueResource, ValueCreationDate)
        valuePermissions       <- ZIO.fromEither(valuePermissions(valueResource))
        valueFileValueFilename <- ZIO.fromEither(valueFileValueFilename(valueResource))
        valueContent <-
          getValueContent(valueType.toString, valueResource, valueFileValueFilename, r.shortcode, ingestState)
            .map(Some(_))
            .orElse(ZIO.none)
        updateValue <- valueContent match
                         case Some(valueContentV2) =>
                           ZIO.succeed(
                             UpdateValueContentV2(
                               r.resourceIriStr,
                               r.resourceClassSmartIri,
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
                                   r.resourceIriStr,
                                   r.resourceClassSmartIri,
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

  def createValueV2FromJsonLd(str: String, ingestState: AssetIngestState): IO[String, CreateValueV2] =
    ZIO.scoped {
      for {
        r                      <- RootUriResource.fromJsonLd(str)
        valueStatement         <- valueStatement(r.resource)
        valuePropertyIri       <- valuePropertyIri(valueStatement)
        valueResource          <- ZIO.fromEither(valueStatement.objectAsResource())
        valueIri               <- valueIri(valueResource)
        valueUuid              <- ZIO.fromEither(valueHasUuid(valueResource))
        valueCreationDate      <- instantOption(valueResource, ValueCreationDate)
        valuePermissions       <- ZIO.fromEither(valuePermissions(valueResource))
        valueFileValueFilename <- ZIO.fromEither(valueFileValueFilename(valueResource))
        valueType              <- valueType(valueResource)
        valueContent <-
          getValueContent(valueType.toString, valueResource, valueFileValueFilename, r.shortcode, ingestState)
      } yield CreateValueV2(
        r.resourceIriStr,
        r.resourceClassSmartIri,
        valuePropertyIri.smartIri,
        valueContent,
        valueIri.map(_.smartIri),
        valueUuid,
        valueCreationDate,
        valuePermissions,
        ingestState,
      )
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
      .flatMap(iri => ZIO.fromEither(PropertyIri.fromApiV2Complex(iri)))

  private def valueType(resource: Resource) = ZIO
    .fromEither(resource.rdfsType.toRight("No rdf:type found for value."))
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

  private def valuePermissions(valueResource: Resource): Either[String, Option[String]] =
    valueResource.objectStringOption(HasPermissions)

  private def valueFileValueFilename(valueResource: Resource): Either[String, Option[String]] =
    valueResource.objectStringOption(FileValueHasFilename)

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
