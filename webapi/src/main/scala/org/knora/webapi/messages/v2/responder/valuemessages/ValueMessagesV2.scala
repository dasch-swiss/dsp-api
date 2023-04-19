/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import zio._
import java.time.Instant
import java.util.UUID

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import dsp.errors.NotImplementedException
import dsp.valueobjects.IriErrorMessages
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex._
import org.knora.webapi.messages.ResponderRequest.KnoraRequestV2
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages.GetFileMetadataRequest
import org.knora.webapi.messages.store.sipimessages.GetFileMetadataResponse
import org.knora.webapi.messages.util.PermissionUtilADM.EntityPermission
import org.knora.webapi.messages.util._
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2.TextWithStandoffTagsV2
import org.knora.webapi.messages.util.standoff.XMLUtil
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.routing.RouteUtilZ

/**
 * A tagging trait for requests handled by [[org.knora.webapi.responders.v2.ValuesResponderV2]].
 */
sealed trait ValuesResponderRequestV2 extends KnoraRequestV2 with RelayedMessage

/**
 * Requests the creation of a value.
 *
 * @param createValue          a [[CreateValueV2]] representing the value to be created. A successful response will be
 *                             a [[CreateValueResponseV2]].
 * @param requestingUser       the user making the request.
 * @param apiRequestID         the API request ID.
 */
case class CreateValueRequestV2(
  createValue: CreateValueV2,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends ValuesResponderRequestV2

/**
 * Constructs [[CreateValueRequestV2]] instances based on JSON-LD input.
 */
object CreateValueRequestV2 {

  /**
   * Converts JSON-LD input to a [[CreateValueRequestV2]].
   *
   * @param jsonLDDocument       the JSON-LD input.
   * @param apiRequestID         the UUID of the API request.
   * @param requestingUser       the user making the request.
   * @return a case class instance representing the input.
   */
  def fromJsonLd(
    jsonLDDocument: JsonLDDocument,
    apiRequestID: UUID,
    requestingUser: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, CreateValueRequestV2] =
    ZIO.serviceWithZIO[StringFormatter] { implicit stringFormatter =>
      for {
        // Get the IRI of the resource that the value is to be created in.
        resourceIri <- ZIO
                         .attempt(jsonLDDocument.requireIDAsKnoraDataIri)
                         .flatMap(RouteUtilZ.ensureIsKnoraResourceIri)

        // Get the resource class.
        resourceClassIri <- ZIO.attempt(jsonLDDocument.requireTypeAsKnoraTypeIri)

        // Get the resource property and the value to be created.
        createValue <-
          jsonLDDocument.requireResourcePropertyValue match {
            case (propertyIri: SmartIri, jsonLdObject: JsonLDObject) =>
              for {
                valueContent <- ValueContentV2.fromJsonLdObject(jsonLdObject, requestingUser)

                // Get and validate the custom value IRI if provided.
                maybeCustomValueIri <- ZIO.attempt(
                                         jsonLdObject.maybeIDAsKnoraDataIri.map { definedNewIri =>
                                           stringFormatter.validateCustomValueIri(
                                             definedNewIri,
                                             resourceIri.getProjectCode.get,
                                             resourceIri.getResourceID.get
                                           )
                                         }
                                       )

                // Get the custom value UUID if provided.
                maybeCustomUUID <- ZIO.attempt(jsonLdObject.maybeUUID(ValueHasUUID))

                // Get the value's creation date.
                // TODO: creationDate for values is a bug, and will not be supported in future. Use valueCreationDate instead.
                maybeCreationDate <- ZIO.attempt(
                                       jsonLdObject
                                         .maybeDatatypeValueInObject(
                                           key = ValueCreationDate,
                                           expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                                           validationFun = (s, errorFun) =>
                                             ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun)
                                         )
                                         .orElse(
                                           jsonLdObject
                                             .maybeDatatypeValueInObject(
                                               key = CreationDate,
                                               expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                                               validationFun = (s, errorFun) =>
                                                 ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun)
                                             )
                                         )
                                     )

                maybePermissions <-
                  ZIO.attempt(
                    jsonLdObject.maybeStringWithValidation(HasPermissions, stringFormatter.toSparqlEncodedString)
                  )
              } yield CreateValueV2(
                resourceIri = resourceIri.toString,
                resourceClassIri = resourceClassIri,
                propertyIri = propertyIri,
                valueContent = valueContent,
                valueIri = maybeCustomValueIri,
                valueUUID = maybeCustomUUID,
                valueCreationDate = maybeCreationDate,
                permissions = maybePermissions
              )
          }
      } yield CreateValueRequestV2(createValue, requestingUser, apiRequestID)
    }
}

/**
 * Represents a successful response to a [[CreateValueRequestV2]].
 *
 * @param valueIri          the IRI of the value that was created.
 * @param valueType         the type of the value that was created.
 * @param valueUUID         the value's UUID.
 * @param valueCreationDate the value's creationDate
 * @param projectADM        the project in which the value was created.
 */
case class CreateValueResponseV2(
  valueIri: IRI,
  valueType: SmartIri,
  valueUUID: UUID,
  valueCreationDate: Instant,
  projectADM: ProjectADM
) extends KnoraJsonLDResponseV2
    with UpdateResultInProject {
  override def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDDocument = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    if (targetSchema != ApiV2Complex) {
      throw AssertionException(s"CreateValueResponseV2 can only be returned in the complex schema")
    }

    JsonLDDocument(
      body = JsonLDObject(
        Map(
          JsonLDKeywords.ID   -> JsonLDString(valueIri),
          JsonLDKeywords.TYPE -> JsonLDString(valueType.toOntologySchema(ApiV2Complex).toString),
          ValueHasUUID        -> JsonLDString(stringFormatter.base64EncodeUuid(valueUUID)),
          ValueCreationDate -> JsonLDUtil.datatypeValueToJsonLDObject(
            value = valueCreationDate.toString,
            datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri
          )
        )
      ),
      context = JsonLDUtil.makeContext(
        fixedPrefixes = Map(
          OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> KnoraApiV2PrefixExpansion
        )
      )
    )
  }
}

/**
 * Requests an update to a value, i.e. the creation of a new version of an existing value.
 *
 * @param updateValue          an [[UpdateValueV2]] representing the new version of the value. A successful response will be
 *                             an [[UpdateValueResponseV2]].
 * @param requestingUser       the user making the request.
 * @param apiRequestID         the API request ID.
 */
case class UpdateValueRequestV2(
  updateValue: UpdateValueV2,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends ValuesResponderRequestV2

/**
 * Constructs [[UpdateValueRequestV2]] instances based on JSON-LD input.
 */
object UpdateValueRequestV2 {

  /**
   * Converts JSON-LD input to a [[CreateValueRequestV2]].
   *
   * @param jsonLdDocument       the JSON-LD input.
   * @param apiRequestID         the UUID of the API request.
   * @param requestingUser       the user making the request.
   * @return a case class instance representing the input.
   */
  def fromJsonLd(
    jsonLdDocument: JsonLDDocument,
    apiRequestID: UUID,
    requestingUser: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, UpdateValueRequestV2] = ZIO.serviceWithZIO[StringFormatter] {
    implicit stringFormatter =>
      def makeUpdateValueContentV2(
        resourceIri: SmartIri,
        resourceClassIri: SmartIri,
        propertyIri: SmartIri,
        jsonLDObject: JsonLDObject,
        valueIri: SmartIri,
        maybeValueCreationDate: Option[Instant],
        maybeNewIri: Option[SmartIri]
      ) = ZIO.serviceWithZIO[StringFormatter] { implicit stringFormatter =>
        for {
          valueContent <- ValueContentV2.fromJsonLdObject(jsonLDObject, requestingUser)
          maybePermissions <-
            ZIO.attempt(
              jsonLDObject.maybeStringWithValidation(HasPermissions, stringFormatter.toSparqlEncodedString)
            )
        } yield UpdateValueContentV2(
          resourceIri = resourceIri.toString,
          resourceClassIri = resourceClassIri,
          propertyIri = propertyIri,
          valueIri = valueIri.toString,
          valueContent = valueContent,
          permissions = maybePermissions,
          valueCreationDate = maybeValueCreationDate,
          newValueVersionIri = maybeNewIri
        )
      }

      def makeUpdateValuePermissionsV2(
        resourceIri: SmartIri,
        resourceClassIri: SmartIri,
        propertyIri: SmartIri,
        jsonLDObject: JsonLDObject,
        valueIri: SmartIri,
        maybeValueCreationDate: Option[Instant],
        maybeNewIri: Option[SmartIri]
      ) = ZIO.serviceWithZIO[StringFormatter] { implicit stringFormatter =>
        // Yes. This is a request to change the value's permissions.
        for {
          valueType <- ZIO.attempt(
                         jsonLDObject.requireStringWithValidation(
                           JsonLDKeywords.TYPE,
                           stringFormatter.toSmartIriWithErr
                         )
                       )
          permissions <- ZIO.attempt(
                           jsonLDObject.requireStringWithValidation(
                             HasPermissions,
                             stringFormatter.toSparqlEncodedString
                           )
                         )
        } yield UpdateValuePermissionsV2(
          resourceIri = resourceIri.toString,
          resourceClassIri = resourceClassIri,
          propertyIri = propertyIri,
          valueIri = valueIri.toString,
          valueType = valueType,
          permissions = permissions,
          valueCreationDate = maybeValueCreationDate,
          newValueVersionIri = maybeNewIri
        )
      }

      for {
        // Get the IRI of the resource that the value is to be created in.
        resourceIri <- ZIO
                         .attempt(jsonLdDocument.requireIDAsKnoraDataIri)
                         .flatMap(RouteUtilZ.ensureIsKnoraResourceIri)
        // Get the resource class.
        resourceClassIri <- ZIO.attempt(jsonLdDocument.requireTypeAsKnoraTypeIri)

        // Get the resource property and the new value version.
        updateValue <- ZIO.attempt(jsonLdDocument.requireResourcePropertyValue).flatMap {
                         case (propertyIri: SmartIri, jsonLDObject: JsonLDObject) =>
                           // Get the custom value creation date, if provided.

                           for {
                             valueIri <- ZIO.attempt(jsonLDObject.requireIDAsKnoraDataIri)
                             // Aside from the value's ID and type and the optional predicates above, does the value object just
                             otherValuePredicates: Set[IRI] = jsonLDObject.value.keySet -- Set(
                                                                JsonLDKeywords.ID,
                                                                JsonLDKeywords.TYPE,
                                                                ValueCreationDate,
                                                                NewValueVersionIri
                                                              )
                             maybeValueCreationDate <- ZIO.attempt(
                                                         jsonLDObject.maybeDatatypeValueInObject(
                                                           key = ValueCreationDate,
                                                           expectedDatatype =
                                                             OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                                                           validationFun = (s, errorFun) =>
                                                             ValuesValidator
                                                               .xsdDateTimeStampToInstant(s)
                                                               .getOrElse(errorFun)
                                                         )
                                                       )
                             // Get and validate the custom new value version IRI, if provided.

                             maybeNewIri <-
                               ZIO
                                 .attempt(
                                   jsonLDObject
                                     .maybeIriInObject(NewValueVersionIri, stringFormatter.toSmartIriWithErr)
                                 )
                                 .flatMap(smartIriMaybe =>
                                   ZIO.foreach(smartIriMaybe) { definedNewIri =>
                                     if (definedNewIri == valueIri) {
                                       ZIO.fail(
                                         BadRequestException(
                                           s"The IRI of a new value version cannot be the same as the IRI of the current version"
                                         )
                                       )
                                     } else {
                                       ZIO.attempt(
                                         stringFormatter.validateCustomValueIri(
                                           customValueIri = definedNewIri,
                                           projectCode = valueIri.getProjectCode.get,
                                           resourceID = valueIri.getResourceID.get
                                         )
                                       )
                                     }
                                   }
                                 )

                             value <- if (otherValuePredicates == Set(HasPermissions)) {
                                        makeUpdateValuePermissionsV2(
                                          resourceIri,
                                          resourceClassIri,
                                          propertyIri,
                                          jsonLDObject,
                                          valueIri,
                                          maybeValueCreationDate,
                                          maybeNewIri
                                        )
                                      } else {
                                        makeUpdateValueContentV2(
                                          resourceIri,
                                          resourceClassIri,
                                          propertyIri,
                                          jsonLDObject,
                                          valueIri,
                                          maybeValueCreationDate,
                                          maybeNewIri
                                        )
                                      }
                           } yield value
                       }
      } yield UpdateValueRequestV2(updateValue, requestingUser, apiRequestID)
  }
}

/**
 * Represents a successful response to an [[UpdateValueRequestV2]].
 *
 * @param valueIri   the IRI of the value version that was created.
 * @param valueType  the type of the value that was updated.
 * @param valueUUID  the value's UUID.
 * @param projectADM the project in which the value was updated.
 */
case class UpdateValueResponseV2(valueIri: IRI, valueType: SmartIri, valueUUID: UUID, projectADM: ProjectADM)
    extends KnoraJsonLDResponseV2
    with UpdateResultInProject {
  override def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDDocument = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    if (targetSchema != ApiV2Complex) {
      throw AssertionException(s"UpdateValueResponseV2 can only be returned in the complex schema")
    }

    JsonLDDocument(
      body = JsonLDObject(
        Map(
          JsonLDKeywords.ID   -> JsonLDString(valueIri),
          JsonLDKeywords.TYPE -> JsonLDString(valueType.toOntologySchema(ApiV2Complex).toString),
          ValueHasUUID        -> JsonLDString(stringFormatter.base64EncodeUuid(valueUUID))
        )
      ),
      context = JsonLDUtil.makeContext(
        fixedPrefixes = Map(
          OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> KnoraApiV2PrefixExpansion
        )
      )
    )
  }
}

/**
 * Requests that a value is marked as deleted. A successful response will be a [[SuccessResponseV2]].
 *
 * @param resourceIri          the IRI of the containing resource.
 * @param resourceClassIri     the IRI of the resource class.
 * @param propertyIri          the IRI of the property pointing to the value to be marked as deleted.
 * @param valueIri             the IRI of the value to be marked as deleted.
 * @param valueTypeIri         the IRI of the value class.
 * @param deleteComment        an optional comment explaining why the value is being marked as deleted.
 * @param deleteDate           an optional timestamp indicating when the value was deleted. If not supplied,
 *                             the current time will be used.
 * @param requestingUser       the user making the request.
 * @param apiRequestID         the API request ID.
 */
case class DeleteValueRequestV2(
  resourceIri: IRI,
  resourceClassIri: SmartIri,
  propertyIri: SmartIri,
  valueIri: IRI,
  valueTypeIri: SmartIri,
  deleteComment: Option[String] = None,
  deleteDate: Option[Instant] = None,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends ValuesResponderRequestV2

object DeleteValueRequestV2 {

  /**
   * Converts JSON-LD input into a case class instance.
   *
   * @param jsonLDDocument       the JSON-LD input.
   * @param apiRequestID         the UUID of the API request.
   * @param requestingUser       the user making the request.
   * @return a case class instance representing the input.
   */
  def fromJsonLd(
    jsonLDDocument: JsonLDDocument,
    apiRequestID: UUID,
    requestingUser: UserADM
  ): ZIO[StringFormatter, Throwable, DeleteValueRequestV2] =
    ZIO.serviceWithZIO[StringFormatter] { implicit stringFormatter =>
      ZIO.attempt(jsonLDDocument.requireResourcePropertyValue).flatMap {
        case (propertyIri: SmartIri, jsonLDObject: JsonLDObject) =>
          for {
            resourceIri <- ZIO.attempt(jsonLDDocument.requireIDAsKnoraDataIri)
            _ <- ZIO
                   .fail(BadRequestException(s"Invalid resource IRI: <$resourceIri>"))
                   .when(!resourceIri.isKnoraResourceIri)
            resourceClassIri <- ZIO.attempt(jsonLDDocument.requireTypeAsKnoraTypeIri)
            valueIri         <- ZIO.attempt(jsonLDObject.requireIDAsKnoraDataIri)
            _                <- ZIO.fail(BadRequestException(s"Invalid value IRI: <$valueIri>")).when(!valueIri.isKnoraValueIri)
            _ <- ZIO
                   .fail(BadRequestException(IriErrorMessages.UuidVersionInvalid))
                   .when(
                     stringFormatter.hasUuidLength(valueIri.toString.split("/").last)
                       && !stringFormatter.isUuidSupported(valueIri.toString)
                   )
            valueTypeIri <- ZIO.attempt(jsonLDObject.requireTypeAsKnoraApiV2ComplexTypeIri)
            deleteComment <- ZIO.attempt(
                               jsonLDObject.maybeStringWithValidation(
                                 OntologyConstants.KnoraApiV2Complex.DeleteComment,
                                 stringFormatter.toSparqlEncodedString
                               )
                             )
            deleteDate <- ZIO.attempt(
                            jsonLDObject.maybeDatatypeValueInObject(
                              key = OntologyConstants.KnoraApiV2Complex.DeleteDate,
                              expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                              validationFun =
                                (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun)
                            )
                          )
          } yield DeleteValueRequestV2(
            resourceIri = resourceIri.toString,
            resourceClassIri = resourceClassIri,
            propertyIri = propertyIri,
            valueIri = valueIri.toString,
            valueTypeIri = valueTypeIri,
            deleteComment = deleteComment,
            deleteDate = deleteDate,
            requestingUser = requestingUser,
            apiRequestID = apiRequestID
          )
      }
    }
}

/**
 * Requests SPARQL for creating multiple values in a new, empty resource. The resource ''must'' be a new, empty
 * resource, i.e. it must have no values. This message is used only internally by Knora, and is not part of the Knora
 * v1 API. All pre-update checks must already have been performed before this message is sent. Specifically, the
 * sender must ensure that:
 *
 * - The requesting user has permission to add values to the resource.
 * - Each submitted value is consistent with the `knora-base:objectClassConstraint` of the property that is supposed
 * to point to it.
 * - The resource class has a suitable cardinality for each submitted value.
 * - All required values are provided.
 * - Redundant values are not submitted.
 * - Any custom permissions in values have been validated and correctly formatted.
 * - The target resources of link values and standoff links exist, if they are expected to exist.
 * - The list nodes referred to by list values exist.
 *
 * A successful response will be a [[GenerateSparqlToCreateMultipleValuesResponseV2]].
 *
 * @param resourceIri    the IRI of the resource in which values are to be created.
 * @param values         a map of property IRIs to the values to be added for each property.
 * @param creationDate   an xsd:dateTimeStamp that will be attached to the values.
 * @param requestingUser the user that is creating the values.
 */
case class GenerateSparqlToCreateMultipleValuesRequestV2(
  resourceIri: IRI,
  values: Map[SmartIri, Seq[GenerateSparqlForValueInNewResourceV2]],
  creationDate: Instant,
  requestingUser: UserADM
) extends ValuesResponderRequestV2 {
  lazy val flatValues: Iterable[GenerateSparqlForValueInNewResourceV2] = values.values.flatten
}

case class GenerateSparqlForValueInNewResourceV2(
  valueContent: ValueContentV2,
  customValueIri: Option[SmartIri],
  customValueUUID: Option[UUID],
  customValueCreationDate: Option[Instant],
  permissions: String
) extends IOValueV2

/**
 * Represents a response to a [[GenerateSparqlToCreateMultipleValuesRequestV2]], providing a string that can be
 * included in the `INSERT DATA` clause of a SPARQL update operation to create the requested values.
 *
 * @param insertSparql     a string containing statements that must be inserted into the INSERT clause of the SPARQL
 *                         update that will create the values.
 * @param unverifiedValues a map of property IRIs to [[UnverifiedValueV2]] objects describing
 *                         the values that should have been created.
 * @param hasStandoffLink  `true` if the property `knora-base:hasStandoffLinkToValue` was automatically added.
 */
case class GenerateSparqlToCreateMultipleValuesResponseV2(
  insertSparql: String,
  unverifiedValues: Map[SmartIri, Seq[UnverifiedValueV2]],
  hasStandoffLink: Boolean
)

/**
 * The value of a Knora property in the context of some particular input or output operation.
 * Any implementation of `IOValueV2` is an API operation-specific wrapper of a `ValueContentV2`.
 */
trait IOValueV2 {
  def valueContent: ValueContentV2
}

/**
 * Provides information about the deletion of a resource or value.
 *
 * @param deleteDate         the date when the resource or value was deleted.
 * @param maybeDeleteComment the reason why the resource or value was deleted.
 */
case class DeletionInfo(deleteDate: Instant, maybeDeleteComment: Option[String]) {
  def toJsonLDFields(targetSchema: ApiV2Schema): Map[IRI, JsonLDValue] = {
    if (targetSchema != ApiV2Complex) {
      throw AssertionException("DeletionInfo is available in JSON-LD only in the complex schema")
    }

    val maybeDeleteCommentStatement = maybeDeleteComment.map { deleteComment =>
      DeleteComment -> JsonLDString(deleteComment)
    }

    Map(
      IsDeleted -> JsonLDBoolean(true),
      DeleteDate -> JsonLDObject(
        Map(
          JsonLDKeywords.TYPE  -> JsonLDString(OntologyConstants.Xsd.DateTimeStamp),
          JsonLDKeywords.VALUE -> JsonLDString(deleteDate.toString)
        )
      )
    ) ++ maybeDeleteCommentStatement
  }
}

/**
 * Represents a Knora value as read from the triplestore.
 */
sealed trait ReadValueV2 extends IOValueV2 {

  /**
   * The IRI of the value.
   */
  def valueIri: IRI

  /**
   * The user that created the value.
   */
  def attachedToUser: IRI

  /**
   * The value's permissions.
   */
  def permissions: String

  /**
   * The permission that the requesting user has on the value.
   */
  def userPermission: EntityPermission

  /**
   * The date when the value was created.
   */
  def valueCreationDate: Instant

  /**
   * The UUID shared by all the versions of this value.
   */
  def valueHasUUID: UUID

  /**
   * The content of the value.
   */
  def valueContent: ValueContentV2

  /**
   * The IRI of the previous version of this value. Not returned in API responses, but needed
   * here for testing.
   */
  def previousValueIri: Option[IRI]

  /**
   * If the value has been marked as deleted, information about its deletion.
   */
  def deletionInfo: Option[DeletionInfo]

  /**
   * Converts this value to the specified ontology schema.
   *
   * @param targetSchema the schema that the value should be converted to.
   */
  def toOntologySchema(targetSchema: ApiV2Schema): ReadValueV2

  /**
   * Converts this value to JSON-LD.
   *
   * @param targetSchema the target schema.
   * @return a JSON-LD representation of this value.
   */
  def toJsonLD(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val valueContentAsJsonLD = valueContent.toJsonLDValue(
      targetSchema = targetSchema,
      projectADM = projectADM,
      appConfig = appConfig,
      schemaOptions = schemaOptions
    )

    // In the complex schema, add the value's IRI and type to the JSON-LD object that represents it.
    targetSchema match {
      case ApiV2Complex =>
        // In the complex schema, the value must be represented as a JSON-LD object.
        valueContentAsJsonLD match {
          case jsonLDObject: JsonLDObject =>
            // Add the value's metadata.

            val valueSmartIri = valueIri.toSmartIri

            val requiredMetadata = Map(
              JsonLDKeywords.ID   -> JsonLDString(valueIri),
              JsonLDKeywords.TYPE -> JsonLDString(valueContent.valueType.toString),
              AttachedToUser      -> JsonLDUtil.iriToJsonLDObject(attachedToUser),
              HasPermissions      -> JsonLDString(permissions),
              UserHasPermission   -> JsonLDString(userPermission.toString),
              ValueCreationDate -> JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueCreationDate.toString,
                datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri
              ),
              ValueHasUUID -> JsonLDString(
                stringFormatter.base64EncodeUuid(valueHasUUID)
              ),
              ArkUrl -> JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueSmartIri.fromValueIriToArkUrl(valueUUID = valueHasUUID),
                datatype = OntologyConstants.Xsd.Uri.toSmartIri
              ),
              VersionArkUrl -> JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueSmartIri
                  .fromValueIriToArkUrl(valueUUID = valueHasUUID, maybeTimestamp = Some(valueCreationDate)),
                datatype = OntologyConstants.Xsd.Uri.toSmartIri
              )
            )

            val valueHasCommentAsJsonLD: Option[(IRI, JsonLDValue)] = valueContent.comment.map { definedComment =>
              ValueHasComment -> JsonLDString(definedComment)
            }

            val deletionInfoAsJsonLD: Map[IRI, JsonLDValue] = deletionInfo match {
              case Some(definedDeletionInfo) => definedDeletionInfo.toJsonLDFields(ApiV2Complex)
              case None                      => Map.empty[IRI, JsonLDValue]
            }

            JsonLDObject(jsonLDObject.value ++ requiredMetadata ++ valueHasCommentAsJsonLD ++ deletionInfoAsJsonLD)

          case other =>
            throw AssertionException(
              s"Expected value $valueIri to be a represented as a JSON-LD object in the complex schema, but found $other"
            )
        }

      case ApiV2Simple => valueContentAsJsonLD
    }
  }

  /**
   * Return a `knora-base:DeletedValue` representation of the present value.
   *
   * @return A ReadValueV2 object identical to the current one (including the IRI),
   *         but with valueContent of type [[DeletedValueContentV2]].
   */
  def asDeletedValue(): ReadValueV2 =
    ReadOtherValueV2(
      valueIri = this.valueIri,
      attachedToUser = this.attachedToUser,
      permissions = this.permissions,
      userPermission = this.userPermission,
      valueCreationDate = this.valueCreationDate,
      valueHasUUID = this.valueHasUUID,
      valueContent = DeletedValueContentV2(InternalSchema),
      previousValueIri = this.previousValueIri,
      deletionInfo = this.deletionInfo
    )

}

/**
 * A text value, or a page of standoff markup attached to a text value, as read from the triplestore.
 *
 * @param valueIri                      the IRI of the value.
 * @param attachedToUser                the user that created the value.
 * @param permissions                   the permissions that the value grants to user groups.
 * @param userPermission                the permission that the requesting user has on the value.
 * @param valueHasUUID                  the UUID shared by all the versions of this value.
 * @param valueContent                  the content of the value.
 * @param valueHasMaxStandoffStartIndex if this text value has standoff markup, the highest
 *                                      `knora-base:standoffTagHasEndIndex`
 *                                      used in its standoff tags.
 * @param previousValueIri              the IRI of the previous version of this value. Not returned in API responses, but needed
 *                                      here for testing.
 * @param deletionInfo                  if this value has been marked as deleted, provides the date when it was
 *                                      deleted and the reason why it was deleted.
 */
case class ReadTextValueV2(
  valueIri: IRI,
  attachedToUser: IRI,
  permissions: String,
  userPermission: EntityPermission,
  valueCreationDate: Instant,
  valueHasUUID: UUID,
  valueContent: TextValueContentV2,
  valueHasMaxStandoffStartIndex: Option[Int],
  previousValueIri: Option[IRI],
  deletionInfo: Option[DeletionInfo]
) extends ReadValueV2
    with KnoraReadV2[ReadTextValueV2] {

  /**
   * Converts this value to the specified ontology schema.
   *
   * @param targetSchema the target schema.
   */
  override def toOntologySchema(targetSchema: ApiV2Schema): ReadTextValueV2 =
    copy(valueContent = valueContent.toOntologySchema(targetSchema))

  override def toJsonLD(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue = {
    val valueAsJsonLDValue: JsonLDValue = super.toJsonLD(
      targetSchema = targetSchema,
      projectADM = projectADM,
      appConfig = appConfig,
      schemaOptions = schemaOptions
    )

    // If this is the complex schema and separate standoff has been requested, and the text value has
    // valueHasMaxStandoffStartIndex, add it along with textValueHasMarkup to the metadata returned with the value.
    targetSchema match {
      case ApiV2Complex =>
        if (SchemaOptions.renderMarkupAsStandoff(targetSchema = ApiV2Complex, schemaOptions = schemaOptions)) {
          valueHasMaxStandoffStartIndex match {
            case Some(maxStartIndex) =>
              val valueAsJsonLDObject: JsonLDObject = valueAsJsonLDValue match {
                case jsonLDObject: JsonLDObject => jsonLDObject
                case other =>
                  throw AssertionException(
                    s"Expected value $valueIri to be a represented as a JSON-LD object in the complex schema, but found $other"
                  )
              }

              JsonLDObject(
                valueAsJsonLDObject.value ++ Map(
                  TextValueHasMarkup                -> JsonLDBoolean(true),
                  TextValueHasMaxStandoffStartIndex -> JsonLDInt(maxStartIndex)
                )
              )

            case None => valueAsJsonLDValue
          }
        } else {
          valueAsJsonLDValue
        }

      case ApiV2Simple => valueAsJsonLDValue
    }
  }
}

/**
 * A link value as read from the triplestore.
 *
 * @param valueIri         the IRI of the value.
 * @param attachedToUser   the user that created the value.
 * @param permissions      the permissions that the value grants to user groups.
 * @param userPermission   the permission that the requesting user has on the value.
 * @param valueHasUUID     the UUID shared by all the versions of this value.
 * @param valueContent     the content of the value.
 * @param valueHasRefCount if this is a link value, its reference count. Not returned in API responses, but needed
 *                         here for testing.
 * @param previousValueIri the IRI of the previous version of this value. Not returned in API responses, but needed
 *                         here for testing.
 * @param deletionInfo     if this value has been marked as deleted, provides the date when it was
 *                         deleted and the reason why it was deleted.
 */
case class ReadLinkValueV2(
  valueIri: IRI,
  attachedToUser: IRI,
  permissions: String,
  userPermission: EntityPermission,
  valueCreationDate: Instant,
  valueHasUUID: UUID,
  valueContent: LinkValueContentV2,
  valueHasRefCount: Int,
  previousValueIri: Option[IRI] = None,
  deletionInfo: Option[DeletionInfo]
) extends ReadValueV2
    with KnoraReadV2[ReadLinkValueV2] {

  /**
   * Converts this value to the specified ontology schema.
   *
   * @param targetSchema the target schema.
   */
  override def toOntologySchema(targetSchema: ApiV2Schema): ReadLinkValueV2 =
    copy(valueContent = valueContent.toOntologySchema(targetSchema))
}

/**
 * A non-text, non-link value as read from the triplestore.
 *
 * @param valueIri         the IRI of the value.
 * @param attachedToUser   the user that created the value.
 * @param permissions      the permissions that the value grants to user groups.
 * @param userPermission   the permission that the requesting user has on the value.
 * @param valueHasUUID     the UUID shared by all the versions of this value.
 * @param valueContent     the content of the value.
 * @param previousValueIri the IRI of the previous version of this value. Not returned in API responses, but needed
 *                         here for testing.
 * @param deletionInfo     if this value has been marked as deleted, provides the date when it was
 *                         deleted and the reason why it was deleted.
 */
case class ReadOtherValueV2(
  valueIri: IRI,
  attachedToUser: IRI,
  permissions: String,
  userPermission: EntityPermission,
  valueCreationDate: Instant,
  valueHasUUID: UUID,
  valueContent: ValueContentV2,
  previousValueIri: Option[IRI],
  deletionInfo: Option[DeletionInfo]
) extends ReadValueV2
    with KnoraReadV2[ReadOtherValueV2] {

  /**
   * Converts this value to the specified ontology schema.
   *
   * @param targetSchema the target schema.
   */
  override def toOntologySchema(targetSchema: ApiV2Schema): ReadOtherValueV2 =
    copy(valueContent = valueContent.toOntologySchema(targetSchema))
}

/**
 * Represents a Knora value to be created in an existing resource.
 *
 * @param resourceIri       the resource the new value should be attached to.
 * @param resourceClassIri  the resource class that the client believes the resource belongs to.
 * @param propertyIri       the property of the new value. If the client wants to create a link, this must be a link value property.
 * @param valueContent      the content of the new value. If the client wants to create a link, this must be a [[LinkValueContentV2]].
 * @param valueIri          the optional custom IRI supplied for the value.
 * @param valueUUID         the optional custom UUID supplied for the value.
 * @param valueCreationDate the optional custom creation date supplied for the value. If not supplied,
 *                          the current time will be used.
 * @param permissions       the permissions to be given to the new value. If not provided, these will be taken from defaults.
 */
case class CreateValueV2(
  resourceIri: IRI,
  resourceClassIri: SmartIri,
  propertyIri: SmartIri,
  valueContent: ValueContentV2,
  valueIri: Option[SmartIri] = None,
  valueUUID: Option[UUID] = None,
  valueCreationDate: Option[Instant] = None,
  permissions: Option[String] = None
) extends IOValueV2

/**
 * A trait for classes representing information to be updated in a value.
 */
trait UpdateValueV2 {

  /**
   * The IRI of the resource containing the value.
   */
  val resourceIri: IRI

  /**
   * The external IRI of the resource class.
   */
  val resourceClassIri: SmartIri

  /**
   * The external IRI of the property pointing to the value.
   */
  val propertyIri: SmartIri

  /**
   * The value IRI.
   */
  val valueIri: IRI

  /**
   * A custom value creation date.
   */
  val valueCreationDate: Option[Instant]
}

/**
 * A new version of a value of a Knora property to be created.
 *
 * @param resourceIri        the resource that the current value version is attached to.
 * @param resourceClassIri   the resource class that the client believes the resource belongs to.
 * @param propertyIri        the property that the client believes points to the value. If the value is a link value,
 *                           this must be a link value property.
 * @param valueIri           the IRI of the value to be updated.
 * @param valueContent       the content of the new version of the value.
 * @param permissions        the permissions to be attached to the new value version.
 * @param valueCreationDate  an optional custom creation date to be attached to the new value version. If not provided,
 *                           the current time will be used.
 * @param newValueVersionIri an optional IRI to be used for the new value version. If not provided, a random IRI
 *                           will be generated.
 */
case class UpdateValueContentV2(
  resourceIri: IRI,
  resourceClassIri: SmartIri,
  propertyIri: SmartIri,
  valueIri: IRI,
  valueContent: ValueContentV2,
  permissions: Option[String] = None,
  valueCreationDate: Option[Instant] = None,
  newValueVersionIri: Option[SmartIri] = None
) extends IOValueV2
    with UpdateValueV2

/**
 * New permissions for a value.
 *
 * @param resourceIri        the resource that the current value version is attached to.
 * @param resourceClassIri   the resource class that the client believes the resource belongs to.
 * @param propertyIri        the property that the client believes points to the value. If the value is a link value,
 *                           this must be a link value property.
 * @param valueIri           the IRI of the value to be updated.
 * @param valueType          the IRI of the value type.
 * @param permissions        the permissions to be attached to the new value version.
 * @param valueCreationDate  an optional custom creation date to be attached to the new value version. If not provided,
 *                           the current time will be used.
 * @param newValueVersionIri an optional IRI to be used for the new value version. If not provided, a random IRI
 *                           will be generated.
 */
case class UpdateValuePermissionsV2(
  resourceIri: IRI,
  resourceClassIri: SmartIri,
  propertyIri: SmartIri,
  valueIri: IRI,
  valueType: SmartIri,
  permissions: String,
  valueCreationDate: Option[Instant] = None,
  newValueVersionIri: Option[SmartIri] = None
) extends UpdateValueV2

/**
 * The IRI and content of a new value or value version whose existence in the triplestore needs to be verified.
 *
 * @param newValueIri  the IRI that was assigned to the new value.
 * @param newValueUUID the UUID attached to the new value.
 * @param valueContent the content of the new value (unescaped, as it would be read from the triplestore).
 * @param permissions  the permissions of the new value.
 * @param creationDate the new value's creation date.
 */
case class UnverifiedValueV2(
  newValueIri: IRI,
  newValueUUID: UUID,
  valueContent: ValueContentV2,
  permissions: String,
  creationDate: Instant
)

/**
 * The content of the value of a Knora property.
 */
sealed trait ValueContentV2 extends KnoraContentV2[ValueContentV2] {
  protected implicit def stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  /**
   * The IRI of the value type.
   */
  def valueType: SmartIri

  /**
   * The string representation of this `ValueContentV2`.
   */
  def valueHasString: String

  /**
   * a comment on this [[ValueContentV2]], if any.
   */
  def comment: Option[String]

  /**
   * Converts this value to the specified ontology schema.
   *
   * @param targetSchema the target schema.
   */
  def toOntologySchema(targetSchema: OntologySchema): ValueContentV2

  /**
   * A representation of the `ValueContentV2` as a [[JsonLDValue]].
   *
   * @param targetSchema the API schema to be used.
   * @return a [[JsonLDValue]] that can be used to generate JSON-LD representing this value.
   */
  def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue

  /**
   * Undoes the SPARQL-escaping of strings in this [[ValueContentV2]].
   *
   * @return the same [[ValueContentV2]] with its strings unescaped.
   */
  def unescape: ValueContentV2

  /**
   * Returns `true` if creating this [[ValueContentV2]] as a new value would duplicate the specified other value.
   * This means that if resource `R` has property `P` with value `V1`, and `V1` would duplicate `V2`, the API server
   * should not add another instance of property `P` with value `V2`. It does not necessarily mean that `V1 == V2`.
   *
   * @param that a [[ValueContentV2]] in the same resource, as read from the triplestore.
   * @return `true` if `other` would duplicate `this`.
   */
  def wouldDuplicateOtherValue(that: ValueContentV2): Boolean

  /**
   * Returns `true` if this [[ValueContentV2]] would be redundant as a new version of an existing value. This means
   * that if resource `R` has property `P` with value `V1`, and `V2` would duplicate `V1`, we should not add `V2`
   * as a new version of `V1`. It does not necessarily mean that `V1 == V2`.
   *
   * @param currentVersion the current version of the value, as read from the triplestore.
   * @return `true` if this [[ValueContentV2]] would duplicate `currentVersion`.
   */
  def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean
}

/**
 * Generates instances of value content classes (subclasses of [[ValueContentV2]]) from JSON-LD input.
 */
object ValueContentV2 {

  /**
   * Converts a JSON-LD object to a [[ValueContentV2]].
   *
   * @param jsonLdObject         the JSON-LD object.
   * @param requestingUser       the user making the request.
   * @return a [[ValueContentV2]].
   */
  def fromJsonLdObject(
    jsonLdObject: JsonLDObject,
    requestingUser: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, ValueContentV2] = ZIO.serviceWithZIO[StringFormatter] {
    stringFormatter =>
      for {
        valueType <-
          ZIO.attempt(jsonLdObject.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr))

        valueContent <-
          valueType.toString match {
            case TextValue            => TextValueContentV2.fromJsonLdObject(jsonLdObject, requestingUser)
            case IntValue             => IntegerValueContentV2.fromJsonLdObject(jsonLdObject)
            case DecimalValue         => DecimalValueContentV2.fromJsonLdObject(jsonLdObject, requestingUser)
            case BooleanValue         => BooleanValueContentV2.fromJsonLdObject(jsonLdObject, requestingUser)
            case DateValue            => DateValueContentV2.fromJsonLdObject(jsonLdObject, requestingUser)
            case GeomValue            => GeomValueContentV2.fromJsonLdObject(jsonLdObject)
            case IntervalValue        => IntervalValueContentV2.fromJsonLdObject(jsonLdObject)
            case TimeValue            => TimeValueContentV2.fromJsonLdObject(jsonLdObject)
            case LinkValue            => LinkValueContentV2.fromJsonLdObject(jsonLdObject)
            case ListValue            => HierarchicalListValueContentV2.fromJsonLdObject(jsonLdObject)
            case UriValue             => UriValueContentV2.fromJsonLdObject(jsonLdObject)
            case GeonameValue         => GeonameValueContentV2.fromJsonLdObject(jsonLdObject)
            case ColorValue           => ColorValueContentV2.fromJsonLdObject(jsonLdObject)
            case StillImageFileValue  => StillImageFileValueContentV2.fromJsonLdObject(jsonLdObject, requestingUser)
            case DocumentFileValue    => DocumentFileValueContentV2.fromJsonLdObject(jsonLdObject, requestingUser)
            case TextFileValue        => TextFileValueContentV2.fromJsonLdObject(jsonLdObject, requestingUser)
            case AudioFileValue       => AudioFileValueContentV2.fromJsonLdObject(jsonLdObject, requestingUser)
            case MovingImageFileValue => MovingImageFileValueContentV2.fromJsonLdObject(jsonLdObject, requestingUser)
            case ArchiveFileValue     => ArchiveFileValueContentV2.fromJsonLdObject(jsonLdObject, requestingUser)
            case other                => ZIO.fail(NotImplementedException(s"Parsing of JSON-LD value type not implemented: $other"))
          }

      } yield valueContent
  }
}

/**
 * Represents a Knora date value.
 *
 * @param valueHasStartJDN       the start of the date as JDN.
 * @param valueHasEndJDN         the end of the date as JDN.
 * @param valueHasStartPrecision the precision of the start date.
 * @param valueHasEndPrecision   the precision of the end date.
 * @param valueHasCalendar       the calendar of the date.
 * @param comment                a comment on this [[DateValueContentV2]], if any.
 */
case class DateValueContentV2(
  ontologySchema: OntologySchema,
  valueHasStartJDN: Int,
  valueHasEndJDN: Int,
  valueHasStartPrecision: DatePrecisionV2,
  valueHasEndPrecision: DatePrecisionV2,
  valueHasCalendar: CalendarNameV2,
  comment: Option[String] = None
) extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.DateValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  private lazy val asCalendarDateRange: CalendarDateRangeV2 = {
    val startCalendarDate = CalendarDateV2.fromJulianDayNumber(
      julianDay = valueHasStartJDN,
      precision = valueHasStartPrecision,
      calendarName = valueHasCalendar
    )

    val endCalendarDate = CalendarDateV2.fromJulianDayNumber(
      julianDay = valueHasEndJDN,
      precision = valueHasEndPrecision,
      calendarName = valueHasCalendar
    )

    CalendarDateRangeV2(
      startCalendarDate = startCalendarDate,
      endCalendarDate = endCalendarDate
    )
  }

  // We compute valueHasString instead of taking it from the triplestore, because the
  // string literal in the triplestore isn't in API v2 format.
  override lazy val valueHasString: String = asCalendarDateRange.toString

  override def toOntologySchema(targetSchema: OntologySchema): DateValueContentV2 = copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasString,
          datatype = OntologyConstants.KnoraApiV2Simple.Date.toSmartIri
        )

      case ApiV2Complex =>
        val startCalendarDate: CalendarDateV2 = asCalendarDateRange.startCalendarDate
        val endCalendarDate: CalendarDateV2   = asCalendarDateRange.endCalendarDate

        val startDateAssertions =
          Map(DateValueHasStartYear -> JsonLDInt(startCalendarDate.year)) ++
            startCalendarDate.maybeMonth.map(month => DateValueHasStartMonth -> JsonLDInt(month)) ++
            startCalendarDate.maybeDay.map(day => DateValueHasStartDay -> JsonLDInt(day)) ++
            startCalendarDate.maybeEra.map(era => DateValueHasStartEra -> JsonLDString(era.toString))

        val endDateAssertions =
          Map(DateValueHasEndYear -> JsonLDInt(endCalendarDate.year)) ++
            endCalendarDate.maybeMonth.map(month => DateValueHasEndMonth -> JsonLDInt(month)) ++
            endCalendarDate.maybeDay.map(day => DateValueHasEndDay -> JsonLDInt(day)) ++
            endCalendarDate.maybeEra.map(era => DateValueHasEndEra -> JsonLDString(era.toString))

        JsonLDObject(
          Map(
            ValueAsString        -> JsonLDString(valueHasString),
            DateValueHasCalendar -> JsonLDString(valueHasCalendar.toString)
          ) ++ startDateAssertions ++ endDateAssertions
        )
    }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatDateValue: DateValueContentV2 =>
        valueHasStartJDN == thatDateValue.valueHasStartJDN &&
        valueHasEndJDN == thatDateValue.valueHasEndJDN &&
        valueHasStartPrecision == thatDateValue.valueHasStartPrecision &&
        valueHasEndPrecision == thatDateValue.valueHasEndPrecision &&
        valueHasCalendar == thatDateValue.valueHasCalendar

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatDateValue: DateValueContentV2 =>
        valueHasStartJDN == thatDateValue.valueHasStartJDN &&
        valueHasEndJDN == thatDateValue.valueHasEndJDN &&
        valueHasStartPrecision == thatDateValue.valueHasStartPrecision &&
        valueHasEndPrecision == thatDateValue.valueHasEndPrecision &&
        valueHasCalendar == thatDateValue.valueHasCalendar &&
        comment == thatDateValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[DateValueContentV2]] objects based on JSON-LD input.
 */
object DateValueContentV2 {

  /**
   * Parses a string representing a date range in API v2 simple format.
   *
   * @param dateStr the string to be parsed.
   * @return a [[DateValueContentV2]] representing the date range.
   */
  def parse(dateStr: String): DateValueContentV2 = {
    val dateRange: CalendarDateRangeV2 = CalendarDateRangeV2.parse(dateStr)
    val (startJDN: Int, endJDN: Int)   = dateRange.toJulianDayRange

    DateValueContentV2(
      ontologySchema = ApiV2Simple,
      valueHasStartJDN = startJDN,
      valueHasEndJDN = endJDN,
      valueHasStartPrecision = dateRange.startCalendarDate.precision,
      valueHasEndPrecision = dateRange.endCalendarDate.precision,
      valueHasCalendar = dateRange.startCalendarDate.calendarName
    )
  }

  /**
   * Converts a JSON-LD object to a [[DateValueContentV2]].
   *
   * @param jsonLDObject         the JSON-LD object.
   * @param requestingUser       the user making the request.
   * @return a [[DateValueContentV2]].
   */
  def fromJsonLdObject(
    jsonLDObject: JsonLDObject,
    requestingUser: UserADM
  ): ZIO[StringFormatter, Throwable, DateValueContentV2] =
    for {
      comment                     <- RouteUtilZ.getComment(jsonLDObject)
      calendarName                <- ZIO.attempt(jsonLDObject.requireStringWithValidation(DateValueHasCalendar, CalendarNameV2.parse))
      dateValueHasStartYear       <- ZIO.attempt(jsonLDObject.requireInt(DateValueHasStartYear))
      maybeDateValueHasStartMonth <- ZIO.attempt(jsonLDObject.maybeInt(DateValueHasStartMonth))
      maybeDateValueHasStartDay   <- ZIO.attempt(jsonLDObject.maybeInt(DateValueHasStartDay))
      maybeDateValueHasStartEra <-
        ZIO.attempt(jsonLDObject.maybeStringWithValidation(DateValueHasStartEra, DateEraV2.parse))
      dateValueHasEndYear       <- ZIO.attempt(jsonLDObject.requireInt(DateValueHasEndYear))
      maybeDateValueHasEndMonth <- ZIO.attempt(jsonLDObject.maybeInt(DateValueHasEndMonth))
      maybeDateValueHasEndDay   <- ZIO.attempt(jsonLDObject.maybeInt(DateValueHasEndDay))
      maybeDateValueHasEndEra <-
        ZIO.attempt(jsonLDObject.maybeStringWithValidation(DateValueHasEndEra, DateEraV2.parse))
      _ <- ZIO
             .fail(AssertionException(s"Invalid date: $jsonLDObject"))
             .when(maybeDateValueHasStartMonth.isEmpty && maybeDateValueHasStartDay.isDefined)
      _ <- ZIO
             .fail(AssertionException(s"Invalid date: $jsonLDObject"))
             .when(maybeDateValueHasEndMonth.isEmpty && maybeDateValueHasEndDay.isDefined)
      // Check that the era is given if required.
      _ <- ZIO
             .fail(AssertionException(s"Era is required in calendar $calendarName"))
             .when(
               calendarName.isInstanceOf[CalendarNameGregorianOrJulian] &&
                 (maybeDateValueHasStartEra.isEmpty || maybeDateValueHasEndEra.isEmpty)
             )

      // Construct a CalendarDateRangeV2 representing the start and end dates.
      startCalendarDate = CalendarDateV2(
                            calendarName = calendarName,
                            year = dateValueHasStartYear,
                            maybeMonth = maybeDateValueHasStartMonth,
                            maybeDay = maybeDateValueHasStartDay,
                            maybeEra = maybeDateValueHasStartEra
                          )

      endCalendarDate = CalendarDateV2(
                          calendarName = calendarName,
                          year = dateValueHasEndYear,
                          maybeMonth = maybeDateValueHasEndMonth,
                          maybeDay = maybeDateValueHasEndDay,
                          maybeEra = maybeDateValueHasEndEra
                        )

      dateRange = CalendarDateRangeV2(startCalendarDate, endCalendarDate)

      // Convert the CalendarDateRangeV2 to start and end Julian Day Numbers.
      (startJDN: Int, endJDN: Int) = dateRange.toJulianDayRange

    } yield DateValueContentV2(
      ontologySchema = ApiV2Complex,
      valueHasStartJDN = startJDN,
      valueHasEndJDN = endJDN,
      valueHasStartPrecision = startCalendarDate.precision,
      valueHasEndPrecision = endCalendarDate.precision,
      valueHasCalendar = calendarName,
      comment
    )
}

/**
 * Represents a [[StandoffTagV2]] for a standoff tag of a certain type (standoff tag class) that is about to be created in the triplestore.
 *
 * @param standoffNode           the standoff node to be created.
 * @param standoffTagInstanceIri the standoff node's IRI.
 * @param startParentIri         the IRI of the parent of the start tag.
 * @param endParentIri           the IRI of the parent of the end tag, if any.
 */
case class CreateStandoffTagV2InTriplestore(
  standoffNode: StandoffTagV2,
  standoffTagInstanceIri: IRI,
  startParentIri: Option[IRI] = None,
  endParentIri: Option[IRI] = None
)

/**
 * Represents a Knora text value, or a page of standoff markup that will be included in a text value.
 *
 * @param maybeValueHasString the string representation of this text value, if available.
 * @param standoff            the standoff markup attached to the text value, if any.
 * @param mappingIri          the IRI of the [[MappingXMLtoStandoff]] used by default with the text value, if any.
 * @param mapping             the [[MappingXMLtoStandoff]] used by default with the text value, if any.
 * @param comment             a comment on this [[TextValueContentV2]], if any.
 */
case class TextValueContentV2(
  ontologySchema: OntologySchema,
  maybeValueHasString: Option[String],
  valueHasLanguage: Option[String] = None,
  standoff: Seq[StandoffTagV2] = Vector.empty,
  mappingIri: Option[IRI] = None,
  mapping: Option[MappingXMLtoStandoff] = None,
  xslt: Option[String] = None,
  comment: Option[String] = None
) extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.TextValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  /**
   * Returns the IRIs of any resources that are target of standoff link tags in this text value.
   */
  lazy val standoffLinkTagTargetResourceIris: Set[IRI] =
    standoffLinkTagIriAttributes.map(_.value)

  /**
   * Returns the IRI attributes representing the target IRIs of any standoff links in this text value.
   */
  lazy val standoffLinkTagIriAttributes: Set[StandoffTagIriAttributeV2] =
    standoff.foldLeft(Set.empty[StandoffTagIriAttributeV2]) { case (acc, standoffTag: StandoffTagV2) =>
      if (standoffTag.dataType.contains(StandoffDataTypeClasses.StandoffLinkTag)) {
        val iriAttributes: Set[StandoffTagIriAttributeV2] = standoffTag.attributes.collect {
          case iriAttribute: StandoffTagIriAttributeV2 => iriAttribute
        }.toSet

        acc ++ iriAttributes
      } else {
        acc
      }
    }

  override def valueHasString: String =
    maybeValueHasString.getOrElse(throw AssertionException("Text value has no valueHasString"))

  /**
   * The content of the text value without standoff, suitable for returning in API responses. This removes
   * INFORMATION SEPARATOR TWO, which is used only internally.
   */
  private lazy val valueHasStringWithoutStandoff: String =
    valueHasString.replace(StringFormatter.INFORMATION_SEPARATOR_TWO.toString, "")

  /**
   * The maximum start index in the standoff attached to this [[TextValueContentV2]]. This is used
   * only when writing a text value to the triplestore.
   */
  lazy val computedMaxStandoffStartIndex: Option[Int] = if (standoff.nonEmpty) {
    Some(standoff.map(_.startIndex).max)
  } else {
    None
  }

  override def toOntologySchema(targetSchema: OntologySchema): TextValueContentV2 = copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple =>
        valueHasLanguage match {
          case Some(lang) =>
            // In the simple schema, if this text value specifies a language, return it using a JSON-LD
            // @language key as per <https://json-ld.org/spec/latest/json-ld/#string-internationalization>.
            JsonLDUtil.objectWithLangToJsonLDObject(
              obj = valueHasStringWithoutStandoff,
              lang = lang
            )

          case None => JsonLDString(valueHasStringWithoutStandoff)
        }

      case ApiV2Complex =>
        val renderStandoffAsXml: Boolean = standoff.nonEmpty && SchemaOptions.renderMarkupAsXml(
          targetSchema = targetSchema,
          schemaOptions = schemaOptions
        )

        // Should we render standoff as XML?
        val objectMap: Map[IRI, JsonLDValue] = if (renderStandoffAsXml) {
          // println(s"Word count: ${maybeValueHasString.get.split("\\W+").length}")
          // println(s"Standoff tag count: ${standoff.size}")

          val definedMappingIri =
            mappingIri.getOrElse(throw BadRequestException(s"Cannot render standoff as XML without a mapping"))
          val definedMapping =
            mapping.getOrElse(throw BadRequestException(s"Cannot render standoff as XML without a mapping"))

          val xmlFromStandoff = StandoffTagUtilV2.convertStandoffTagV2ToXML(
            utf8str = valueHasString,
            standoff = standoff,
            mappingXMLtoStandoff = definedMapping
          )

          // check if there is an XSL transformation
          xslt match {
            case Some(definedXslt) =>
              val xmlTransformed: String = XMLUtil.applyXSLTransformation(
                xml = xmlFromStandoff,
                xslt = definedXslt
              )

              // the xml was converted to HTML
              Map(
                TextValueAsHtml -> JsonLDString(xmlTransformed),
                TextValueAsXml  -> JsonLDString(xmlFromStandoff),
                TextValueHasMapping -> JsonLDUtil.iriToJsonLDObject(
                  definedMappingIri
                )
              )

            case None =>
              Map(
                TextValueAsXml -> JsonLDString(xmlFromStandoff),
                TextValueHasMapping -> JsonLDUtil.iriToJsonLDObject(
                  definedMappingIri
                )
              )
          }
        } else {
          // We're not rendering standoff as XML. Return the text without markup.
          Map(ValueAsString -> JsonLDString(valueHasStringWithoutStandoff))
        }

        // In the complex schema, if this text value specifies a language, return it using the predicate
        // knora-api:textValueHasLanguage.
        val objectMapWithLanguage: Map[IRI, JsonLDValue] = valueHasLanguage match {
          case Some(lang) =>
            objectMap + (TextValueHasLanguage -> JsonLDString(lang))
          case None =>
            objectMap
        }

        JsonLDObject(objectMapWithLanguage)
    }

  /**
   * A convenience method that creates an IRI for each [[StandoffTagV2]] and resolves internal references to standoff node Iris.
   *
   * @return a list of [[CreateStandoffTagV2InTriplestore]] each representing a [[StandoffTagV2]] object
   *         along with is standoff tag class and IRI that is going to identify it in the triplestore.
   */
  def prepareForSparqlInsert(valueIri: IRI): Seq[CreateStandoffTagV2InTriplestore] =
    if (standoff.nonEmpty) {
      // create an IRI for each standoff tag
      // internal references to XML ids are not resolved yet
      val standoffTagsWithOriginalXMLIDs: Seq[CreateStandoffTagV2InTriplestore] = standoff.map {
        standoffNode: StandoffTagV2 =>
          CreateStandoffTagV2InTriplestore(
            standoffNode = standoffNode,
            standoffTagInstanceIri = stringFormatter.makeRandomStandoffTagIri(
              valueIri = valueIri,
              startIndex = standoffNode.startIndex
            ) // generate IRI for new standoff node
          )
      }

      // collect all the standoff tags that contain XML ids and
      // map the XML ids to standoff node Iris
      val iDsToStandoffNodeIris: Map[IRI, IRI] = standoffTagsWithOriginalXMLIDs.filter {
        standoffTag: CreateStandoffTagV2InTriplestore =>
          // filter those tags out that have an XML id
          standoffTag.standoffNode.originalXMLID.isDefined
      }.map { standoffTagWithID: CreateStandoffTagV2InTriplestore =>
        // return the XML id as a key and the standoff IRI as the value
        standoffTagWithID.standoffNode.originalXMLID.get -> standoffTagWithID.standoffTagInstanceIri
      }.toMap

      // Map the start index of each tag to its IRI, so we can resolve references to parent tags as references to
      // tag IRIs. We only care about start indexes here, because only hierarchical tags can be parents, and
      // hierarchical tags don't have end indexes.
      val startIndexesToStandoffNodeIris: Map[Int, IRI] = standoffTagsWithOriginalXMLIDs.map { tagWithIndex =>
        tagWithIndex.standoffNode.startIndex -> tagWithIndex.standoffTagInstanceIri
      }.toMap

      // resolve the original XML ids to standoff Iris every the `StandoffTagInternalReferenceAttributeV2`
      val standoffTagsWithNodeReferences: Seq[CreateStandoffTagV2InTriplestore] = standoffTagsWithOriginalXMLIDs.map {
        standoffTag: CreateStandoffTagV2InTriplestore =>
          // resolve original XML ids to standoff node Iris for `StandoffTagInternalReferenceAttributeV2`
          val attributesWithStandoffNodeIriReferences: Seq[StandoffTagAttributeV2] =
            standoffTag.standoffNode.attributes.map {
              case refAttr: StandoffTagInternalReferenceAttributeV2 =>
                // resolve the XML id to the corresponding standoff node IRI
                refAttr.copy(value = iDsToStandoffNodeIris(refAttr.value))
              case attr => attr
            }

          val startParentIndex: Option[Int] = standoffTag.standoffNode.startParentIndex
          val endParentIndex: Option[Int]   = standoffTag.standoffNode.endParentIndex

          // return standoff tag with updated attributes
          standoffTag.copy(
            standoffNode = standoffTag.standoffNode.copy(attributes = attributesWithStandoffNodeIriReferences),
            startParentIri = startParentIndex.map(parentIndex =>
              startIndexesToStandoffNodeIris(parentIndex)
            ), // If there's a start parent index, get its IRI, otherwise None
            endParentIri = endParentIndex.map(parentIndex =>
              startIndexesToStandoffNodeIris(parentIndex)
            ) // If there's an end parent index, get its IRI, otherwise None
          )
      }

      standoffTagsWithNodeReferences
    } else {
      Seq.empty[CreateStandoffTagV2InTriplestore]
    }

  override def unescape: ValueContentV2 = {
    // Unescape the text in standoff string attributes.
    val unescapedStandoff = standoff.map { standoffTag =>
      standoffTag.copy(
        attributes = standoffTag.attributes.map {
          case stringAttribute: StandoffTagStringAttributeV2 =>
            stringAttribute.copy(value = stringFormatter.fromSparqlEncodedString(stringAttribute.value))

          case other => other
        }
      )
    }

    copy(
      maybeValueHasString = maybeValueHasString.map(str => stringFormatter.fromSparqlEncodedString(str)),
      standoff = unescapedStandoff,
      comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr))
    )
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    // It doesn't make sense for a resource to have two different text values associated with the same property,
    // containing the same text but different markup.
    that match {
      case thatTextValue: TextValueContentV2 => valueHasString == thatTextValue.valueHasString

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    // It's OK to add a new version of a text value as long as something has been changed in it, even if it's only the markup
    // or the comment.
    currentVersion match {
      case thatTextValue: TextValueContentV2 =>
        val valueHasStringIdentical: Boolean = valueHasString == thatTextValue.valueHasString

        val mappingIdentitcal = mappingIri == thatTextValue.mappingIri

        // compare standoff nodes (sort them first by index) and the XML-to-standoff mapping IRI
        val standoffIdentical = StandoffTagUtilV2.makeComparableStandoffCollection(standoff) == StandoffTagUtilV2
          .makeComparableStandoffCollection(thatTextValue.standoff)

        valueHasStringIdentical && standoffIdentical && mappingIdentitcal && comment == thatTextValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[TextValueContentV2]] objects based on JSON-LD input.
 */
object TextValueContentV2 {
  private def getSparqlEncodedString(
    obj: JsonLDObject,
    key: String
  ): ZIO[StringFormatter, BadRequestException, Option[IRI]] =
    obj
      .getString(key)
      .mapError(BadRequestException(_))
      .flatMap(ZIO.foreach(_)(it => RouteUtilZ.toSparqlEncodedString(it, s"Invalid key: $key: $it")))

  private def getIriFromObject(obj: JsonLDObject, key: String): ZIO[StringFormatter, BadRequestException, Option[IRI]] =
    obj
      .getObjectIri(key)
      .mapError(BadRequestException(_))
      .flatMap(ZIO.foreach(_)(it => RouteUtilZ.validateAndEscapeIri(it, s"Invalid key: $key: $it")))

  private def getTextValue(
    maybeValueAsString: Option[IRI],
    maybeTextValueAsXml: Option[String],
    maybeValueHasLanguage: Option[IRI],
    maybeMappingResponse: Option[GetMappingResponseV2],
    jsonLdObject: JsonLDObject
  ) =
    (maybeValueAsString, maybeTextValueAsXml, maybeMappingResponse) match {
      case (Some(valueAsString), None, None) => // Text without standoff.
        RouteUtilZ
          .getComment(jsonLdObject)
          .map(comment =>
            TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some(valueAsString),
              comment = comment
            )
          )

      case (None, Some(textValueAsXml), Some(mappingResponse)) =>
        // Text with standoff. TODO: support submitting text with standoff as JSON-LD rather than as XML.
        for {
          textWithStandoffTags <- ZIO.attempt(
                                    StandoffTagUtilV2.convertXMLtoStandoffTagV2(
                                      textValueAsXml,
                                      mappingResponse,
                                      acceptStandoffLinksToClientIDs = false
                                    )
                                  )
          text    <- RouteUtilZ.toSparqlEncodedString(textWithStandoffTags.text, "Text value contains invalid characters")
          comment <- RouteUtilZ.getComment(jsonLdObject)
        } yield TextValueContentV2(
          ontologySchema = ApiV2Complex,
          maybeValueHasString = Some(text),
          valueHasLanguage = maybeValueHasLanguage,
          standoff = textWithStandoffTags.standoffTagV2,
          mappingIri = Some(mappingResponse.mappingIri),
          mapping = Some(mappingResponse.mapping),
          comment = comment
        )

      case _ =>
        ZIO.fail(
          BadRequestException(
            s"Invalid combination of knora-api:valueHasString, knora-api:textValueAsXml, and/or knora-api:textValueHasMapping"
          )
        )
    }

  /**
   * Converts a JSON-LD object to a [[TextValueContentV2]].
   *
   * @param jsonLdObject         the JSON-LD object.
   * @param requestingUser       the user making the request.
   * @return a [[TextValueContentV2]].
   */
  def fromJsonLdObject(
    jsonLdObject: JsonLDObject,
    requestingUser: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, TextValueContentV2] = ZIO.serviceWithZIO[StringFormatter] {
    stringFormatter =>
      for {
        maybeValueAsString    <- getSparqlEncodedString(jsonLdObject, ValueAsString)
        maybeValueHasLanguage <- getSparqlEncodedString(jsonLdObject, TextValueHasLanguage)
        maybeTextValueAsXml   <- jsonLdObject.getString(TextValueAsXml).mapError(BadRequestException(_))

        // If the client supplied the IRI of a standoff-to-XML mapping, get the mapping.
        maybeMappingResponse <-
          getIriFromObject(jsonLdObject, TextValueHasMapping).flatMap(mappingIriOption =>
            ZIO.foreach(mappingIriOption) { mappingIri =>
              MessageRelay.ask[GetMappingResponseV2](GetMappingRequestV2(mappingIri, requestingUser))
            }
          )

        comment <- RouteUtilZ.getComment(jsonLdObject)
        // Did the client submit text with or without standoff markup?
        textValue <- getTextValue(
                       maybeValueAsString,
                       maybeTextValueAsXml,
                       maybeValueHasLanguage,
                       maybeMappingResponse,
                       jsonLdObject
                     )

      } yield textValue
  }
}

/**
 * Represents a Knora integer value.
 *
 * @param valueHasInteger the integer value.
 * @param comment         a comment on this [[IntegerValueContentV2]], if any.
 */
case class IntegerValueContentV2(ontologySchema: OntologySchema, valueHasInteger: Int, comment: Option[String] = None)
    extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.IntValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override lazy val valueHasString: String = valueHasInteger.toString

  override def toOntologySchema(targetSchema: OntologySchema): IntegerValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple => JsonLDInt(valueHasInteger)

      case ApiV2Complex =>
        JsonLDObject(Map(IntValueAsInt -> JsonLDInt(valueHasInteger)))

    }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatIntegerValue: IntegerValueContentV2 => valueHasInteger == thatIntegerValue.valueHasInteger
      case _                                       => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatIntegerValue: IntegerValueContentV2 =>
        valueHasInteger == thatIntegerValue.valueHasInteger &&
        comment == thatIntegerValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[IntegerValueContentV2]] objects based on JSON-LD input.
 */
object IntegerValueContentV2 {

  /**
   * Converts a JSON-LD object to an [[IntegerValueContentV2]].
   *
   * @param jsonLDObject     the JSON-LD object.
   * @return an [[IntegerValueContentV2]].
   */
  def fromJsonLdObject(
    jsonLDObject: JsonLDObject
  ): ZIO[StringFormatter, Throwable, IntegerValueContentV2] =
    for {
      intValue <- ZIO.attempt(jsonLDObject.requireInt(IntValueAsInt))
      comment  <- RouteUtilZ.getComment(jsonLDObject)
    } yield IntegerValueContentV2(ApiV2Complex, intValue, comment)
}

/**
 * Represents a Knora decimal value.
 *
 * @param valueHasDecimal the decimal value.
 * @param comment         a comment on this [[DecimalValueContentV2]], if any.
 */
case class DecimalValueContentV2(
  ontologySchema: OntologySchema,
  valueHasDecimal: BigDecimal,
  comment: Option[String] = None
) extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.DecimalValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override lazy val valueHasString: String = valueHasDecimal.toString

  override def toOntologySchema(targetSchema: OntologySchema): DecimalValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue = {
    val decimalValueAsJsonLDObject = JsonLDUtil.datatypeValueToJsonLDObject(
      value = valueHasDecimal.toString,
      datatype = OntologyConstants.Xsd.Decimal.toSmartIri
    )

    targetSchema match {
      case ApiV2Simple => decimalValueAsJsonLDObject

      case ApiV2Complex =>
        JsonLDObject(Map(DecimalValueAsDecimal -> decimalValueAsJsonLDObject))
    }
  }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatDecimalValue: DecimalValueContentV2 => valueHasDecimal == thatDecimalValue.valueHasDecimal
      case _                                       => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatDecimalValue: DecimalValueContentV2 =>
        valueHasDecimal == thatDecimalValue.valueHasDecimal &&
        comment == thatDecimalValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[DecimalValueContentV2]] objects based on JSON-LD input.
 */
object DecimalValueContentV2 {

  /**
   * Converts a JSON-LD object to a [[DecimalValueContentV2]].
   *
   * @param jsonLdObject     the JSON-LD object.
   * @param requestingUser   the user making the request.
   * @return an [[DecimalValueContentV2]].
   */
  def fromJsonLdObject(
    jsonLdObject: JsonLDObject,
    requestingUser: UserADM
  ): ZIO[StringFormatter, Throwable, DecimalValueContentV2] = ZIO.serviceWithZIO[StringFormatter] {
    implicit stringFormatter =>
      for {
        decimalValue <- ZIO.attempt(
                          jsonLdObject.requireDatatypeValueInObject(
                            key = DecimalValueAsDecimal,
                            expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
                            validationFun = (s, errorFun) => ValuesValidator.validateBigDecimal(s).getOrElse(errorFun)
                          )
                        )
        comment <- RouteUtilZ.getComment(jsonLdObject)
      } yield DecimalValueContentV2(ApiV2Complex, decimalValue, comment)
  }
}

/**
 * Represents a Boolean value.
 *
 * @param valueHasBoolean the Boolean value.
 * @param comment         a comment on this [[BooleanValueContentV2]], if any.
 */
case class BooleanValueContentV2(
  ontologySchema: OntologySchema,
  valueHasBoolean: Boolean,
  comment: Option[String] = None
) extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.BooleanValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override lazy val valueHasString: String = valueHasBoolean.toString

  override def toOntologySchema(targetSchema: OntologySchema): BooleanValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple => JsonLDBoolean(valueHasBoolean)

      case ApiV2Complex =>
        JsonLDObject(Map(BooleanValueAsBoolean -> JsonLDBoolean(valueHasBoolean)))
    }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    // Always returns true, because it doesn't make sense to have two instances of the same boolean property.
    true

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatBooleanValue: BooleanValueContentV2 =>
        valueHasBoolean == thatBooleanValue.valueHasBoolean &&
        comment == thatBooleanValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[BooleanValueContentV2]] objects based on JSON-LD input.
 */
object BooleanValueContentV2 {

  /**
   * Converts a JSON-LD object to a [[BooleanValueContentV2]].
   *
   * @param jsonLdObject     the JSON-LD object.
   * @param requestingUser   the user making the request.
   * @return an [[BooleanValueContentV2]].
   */
  def fromJsonLdObject(
    jsonLdObject: JsonLDObject,
    requestingUser: UserADM
  ): ZIO[StringFormatter, Throwable, BooleanValueContentV2] =
    for {
      booleanValue <- ZIO.attempt(jsonLdObject.requireBoolean(BooleanValueAsBoolean))
      comment      <- RouteUtilZ.getComment(jsonLdObject)
    } yield BooleanValueContentV2(ApiV2Complex, booleanValue, comment)
}

/**
 * Represents a Knora geometry value (a 2D-shape).
 *
 * @param valueHasGeometry JSON representing a 2D geometrical shape.
 * @param comment          a comment on this [[GeomValueContentV2]], if any.
 */
case class GeomValueContentV2(ontologySchema: OntologySchema, valueHasGeometry: String, comment: Option[String] = None)
    extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.GeomValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = valueHasGeometry

  override def toOntologySchema(targetSchema: OntologySchema): GeomValueContentV2 = copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasGeometry,
          datatype = OntologyConstants.KnoraApiV2Simple.Geom.toSmartIri
        )

      case ApiV2Complex =>
        JsonLDObject(Map(GeometryValueAsGeometry -> JsonLDString(valueHasGeometry)))
    }

  override def unescape: ValueContentV2 =
    copy(
      valueHasGeometry = stringFormatter.fromSparqlEncodedString(valueHasGeometry),
      comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr))
    )

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatGeomValue: GeomValueContentV2 => valueHasGeometry == thatGeomValue.valueHasGeometry
      case _                                 => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatGeomValue: GeomValueContentV2 =>
        valueHasGeometry == thatGeomValue.valueHasGeometry &&
        comment == thatGeomValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[GeomValueContentV2]] objects based on JSON-LD input.
 */
object GeomValueContentV2 {

  /**
   * Converts a JSON-LD object to a [[GeomValueContentV2]].
   *
   * @param jsonLDObject     the JSON-LD object.
   * @return an [[GeomValueContentV2]].
   */
  def fromJsonLdObject(jsonLDObject: JsonLDObject): ZIO[StringFormatter, Throwable, GeomValueContentV2] =
    for {
      geometryValueAsGeometry <- ZIO.attempt(
                                   jsonLDObject.requireStringWithValidation(
                                     GeometryValueAsGeometry,
                                     (s, errorFun) => ValuesValidator.validateGeometryString(s).getOrElse(errorFun)
                                   )
                                 )
      comment <- RouteUtilZ.getComment(jsonLDObject)
    } yield GeomValueContentV2(ontologySchema = ApiV2Complex, geometryValueAsGeometry, comment)
}

/**
 * Represents a Knora time interval value.
 *
 * @param valueHasIntervalStart the start of the time interval.
 * @param valueHasIntervalEnd   the end of the time interval.
 * @param comment               a comment on this [[IntervalValueContentV2]], if any.
 */
case class IntervalValueContentV2(
  ontologySchema: OntologySchema,
  valueHasIntervalStart: BigDecimal,
  valueHasIntervalEnd: BigDecimal,
  comment: Option[String] = None
) extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.IntervalValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override lazy val valueHasString: String = s"$valueHasIntervalStart - $valueHasIntervalEnd"

  override def toOntologySchema(targetSchema: OntologySchema): IntervalValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasString,
          datatype = OntologyConstants.KnoraApiV2Simple.Interval.toSmartIri
        )

      case ApiV2Complex =>
        JsonLDObject(
          Map(
            IntervalValueHasStart ->
              JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueHasIntervalStart.toString,
                datatype = OntologyConstants.Xsd.Decimal.toSmartIri
              ),
            IntervalValueHasEnd ->
              JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueHasIntervalEnd.toString,
                datatype = OntologyConstants.Xsd.Decimal.toSmartIri
              )
          )
        )
    }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatIntervalValueContent: IntervalValueContentV2 =>
        valueHasIntervalStart == thatIntervalValueContent.valueHasIntervalStart &&
        valueHasIntervalEnd == thatIntervalValueContent.valueHasIntervalEnd

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatIntervalValueContent: IntervalValueContentV2 =>
        valueHasIntervalStart == thatIntervalValueContent.valueHasIntervalStart &&
        valueHasIntervalEnd == thatIntervalValueContent.valueHasIntervalEnd &&
        comment == thatIntervalValueContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[IntervalValueContentV2]] objects based on JSON-LD input.
 */
object IntervalValueContentV2 {

  /**
   * Converts a JSON-LD object to an [[IntervalValueContentV2]].
   *
   * @param jsonLDObject     the JSON-LD object.
   * @return an [[IntervalValueContentV2]].
   */
  def fromJsonLdObject(jsonLDObject: JsonLDObject): ZIO[StringFormatter, Throwable, IntervalValueContentV2] =
    ZIO.serviceWithZIO[StringFormatter] { implicit stringFormatter =>
      for {
        intervalValueHasStart <- ZIO.attempt(
                                   jsonLDObject.requireDatatypeValueInObject(
                                     key = IntervalValueHasStart,
                                     expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
                                     validationFun =
                                       (s, errorFun) => ValuesValidator.validateBigDecimal(s).getOrElse(errorFun)
                                   )
                                 )

        intervalValueHasEnd <- ZIO.attempt(
                                 jsonLDObject.requireDatatypeValueInObject(
                                   key = IntervalValueHasEnd,
                                   expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
                                   validationFun =
                                     (s, errorFun) => ValuesValidator.validateBigDecimal(s).getOrElse(errorFun)
                                 )
                               )
        comment <- RouteUtilZ.getComment(jsonLDObject)
      } yield IntervalValueContentV2(ApiV2Complex, intervalValueHasStart, intervalValueHasEnd, comment)
    }
}

/**
 * Represents a Knora timestamp value.
 *
 * @param valueHasTimeStamp the timestamp.
 * @param comment           a comment on this [[TimeValueContentV2]], if any.
 */
case class TimeValueContentV2(
  ontologySchema: OntologySchema,
  valueHasTimeStamp: Instant,
  comment: Option[String] = None
) extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.TimeValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override lazy val valueHasString: String = s"$valueHasTimeStamp"

  override def toOntologySchema(targetSchema: OntologySchema): TimeValueContentV2 = copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasTimeStamp.toString,
          datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri
        )

      case ApiV2Complex =>
        JsonLDObject(
          Map(
            TimeValueAsTimeStamp ->
              JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueHasTimeStamp.toString,
                datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri
              )
          )
        )
    }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatTimeValueContent: TimeValueContentV2 =>
        valueHasTimeStamp == thatTimeValueContent.valueHasTimeStamp

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatTimeValueContent: TimeValueContentV2 =>
        valueHasTimeStamp == thatTimeValueContent.valueHasTimeStamp &&
        comment == thatTimeValueContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[TimeValueContentV2]] objects based on JSON-LD input.
 */
object TimeValueContentV2 {

  /**
   * Converts a JSON-LD object to a [[TimeValueContentV2]].
   *
   * @param jsonLDObject     the JSON-LD object.
   * @return an [[IntervalValueContentV2]].
   */
  def fromJsonLdObject(jsonLDObject: JsonLDObject): ZIO[StringFormatter, Throwable, TimeValueContentV2] =
    ZIO.serviceWithZIO[StringFormatter] { implicit stringFormatter =>
      for {
        valueHasTimeStamp <- ZIO.attempt(
                               jsonLDObject.requireDatatypeValueInObject(
                                 key = TimeValueAsTimeStamp,
                                 expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                                 validationFun =
                                   (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun)
                               )
                             )
        comment <- RouteUtilZ.getComment(jsonLDObject)
      } yield TimeValueContentV2(ApiV2Complex, valueHasTimeStamp, comment)
    }
}

/**
 * Represents a value pointing to a Knora hierarchical list node.
 *
 * @param valueHasListNode the IRI of the hierarchical list node pointed to.
 * @param listNodeLabel    the label of the hierarchical list node pointed to.
 * @param comment          a comment on this [[HierarchicalListValueContentV2]], if any.
 */
case class HierarchicalListValueContentV2(
  ontologySchema: OntologySchema,
  valueHasListNode: IRI,
  listNodeLabel: Option[String] = None,
  comment: Option[String] = None
) extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.ListValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = valueHasListNode

  override def toOntologySchema(targetSchema: OntologySchema): HierarchicalListValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple =>
        listNodeLabel match {
          case Some(labelStr) =>
            JsonLDUtil.datatypeValueToJsonLDObject(
              value = labelStr,
              datatype = OntologyConstants.KnoraApiV2Simple.ListNode.toSmartIri
            )
          case None =>
            throw AssertionException("Can't convert list value to simple schema because it has no list node label")

        }

      case ApiV2Complex =>
        JsonLDObject(
          Map(
            ListValueAsListNode -> JsonLDUtil.iriToJsonLDObject(valueHasListNode)
          )
        )
    }

  override def unescape: ValueContentV2 =
    copy(
      listNodeLabel = listNodeLabel.map(labelStr => stringFormatter.fromSparqlEncodedString(labelStr)),
      comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr))
    )

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatListContent: HierarchicalListValueContentV2 => valueHasListNode == thatListContent.valueHasListNode
      case _                                               => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatListContent: HierarchicalListValueContentV2 =>
        valueHasListNode == thatListContent.valueHasListNode &&
        comment == thatListContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[HierarchicalListValueContentV2]] objects based on JSON-LD input.
 */
object HierarchicalListValueContentV2 {

  /**
   * Converts a JSON-LD object to a [[HierarchicalListValueContentV2]].
   *
   * @param jsonLDObject     the JSON-LD object.
   * @return a [[HierarchicalListValueContentV2]].
   */
  def fromJsonLdObject(
    jsonLDObject: JsonLDObject
  ): ZIO[StringFormatter, Throwable, HierarchicalListValueContentV2] = ZIO.serviceWithZIO[StringFormatter] {
    implicit stringFormatter =>
      for {
        listValueAsListNode <- ZIO.attempt(
                                 jsonLDObject.requireIriInObject(ListValueAsListNode, stringFormatter.toSmartIriWithErr)
                               )
        _ <- ZIO
               .fail(BadRequestException(s"List node IRI <$listValueAsListNode> is not a Knora data IRI"))
               .when(!listValueAsListNode.isKnoraDataIri)
        comment <- RouteUtilZ.getComment(jsonLDObject)
      } yield HierarchicalListValueContentV2(ApiV2Complex, listValueAsListNode.toString, comment)
  }
}

/**
 * Represents a Knora color value.
 *
 * @param valueHasColor a hexadecimal string containing the RGB color value
 * @param comment       a comment on this [[ColorValueContentV2]], if any.
 */
case class ColorValueContentV2(ontologySchema: OntologySchema, valueHasColor: String, comment: Option[String] = None)
    extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.ColorValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = valueHasColor

  override def toOntologySchema(targetSchema: OntologySchema): ColorValueContentV2 = copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasColor,
          datatype = OntologyConstants.KnoraApiV2Simple.Color.toSmartIri
        )

      case ApiV2Complex =>
        JsonLDObject(Map(ColorValueAsColor -> JsonLDString(valueHasColor)))
    }

  override def unescape: ValueContentV2 =
    copy(
      valueHasColor = stringFormatter.fromSparqlEncodedString(valueHasColor),
      comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr))
    )

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatColorContent: ColorValueContentV2 => valueHasColor == thatColorContent.valueHasColor
      case _                                     => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatColorContent: ColorValueContentV2 =>
        valueHasColor == thatColorContent.valueHasColor &&
        comment == thatColorContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[ColorValueContentV2]] objects based on JSON-LD input.
 */
object ColorValueContentV2 {

  /**
   * Converts a JSON-LD object to a [[ColorValueContentV2]].
   *
   * @param jsonLDObject     the JSON-LD object.
   * @return a [[ColorValueContentV2]].
   */
  def fromJsonLdObject(jsonLDObject: JsonLDObject): ZIO[StringFormatter, Throwable, ColorValueContentV2] =
    ZIO.serviceWithZIO[StringFormatter] { implicit stringFormatter =>
      for {
        colorValueAsColor <- ZIO.attempt(
                               jsonLDObject.requireStringWithValidation(
                                 ColorValueAsColor,
                                 stringFormatter.toSparqlEncodedString
                               )
                             )
        comment <- RouteUtilZ.getComment(jsonLDObject)
      } yield ColorValueContentV2(ApiV2Complex, colorValueAsColor, comment)
    }
}

/**
 * Represents a Knora URI value.
 *
 * @param valueHasUri the URI value.
 * @param comment     a comment on this [[UriValueContentV2]], if any.
 */
case class UriValueContentV2(ontologySchema: OntologySchema, valueHasUri: String, comment: Option[String] = None)
    extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.UriValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = valueHasUri

  override def toOntologySchema(targetSchema: OntologySchema): UriValueContentV2 = copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue = {
    val uriAsJsonLDObject = JsonLDUtil.datatypeValueToJsonLDObject(
      value = valueHasUri,
      datatype = OntologyConstants.Xsd.Uri.toSmartIri
    )

    targetSchema match {
      case ApiV2Simple => uriAsJsonLDObject

      case ApiV2Complex =>
        JsonLDObject(Map(UriValueAsUri -> uriAsJsonLDObject))
    }
  }

  override def unescape: ValueContentV2 =
    copy(
      valueHasUri = stringFormatter.fromSparqlEncodedString(valueHasUri),
      comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr))
    )

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatUriContent: UriValueContentV2 => valueHasUri == thatUriContent.valueHasUri
      case _                                 => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatUriContent: UriValueContentV2 =>
        valueHasUri == thatUriContent.valueHasUri &&
        comment == thatUriContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[UriValueContentV2]] objects based on JSON-LD input.
 */
object UriValueContentV2 {

  /**
   * Converts a JSON-LD object to a [[UriValueContentV2]].
   *
   * @param jsonLDObject     the JSON-LD object.
   * @return a [[UriValueContentV2]].
   */
  def fromJsonLdObject(jsonLDObject: JsonLDObject): ZIO[StringFormatter, Throwable, UriValueContentV2] =
    ZIO.serviceWithZIO[StringFormatter] { implicit stringFormatter =>
      for {
        uriValueAsUri <- ZIO.attempt(
                           jsonLDObject.requireDatatypeValueInObject(
                             key = UriValueAsUri,
                             expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
                             validationFun = stringFormatter.toSparqlEncodedString
                           )
                         )
        comment <- RouteUtilZ.getComment(jsonLDObject)
      } yield UriValueContentV2(ApiV2Complex, uriValueAsUri, comment)
    }
}

/**
 * Represents a Knora geoname value.
 *
 * @param valueHasGeonameCode the geoname code.
 * @param comment             a comment on this [[GeonameValueContentV2]], if any.
 */
case class GeonameValueContentV2(
  ontologySchema: OntologySchema,
  valueHasGeonameCode: String,
  comment: Option[String] = None
) extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.GeonameValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = valueHasGeonameCode

  override def toOntologySchema(targetSchema: OntologySchema): GeonameValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasGeonameCode,
          datatype = OntologyConstants.KnoraApiV2Simple.Geoname.toSmartIri
        )

      case ApiV2Complex =>
        JsonLDObject(
          Map(GeonameValueAsGeonameCode -> JsonLDString(valueHasGeonameCode))
        )
    }

  override def unescape: ValueContentV2 =
    copy(
      valueHasGeonameCode = stringFormatter.fromSparqlEncodedString(valueHasGeonameCode),
      comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr))
    )

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatGeonameContent: GeonameValueContentV2 => valueHasGeonameCode == thatGeonameContent.valueHasGeonameCode
      case _                                         => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatGeonameContent: GeonameValueContentV2 =>
        valueHasGeonameCode == thatGeonameContent.valueHasGeonameCode &&
        comment == thatGeonameContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[GeonameValueContentV2]] objects based on JSON-LD input.
 */
object GeonameValueContentV2 {

  /**
   * Converts a JSON-LD object to a [[GeonameValueContentV2]].
   *
   * @param jsonLDObject     the JSON-LD object.
   * @return a [[GeonameValueContentV2]].
   */
  def fromJsonLdObject(jsonLDObject: JsonLDObject): ZIO[StringFormatter, Throwable, GeonameValueContentV2] =
    ZIO.serviceWithZIO[StringFormatter] { implicit stringFormatter =>
      for {
        geonameValueAsGeonameCode <- ZIO.attempt(
                                       jsonLDObject.requireStringWithValidation(
                                         GeonameValueAsGeonameCode,
                                         stringFormatter.toSparqlEncodedString
                                       )
                                     )
        comment <- RouteUtilZ.getComment(jsonLDObject)
      } yield GeonameValueContentV2(ApiV2Complex, geonameValueAsGeonameCode, comment)
    }
}

/**
 * Represents the basic metadata stored about any file value.
 */
case class FileValueV2(
  internalFilename: String,
  internalMimeType: String,
  originalFilename: Option[String],
  originalMimeType: Option[String]
)

/**
 * Holds a [[FileValueV2]] and the metadata that Sipi returned about the file.
 *
 * @param fileValue        a [[FileValueV2]].
 * @param sipiFileMetadata the metadata that Sipi returned about the file.
 */
case class FileValueWithSipiMetadata(fileValue: FileValueV2, sipiFileMetadata: GetFileMetadataResponse)

/**
 * Constructs [[FileValueWithSipiMetadata]] objects based on JSON-LD input.
 */
object FileValueWithSipiMetadata {
  def fromJsonLdObject(
    jsonLDObject: JsonLDObject,
    requestingUser: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, FileValueWithSipiMetadata] =
    ZIO.serviceWithZIO[StringFormatter] { stringFormatter =>
      for {
        // The submitted value provides only Sipi's internal filename for the file.
        internalFilename <- ZIO.attempt(
                              jsonLDObject.requireStringWithValidation(
                                FileValueHasFilename,
                                stringFormatter.toSparqlEncodedString
                              )
                            )

        // Ask Sipi about the rest of the file's metadata.
        tempFilePath <- ZIO.attempt(stringFormatter.makeSipiTempFilePath(internalFilename))
        fileMetadataResponse <-
          MessageRelay.ask[GetFileMetadataResponse](GetFileMetadataRequest(tempFilePath, requestingUser))
        fileValue = FileValueV2(
                      internalFilename = internalFilename,
                      internalMimeType = fileMetadataResponse.internalMimeType,
                      originalFilename = fileMetadataResponse.originalFilename,
                      originalMimeType = fileMetadataResponse.originalMimeType
                    )
      } yield FileValueWithSipiMetadata(fileValue, fileMetadataResponse)
    }
}

/**
 * A trait for case classes representing different types of file values.
 */
sealed trait FileValueContentV2 extends ValueContentV2 {

  /**
   * The basic metadata about the file value.
   */
  def fileValue: FileValueV2

  def toJsonLDValueInSimpleSchema(fileUrl: String): JsonLDObject = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    JsonLDUtil.datatypeValueToJsonLDObject(
      value = fileUrl,
      datatype = OntologyConstants.KnoraApiV2Simple.File.toSmartIri
    )
  }

  def toJsonLDObjectMapInComplexSchema(fileUrl: String): Map[IRI, JsonLDValue] = Map(
    FileValueHasFilename -> JsonLDString(fileValue.internalFilename),
    FileValueAsUrl -> JsonLDUtil.datatypeValueToJsonLDObject(
      value = fileUrl,
      datatype = OntologyConstants.Xsd.Uri.toSmartIri
    )
  )
}

/**
 * Represents image file metadata.
 *
 * @param fileValue the basic metadata about the file value.
 * @param dimX      the with of the the image in pixels.
 * @param dimY      the height of the the image in pixels.
 * @param comment   a comment on this `StillImageFileValueContentV2`, if any.
 */
case class StillImageFileValueContentV2(
  ontologySchema: OntologySchema,
  fileValue: FileValueV2,
  dimX: Int,
  dimY: Int,
  comment: Option[String] = None
) extends FileValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.StillImageFileValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = fileValue.internalFilename

  override def toOntologySchema(targetSchema: OntologySchema): StillImageFileValueContentV2 =
    copy(ontologySchema = targetSchema)

  def makeFileUrl(projectADM: ProjectADM, url: String): String =
    s"$url/${projectADM.shortcode}/${fileValue.internalFilename}/full/$dimX,$dimY/0/default.jpg"

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue = {
    val fileUrl: String = makeFileUrl(projectADM, appConfig.sipi.externalBaseUrl)

    targetSchema match {
      case ApiV2Simple => toJsonLDValueInSimpleSchema(fileUrl)

      case ApiV2Complex =>
        JsonLDObject(
          toJsonLDObjectMapInComplexSchema(fileUrl) ++ Map(
            StillImageFileValueHasDimX -> JsonLDInt(dimX),
            StillImageFileValueHasDimY -> JsonLDInt(dimY),
            StillImageFileValueHasIIIFBaseUrl -> JsonLDUtil
              .datatypeValueToJsonLDObject(
                value = s"${appConfig.sipi.externalBaseUrl}/${projectADM.shortcode}",
                datatype = OntologyConstants.Xsd.Uri.toSmartIri
              )
          )
        )
    }
  }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatStillImage: StillImageFileValueContentV2 =>
        fileValue == thatStillImage.fileValue &&
        dimX == thatStillImage.dimX &&
        dimY == thatStillImage.dimY

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatStillImage: StillImageFileValueContentV2 =>
        wouldDuplicateOtherValue(thatStillImage) && comment == thatStillImage.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }

}

/**
 * Constructs [[StillImageFileValueContentV2]] objects based on JSON-LD input.
 */
object StillImageFileValueContentV2 {
  def fromJsonLdObject(
    jsonLDObject: JsonLDObject,
    requestingUser: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, StillImageFileValueContentV2] =
    for {
      fileValueWithSipiMetadata <- FileValueWithSipiMetadata.fromJsonLdObject(jsonLDObject, requestingUser)
      comment                   <- RouteUtilZ.getComment(jsonLDObject)
    } yield StillImageFileValueContentV2(
      ontologySchema = ApiV2Complex,
      fileValue = fileValueWithSipiMetadata.fileValue,
      dimX = fileValueWithSipiMetadata.sipiFileMetadata.width.getOrElse(0),
      dimY = fileValueWithSipiMetadata.sipiFileMetadata.height.getOrElse(0),
      comment = comment
    )
}

/**
 * Represents document file metadata.
 *
 * @param fileValue the basic metadata about the file value.
 * @param pageCount the number of pages in the document.
 * @param dimX      the with of the the document in pixels.
 * @param dimY      the height of the the document in pixels.
 * @param comment   a comment on this `DocumentFileValueContentV2`, if any.
 */
case class DocumentFileValueContentV2(
  ontologySchema: OntologySchema,
  fileValue: FileValueV2,
  pageCount: Option[Int],
  dimX: Option[Int],
  dimY: Option[Int],
  comment: Option[String] = None
) extends FileValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.DocumentFileValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = fileValue.internalFilename

  override def toOntologySchema(targetSchema: OntologySchema): DocumentFileValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue = {
    val fileUrl: String =
      s"${appConfig.sipi.externalBaseUrl}/${projectADM.shortcode}/${fileValue.internalFilename}/file"

    targetSchema match {
      case ApiV2Simple => toJsonLDValueInSimpleSchema(fileUrl)

      case ApiV2Complex =>
        JsonLDObject(
          toJsonLDObjectMapInComplexSchema(
            fileUrl
          )
        )
    }
  }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatDocumentFile: DocumentFileValueContentV2 =>
        fileValue == thatDocumentFile.fileValue

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatDocumentFile: DocumentFileValueContentV2 =>
        wouldDuplicateOtherValue(thatDocumentFile) && comment == thatDocumentFile.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Represents archive file metadata.
 *
 * @param fileValue the basic metadata about the file value.
 * @param comment   a comment on this `ArchiveFileValueContentV2`, if any.
 */
case class ArchiveFileValueContentV2(
  ontologySchema: OntologySchema,
  fileValue: FileValueV2,
  comment: Option[String] = None
) extends FileValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.ArchiveFileValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = fileValue.internalFilename

  override def toOntologySchema(targetSchema: OntologySchema): ArchiveFileValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue = {
    val fileUrl: String =
      s"${appConfig.sipi.externalBaseUrl}/${projectADM.shortcode}/${fileValue.internalFilename}/file"

    targetSchema match {
      case ApiV2Simple => toJsonLDValueInSimpleSchema(fileUrl)
      case ApiV2Complex =>
        JsonLDObject(
          toJsonLDObjectMapInComplexSchema(
            fileUrl
          )
        )
    }
  }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatArchiveFile: ArchiveFileValueContentV2 =>
        fileValue == thatArchiveFile.fileValue

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatArchiveFile: ArchiveFileValueContentV2 =>
        wouldDuplicateOtherValue(thatArchiveFile) && comment == thatArchiveFile.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[DocumentFileValueContentV2]] objects based on JSON-LD input.
 */
object DocumentFileValueContentV2 {
  def fromJsonLdObject(
    jsonLdObject: JsonLDObject,
    requestingUser: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, DocumentFileValueContentV2] =
    for {
      fileValueWithSipiMetadata <- FileValueWithSipiMetadata.fromJsonLdObject(jsonLdObject, requestingUser)
      comment                   <- RouteUtilZ.getComment(jsonLdObject)
    } yield DocumentFileValueContentV2(
      ontologySchema = ApiV2Complex,
      fileValue = fileValueWithSipiMetadata.fileValue,
      pageCount = fileValueWithSipiMetadata.sipiFileMetadata.pageCount,
      dimX = fileValueWithSipiMetadata.sipiFileMetadata.width,
      dimY = fileValueWithSipiMetadata.sipiFileMetadata.height,
      comment
    )
}

/**
 * Constructs [[ArchiveFileValueContentV2]] objects based on JSON-LD input.
 */
object ArchiveFileValueContentV2 {
  def fromJsonLdObject(
    jsonLdObject: JsonLDObject,
    requestingUser: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, ArchiveFileValueContentV2] =
    for {
      fileValueWithSipiMetadata <- FileValueWithSipiMetadata.fromJsonLdObject(jsonLdObject, requestingUser)
      comment                   <- RouteUtilZ.getComment(jsonLdObject)
    } yield ArchiveFileValueContentV2(ApiV2Complex, fileValueWithSipiMetadata.fileValue, comment)
}

/**
 * Represents text file metadata.
 *
 * @param fileValue the basic metadata about the file value.
 * @param comment   a comment on this [[TextFileValueContentV2]], if any.
 */
case class TextFileValueContentV2(
  ontologySchema: OntologySchema,
  fileValue: FileValueV2,
  comment: Option[String] = None
) extends FileValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.TextFileValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = fileValue.internalFilename

  override def toOntologySchema(targetSchema: OntologySchema): TextFileValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue = {
    val fileUrl: String =
      s"${appConfig.sipi.externalBaseUrl}/${projectADM.shortcode}/${fileValue.internalFilename}/file"

    targetSchema match {
      case ApiV2Simple => toJsonLDValueInSimpleSchema(fileUrl)

      case ApiV2Complex =>
        JsonLDObject(toJsonLDObjectMapInComplexSchema(fileUrl))
    }
  }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatTextFile: TextFileValueContentV2 =>
        fileValue == thatTextFile.fileValue

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatTextFile: TextFileValueContentV2 =>
        fileValue == thatTextFile.fileValue &&
        comment == thatTextFile.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[TextFileValueContentV2]] objects based on JSON-LD input.
 */
object TextFileValueContentV2 {
  def fromJsonLdObject(
    jsonLDObject: JsonLDObject,
    requestingUser: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, TextFileValueContentV2] =
    for {
      fileValueWithSipiMetadata <- FileValueWithSipiMetadata.fromJsonLdObject(jsonLDObject, requestingUser)
      comment                   <- RouteUtilZ.getComment(jsonLDObject)
    } yield TextFileValueContentV2(ApiV2Complex, fileValueWithSipiMetadata.fileValue, comment)
}

/**
 * Represents audio file metadata.
 *
 * @param fileValue the basic metadata about the file value.
 * @param comment a comment on this [[AudioFileValueContentV2]], if any.
 */
case class AudioFileValueContentV2(
  ontologySchema: OntologySchema,
  fileValue: FileValueV2,
  comment: Option[String] = None
) extends FileValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.AudioFileValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = fileValue.internalFilename

  override def toOntologySchema(targetSchema: OntologySchema): AudioFileValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue = {
    val fileUrl: String =
      s"${appConfig.sipi.externalBaseUrl}/${projectADM.shortcode}/${fileValue.internalFilename}/file"

    targetSchema match {
      case ApiV2Simple => toJsonLDValueInSimpleSchema(fileUrl)

      case ApiV2Complex =>
        JsonLDObject(toJsonLDObjectMapInComplexSchema(fileUrl))
    }
  }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatAudioFile: AudioFileValueContentV2 =>
        fileValue == thatAudioFile.fileValue

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatAudioFile: AudioFileValueContentV2 =>
        fileValue == thatAudioFile.fileValue &&
        comment == thatAudioFile.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[AudioFileValueContentV2]] objects based on JSON-LD input.
 */
object AudioFileValueContentV2 {
  def fromJsonLdObject(
    jsonLDObject: JsonLDObject,
    requestingUser: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, AudioFileValueContentV2] =
    for {
      fileValueWithSipiMetadata <- FileValueWithSipiMetadata.fromJsonLdObject(jsonLDObject, requestingUser)
      comment                   <- RouteUtilZ.getComment(jsonLDObject)
    } yield AudioFileValueContentV2(ApiV2Complex, fileValueWithSipiMetadata.fileValue, comment)
}

/**
 * Represents video file metadata.
 *
 * @param fileValue the basic metadata about the file value.
 * @param comment a comment on this [[MovingImageFileValueContentV2]], if any.
 */
case class MovingImageFileValueContentV2(
  ontologySchema: OntologySchema,
  fileValue: FileValueV2,
  comment: Option[String] = None
) extends FileValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.MovingImageFileValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = fileValue.internalFilename

  override def toOntologySchema(targetSchema: OntologySchema): MovingImageFileValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue = {
    val fileUrl: String =
      s"${appConfig.sipi.externalBaseUrl}/${projectADM.shortcode}/${fileValue.internalFilename}/file"

    targetSchema match {
      case ApiV2Simple => toJsonLDValueInSimpleSchema(fileUrl)

      case ApiV2Complex =>
        JsonLDObject(
          toJsonLDObjectMapInComplexSchema(fileUrl)
        )
    }
  }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatVideoFile: MovingImageFileValueContentV2 =>
        fileValue == thatVideoFile.fileValue

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatVideoFile: MovingImageFileValueContentV2 =>
        fileValue == thatVideoFile.fileValue &&
        comment == thatVideoFile.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[MovingImageFileValueContentV2]] objects based on JSON-LD input.
 */
object MovingImageFileValueContentV2 {
  def fromJsonLdObject(
    jsonLDObject: JsonLDObject,
    requestingUser: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, MovingImageFileValueContentV2] =
    for {
      fileValueWithSipiMetadata <- FileValueWithSipiMetadata.fromJsonLdObject(jsonLDObject, requestingUser)
      comment                   <- RouteUtilZ.getComment(jsonLDObject)
    } yield MovingImageFileValueContentV2(ApiV2Complex, fileValueWithSipiMetadata.fileValue, comment)
}

/**
 * Represents a Knora link value.
 *
 * @param referredResourceIri    the IRI of resource that this link value refers to (either the source
 *                               of an incoming link, or the target of an outgoing link).
 * @param referredResourceExists `true` if the referred resource already exists, `false` if it is being created in the
 *                               same transaction.
 * @param isIncomingLink         indicates if it is an incoming link.
 * @param nestedResource         information about the nested resource, if given.
 * @param comment                a comment on the link.
 */
case class LinkValueContentV2(
  ontologySchema: OntologySchema,
  referredResourceIri: IRI,
  referredResourceExists: Boolean = true,
  isIncomingLink: Boolean = false,
  nestedResource: Option[ReadResourceV2] = None,
  comment: Option[String] = None
) extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.LinkValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = referredResourceIri

  override def toOntologySchema(targetSchema: OntologySchema): LinkValueContentV2 = {
    val convertedNestedResource = nestedResource.map { nested =>
      val targetApiSchema: ApiV2Schema = targetSchema match {
        case apiSchema: ApiV2Schema => apiSchema
        case _                      => throw AssertionException(s"Can't convert a nested resource to $targetSchema")
      }

      nested.toOntologySchema(targetApiSchema)
    }

    copy(
      ontologySchema = targetSchema,
      nestedResource = convertedNestedResource
    )
  }

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple => JsonLDUtil.iriToJsonLDObject(referredResourceIri)

      case ApiV2Complex =>
        // check if the referred resource has to be included in the JSON response
        val objectMap: Map[IRI, JsonLDValue] = nestedResource match {
          case Some(targetResource: ReadResourceV2) =>
            // include the nested resource in the response
            val referredResourceAsJsonLDValue: JsonLDObject = targetResource.toJsonLD(
              targetSchema = targetSchema,
              appConfig = appConfig,
              schemaOptions = schemaOptions
            )

            // check whether the nested resource is the target or the source of the link
            if (!isIncomingLink) {
              Map(LinkValueHasTarget -> referredResourceAsJsonLDValue)
            } else {
              Map(LinkValueHasSource -> referredResourceAsJsonLDValue)
            }
          case None =>
            // check whether it is an outgoing or incoming link
            if (!isIncomingLink) {
              Map(
                LinkValueHasTargetIri -> JsonLDUtil.iriToJsonLDObject(
                  referredResourceIri
                )
              )
            } else {
              Map(
                LinkValueHasSourceIri -> JsonLDUtil.iriToJsonLDObject(
                  referredResourceIri
                )
              )
            }
        }

        JsonLDObject(objectMap)
    }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatLinkValue: LinkValueContentV2 =>
        referredResourceIri == thatLinkValue.referredResourceIri &&
        isIncomingLink == thatLinkValue.isIncomingLink

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatLinkValue: LinkValueContentV2 =>
        referredResourceIri == thatLinkValue.referredResourceIri &&
        isIncomingLink == thatLinkValue.isIncomingLink &&
        comment == thatLinkValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[LinkValueContentV2]] objects based on JSON-LD input.
 */
object LinkValueContentV2 {
  def fromJsonLdObject(
    jsonLDObject: JsonLDObject
  ): ZIO[StringFormatter, Throwable, LinkValueContentV2] = ZIO.serviceWithZIO[StringFormatter] {
    implicit stringFormatter =>
      for {
        targetIri <- ZIO.attempt(
                       jsonLDObject.requireIriInObject(
                         LinkValueHasTargetIri,
                         stringFormatter.toSmartIriWithErr
                       )
                     )
        _ <- ZIO
               .fail(BadRequestException(s"Link target IRI <$targetIri> is not a Knora data IRI"))
               .when(!targetIri.isKnoraDataIri)
        comment <- RouteUtilZ.getComment(jsonLDObject)
      } yield LinkValueContentV2(ApiV2Complex, referredResourceIri = targetIri.toString, comment = comment)
  }
}

/**
 * Generic representation of a deleted value.
 *
 * @param ontologySchema the ontology schema
 * @param comment        optional comment
 */
case class DeletedValueContentV2(ontologySchema: OntologySchema, comment: Option[String] = None)
    extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.DeletedValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def toOntologySchema(targetSchema: OntologySchema): DeletedValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: ProjectADM,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDValue = JsonLDObject(Map(OntologyConstants.KnoraBase.DeletedValue -> JsonLDString("DeletedValue")))

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = false

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = false

  override def valueHasString: String = "Deleted Value"
}
