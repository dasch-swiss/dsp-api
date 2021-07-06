/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import java.time.Instant
import java.util.UUID

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.exceptions.{AssertionException, BadRequestException, NotImplementedException, SipiException}
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages.{GetFileMetadataRequest, GetFileMetadataResponse}
import org.knora.webapi.messages.util.PermissionUtilADM.EntityPermission
import org.knora.webapi.messages.util._
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2.TextWithStandoffTagsV2
import org.knora.webapi.messages.util.standoff.{StandoffTagUtilV2, XMLUtil}
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.util._

import scala.concurrent.{ExecutionContext, Future}

/**
  * A tagging trait for requests handled by [[org.knora.webapi.responders.v2.ValuesResponderV2]].
  */
sealed trait ValuesResponderRequestV2 extends KnoraRequestV2

/**
  * Requests the creation of a value.
  *
  * @param createValue          a [[CreateValueV2]] representing the value to be created. A successful response will be
  *                             a [[CreateValueResponseV2]].
  * @param featureFactoryConfig the feature factory configuration.
  * @param requestingUser       the user making the request.
  * @param apiRequestID         the API request ID.
  */
case class CreateValueRequestV2(createValue: CreateValueV2,
                                featureFactoryConfig: FeatureFactoryConfig,
                                requestingUser: UserADM,
                                apiRequestID: UUID)
    extends ValuesResponderRequestV2

/**
  * Constructs [[CreateValueRequestV2]] instances based on JSON-LD input.
  */
object CreateValueRequestV2 extends KnoraJsonLDRequestReaderV2[CreateValueRequestV2] {

  /**
    * Converts JSON-LD input to a [[CreateValueRequestV2]].
    *
    * @param jsonLDDocument       the JSON-LD input.
    * @param apiRequestID         the UUID of the API request.
    * @param requestingUser       the user making the request.
    * @param responderManager     a reference to the responder manager.
    * @param storeManager         a reference to the store manager.
    * @param featureFactoryConfig the feature factory configuration.
    * @param settings             the application settings.
    * @param log                  a logging adapter.
    * @return a case class instance representing the input.
    */
  override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                          apiRequestID: UUID,
                          requestingUser: UserADM,
                          responderManager: ActorRef,
                          storeManager: ActorRef,
                          featureFactoryConfig: FeatureFactoryConfig,
                          settings: KnoraSettingsImpl,
                          log: LoggingAdapter)(implicit timeout: Timeout,
                                               executionContext: ExecutionContext): Future[CreateValueRequestV2] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    for {
      // Get the IRI of the resource that the value is to be created in.
      resourceIri: SmartIri <- Future(jsonLDDocument.requireIDAsKnoraDataIri)

      _ = if (!resourceIri.isKnoraResourceIri) {
        throw BadRequestException(s"Invalid resource IRI: <$resourceIri>")
      }

      // Get the resource class.
      resourceClassIri: SmartIri = jsonLDDocument.requireTypeAsKnoraTypeIri

      // Get the resource property and the value to be created.
      createValue: CreateValueV2 <- jsonLDDocument.requireResourcePropertyValue match {
        case (propertyIri: SmartIri, jsonLDObject: JsonLDObject) =>
          for {
            valueContent: ValueContentV2 <- ValueContentV2.fromJsonLDObject(
              jsonLDObject = jsonLDObject,
              requestingUser = requestingUser,
              responderManager = responderManager,
              storeManager = storeManager,
              featureFactoryConfig = featureFactoryConfig,
              settings = settings,
              log = log
            )

            // Get and validate the custom value IRI if provided.
            maybeCustomValueIri: Option[SmartIri] = jsonLDObject.maybeIDAsKnoraDataIri.map { definedNewIri =>
              stringFormatter.validateCustomValueIri(
                customValueIri = definedNewIri,
                resourceID = resourceIri.getResourceID.get
              )
            }

            // Get the custom value UUID if provided.
            maybeCustomUUID: Option[UUID] = jsonLDObject.maybeUUID(OntologyConstants.KnoraApiV2Complex.ValueHasUUID)

            // Get the value's creation date.
            // TODO: creationDate for values is a bug, and will not be supported in future. Use valueCreationDate instead.
            maybeCreationDate: Option[Instant] = jsonLDObject
              .maybeDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2Complex.ValueCreationDate,
                expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                validationFun = stringFormatter.xsdDateTimeStampToInstant
              )
              .orElse(jsonLDObject.maybeDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2Complex.CreationDate,
                expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                validationFun = stringFormatter.xsdDateTimeStampToInstant
              ))

            maybePermissions: Option[String] = jsonLDObject.maybeStringWithValidation(
              OntologyConstants.KnoraApiV2Complex.HasPermissions,
              stringFormatter.toSparqlEncodedString)
          } yield
            CreateValueV2(
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
    } yield
      CreateValueRequestV2(
        createValue = createValue,
        featureFactoryConfig = featureFactoryConfig,
        apiRequestID = apiRequestID,
        requestingUser = requestingUser
      )
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
case class CreateValueResponseV2(valueIri: IRI,
                                 valueType: SmartIri,
                                 valueUUID: UUID,
                                 valueCreationDate: Instant,
                                 projectADM: ProjectADM)
    extends KnoraJsonLDResponseV2
    with UpdateResultInProject {
  override def toJsonLDDocument(targetSchema: ApiV2Schema,
                                settings: KnoraSettingsImpl,
                                schemaOptions: Set[SchemaOption]): JsonLDDocument = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    if (targetSchema != ApiV2Complex) {
      throw AssertionException(s"CreateValueResponseV2 can only be returned in the complex schema")
    }

    JsonLDDocument(
      body = JsonLDObject(
        Map(
          JsonLDKeywords.ID -> JsonLDString(valueIri),
          JsonLDKeywords.TYPE -> JsonLDString(valueType.toOntologySchema(ApiV2Complex).toString),
          OntologyConstants.KnoraApiV2Complex.ValueHasUUID -> JsonLDString(stringFormatter.base64EncodeUuid(valueUUID)),
          OntologyConstants.KnoraApiV2Complex.ValueCreationDate -> JsonLDUtil.datatypeValueToJsonLDObject(
            value = valueCreationDate.toString,
            datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri
          )
        )
      ),
      context = JsonLDUtil.makeContext(
        fixedPrefixes = Map(
          OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion
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
  * @param featureFactoryConfig the feature factory configuration.
  * @param requestingUser       the user making the request.
  * @param apiRequestID         the API request ID.
  */
case class UpdateValueRequestV2(updateValue: UpdateValueV2,
                                featureFactoryConfig: FeatureFactoryConfig,
                                requestingUser: UserADM,
                                apiRequestID: UUID)
    extends ValuesResponderRequestV2

/**
  * Constructs [[UpdateValueRequestV2]] instances based on JSON-LD input.
  */
object UpdateValueRequestV2 extends KnoraJsonLDRequestReaderV2[UpdateValueRequestV2] {

  /**
    * Converts JSON-LD input to a [[CreateValueRequestV2]].
    *
    * @param jsonLDDocument       the JSON-LD input.
    * @param apiRequestID         the UUID of the API request.
    * @param requestingUser       the user making the request.
    * @param responderManager     a reference to the responder manager.
    * @param storeManager         a reference to the store manager.
    * @param featureFactoryConfig the feature factory configuration.
    * @param settings             the application settings.
    * @param log                  a logging adapter.
    * @return a case class instance representing the input.
    */
  override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                          apiRequestID: UUID,
                          requestingUser: UserADM,
                          responderManager: ActorRef,
                          storeManager: ActorRef,
                          featureFactoryConfig: FeatureFactoryConfig,
                          settings: KnoraSettingsImpl,
                          log: LoggingAdapter)(implicit timeout: Timeout,
                                               executionContext: ExecutionContext): Future[UpdateValueRequestV2] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    for {
      // Get the IRI of the resource that the value is to be created in.
      resourceIri: SmartIri <- Future(jsonLDDocument.requireIDAsKnoraDataIri)

      _ = if (!resourceIri.isKnoraResourceIri) {
        throw BadRequestException(s"Invalid resource IRI: <$resourceIri>")
      }

      // Get the resource class.
      resourceClassIri: SmartIri = jsonLDDocument.requireTypeAsKnoraTypeIri

      // Get the resource property and the new value version.
      updateValue: UpdateValueV2 <- jsonLDDocument.requireResourcePropertyValue match {
        case (propertyIri: SmartIri, jsonLDObject: JsonLDObject) =>
          val valueIri: SmartIri = jsonLDObject.requireIDAsKnoraDataIri

          // Get the custom value creation date, if provided.
          val maybeValueCreationDate: Option[Instant] = jsonLDObject.maybeDatatypeValueInObject(
            key = OntologyConstants.KnoraApiV2Complex.ValueCreationDate,
            expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
            validationFun = stringFormatter.xsdDateTimeStampToInstant
          )

          // Get and validate the custom new value version IRI, if provided.

          val maybeNewIri: Option[SmartIri] = jsonLDObject
            .maybeIriInObject(
              OntologyConstants.KnoraApiV2Complex.NewValueVersionIri,
              stringFormatter.toSmartIriWithErr
            )
            .map { definedNewIri =>
              if (definedNewIri == valueIri) {
                throw BadRequestException(
                  s"The IRI of a new value version cannot be the same as the IRI of the current version")
              }

              stringFormatter.validateCustomValueIri(
                customValueIri = definedNewIri,
                resourceID = valueIri.getResourceID.get
              )
            }

          // Aside from the value's ID and type and the optional predicates above, does the value object just
          // contain knora-api:hasPermissions?

          val otherValuePredicates: Set[IRI] = jsonLDObject.value.keySet -- Set(
            JsonLDKeywords.ID,
            JsonLDKeywords.TYPE,
            OntologyConstants.KnoraApiV2Complex.ValueCreationDate,
            OntologyConstants.KnoraApiV2Complex.NewValueVersionIri
          )

          if (otherValuePredicates == Set(OntologyConstants.KnoraApiV2Complex.HasPermissions)) {
            // Yes. This is a request to change the value's permissions.

            val valueType: SmartIri =
              jsonLDObject.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
            val permissions = jsonLDObject.requireStringWithValidation(
              OntologyConstants.KnoraApiV2Complex.HasPermissions,
              stringFormatter.toSparqlEncodedString)

            FastFuture.successful(
              UpdateValuePermissionsV2(
                resourceIri = resourceIri.toString,
                resourceClassIri = resourceClassIri,
                propertyIri = propertyIri,
                valueIri = valueIri.toString,
                valueType = valueType,
                permissions = permissions,
                valueCreationDate = maybeValueCreationDate,
                newValueVersionIri = maybeNewIri
              )
            )
          } else {
            // No. This is a request to change the value content.

            for {
              valueContent: ValueContentV2 <- ValueContentV2.fromJsonLDObject(
                jsonLDObject = jsonLDObject,
                requestingUser = requestingUser,
                responderManager = responderManager,
                storeManager = storeManager,
                featureFactoryConfig: FeatureFactoryConfig,
                settings = settings,
                log = log
              )

              maybePermissions: Option[String] = jsonLDObject.maybeStringWithValidation(
                OntologyConstants.KnoraApiV2Complex.HasPermissions,
                stringFormatter.toSparqlEncodedString)
            } yield
              UpdateValueContentV2(
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
      }
    } yield
      UpdateValueRequestV2(
        updateValue = updateValue,
        featureFactoryConfig = featureFactoryConfig,
        apiRequestID = apiRequestID,
        requestingUser = requestingUser
      )
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
  override def toJsonLDDocument(targetSchema: ApiV2Schema,
                                settings: KnoraSettingsImpl,
                                schemaOptions: Set[SchemaOption]): JsonLDDocument = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    if (targetSchema != ApiV2Complex) {
      throw AssertionException(s"UpdateValueResponseV2 can only be returned in the complex schema")
    }

    JsonLDDocument(
      body = JsonLDObject(
        Map(
          JsonLDKeywords.ID -> JsonLDString(valueIri),
          JsonLDKeywords.TYPE -> JsonLDString(valueType.toOntologySchema(ApiV2Complex).toString),
          OntologyConstants.KnoraApiV2Complex.ValueHasUUID -> JsonLDString(stringFormatter.base64EncodeUuid(valueUUID))
        )
      ),
      context = JsonLDUtil.makeContext(
        fixedPrefixes = Map(
          OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion
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
  * @param featureFactoryConfig the feature factory configuration.
  * @param requestingUser       the user making the request.
  * @param apiRequestID         the API request ID.
  */
case class DeleteValueRequestV2(resourceIri: IRI,
                                resourceClassIri: SmartIri,
                                propertyIri: SmartIri,
                                valueIri: IRI,
                                valueTypeIri: SmartIri,
                                deleteComment: Option[String] = None,
                                deleteDate: Option[Instant] = None,
                                featureFactoryConfig: FeatureFactoryConfig,
                                requestingUser: UserADM,
                                apiRequestID: UUID)
    extends ValuesResponderRequestV2

object DeleteValueRequestV2 extends KnoraJsonLDRequestReaderV2[DeleteValueRequestV2] {

  /**
    * Converts JSON-LD input into a case class instance.
    *
    * @param jsonLDDocument       the JSON-LD input.
    * @param apiRequestID         the UUID of the API request.
    * @param requestingUser       the user making the request.
    * @param responderManager     a reference to the responder manager.
    * @param storeManager         a reference to the store manager.
    * @param featureFactoryConfig the feature factory configuration.
    * @param settings             the application settings.
    * @param log                  a logging adapter.
    * @return a case class instance representing the input.
    */
  override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                          apiRequestID: UUID,
                          requestingUser: UserADM,
                          responderManager: ActorRef,
                          storeManager: ActorRef,
                          featureFactoryConfig: FeatureFactoryConfig,
                          settings: KnoraSettingsImpl,
                          log: LoggingAdapter)(implicit timeout: Timeout,
                                               executionContext: ExecutionContext): Future[DeleteValueRequestV2] = {
    Future {
      fromJsonLDSync(
        jsonLDDocument = jsonLDDocument,
        apiRequestID = apiRequestID,
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = requestingUser
      )
    }
  }

  private def fromJsonLDSync(jsonLDDocument: JsonLDDocument,
                             apiRequestID: UUID,
                             featureFactoryConfig: FeatureFactoryConfig,
                             requestingUser: UserADM): DeleteValueRequestV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    // Get the IRI of the resource that the value is to be created in.
    val resourceIri: SmartIri = jsonLDDocument.requireIDAsKnoraDataIri

    if (!resourceIri.isKnoraResourceIri) {
      throw BadRequestException(s"Invalid resource IRI: <$resourceIri>")
    }

    // Get the resource class.
    val resourceClassIri: SmartIri = jsonLDDocument.requireTypeAsKnoraTypeIri

    // Get the resource property and the IRI and class of the value to be deleted.
    jsonLDDocument.requireResourcePropertyValue match {
      case (propertyIri: SmartIri, jsonLDObject: JsonLDObject) =>
        val valueIri = jsonLDObject.requireIDAsKnoraDataIri

        if (!valueIri.isKnoraValueIri) {
          throw BadRequestException(s"Invalid value IRI: <$valueIri>")
        }

        val valueTypeIri: SmartIri = jsonLDObject.requireTypeAsKnoraApiV2ComplexTypeIri

        val deleteComment: Option[String] = jsonLDObject.maybeStringWithValidation(
          OntologyConstants.KnoraApiV2Complex.DeleteComment,
          stringFormatter.toSparqlEncodedString)

        val deleteDate: Option[Instant] = jsonLDObject.maybeDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.DeleteDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        DeleteValueRequestV2(
          resourceIri = resourceIri.toString,
          resourceClassIri = resourceClassIri,
          propertyIri = propertyIri,
          valueIri = valueIri.toString,
          valueTypeIri = valueTypeIri,
          deleteComment = deleteComment,
          deleteDate = deleteDate,
          featureFactoryConfig = featureFactoryConfig,
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
    requestingUser: UserADM)
    extends ValuesResponderRequestV2 {
  lazy val flatValues: Iterable[GenerateSparqlForValueInNewResourceV2] = values.values.flatten
}

case class GenerateSparqlForValueInNewResourceV2(valueContent: ValueContentV2,
                                                 customValueIri: Option[SmartIri],
                                                 customValueUUID: Option[UUID],
                                                 customValueCreationDate: Option[Instant],
                                                 permissions: String)
    extends IOValueV2

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
case class GenerateSparqlToCreateMultipleValuesResponseV2(insertSparql: String,
                                                          unverifiedValues: Map[SmartIri, Seq[UnverifiedValueV2]],
                                                          hasStandoffLink: Boolean)

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
      OntologyConstants.KnoraApiV2Complex.DeleteComment -> JsonLDString(deleteComment)
    }

    Map(
      OntologyConstants.KnoraApiV2Complex.IsDeleted -> JsonLDBoolean(true),
      OntologyConstants.KnoraApiV2Complex.DeleteDate -> JsonLDObject(
        Map(
          JsonLDKeywords.TYPE -> JsonLDString(OntologyConstants.Xsd.DateTimeStamp),
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
    * @param settings     the application settings.
    * @return a JSON-LD representation of this value.
    */
  def toJsonLD(targetSchema: ApiV2Schema,
               projectADM: ProjectADM,
               settings: KnoraSettingsImpl,
               schemaOptions: Set[SchemaOption]): JsonLDValue = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val valueContentAsJsonLD = valueContent.toJsonLDValue(
      targetSchema = targetSchema,
      projectADM = projectADM,
      settings = settings,
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
              JsonLDKeywords.ID -> JsonLDString(valueIri),
              JsonLDKeywords.TYPE -> JsonLDString(valueContent.valueType.toString),
              OntologyConstants.KnoraApiV2Complex.AttachedToUser -> JsonLDUtil.iriToJsonLDObject(attachedToUser),
              OntologyConstants.KnoraApiV2Complex.HasPermissions -> JsonLDString(permissions),
              OntologyConstants.KnoraApiV2Complex.UserHasPermission -> JsonLDString(userPermission.toString),
              OntologyConstants.KnoraApiV2Complex.ValueCreationDate -> JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueCreationDate.toString,
                datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri
              ),
              OntologyConstants.KnoraApiV2Complex.ValueHasUUID -> JsonLDString(
                stringFormatter.base64EncodeUuid(valueHasUUID)),
              OntologyConstants.KnoraApiV2Complex.ArkUrl -> JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueSmartIri.fromValueIriToArkUrl(valueUUID = valueHasUUID),
                datatype = OntologyConstants.Xsd.Uri.toSmartIri
              ),
              OntologyConstants.KnoraApiV2Complex.VersionArkUrl -> JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueSmartIri.fromValueIriToArkUrl(valueUUID = valueHasUUID,
                                                           maybeTimestamp = Some(valueCreationDate)),
                datatype = OntologyConstants.Xsd.Uri.toSmartIri
              )
            )

            val valueHasCommentAsJsonLD: Option[(IRI, JsonLDValue)] = valueContent.comment.map { definedComment =>
              OntologyConstants.KnoraApiV2Complex.ValueHasComment -> JsonLDString(definedComment)
            }

            val deletionInfoAsJsonLD: Map[IRI, JsonLDValue] = deletionInfo match {
              case Some(definedDeletionInfo) => definedDeletionInfo.toJsonLDFields(ApiV2Complex)
              case None                      => Map.empty[IRI, JsonLDValue]
            }

            JsonLDObject(jsonLDObject.value ++ requiredMetadata ++ valueHasCommentAsJsonLD ++ deletionInfoAsJsonLD)

          case other =>
            throw AssertionException(
              s"Expected value $valueIri to be a represented as a JSON-LD object in the complex schema, but found $other")
        }

      case ApiV2Simple => valueContentAsJsonLD
    }
  }
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
case class ReadTextValueV2(valueIri: IRI,
                           attachedToUser: IRI,
                           permissions: String,
                           userPermission: EntityPermission,
                           valueCreationDate: Instant,
                           valueHasUUID: UUID,
                           valueContent: TextValueContentV2,
                           valueHasMaxStandoffStartIndex: Option[Int],
                           previousValueIri: Option[IRI],
                           deletionInfo: Option[DeletionInfo])
    extends ReadValueV2
    with KnoraReadV2[ReadTextValueV2] {

  /**
    * Converts this value to the specified ontology schema.
    *
    * @param targetSchema the target schema.
    */
  override def toOntologySchema(targetSchema: ApiV2Schema): ReadTextValueV2 = {
    copy(valueContent = valueContent.toOntologySchema(targetSchema))
  }

  override def toJsonLD(targetSchema: ApiV2Schema,
                        projectADM: ProjectADM,
                        settings: KnoraSettingsImpl,
                        schemaOptions: Set[SchemaOption]): JsonLDValue = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val valueAsJsonLDValue: JsonLDValue = super.toJsonLD(
      targetSchema = targetSchema,
      projectADM = projectADM,
      settings = settings,
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
                    s"Expected value $valueIri to be a represented as a JSON-LD object in the complex schema, but found $other")
              }

              JsonLDObject(
                valueAsJsonLDObject.value ++ Map(
                  OntologyConstants.KnoraApiV2Complex.TextValueHasMarkup -> JsonLDBoolean(true),
                  OntologyConstants.KnoraApiV2Complex.TextValueHasMaxStandoffStartIndex -> JsonLDInt(maxStartIndex)
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
case class ReadLinkValueV2(valueIri: IRI,
                           attachedToUser: IRI,
                           permissions: String,
                           userPermission: EntityPermission,
                           valueCreationDate: Instant,
                           valueHasUUID: UUID,
                           valueContent: LinkValueContentV2,
                           valueHasRefCount: Int,
                           previousValueIri: Option[IRI] = None,
                           deletionInfo: Option[DeletionInfo])
    extends ReadValueV2
    with KnoraReadV2[ReadLinkValueV2] {

  /**
    * Converts this value to the specified ontology schema.
    *
    * @param targetSchema the target schema.
    */
  override def toOntologySchema(targetSchema: ApiV2Schema): ReadLinkValueV2 = {
    copy(valueContent = valueContent.toOntologySchema(targetSchema))
  }
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
case class ReadOtherValueV2(valueIri: IRI,
                            attachedToUser: IRI,
                            permissions: String,
                            userPermission: EntityPermission,
                            valueCreationDate: Instant,
                            valueHasUUID: UUID,
                            valueContent: ValueContentV2,
                            previousValueIri: Option[IRI],
                            deletionInfo: Option[DeletionInfo])
    extends ReadValueV2
    with KnoraReadV2[ReadOtherValueV2] {

  /**
    * Converts this value to the specified ontology schema.
    *
    * @param targetSchema the target schema.
    */
  override def toOntologySchema(targetSchema: ApiV2Schema): ReadOtherValueV2 = {
    copy(valueContent = valueContent.toOntologySchema(targetSchema))
  }
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
case class CreateValueV2(resourceIri: IRI,
                         resourceClassIri: SmartIri,
                         propertyIri: SmartIri,
                         valueContent: ValueContentV2,
                         valueIri: Option[SmartIri] = None,
                         valueUUID: Option[UUID] = None,
                         valueCreationDate: Option[Instant] = None,
                         permissions: Option[String] = None)
    extends IOValueV2

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
case class UpdateValueContentV2(resourceIri: IRI,
                                resourceClassIri: SmartIri,
                                propertyIri: SmartIri,
                                valueIri: IRI,
                                valueContent: ValueContentV2,
                                permissions: Option[String] = None,
                                valueCreationDate: Option[Instant] = None,
                                newValueVersionIri: Option[SmartIri] = None)
    extends IOValueV2
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
case class UpdateValuePermissionsV2(resourceIri: IRI,
                                    resourceClassIri: SmartIri,
                                    propertyIri: SmartIri,
                                    valueIri: IRI,
                                    valueType: SmartIri,
                                    permissions: String,
                                    valueCreationDate: Option[Instant] = None,
                                    newValueVersionIri: Option[SmartIri] = None)
    extends UpdateValueV2

/**
  * The IRI and content of a new value or value version whose existence in the triplestore needs to be verified.
  *
  * @param newValueIri  the IRI that was assigned to the new value.
  * @param newValueUUID the UUID attached to the new value.
  * @param valueContent the content of the new value (unescaped, as it would be read from the triplestore).
  * @param permissions  the permissions of the new value.
  * @param creationDate the new value's creation date.
  */
case class UnverifiedValueV2(newValueIri: IRI,
                             newValueUUID: UUID,
                             valueContent: ValueContentV2,
                             permissions: String,
                             creationDate: Instant)

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
    * @param settings     the configuration options.
    * @return a [[JsonLDValue]] that can be used to generate JSON-LD representing this value.
    */
  def toJsonLDValue(targetSchema: ApiV2Schema,
                    projectADM: ProjectADM,
                    settings: KnoraSettingsImpl,
                    schemaOptions: Set[SchemaOption]): JsonLDValue

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
  * A trait for objects that can convert JSON-LD objects into value content objects (subclasses of [[ValueContentV2]]).
  *
  * @tparam C a subclass of [[ValueContentV2]].
  */
trait ValueContentReaderV2[C <: ValueContentV2] {

  /**
    * Converts a JSON-LD object to a subclass of [[ValueContentV2]].
    *
    * @param jsonLDObject         the JSON-LD object.
    * @param requestingUser       the user making the request.
    * @param responderManager     a reference to the responder manager.
    * @param storeManager         a reference to the store manager.
    * @param featureFactoryConfig the feature factory configuration.
    * @param settings             the application settings.
    * @param log                  a logging adapter.
    * @return a subclass of [[ValueContentV2]].
    */
  def fromJsonLDObject(jsonLDObject: JsonLDObject,
                       requestingUser: UserADM,
                       responderManager: ActorRef,
                       storeManager: ActorRef,
                       featureFactoryConfig: FeatureFactoryConfig,
                       settings: KnoraSettingsImpl,
                       log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[C]

  protected def getComment(jsonLDObject: JsonLDObject)(implicit stringFormatter: StringFormatter): Option[String] = {
    jsonLDObject.maybeStringWithValidation(OntologyConstants.KnoraApiV2Complex.ValueHasComment,
                                           stringFormatter.toSparqlEncodedString)
  }
}

/**
  * Generates instances of value content classes (subclasses of [[ValueContentV2]]) from JSON-LD input.
  */
object ValueContentV2 extends ValueContentReaderV2[ValueContentV2] {

  /**
    * Converts a JSON-LD object to a [[ValueContentV2]].
    *
    * @param jsonLDObject         the JSON-LD object.
    * @param requestingUser       the user making the request.
    * @param responderManager     a reference to the responder manager.
    * @param storeManager         a reference to the store manager.
    * @param featureFactoryConfig the feature factory configuration.
    * @param settings             the application settings.
    * @param log                  a logging adapter.
    * @return a [[ValueContentV2]].
    */
  override def fromJsonLDObject(
      jsonLDObject: JsonLDObject,
      requestingUser: UserADM,
      responderManager: ActorRef,
      storeManager: ActorRef,
      featureFactoryConfig: FeatureFactoryConfig,
      settings: KnoraSettingsImpl,
      log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ValueContentV2] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    for {
      valueType: SmartIri <- Future(
        jsonLDObject.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr))

      valueContent: ValueContentV2 <- valueType.toString match {
        case OntologyConstants.KnoraApiV2Complex.TextValue =>
          TextValueContentV2.fromJsonLDObject(
            jsonLDObject = jsonLDObject,
            requestingUser = requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            log = log
          )

        case OntologyConstants.KnoraApiV2Complex.IntValue =>
          IntegerValueContentV2.fromJsonLDObject(
            jsonLDObject = jsonLDObject,
            requestingUser = requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            log = log
          )

        case OntologyConstants.KnoraApiV2Complex.DecimalValue =>
          DecimalValueContentV2.fromJsonLDObject(
            jsonLDObject = jsonLDObject,
            requestingUser = requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            log = log
          )

        case OntologyConstants.KnoraApiV2Complex.BooleanValue =>
          BooleanValueContentV2.fromJsonLDObject(
            jsonLDObject = jsonLDObject,
            requestingUser = requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            log = log
          )

        case OntologyConstants.KnoraApiV2Complex.DateValue =>
          DateValueContentV2.fromJsonLDObject(
            jsonLDObject = jsonLDObject,
            requestingUser = requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            log = log
          )

        case OntologyConstants.KnoraApiV2Complex.GeomValue =>
          GeomValueContentV2.fromJsonLDObject(
            jsonLDObject = jsonLDObject,
            requestingUser = requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            log = log
          )

        case OntologyConstants.KnoraApiV2Complex.IntervalValue =>
          IntervalValueContentV2.fromJsonLDObject(
            jsonLDObject = jsonLDObject,
            requestingUser = requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            log = log
          )

        case OntologyConstants.KnoraApiV2Complex.TimeValue =>
          TimeValueContentV2.fromJsonLDObject(
            jsonLDObject = jsonLDObject,
            requestingUser = requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            log = log
          )

        case OntologyConstants.KnoraApiV2Complex.LinkValue =>
          LinkValueContentV2.fromJsonLDObject(
            jsonLDObject = jsonLDObject,
            requestingUser = requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            log = log
          )

        case OntologyConstants.KnoraApiV2Complex.ListValue =>
          HierarchicalListValueContentV2.fromJsonLDObject(
            jsonLDObject = jsonLDObject,
            requestingUser = requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            log = log
          )

        case OntologyConstants.KnoraApiV2Complex.UriValue =>
          UriValueContentV2.fromJsonLDObject(
            jsonLDObject = jsonLDObject,
            requestingUser = requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            log = log
          )

        case OntologyConstants.KnoraApiV2Complex.GeonameValue =>
          GeonameValueContentV2.fromJsonLDObject(
            jsonLDObject = jsonLDObject,
            requestingUser = requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            log = log
          )

        case OntologyConstants.KnoraApiV2Complex.ColorValue =>
          ColorValueContentV2.fromJsonLDObject(
            jsonLDObject = jsonLDObject,
            requestingUser = requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            log = log
          )

        case OntologyConstants.KnoraApiV2Complex.StillImageFileValue =>
          StillImageFileValueContentV2.fromJsonLDObject(
            jsonLDObject = jsonLDObject,
            requestingUser = requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            log = log
          )

        case OntologyConstants.KnoraApiV2Complex.DocumentFileValue =>
          DocumentFileValueContentV2.fromJsonLDObject(
            jsonLDObject = jsonLDObject,
            requestingUser = requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            log = log
          )

        case OntologyConstants.KnoraApiV2Complex.TextFileValue =>
          TextFileValueContentV2.fromJsonLDObject(
            jsonLDObject = jsonLDObject,
            requestingUser = requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            log = log
          )

        case OntologyConstants.KnoraApiV2Complex.AudioFileValue =>
          AudioFileValueContentV2.fromJsonLDObject(
            jsonLDObject = jsonLDObject,
            requestingUser = requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            log = log
          )

        case other => throw NotImplementedException(s"Parsing of JSON-LD value type not implemented: $other")
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
case class DateValueContentV2(ontologySchema: OntologySchema,
                              valueHasStartJDN: Int,
                              valueHasEndJDN: Int,
                              valueHasStartPrecision: DatePrecisionV2,
                              valueHasEndPrecision: DatePrecisionV2,
                              valueHasCalendar: CalendarNameV2,
                              comment: Option[String] = None)
    extends ValueContentV2 {
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

  override def toJsonLDValue(targetSchema: ApiV2Schema,
                             projectADM: ProjectADM,
                             settings: KnoraSettingsImpl,
                             schemaOptions: Set[SchemaOption]): JsonLDValue = {
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasString,
          datatype = OntologyConstants.KnoraApiV2Simple.Date.toSmartIri
        )

      case ApiV2Complex =>
        val startCalendarDate: CalendarDateV2 = asCalendarDateRange.startCalendarDate
        val endCalendarDate: CalendarDateV2 = asCalendarDateRange.endCalendarDate

        val startDateAssertions = Map(
          OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear -> JsonLDInt(startCalendarDate.year)) ++
          startCalendarDate.maybeMonth.map(month =>
            OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth -> JsonLDInt(month)) ++
          startCalendarDate.maybeDay.map(day =>
            OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay -> JsonLDInt(day)) ++
          startCalendarDate.maybeEra.map(era =>
            OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra -> JsonLDString(era.toString))

        val endDateAssertions = Map(
          OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear -> JsonLDInt(endCalendarDate.year)) ++
          endCalendarDate.maybeMonth.map(month =>
            OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth -> JsonLDInt(month)) ++
          endCalendarDate.maybeDay.map(day => OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay -> JsonLDInt(day)) ++
          endCalendarDate.maybeEra.map(era =>
            OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra -> JsonLDString(era.toString))

        JsonLDObject(
          Map(
            OntologyConstants.KnoraApiV2Complex.ValueAsString -> JsonLDString(valueHasString),
            OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar -> JsonLDString(valueHasCalendar.toString)
          ) ++ startDateAssertions ++ endDateAssertions)
    }
  }

  override def unescape: ValueContentV2 = {
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
    that match {
      case thatDateValue: DateValueContentV2 =>
        valueHasStartJDN == thatDateValue.valueHasStartJDN &&
          valueHasEndJDN == thatDateValue.valueHasEndJDN &&
          valueHasStartPrecision == thatDateValue.valueHasStartPrecision &&
          valueHasEndPrecision == thatDateValue.valueHasEndPrecision &&
          valueHasCalendar == thatDateValue.valueHasCalendar

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }
  }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
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
}

/**
  * Constructs [[DateValueContentV2]] objects based on JSON-LD input.
  */
object DateValueContentV2 extends ValueContentReaderV2[DateValueContentV2] {

  /**
    * Parses a string representing a date range in API v2 simple format.
    *
    * @param dateStr the string to be parsed.
    * @return a [[DateValueContentV2]] representing the date range.
    */
  def parse(dateStr: String): DateValueContentV2 = {
    val dateRange: CalendarDateRangeV2 = CalendarDateRangeV2.parse(dateStr)
    val (startJDN: Int, endJDN: Int) = dateRange.toJulianDayRange

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
    * @param responderManager     a reference to the responder manager.
    * @param storeManager         a reference to the store manager.
    * @param featureFactoryConfig the feature factory configuration.
    * @param settings             the application settings.
    * @param log                  a logging adapter.
    * @return a [[DateValueContentV2]].
    */
  override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                requestingUser: UserADM,
                                responderManager: ActorRef,
                                storeManager: ActorRef,
                                featureFactoryConfig: FeatureFactoryConfig,
                                settings: KnoraSettingsImpl,
                                log: LoggingAdapter)(implicit timeout: Timeout,
                                                     executionContext: ExecutionContext): Future[DateValueContentV2] = {
    Future(fromJsonLDObjectSync(jsonLDObject))
  }

  private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): DateValueContentV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    // Get the values given in the the JSON-LD object.

    val calendarName: CalendarNameV2 = jsonLDObject.requireStringWithValidation(
      OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar,
      CalendarNameV2.parse)

    val dateValueHasStartYear: Int = jsonLDObject.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear)
    val maybeDateValueHasStartMonth: Option[Int] =
      jsonLDObject.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth)
    val maybeDateValueHasStartDay: Option[Int] =
      jsonLDObject.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay)
    val maybeDateValueHasStartEra: Option[DateEraV2] =
      jsonLDObject.maybeStringWithValidation(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra, DateEraV2.parse)

    val dateValueHasEndYear: Int = jsonLDObject.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear)
    val maybeDateValueHasEndMonth: Option[Int] =
      jsonLDObject.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth)
    val maybeDateValueHasEndDay: Option[Int] =
      jsonLDObject.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay)
    val maybeDateValueHasEndEra: Option[DateEraV2] =
      jsonLDObject.maybeStringWithValidation(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra, DateEraV2.parse)

    // Check that the date precisions are valid.

    if (maybeDateValueHasStartMonth.isEmpty && maybeDateValueHasStartDay.isDefined) {
      throw AssertionException(s"Invalid date: $jsonLDObject")
    }

    if (maybeDateValueHasEndMonth.isEmpty && maybeDateValueHasEndDay.isDefined) {
      throw AssertionException(s"Invalid date: $jsonLDObject")
    }

    // Check that the era is given if required.

    calendarName match {
      case _: CalendarNameGregorianOrJulian =>
        if (maybeDateValueHasStartEra.isEmpty || maybeDateValueHasEndEra.isEmpty) {
          throw AssertionException(s"Era is required in calendar $calendarName")
        }

      case _ => ()
    }

    // Construct a CalendarDateRangeV2 representing the start and end dates.

    val startCalendarDate = CalendarDateV2(
      calendarName = calendarName,
      year = dateValueHasStartYear,
      maybeMonth = maybeDateValueHasStartMonth,
      maybeDay = maybeDateValueHasStartDay,
      maybeEra = maybeDateValueHasStartEra
    )

    val endCalendarDate = CalendarDateV2(
      calendarName = calendarName,
      year = dateValueHasEndYear,
      maybeMonth = maybeDateValueHasEndMonth,
      maybeDay = maybeDateValueHasEndDay,
      maybeEra = maybeDateValueHasEndEra
    )

    val dateRange = CalendarDateRangeV2(
      startCalendarDate = startCalendarDate,
      endCalendarDate = endCalendarDate
    )

    // Convert the CalendarDateRangeV2 to start and end Julian Day Numbers.

    val (startJDN: Int, endJDN: Int) = dateRange.toJulianDayRange

    DateValueContentV2(
      ontologySchema = ApiV2Complex,
      valueHasStartJDN = startJDN,
      valueHasEndJDN = endJDN,
      valueHasStartPrecision = startCalendarDate.precision,
      valueHasEndPrecision = endCalendarDate.precision,
      valueHasCalendar = calendarName,
      comment = getComment(jsonLDObject)
    )
  }
}

/**
  * Represents a [[StandoffTagV2]] for a standoff tag of a certain type (standoff tag class) that is about to be created in the triplestore.
  *
  * @param standoffNode           the standoff node to be created.
  * @param standoffTagInstanceIri the standoff node's IRI.
  * @param startParentIri         the IRI of the parent of the start tag.
  * @param endParentIri           the IRI of the parent of the end tag, if any.
  */
case class CreateStandoffTagV2InTriplestore(standoffNode: StandoffTagV2,
                                            standoffTagInstanceIri: IRI,
                                            startParentIri: Option[IRI] = None,
                                            endParentIri: Option[IRI] = None)

/**
  * Represents a Knora text value, or a page of standoff markup that will be included in a text value.
  *
  * @param maybeValueHasString the string representation of this text value, if available.
  * @param standoff            the standoff markup attached to the text value, if any.
  * @param mappingIri          the IRI of the [[MappingXMLtoStandoff]] used by default with the text value, if any.
  * @param mapping             the [[MappingXMLtoStandoff]] used by default with the text value, if any.
  * @param comment             a comment on this [[TextValueContentV2]], if any.
  */
case class TextValueContentV2(ontologySchema: OntologySchema,
                              maybeValueHasString: Option[String],
                              valueHasLanguage: Option[String] = None,
                              standoff: Seq[StandoffTagV2] = Vector.empty,
                              mappingIri: Option[IRI] = None,
                              mapping: Option[MappingXMLtoStandoff] = None,
                              xslt: Option[String] = None,
                              comment: Option[String] = None)
    extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.TextValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  /**
    * Returns the IRIs of any resources that are target of standoff link tags in this text value.
    */
  lazy val standoffLinkTagTargetResourceIris: Set[IRI] = {
    standoffLinkTagIriAttributes.map(_.value)
  }

  /**
    * Returns the IRI attributes representing the target IRIs of any standoff links in this text value.
    */
  lazy val standoffLinkTagIriAttributes: Set[StandoffTagIriAttributeV2] = {
    standoff.foldLeft(Set.empty[StandoffTagIriAttributeV2]) {
      case (acc, standoffTag: StandoffTagV2) =>
        if (standoffTag.dataType.contains(StandoffDataTypeClasses.StandoffLinkTag)) {
          val iriAttributes: Set[StandoffTagIriAttributeV2] = standoffTag.attributes.collect {
            case iriAttribute: StandoffTagIriAttributeV2 => iriAttribute
          }.toSet

          acc ++ iriAttributes
        } else {
          acc
        }
    }

  }

  override def valueHasString: String =
    maybeValueHasString.getOrElse(throw AssertionException("Text value has no valueHasString"))

  /**
    * The content of the text value without standoff, suitable for returning in API responses. This removes
    * INFORMATION SEPARATOR TWO, which is used only internally.
    */
  lazy val valueHasStringWithoutStandoff: String =
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

  override def toJsonLDValue(targetSchema: ApiV2Schema,
                             projectADM: ProjectADM,
                             settings: KnoraSettingsImpl,
                             schemaOptions: Set[SchemaOption]): JsonLDValue = {
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
        val renderStandoffAsXml: Boolean = standoff.nonEmpty && SchemaOptions.renderMarkupAsXml(targetSchema =
                                                                                                  targetSchema,
                                                                                                schemaOptions =
                                                                                                  schemaOptions)

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
              Map(OntologyConstants.KnoraApiV2Complex.TextValueAsHtml -> JsonLDString(xmlTransformed))

            case None =>
              Map(
                OntologyConstants.KnoraApiV2Complex.TextValueAsXml -> JsonLDString(xmlFromStandoff),
                OntologyConstants.KnoraApiV2Complex.TextValueHasMapping -> JsonLDUtil.iriToJsonLDObject(
                  definedMappingIri)
              )
          }
        } else {
          // We're not rendering standoff as XML. Return the text without markup.
          Map(OntologyConstants.KnoraApiV2Complex.ValueAsString -> JsonLDString(valueHasStringWithoutStandoff))
        }

        // In the complex schema, if this text value specifies a language, return it using the predicate
        // knora-api:textValueHasLanguage.
        val objectMapWithLanguage: Map[IRI, JsonLDValue] = valueHasLanguage match {
          case Some(lang) =>
            objectMap + (OntologyConstants.KnoraApiV2Complex.TextValueHasLanguage -> JsonLDString(lang))
          case None =>
            objectMap
        }

        JsonLDObject(objectMapWithLanguage)
    }
  }

  /**
    * A convenience method that creates an IRI for each [[StandoffTagV2]] and resolves internal references to standoff node Iris.
    *
    * @return a list of [[CreateStandoffTagV2InTriplestore]] each representing a [[StandoffTagV2]] object
    *         along with is standoff tag class and IRI that is going to identify it in the triplestore.
    */
  def prepareForSparqlInsert(valueIri: IRI): Seq[CreateStandoffTagV2InTriplestore] = {

    if (standoff.nonEmpty) {
      // create an IRI for each standoff tag
      // internal references to XML ids are not resolved yet
      val standoffTagsWithOriginalXMLIDs: Seq[CreateStandoffTagV2InTriplestore] = standoff.map {
        standoffNode: StandoffTagV2 =>
          CreateStandoffTagV2InTriplestore(
            standoffNode = standoffNode,
            standoffTagInstanceIri = stringFormatter.makeStandoffTagIri(
              valueIri = valueIri,
              startIndex = standoffNode.startIndex) // generate IRI for new standoff node
          )
      }

      // collect all the standoff tags that contain XML ids and
      // map the XML ids to standoff node Iris
      val iDsToStandoffNodeIris: Map[IRI, IRI] = standoffTagsWithOriginalXMLIDs
        .filter { standoffTag: CreateStandoffTagV2InTriplestore =>
          // filter those tags out that have an XML id
          standoffTag.standoffNode.originalXMLID.isDefined
        }
        .map { standoffTagWithID: CreateStandoffTagV2InTriplestore =>
          // return the XML id as a key and the standoff IRI as the value
          standoffTagWithID.standoffNode.originalXMLID.get -> standoffTagWithID.standoffTagInstanceIri
        }
        .toMap

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
            standoffTag.standoffNode.attributes.map { attributeWithOriginalXMLID: StandoffTagAttributeV2 =>
              attributeWithOriginalXMLID match {
                case refAttr: StandoffTagInternalReferenceAttributeV2 =>
                  // resolve the XML id to the corresponding standoff node IRI
                  refAttr.copy(value = iDsToStandoffNodeIris(refAttr.value))
                case attr => attr
              }
            }

          val startParentIndex: Option[Int] = standoffTag.standoffNode.startParentIndex
          val endParentIndex: Option[Int] = standoffTag.standoffNode.endParentIndex

          // return standoff tag with updated attributes
          standoffTag.copy(
            standoffNode = standoffTag.standoffNode.copy(attributes = attributesWithStandoffNodeIriReferences),
            startParentIri = startParentIndex.map(parentIndex => startIndexesToStandoffNodeIris(parentIndex)), // If there's a start parent index, get its IRI, otherwise None
            endParentIri = endParentIndex.map(parentIndex => startIndexesToStandoffNodeIris(parentIndex)) // If there's an end parent index, get its IRI, otherwise None
          )
      }

      standoffTagsWithNodeReferences
    } else {
      Seq.empty[CreateStandoffTagV2InTriplestore]
    }

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

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
    // It doesn't make sense for a resource to have two different text values associated with the same property,
    // containing the same text but different markup.
    that match {
      case thatTextValue: TextValueContentV2 => valueHasString == thatTextValue.valueHasString

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }
  }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
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
}

/**
  * Constructs [[TextValueContentV2]] objects based on JSON-LD input.
  */
object TextValueContentV2 extends ValueContentReaderV2[TextValueContentV2] {

  /**
    * Converts a JSON-LD object to a [[TextValueContentV2]].
    *
    * @param jsonLDObject         the JSON-LD object.
    * @param requestingUser       the user making the request.
    * @param responderManager     a reference to the responder manager.
    * @param storeManager         a reference to the store manager.
    * @param featureFactoryConfig the feature factory configuration.
    * @param settings             the application settings.
    * @param log                  a logging adapter.
    * @return a [[TextValueContentV2]].
    */
  override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                requestingUser: UserADM,
                                responderManager: ActorRef,
                                storeManager: ActorRef,
                                featureFactoryConfig: FeatureFactoryConfig,
                                settings: KnoraSettingsImpl,
                                log: LoggingAdapter)(implicit timeout: Timeout,
                                                     executionContext: ExecutionContext): Future[TextValueContentV2] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    for {
      maybeValueAsString: Option[String] <- Future(
        jsonLDObject.maybeStringWithValidation(OntologyConstants.KnoraApiV2Complex.ValueAsString,
                                               stringFormatter.toSparqlEncodedString))
      maybeValueHasLanguage: Option[String] = jsonLDObject.maybeStringWithValidation(
        OntologyConstants.KnoraApiV2Complex.TextValueHasLanguage,
        stringFormatter.toSparqlEncodedString)
      maybeTextValueAsXml: Option[String] = jsonLDObject.maybeString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml)
      maybeMappingIri: Option[IRI] = jsonLDObject.maybeIriInObject(
        OntologyConstants.KnoraApiV2Complex.TextValueHasMapping,
        stringFormatter.validateAndEscapeIri)

      // If the client supplied the IRI of a standoff-to-XML mapping, get the mapping.

      maybeMappingFuture: Option[Future[GetMappingResponseV2]] = maybeMappingIri.map { mappingIri =>
        for {
          mappingResponse: GetMappingResponseV2 <- (responderManager ? GetMappingRequestV2(
            mappingIri = mappingIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )).mapTo[GetMappingResponseV2]
        } yield mappingResponse
      }

      maybeMappingResponse: Option[GetMappingResponseV2] <- ActorUtil.optionFuture2FutureOption(maybeMappingFuture)

      // Did the client submit text with or without standoff markup?
      textValue: TextValueContentV2 = (maybeValueAsString, maybeTextValueAsXml, maybeMappingResponse) match {
        case (Some(valueAsString), None, None) =>
          // Text without standoff.
          TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueAsString),
            comment = getComment(jsonLDObject)
          )

        case (None, Some(textValueAsXml), Some(mappingResponse)) =>
          // Text with standoff. TODO: support submitting text with standoff as JSON-LD rather than as XML.

          val textWithStandoffTags: TextWithStandoffTagsV2 = StandoffTagUtilV2.convertXMLtoStandoffTagV2(
            xml = textValueAsXml,
            mapping = mappingResponse,
            acceptStandoffLinksToClientIDs = false,
            log = log
          )

          TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(
              stringFormatter.toSparqlEncodedString(
                textWithStandoffTags.text,
                throw BadRequestException("Text value contains invalid characters"))),
            valueHasLanguage = maybeValueHasLanguage,
            standoff = textWithStandoffTags.standoffTagV2,
            mappingIri = Some(mappingResponse.mappingIri),
            mapping = Some(mappingResponse.mapping),
            comment = getComment(jsonLDObject)
          )

        case _ =>
          throw BadRequestException(
            s"Invalid combination of knora-api:valueHasString, knora-api:textValueAsXml, and/or knora-api:textValueHasMapping")
      }

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

  override def toJsonLDValue(targetSchema: ApiV2Schema,
                             projectADM: ProjectADM,
                             settings: KnoraSettingsImpl,
                             schemaOptions: Set[SchemaOption]): JsonLDValue = {
    targetSchema match {
      case ApiV2Simple => JsonLDInt(valueHasInteger)

      case ApiV2Complex =>
        JsonLDObject(Map(OntologyConstants.KnoraApiV2Complex.IntValueAsInt -> JsonLDInt(valueHasInteger)))

    }
  }

  override def unescape: ValueContentV2 = {
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
    that match {
      case thatIntegerValue: IntegerValueContentV2 => valueHasInteger == thatIntegerValue.valueHasInteger
      case _                                       => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }
  }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
    currentVersion match {
      case thatIntegerValue: IntegerValueContentV2 =>
        valueHasInteger == thatIntegerValue.valueHasInteger &&
          comment == thatIntegerValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
  }
}

/**
  * Constructs [[IntegerValueContentV2]] objects based on JSON-LD input.
  */
object IntegerValueContentV2 extends ValueContentReaderV2[IntegerValueContentV2] {

  /**
    * Converts a JSON-LD object to an [[IntegerValueContentV2]].
    *
    * @param jsonLDObject     the JSON-LD object.
    * @param requestingUser   the user making the request.
    * @param responderManager a reference to the responder manager.
    * @param storeManager     a reference to the store manager.
    * @param log              a logging adapter.
    * @param timeout          a timeout for `ask` messages.
    * @param executionContext an execution context for futures.
    * @return an [[IntegerValueContentV2]].
    */
  override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                requestingUser: UserADM,
                                responderManager: ActorRef,
                                storeManager: ActorRef,
                                featureFactoryConfig: FeatureFactoryConfig,
                                settings: KnoraSettingsImpl,
                                log: LoggingAdapter)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[IntegerValueContentV2] = {
    Future(fromJsonLDObjectSync(jsonLDObject))
  }

  private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): IntegerValueContentV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val intValueAsInt: Int = jsonLDObject.requireInt(OntologyConstants.KnoraApiV2Complex.IntValueAsInt)

    IntegerValueContentV2(
      ontologySchema = ApiV2Complex,
      valueHasInteger = intValueAsInt,
      comment = getComment(jsonLDObject)
    )
  }
}

/**
  * Represents a Knora decimal value.
  *
  * @param valueHasDecimal the decimal value.
  * @param comment         a comment on this [[DecimalValueContentV2]], if any.
  */
case class DecimalValueContentV2(ontologySchema: OntologySchema,
                                 valueHasDecimal: BigDecimal,
                                 comment: Option[String] = None)
    extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.DecimalValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override lazy val valueHasString: String = valueHasDecimal.toString

  override def toOntologySchema(targetSchema: OntologySchema): DecimalValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(targetSchema: ApiV2Schema,
                             projectADM: ProjectADM,
                             settings: KnoraSettingsImpl,
                             schemaOptions: Set[SchemaOption]): JsonLDValue = {
    val decimalValueAsJsonLDObject = JsonLDUtil.datatypeValueToJsonLDObject(
      value = valueHasDecimal.toString,
      datatype = OntologyConstants.Xsd.Decimal.toSmartIri
    )

    targetSchema match {
      case ApiV2Simple => decimalValueAsJsonLDObject

      case ApiV2Complex =>
        JsonLDObject(Map(OntologyConstants.KnoraApiV2Complex.DecimalValueAsDecimal -> decimalValueAsJsonLDObject))
    }
  }

  override def unescape: ValueContentV2 = {
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
    that match {
      case thatDecimalValue: DecimalValueContentV2 => valueHasDecimal == thatDecimalValue.valueHasDecimal
      case _                                       => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }
  }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
    currentVersion match {
      case thatDecimalValue: DecimalValueContentV2 =>
        valueHasDecimal == thatDecimalValue.valueHasDecimal &&
          comment == thatDecimalValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
  }
}

/**
  * Constructs [[DecimalValueContentV2]] objects based on JSON-LD input.
  */
object DecimalValueContentV2 extends ValueContentReaderV2[DecimalValueContentV2] {

  /**
    * Converts a JSON-LD object to a [[DecimalValueContentV2]].
    *
    * @param jsonLDObject     the JSON-LD object.
    * @param requestingUser   the user making the request.
    * @param responderManager a reference to the responder manager.
    * @param storeManager     a reference to the store manager.
    * @param log              a logging adapter.
    * @param timeout          a timeout for `ask` messages.
    * @param executionContext an execution context for futures.
    * @return an [[DecimalValueContentV2]].
    */
  override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                requestingUser: UserADM,
                                responderManager: ActorRef,
                                storeManager: ActorRef,
                                featureFactoryConfig: FeatureFactoryConfig,
                                settings: KnoraSettingsImpl,
                                log: LoggingAdapter)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[DecimalValueContentV2] = {
    Future(fromJsonLDObjectSync(jsonLDObject))
  }

  private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): DecimalValueContentV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val decimalValueAsDecimal: BigDecimal = jsonLDObject.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.DecimalValueAsDecimal,
      expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
      validationFun = stringFormatter.validateBigDecimal
    )

    DecimalValueContentV2(
      ontologySchema = ApiV2Complex,
      valueHasDecimal = decimalValueAsDecimal,
      comment = getComment(jsonLDObject)
    )
  }
}

/**
  * Represents a Boolean value.
  *
  * @param valueHasBoolean the Boolean value.
  * @param comment         a comment on this [[BooleanValueContentV2]], if any.
  */
case class BooleanValueContentV2(ontologySchema: OntologySchema,
                                 valueHasBoolean: Boolean,
                                 comment: Option[String] = None)
    extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.BooleanValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override lazy val valueHasString: String = valueHasBoolean.toString

  override def toOntologySchema(targetSchema: OntologySchema): BooleanValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(targetSchema: ApiV2Schema,
                             projectADM: ProjectADM,
                             settings: KnoraSettingsImpl,
                             schemaOptions: Set[SchemaOption]): JsonLDValue = {
    targetSchema match {
      case ApiV2Simple => JsonLDBoolean(valueHasBoolean)

      case ApiV2Complex =>
        JsonLDObject(Map(OntologyConstants.KnoraApiV2Complex.BooleanValueAsBoolean -> JsonLDBoolean(valueHasBoolean)))
    }
  }

  override def unescape: ValueContentV2 = {
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
    // Always returns true, because it doesn't make sense to have two instances of the same boolean property.
    true
  }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
    currentVersion match {
      case thatBooleanValue: BooleanValueContentV2 =>
        valueHasBoolean == thatBooleanValue.valueHasBoolean &&
          comment == thatBooleanValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
  }
}

/**
  * Constructs [[BooleanValueContentV2]] objects based on JSON-LD input.
  */
object BooleanValueContentV2 extends ValueContentReaderV2[BooleanValueContentV2] {

  /**
    * Converts a JSON-LD object to a [[BooleanValueContentV2]].
    *
    * @param jsonLDObject     the JSON-LD object.
    * @param requestingUser   the user making the request.
    * @param responderManager a reference to the responder manager.
    * @param storeManager     a reference to the store manager.
    * @param log              a logging adapter.
    * @param timeout          a timeout for `ask` messages.
    * @param executionContext an execution context for futures.
    * @return an [[BooleanValueContentV2]].
    */
  override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                requestingUser: UserADM,
                                responderManager: ActorRef,
                                storeManager: ActorRef,
                                featureFactoryConfig: FeatureFactoryConfig,
                                settings: KnoraSettingsImpl,
                                log: LoggingAdapter)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[BooleanValueContentV2] = {
    Future(fromJsonLDObjectSync(jsonLDObject))
  }

  private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): BooleanValueContentV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val booleanValueAsBoolean: Boolean =
      jsonLDObject.requireBoolean(OntologyConstants.KnoraApiV2Complex.BooleanValueAsBoolean)

    BooleanValueContentV2(
      ontologySchema = ApiV2Complex,
      valueHasBoolean = booleanValueAsBoolean,
      comment = getComment(jsonLDObject)
    )
  }
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

  override def toJsonLDValue(targetSchema: ApiV2Schema,
                             projectADM: ProjectADM,
                             settings: KnoraSettingsImpl,
                             schemaOptions: Set[SchemaOption]): JsonLDValue = {
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasGeometry,
          datatype = OntologyConstants.KnoraApiV2Simple.Geom.toSmartIri
        )

      case ApiV2Complex =>
        JsonLDObject(Map(OntologyConstants.KnoraApiV2Complex.GeometryValueAsGeometry -> JsonLDString(valueHasGeometry)))
    }
  }

  override def unescape: ValueContentV2 = {
    copy(
      valueHasGeometry = stringFormatter.fromSparqlEncodedString(valueHasGeometry),
      comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr))
    )
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
    that match {
      case thatGeomValue: GeomValueContentV2 => valueHasGeometry == thatGeomValue.valueHasGeometry
      case _                                 => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }
  }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
    currentVersion match {
      case thatGeomValue: GeomValueContentV2 =>
        valueHasGeometry == thatGeomValue.valueHasGeometry &&
          comment == thatGeomValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
  }
}

/**
  * Constructs [[GeomValueContentV2]] objects based on JSON-LD input.
  */
object GeomValueContentV2 extends ValueContentReaderV2[GeomValueContentV2] {

  /**
    * Converts a JSON-LD object to a [[GeomValueContentV2]].
    *
    * @param jsonLDObject     the JSON-LD object.
    * @param requestingUser   the user making the request.
    * @param responderManager a reference to the responder manager.
    * @param storeManager     a reference to the store manager.
    * @param log              a logging adapter.
    * @param timeout          a timeout for `ask` messages.
    * @param executionContext an execution context for futures.
    * @return an [[GeomValueContentV2]].
    */
  override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                requestingUser: UserADM,
                                responderManager: ActorRef,
                                storeManager: ActorRef,
                                featureFactoryConfig: FeatureFactoryConfig,
                                settings: KnoraSettingsImpl,
                                log: LoggingAdapter)(implicit timeout: Timeout,
                                                     executionContext: ExecutionContext): Future[GeomValueContentV2] = {
    Future(fromJsonLDObjectSync(jsonLDObject))
  }

  private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): GeomValueContentV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val geometryValueAsGeometry: String = jsonLDObject.requireStringWithValidation(
      OntologyConstants.KnoraApiV2Complex.GeometryValueAsGeometry,
      stringFormatter.validateGeometryString)

    GeomValueContentV2(
      ontologySchema = ApiV2Complex,
      valueHasGeometry = geometryValueAsGeometry,
      comment = getComment(jsonLDObject)
    )
  }
}

/**
  * Represents a Knora time interval value.
  *
  * @param valueHasIntervalStart the start of the time interval.
  * @param valueHasIntervalEnd   the end of the time interval.
  * @param comment               a comment on this [[IntervalValueContentV2]], if any.
  */
case class IntervalValueContentV2(ontologySchema: OntologySchema,
                                  valueHasIntervalStart: BigDecimal,
                                  valueHasIntervalEnd: BigDecimal,
                                  comment: Option[String] = None)
    extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.IntervalValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override lazy val valueHasString: String = s"$valueHasIntervalStart - $valueHasIntervalEnd"

  override def toOntologySchema(targetSchema: OntologySchema): IntervalValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(targetSchema: ApiV2Schema,
                             projectADM: ProjectADM,
                             settings: KnoraSettingsImpl,
                             schemaOptions: Set[SchemaOption]): JsonLDValue = {
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasString,
          datatype = OntologyConstants.KnoraApiV2Simple.Interval.toSmartIri
        )

      case ApiV2Complex =>
        JsonLDObject(
          Map(
            OntologyConstants.KnoraApiV2Complex.IntervalValueHasStart ->
              JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueHasIntervalStart.toString,
                datatype = OntologyConstants.Xsd.Decimal.toSmartIri
              ),
            OntologyConstants.KnoraApiV2Complex.IntervalValueHasEnd ->
              JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueHasIntervalEnd.toString,
                datatype = OntologyConstants.Xsd.Decimal.toSmartIri
              )
          ))
    }
  }

  override def unescape: ValueContentV2 = {
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
    that match {
      case thatIntervalValueContent: IntervalValueContentV2 =>
        valueHasIntervalStart == thatIntervalValueContent.valueHasIntervalStart &&
          valueHasIntervalEnd == thatIntervalValueContent.valueHasIntervalEnd

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }
  }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
    currentVersion match {
      case thatIntervalValueContent: IntervalValueContentV2 =>
        valueHasIntervalStart == thatIntervalValueContent.valueHasIntervalStart &&
          valueHasIntervalEnd == thatIntervalValueContent.valueHasIntervalEnd &&
          comment == thatIntervalValueContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
  }
}

/**
  * Constructs [[IntervalValueContentV2]] objects based on JSON-LD input.
  */
object IntervalValueContentV2 extends ValueContentReaderV2[IntervalValueContentV2] {

  /**
    * Converts a JSON-LD object to an [[IntervalValueContentV2]].
    *
    * @param jsonLDObject     the JSON-LD object.
    * @param requestingUser   the user making the request.
    * @param responderManager a reference to the responder manager.
    * @param storeManager     a reference to the store manager.
    * @param log              a logging adapter.
    * @param timeout          a timeout for `ask` messages.
    * @param executionContext an execution context for futures.
    * @return an [[IntervalValueContentV2]].
    */
  override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                requestingUser: UserADM,
                                responderManager: ActorRef,
                                storeManager: ActorRef,
                                featureFactoryConfig: FeatureFactoryConfig,
                                settings: KnoraSettingsImpl,
                                log: LoggingAdapter)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[IntervalValueContentV2] = {
    Future(fromJsonLDObjectSync(jsonLDObject))
  }

  private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): IntervalValueContentV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val intervalValueHasStart: BigDecimal = jsonLDObject.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.IntervalValueHasStart,
      expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
      validationFun = stringFormatter.validateBigDecimal
    )

    val intervalValueHasEnd: BigDecimal = jsonLDObject.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.IntervalValueHasEnd,
      expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
      validationFun = stringFormatter.validateBigDecimal
    )

    IntervalValueContentV2(
      ontologySchema = ApiV2Complex,
      valueHasIntervalStart = intervalValueHasStart,
      valueHasIntervalEnd = intervalValueHasEnd,
      comment = getComment(jsonLDObject)
    )
  }
}

/**
  * Represents a Knora timestamp value.
  *
  * @param valueHasTimeStamp the timestamp.
  * @param comment           a comment on this [[TimeValueContentV2]], if any.
  */
case class TimeValueContentV2(ontologySchema: OntologySchema,
                              valueHasTimeStamp: Instant,
                              comment: Option[String] = None)
    extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.TimeValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override lazy val valueHasString: String = s"$valueHasTimeStamp"

  override def toOntologySchema(targetSchema: OntologySchema): TimeValueContentV2 = copy(ontologySchema = targetSchema)

  override def toJsonLDValue(targetSchema: ApiV2Schema,
                             projectADM: ProjectADM,
                             settings: KnoraSettingsImpl,
                             schemaOptions: Set[SchemaOption]): JsonLDValue = {
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasTimeStamp.toString,
          datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri
        )

      case ApiV2Complex =>
        JsonLDObject(
          Map(
            OntologyConstants.KnoraApiV2Complex.TimeValueAsTimeStamp ->
              JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueHasTimeStamp.toString,
                datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri
              )
          ))
    }
  }

  override def unescape: ValueContentV2 = {
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
    that match {
      case thatTimeValueContent: TimeValueContentV2 =>
        valueHasTimeStamp == thatTimeValueContent.valueHasTimeStamp

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }
  }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
    currentVersion match {
      case thatTimeValueContent: TimeValueContentV2 =>
        valueHasTimeStamp == thatTimeValueContent.valueHasTimeStamp &&
          comment == thatTimeValueContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
  }
}

/**
  * Constructs [[TimeValueContentV2]] objects based on JSON-LD input.
  */
object TimeValueContentV2 extends ValueContentReaderV2[TimeValueContentV2] {

  /**
    * Converts a JSON-LD object to a [[TimeValueContentV2]].
    *
    * @param jsonLDObject     the JSON-LD object.
    * @param requestingUser   the user making the request.
    * @param responderManager a reference to the responder manager.
    * @param storeManager     a reference to the store manager.
    * @param log              a logging adapter.
    * @param timeout          a timeout for `ask` messages.
    * @param executionContext an execution context for futures.
    * @return an [[IntervalValueContentV2]].
    */
  override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                requestingUser: UserADM,
                                responderManager: ActorRef,
                                storeManager: ActorRef,
                                featureFactoryConfig: FeatureFactoryConfig,
                                settings: KnoraSettingsImpl,
                                log: LoggingAdapter)(implicit timeout: Timeout,
                                                     executionContext: ExecutionContext): Future[TimeValueContentV2] = {
    Future(fromJsonLDObjectSync(jsonLDObject))
  }

  private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): TimeValueContentV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val valueHasTimeStamp: Instant = jsonLDObject.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.TimeValueAsTimeStamp,
      expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
      validationFun = stringFormatter.xsdDateTimeStampToInstant
    )

    TimeValueContentV2(
      ontologySchema = ApiV2Complex,
      valueHasTimeStamp = valueHasTimeStamp,
      comment = getComment(jsonLDObject)
    )
  }
}

/**
  * Represents a value pointing to a Knora hierarchical list node.
  *
  * @param valueHasListNode the IRI of the hierarchical list node pointed to.
  * @param listNodeLabel    the label of the hierarchical list node pointed to.
  * @param comment          a comment on this [[HierarchicalListValueContentV2]], if any.
  */
case class HierarchicalListValueContentV2(ontologySchema: OntologySchema,
                                          valueHasListNode: IRI,
                                          listNodeLabel: Option[String] = None,
                                          comment: Option[String] = None)
    extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.ListValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = valueHasListNode

  override def toOntologySchema(targetSchema: OntologySchema): HierarchicalListValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(targetSchema: ApiV2Schema,
                             projectADM: ProjectADM,
                             settings: KnoraSettingsImpl,
                             schemaOptions: Set[SchemaOption]): JsonLDValue = {
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
            OntologyConstants.KnoraApiV2Complex.ListValueAsListNode -> JsonLDUtil.iriToJsonLDObject(valueHasListNode)
          )
        )
    }
  }

  override def unescape: ValueContentV2 = {
    copy(
      listNodeLabel = listNodeLabel.map(labelStr => stringFormatter.fromSparqlEncodedString(labelStr)),
      comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr))
    )
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
    that match {
      case thatListContent: HierarchicalListValueContentV2 => valueHasListNode == thatListContent.valueHasListNode
      case _                                               => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }
  }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
    currentVersion match {
      case thatListContent: HierarchicalListValueContentV2 =>
        valueHasListNode == thatListContent.valueHasListNode &&
          comment == thatListContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
  }
}

/**
  * Constructs [[HierarchicalListValueContentV2]] objects based on JSON-LD input.
  */
object HierarchicalListValueContentV2 extends ValueContentReaderV2[HierarchicalListValueContentV2] {

  /**
    * Converts a JSON-LD object to a [[HierarchicalListValueContentV2]].
    *
    * @param jsonLDObject     the JSON-LD object.
    * @param requestingUser   the user making the request.
    * @param responderManager a reference to the responder manager.
    * @param storeManager     a reference to the store manager.
    * @param log              a logging adapter.
    * @param timeout          a timeout for `ask` messages.
    * @param executionContext an execution context for futures.
    * @return a [[HierarchicalListValueContentV2]].
    */
  override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                requestingUser: UserADM,
                                responderManager: ActorRef,
                                storeManager: ActorRef,
                                featureFactoryConfig: FeatureFactoryConfig,
                                settings: KnoraSettingsImpl,
                                log: LoggingAdapter)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[HierarchicalListValueContentV2] = {
    Future(fromJsonLDObjectSync(jsonLDObject))
  }

  private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): HierarchicalListValueContentV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val listValueAsListNode: SmartIri = jsonLDObject.requireIriInObject(
      OntologyConstants.KnoraApiV2Complex.ListValueAsListNode,
      stringFormatter.toSmartIriWithErr)

    if (!listValueAsListNode.isKnoraDataIri) {
      throw BadRequestException(s"List node IRI <$listValueAsListNode> is not a Knora data IRI")
    }

    HierarchicalListValueContentV2(
      ontologySchema = ApiV2Complex,
      valueHasListNode = listValueAsListNode.toString,
      comment = getComment(jsonLDObject)
    )
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

  override def toJsonLDValue(targetSchema: ApiV2Schema,
                             projectADM: ProjectADM,
                             settings: KnoraSettingsImpl,
                             schemaOptions: Set[SchemaOption]): JsonLDValue = {
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasColor,
          datatype = OntologyConstants.KnoraApiV2Simple.Color.toSmartIri
        )

      case ApiV2Complex =>
        JsonLDObject(Map(OntologyConstants.KnoraApiV2Complex.ColorValueAsColor -> JsonLDString(valueHasColor)))
    }
  }

  override def unescape: ValueContentV2 = {
    copy(
      valueHasColor = stringFormatter.fromSparqlEncodedString(valueHasColor),
      comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr))
    )
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
    that match {
      case thatColorContent: ColorValueContentV2 => valueHasColor == thatColorContent.valueHasColor
      case _                                     => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }
  }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
    currentVersion match {
      case thatColorContent: ColorValueContentV2 =>
        valueHasColor == thatColorContent.valueHasColor &&
          comment == thatColorContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
  }
}

/**
  * Constructs [[ColorValueContentV2]] objects based on JSON-LD input.
  */
object ColorValueContentV2 extends ValueContentReaderV2[ColorValueContentV2] {

  /**
    * Converts a JSON-LD object to a [[ColorValueContentV2]].
    *
    * @param jsonLDObject     the JSON-LD object.
    * @param requestingUser   the user making the request.
    * @param responderManager a reference to the responder manager.
    * @param storeManager     a reference to the store manager.
    * @param log              a logging adapter.
    * @param timeout          a timeout for `ask` messages.
    * @param executionContext an execution context for futures.
    * @return a [[ColorValueContentV2]].
    */
  override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                requestingUser: UserADM,
                                responderManager: ActorRef,
                                storeManager: ActorRef,
                                featureFactoryConfig: FeatureFactoryConfig,
                                settings: KnoraSettingsImpl,
                                log: LoggingAdapter)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[ColorValueContentV2] = {
    Future(fromJsonLDObjectSync(jsonLDObject))
  }

  private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): ColorValueContentV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val colorValueAsColor: String = jsonLDObject.requireStringWithValidation(
      OntologyConstants.KnoraApiV2Complex.ColorValueAsColor,
      stringFormatter.toSparqlEncodedString)

    ColorValueContentV2(
      ontologySchema = ApiV2Complex,
      valueHasColor = colorValueAsColor,
      comment = getComment(jsonLDObject)
    )
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

  override def toJsonLDValue(targetSchema: ApiV2Schema,
                             projectADM: ProjectADM,
                             settings: KnoraSettingsImpl,
                             schemaOptions: Set[SchemaOption]): JsonLDValue = {
    val uriAsJsonLDObject = JsonLDUtil.datatypeValueToJsonLDObject(
      value = valueHasUri,
      datatype = OntologyConstants.Xsd.Uri.toSmartIri
    )

    targetSchema match {
      case ApiV2Simple => uriAsJsonLDObject

      case ApiV2Complex =>
        JsonLDObject(Map(OntologyConstants.KnoraApiV2Complex.UriValueAsUri -> uriAsJsonLDObject))
    }
  }

  override def unescape: ValueContentV2 = {
    copy(
      valueHasUri = stringFormatter.fromSparqlEncodedString(valueHasUri),
      comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr))
    )
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
    that match {
      case thatUriContent: UriValueContentV2 => valueHasUri == thatUriContent.valueHasUri
      case _                                 => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }
  }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
    currentVersion match {
      case thatUriContent: UriValueContentV2 =>
        valueHasUri == thatUriContent.valueHasUri &&
          comment == thatUriContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
  }
}

/**
  * Constructs [[UriValueContentV2]] objects based on JSON-LD input.
  */
object UriValueContentV2 extends ValueContentReaderV2[UriValueContentV2] {

  /**
    * Converts a JSON-LD object to a [[UriValueContentV2]].
    *
    * @param jsonLDObject     the JSON-LD object.
    * @param requestingUser   the user making the request.
    * @param responderManager a reference to the responder manager.
    * @param storeManager     a reference to the store manager.
    * @param log              a logging adapter.
    * @param timeout          a timeout for `ask` messages.
    * @param executionContext an execution context for futures.
    * @return a [[UriValueContentV2]].
    */
  override def fromJsonLDObject(
      jsonLDObject: JsonLDObject,
      requestingUser: UserADM,
      responderManager: ActorRef,
      storeManager: ActorRef,
      featureFactoryConfig: FeatureFactoryConfig,
      settings: KnoraSettingsImpl,
      log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[UriValueContentV2] = {
    Future(fromJsonLDObjectSync(jsonLDObject))
  }

  private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): UriValueContentV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val uriValueAsUri: String = jsonLDObject.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.UriValueAsUri,
      expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
      validationFun = stringFormatter.toSparqlEncodedString
    )

    UriValueContentV2(
      ontologySchema = ApiV2Complex,
      valueHasUri = uriValueAsUri,
      comment = getComment(jsonLDObject)
    )
  }
}

/**
  *
  * Represents a Knora geoname value.
  *
  * @param valueHasGeonameCode the geoname code.
  * @param comment             a comment on this [[GeonameValueContentV2]], if any.
  */
case class GeonameValueContentV2(ontologySchema: OntologySchema,
                                 valueHasGeonameCode: String,
                                 comment: Option[String] = None)
    extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.GeonameValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = valueHasGeonameCode

  override def toOntologySchema(targetSchema: OntologySchema): GeonameValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(targetSchema: ApiV2Schema,
                             projectADM: ProjectADM,
                             settings: KnoraSettingsImpl,
                             schemaOptions: Set[SchemaOption]): JsonLDValue = {
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasGeonameCode,
          datatype = OntologyConstants.KnoraApiV2Simple.Geoname.toSmartIri
        )

      case ApiV2Complex =>
        JsonLDObject(
          Map(OntologyConstants.KnoraApiV2Complex.GeonameValueAsGeonameCode -> JsonLDString(valueHasGeonameCode)))
    }
  }

  override def unescape: ValueContentV2 = {
    copy(
      valueHasGeonameCode = stringFormatter.fromSparqlEncodedString(valueHasGeonameCode),
      comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr))
    )
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
    that match {
      case thatGeonameContent: GeonameValueContentV2 => valueHasGeonameCode == thatGeonameContent.valueHasGeonameCode
      case _                                         => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }
  }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
    currentVersion match {
      case thatGeonameContent: GeonameValueContentV2 =>
        valueHasGeonameCode == thatGeonameContent.valueHasGeonameCode &&
          comment == thatGeonameContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
  }
}

/**
  * Constructs [[GeonameValueContentV2]] objects based on JSON-LD input.
  */
object GeonameValueContentV2 extends ValueContentReaderV2[GeonameValueContentV2] {

  /**
    * Converts a JSON-LD object to a [[GeonameValueContentV2]].
    *
    * @param jsonLDObject     the JSON-LD object.
    * @param requestingUser   the user making the request.
    * @param responderManager a reference to the responder manager.
    * @param storeManager     a reference to the store manager.
    * @param log              a logging adapter.
    * @param timeout          a timeout for `ask` messages.
    * @param executionContext an execution context for futures.
    * @return a [[GeonameValueContentV2]].
    */
  override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                requestingUser: UserADM,
                                responderManager: ActorRef,
                                storeManager: ActorRef,
                                featureFactoryConfig: FeatureFactoryConfig,
                                settings: KnoraSettingsImpl,
                                log: LoggingAdapter)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[GeonameValueContentV2] = {
    Future(fromJsonLDObjectSync(jsonLDObject))
  }

  private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): GeonameValueContentV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val geonameValueAsGeonameCode: String = jsonLDObject.requireStringWithValidation(
      OntologyConstants.KnoraApiV2Complex.GeonameValueAsGeonameCode,
      stringFormatter.toSparqlEncodedString)

    GeonameValueContentV2(
      ontologySchema = ApiV2Complex,
      valueHasGeonameCode = geonameValueAsGeonameCode,
      comment = getComment(jsonLDObject)
    )
  }
}

/**
  * Represents the basic metadata stored about any file value.
  */
case class FileValueV2(internalFilename: String,
                       internalMimeType: String,
                       originalFilename: Option[String],
                       originalMimeType: Option[String])

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
  def fromJsonLDObject(jsonLDObject: JsonLDObject,
                       requestingUser: UserADM,
                       responderManager: ActorRef,
                       storeManager: ActorRef,
                       settings: KnoraSettingsImpl,
                       log: LoggingAdapter)(implicit timeout: Timeout,
                                            executionContext: ExecutionContext): Future[FileValueWithSipiMetadata] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    for {
      // The submitted value provides only Sipi's internal filename for the file.
      internalFilename <- Future(
        jsonLDObject.requireStringWithValidation(OntologyConstants.KnoraApiV2Complex.FileValueHasFilename,
                                                 stringFormatter.toSparqlEncodedString))

      // Ask Sipi about the rest of the file's metadata.
      tempFileUrl = stringFormatter.makeSipiTempFileUrl(settings, internalFilename)
      fileMetadataResponse: GetFileMetadataResponse <- (storeManager ? GetFileMetadataRequest(
        fileUrl = tempFileUrl,
        requestingUser = requestingUser)).mapTo[GetFileMetadataResponse]

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
    OntologyConstants.KnoraApiV2Complex.FileValueHasFilename -> JsonLDString(fileValue.internalFilename),
    OntologyConstants.KnoraApiV2Complex.FileValueAsUrl -> JsonLDUtil.datatypeValueToJsonLDObject(
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
case class StillImageFileValueContentV2(ontologySchema: OntologySchema,
                                        fileValue: FileValueV2,
                                        dimX: Int,
                                        dimY: Int,
                                        comment: Option[String] = None)
    extends FileValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.StillImageFileValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = fileValue.internalFilename

  override def toOntologySchema(targetSchema: OntologySchema): StillImageFileValueContentV2 =
    copy(ontologySchema = targetSchema)

  def makeFileUrl(projectADM: ProjectADM, settings: KnoraSettingsImpl): String = {
    s"${settings.externalSipiIIIFGetUrl}/${projectADM.shortcode}/${fileValue.internalFilename}/full/$dimX,$dimY/0/default.jpg"
  }

  override def toJsonLDValue(targetSchema: ApiV2Schema,
                             projectADM: ProjectADM,
                             settings: KnoraSettingsImpl,
                             schemaOptions: Set[SchemaOption]): JsonLDValue = {
    val fileUrl: String = makeFileUrl(projectADM, settings)

    targetSchema match {
      case ApiV2Simple => toJsonLDValueInSimpleSchema(fileUrl)

      case ApiV2Complex =>
        JsonLDObject(
          toJsonLDObjectMapInComplexSchema(fileUrl) ++ Map(
            OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasDimX -> JsonLDInt(dimX),
            OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasDimY -> JsonLDInt(dimY),
            OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasIIIFBaseUrl -> JsonLDUtil
              .datatypeValueToJsonLDObject(
                value = s"${settings.externalSipiIIIFGetUrl}/${projectADM.shortcode}",
                datatype = OntologyConstants.Xsd.Uri.toSmartIri
              )
          ))
    }
  }

  override def unescape: ValueContentV2 = {
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
    that match {
      case thatStillImage: StillImageFileValueContentV2 =>
        fileValue == thatStillImage.fileValue &&
          dimX == thatStillImage.dimX &&
          dimY == thatStillImage.dimY

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }
  }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
    currentVersion match {
      case thatStillImage: StillImageFileValueContentV2 =>
        wouldDuplicateOtherValue(thatStillImage) && comment == thatStillImage.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
  }
}

/**
  * Constructs [[StillImageFileValueContentV2]] objects based on JSON-LD input.
  */
object StillImageFileValueContentV2 extends ValueContentReaderV2[StillImageFileValueContentV2] {
  override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                requestingUser: UserADM,
                                responderManager: ActorRef,
                                storeManager: ActorRef,
                                featureFactoryConfig: FeatureFactoryConfig,
                                settings: KnoraSettingsImpl,
                                log: LoggingAdapter)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[StillImageFileValueContentV2] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    for {
      fileValueWithSipiMetadata <- FileValueWithSipiMetadata.fromJsonLDObject(
        jsonLDObject = jsonLDObject,
        requestingUser = requestingUser,
        responderManager = responderManager,
        storeManager = storeManager,
        settings = settings,
        log = log
      )

      _ = if (!settings.imageMimeTypes.contains(fileValueWithSipiMetadata.fileValue.internalMimeType)) {
        throw BadRequestException(
          s"File ${fileValueWithSipiMetadata.fileValue.internalFilename} has MIME type ${fileValueWithSipiMetadata.fileValue.internalMimeType}, which is not supported for still image files")
      }
    } yield
      StillImageFileValueContentV2(
        ontologySchema = ApiV2Complex,
        fileValue = fileValueWithSipiMetadata.fileValue,
        dimX = fileValueWithSipiMetadata.sipiFileMetadata.width
          .getOrElse(throw SipiException(s"Sipi did not return the image width")),
        dimY = fileValueWithSipiMetadata.sipiFileMetadata.height
          .getOrElse(throw SipiException(s"Sipi did not return the image height")),
        comment = getComment(jsonLDObject)
      )
  }
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
case class DocumentFileValueContentV2(ontologySchema: OntologySchema,
                                      fileValue: FileValueV2,
                                      pageCount: Option[Int],
                                      dimX: Option[Int],
                                      dimY: Option[Int],
                                      comment: Option[String] = None)
    extends FileValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.DocumentFileValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = fileValue.internalFilename

  override def toOntologySchema(targetSchema: OntologySchema): DocumentFileValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(targetSchema: ApiV2Schema,
                             projectADM: ProjectADM,
                             settings: KnoraSettingsImpl,
                             schemaOptions: Set[SchemaOption]): JsonLDValue = {
    val fileUrl: String = s"${settings.externalSipiBaseUrl}/${projectADM.shortcode}/${fileValue.internalFilename}/file"

    targetSchema match {
      case ApiV2Simple => toJsonLDValueInSimpleSchema(fileUrl)

      case ApiV2Complex =>
        val maybeDimXStatement: Option[(IRI, JsonLDInt)] = dimX.map { definedDimX =>
          OntologyConstants.KnoraApiV2Complex.DocumentFileValueHasDimX -> JsonLDInt(definedDimX)
        }

        val maybeDimYStatement: Option[(IRI, JsonLDInt)] = dimY.map { definedDimY =>
          OntologyConstants.KnoraApiV2Complex.DocumentFileValueHasDimY -> JsonLDInt(definedDimY)
        }

        val maybePageCountStatement: Option[(IRI, JsonLDInt)] = pageCount.map { definedPageCount =>
          OntologyConstants.KnoraApiV2Complex.DocumentFileValueHasPageCount -> JsonLDInt(definedPageCount)
        }

        JsonLDObject(
          toJsonLDObjectMapInComplexSchema(fileUrl) ++ maybeDimXStatement ++ maybeDimYStatement ++ maybePageCountStatement
        )
    }
  }

  override def unescape: ValueContentV2 = {
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
    that match {
      case thatDocumentFile: DocumentFileValueContentV2 =>
        fileValue == thatDocumentFile.fileValue

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }
  }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
    currentVersion match {
      case thatDocumentFile: DocumentFileValueContentV2 =>
        wouldDuplicateOtherValue(thatDocumentFile) && comment == thatDocumentFile.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
  }
}

/**
  * Constructs [[DocumentFileValueContentV2]] objects based on JSON-LD input.
  */
object DocumentFileValueContentV2 extends ValueContentReaderV2[DocumentFileValueContentV2] {
  override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                requestingUser: UserADM,
                                responderManager: ActorRef,
                                storeManager: ActorRef,
                                featureFactoryConfig: FeatureFactoryConfig,
                                settings: KnoraSettingsImpl,
                                log: LoggingAdapter)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[DocumentFileValueContentV2] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    for {
      fileValueWithSipiMetadata <- FileValueWithSipiMetadata.fromJsonLDObject(
        jsonLDObject = jsonLDObject,
        requestingUser = requestingUser,
        responderManager = responderManager,
        storeManager = storeManager,
        settings = settings,
        log = log
      )

      _ = if (!settings.documentMimeTypes.contains(fileValueWithSipiMetadata.fileValue.internalMimeType)) {
        throw BadRequestException(
          s"File ${fileValueWithSipiMetadata.fileValue.internalFilename} has MIME type ${fileValueWithSipiMetadata.fileValue.internalMimeType}, which is not supported for document files")
      }
    } yield
      DocumentFileValueContentV2(
        ontologySchema = ApiV2Complex,
        fileValue = fileValueWithSipiMetadata.fileValue,
        pageCount = fileValueWithSipiMetadata.sipiFileMetadata.pageCount,
        dimX = fileValueWithSipiMetadata.sipiFileMetadata.width,
        dimY = fileValueWithSipiMetadata.sipiFileMetadata.height,
        comment = getComment(jsonLDObject)
      )
  }
}

/**
  * Represents text file metadata.
  *
  * @param fileValue the basic metadata about the file value.
  * @param comment   a comment on this [[TextFileValueContentV2]], if any.
  */
case class TextFileValueContentV2(ontologySchema: OntologySchema,
                                  fileValue: FileValueV2,
                                  comment: Option[String] = None)
    extends FileValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.TextFileValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = fileValue.internalFilename

  override def toOntologySchema(targetSchema: OntologySchema): TextFileValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(targetSchema: ApiV2Schema,
                             projectADM: ProjectADM,
                             settings: KnoraSettingsImpl,
                             schemaOptions: Set[SchemaOption]): JsonLDValue = {
    val fileUrl: String = s"${settings.externalSipiBaseUrl}/${projectADM.shortcode}/${fileValue.internalFilename}/file"

    targetSchema match {
      case ApiV2Simple => toJsonLDValueInSimpleSchema(fileUrl)

      case ApiV2Complex =>
        JsonLDObject(toJsonLDObjectMapInComplexSchema(fileUrl))
    }
  }

  override def unescape: ValueContentV2 = {
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
    that match {
      case thatTextFile: TextFileValueContentV2 =>
        fileValue == thatTextFile.fileValue

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }
  }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
    currentVersion match {
      case thatTextFile: TextFileValueContentV2 =>
        fileValue == thatTextFile.fileValue &&
          comment == thatTextFile.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
  }
}

/**
  * Constructs [[TextFileValueContentV2]] objects based on JSON-LD input.
  */
object TextFileValueContentV2 extends ValueContentReaderV2[TextFileValueContentV2] {
  override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                requestingUser: UserADM,
                                responderManager: ActorRef,
                                storeManager: ActorRef,
                                featureFactoryConfig: FeatureFactoryConfig,
                                settings: KnoraSettingsImpl,
                                log: LoggingAdapter)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[TextFileValueContentV2] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    for {
      fileValueWithSipiMetadata <- FileValueWithSipiMetadata.fromJsonLDObject(
        jsonLDObject = jsonLDObject,
        requestingUser = requestingUser,
        responderManager = responderManager,
        storeManager = storeManager,
        settings = settings,
        log = log
      )

      _ = if (!settings.textMimeTypes.contains(fileValueWithSipiMetadata.fileValue.internalMimeType)) {
        throw BadRequestException(
          s"File ${fileValueWithSipiMetadata.fileValue.internalFilename} has MIME type ${fileValueWithSipiMetadata.fileValue.internalMimeType}, which is not supported for text files")
      }
    } yield
      TextFileValueContentV2(
        ontologySchema = ApiV2Complex,
        fileValue = fileValueWithSipiMetadata.fileValue,
        comment = getComment(jsonLDObject)
      )
  }
}

/**
  * Represents audio file metadata.
  *
  * @param fileValue the basic metadata about the file value.
  * @param duration the duration of the audio file in seconds.
  * @param comment a comment on this [[AudioFileValueContentV2]], if any.
  */
case class AudioFileValueContentV2(ontologySchema: OntologySchema,
                                   fileValue: FileValueV2,
                                   duration: Option[BigDecimal] = None,
                                   comment: Option[String] = None)
    extends FileValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.AudioFileValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = fileValue.internalFilename

  override def toOntologySchema(targetSchema: OntologySchema): AudioFileValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(targetSchema: ApiV2Schema,
                             projectADM: ProjectADM,
                             settings: KnoraSettingsImpl,
                             schemaOptions: Set[SchemaOption]): JsonLDValue = {
    val fileUrl: String = s"${settings.externalSipiBaseUrl}/${projectADM.shortcode}/${fileValue.internalFilename}/file"

    targetSchema match {
      case ApiV2Simple => toJsonLDValueInSimpleSchema(fileUrl)

      case ApiV2Complex =>
        JsonLDObject(toJsonLDObjectMapInComplexSchema(fileUrl))
    }
  }

  override def unescape: ValueContentV2 = {
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
    that match {
      case thatAudioFile: AudioFileValueContentV2 =>
        fileValue == thatAudioFile.fileValue

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }
  }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
    currentVersion match {
      case thatAudioFile: AudioFileValueContentV2 =>
        fileValue == thatAudioFile.fileValue &&
          comment == thatAudioFile.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
  }
}

/**
  * Constructs [[AudioFileValueContentV2]] objects based on JSON-LD input.
  */
object AudioFileValueContentV2 extends ValueContentReaderV2[AudioFileValueContentV2] {
  override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                requestingUser: UserADM,
                                responderManager: ActorRef,
                                storeManager: ActorRef,
                                featureFactoryConfig: FeatureFactoryConfig,
                                settings: KnoraSettingsImpl,
                                log: LoggingAdapter)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[AudioFileValueContentV2] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    for {
      fileValueWithSipiMetadata <- FileValueWithSipiMetadata.fromJsonLDObject(
        jsonLDObject = jsonLDObject,
        requestingUser = requestingUser,
        responderManager = responderManager,
        storeManager = storeManager,
        settings = settings,
        log = log
      )

      _ = if (!settings.audioMimeTypes.contains(fileValueWithSipiMetadata.fileValue.internalMimeType)) {
        throw BadRequestException(
          s"File ${fileValueWithSipiMetadata.fileValue.internalFilename} has MIME type ${fileValueWithSipiMetadata.fileValue.internalMimeType}, which is not supported for audio files")
      }
    } yield
      AudioFileValueContentV2(
        ontologySchema = ApiV2Complex,
        fileValue = fileValueWithSipiMetadata.fileValue,
        duration = fileValueWithSipiMetadata.sipiFileMetadata.duration,
        comment = getComment(jsonLDObject)
      )
  }
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
case class LinkValueContentV2(ontologySchema: OntologySchema,
                              referredResourceIri: IRI,
                              referredResourceExists: Boolean = true,
                              isIncomingLink: Boolean = false,
                              nestedResource: Option[ReadResourceV2] = None,
                              comment: Option[String] = None)
    extends ValueContentV2 {
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

  override def toJsonLDValue(targetSchema: ApiV2Schema,
                             projectADM: ProjectADM,
                             settings: KnoraSettingsImpl,
                             schemaOptions: Set[SchemaOption]): JsonLDValue = {
    targetSchema match {
      case ApiV2Simple => JsonLDUtil.iriToJsonLDObject(referredResourceIri)

      case ApiV2Complex =>
        // check if the referred resource has to be included in the JSON response
        val objectMap: Map[IRI, JsonLDValue] = nestedResource match {
          case Some(targetResource: ReadResourceV2) =>
            // include the nested resource in the response
            val referredResourceAsJsonLDValue: JsonLDObject = targetResource.toJsonLD(
              targetSchema = targetSchema,
              settings = settings,
              schemaOptions = schemaOptions
            )

            // check whether the nested resource is the target or the source of the link
            if (!isIncomingLink) {
              Map(OntologyConstants.KnoraApiV2Complex.LinkValueHasTarget -> referredResourceAsJsonLDValue)
            } else {
              Map(OntologyConstants.KnoraApiV2Complex.LinkValueHasSource -> referredResourceAsJsonLDValue)
            }
          case None =>
            // check whether it is an outgoing or incoming link
            if (!isIncomingLink) {
              Map(
                OntologyConstants.KnoraApiV2Complex.LinkValueHasTargetIri -> JsonLDUtil.iriToJsonLDObject(
                  referredResourceIri))
            } else {
              Map(
                OntologyConstants.KnoraApiV2Complex.LinkValueHasSourceIri -> JsonLDUtil.iriToJsonLDObject(
                  referredResourceIri))
            }
        }

        JsonLDObject(objectMap)
    }
  }

  override def unescape: ValueContentV2 = {
    copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
    that match {
      case thatLinkValue: LinkValueContentV2 =>
        referredResourceIri == thatLinkValue.referredResourceIri &&
          isIncomingLink == thatLinkValue.isIncomingLink

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }
  }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
    currentVersion match {
      case thatLinkValue: LinkValueContentV2 =>
        referredResourceIri == thatLinkValue.referredResourceIri &&
          isIncomingLink == thatLinkValue.isIncomingLink &&
          comment == thatLinkValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
  }
}

/**
  * Constructs [[LinkValueContentV2]] objects based on JSON-LD input.
  */
object LinkValueContentV2 extends ValueContentReaderV2[LinkValueContentV2] {
  override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                requestingUser: UserADM,
                                responderManager: ActorRef,
                                storeManager: ActorRef,
                                featureFactoryConfig: FeatureFactoryConfig,
                                settings: KnoraSettingsImpl,
                                log: LoggingAdapter)(implicit timeout: Timeout,
                                                     executionContext: ExecutionContext): Future[LinkValueContentV2] = {
    Future(fromJsonLDObjectSync(jsonLDObject))
  }

  private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): LinkValueContentV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val targetIri: SmartIri = jsonLDObject.requireIriInObject(OntologyConstants.KnoraApiV2Complex.LinkValueHasTargetIri,
                                                              stringFormatter.toSmartIriWithErr)

    if (!targetIri.isKnoraDataIri) {
      throw BadRequestException(s"Link target IRI <$targetIri> is not a Knora data IRI")
    }

    LinkValueContentV2(
      ontologySchema = ApiV2Complex,
      referredResourceIri = targetIri.toString,
      comment = getComment(jsonLDObject)
    )
  }
}
