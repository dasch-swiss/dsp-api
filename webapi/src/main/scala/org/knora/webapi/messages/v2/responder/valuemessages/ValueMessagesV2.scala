/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import java.time.Instant
import java.util.UUID

import akka.actor.ActorSelection
import akka.event.LoggingAdapter
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.valuemessages.{JulianDayNumberValueV1, KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.standoffmessages.{GetMappingRequestV2, GetMappingResponseV2, MappingXMLtoStandoff, StandoffDataTypeClasses}
import org.knora.webapi.twirl.{StandoffTagAttributeV2, StandoffTagInternalReferenceAttributeV2, StandoffTagIriAttributeV2, StandoffTagV2}
import org.knora.webapi.util.DateUtilV2.{DateYearMonthDay, KnoraEraV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util._
import org.knora.webapi.util.jsonld._
import org.knora.webapi.util.standoff.StandoffTagUtilV2.TextWithStandoffTagsV2
import org.knora.webapi.util.standoff.{StandoffTagUtilV2, XMLUtil}

import scala.concurrent.{ExecutionContext, Future}

/**
  * A tagging trait for requests handled by [[org.knora.webapi.responders.v2.ValuesResponderV2]].
  */
sealed trait ValuesResponderRequestV2 extends KnoraRequestV2

/**
  * Requests the creation of a value.
  *
  * @param createValue    a [[CreateValueV2]] representing the value to be created. A successful response will be
  *                       a [[CreateValueResponseV2]].
  * @param requestingUser the user making the request.
  * @param apiRequestID   the API request ID.
  */
case class CreateValueRequestV2(createValue: CreateValueV2,
                                requestingUser: UserADM,
                                apiRequestID: UUID) extends ValuesResponderRequestV2


/**
  * Constructs [[CreateValueRequestV2]] instances based on JSON-LD input.
  */
object CreateValueRequestV2 extends KnoraJsonLDRequestReaderV2[CreateValueRequestV2] {
    /**
      * Converts JSON-LD input to a [[CreateValueRequestV2]].
      *
      * @param jsonLDDocument   the JSON-LD input.
      * @param apiRequestID     the UUID of the API request.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a case class instance representing the input.
      */
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            requestingUser: UserADM,
                            responderManager: ActorSelection,
                            log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[CreateValueRequestV2] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        for {
            // Get the IRI of the resource that the value is to be created in.
            resourceIri: SmartIri <- Future(jsonLDDocument.getIDAsKnoraDataIri)

            // Get the resource class.
            resourceClassIri: SmartIri = jsonLDDocument.getTypeAsKnoraTypeIri

            // Get the resource property and the value to be created.
            createValue: CreateValueV2 <- jsonLDDocument.getResourcePropertyValue match {
                case (propertyIri: SmartIri, jsonLDObject: JsonLDObject) =>
                    for {
                        valueContent: ValueContentV2 <-
                            ValueContentV2.fromJsonLDObject(
                                jsonLDObject = jsonLDObject,
                                requestingUser = requestingUser,
                                responderManager = responderManager,
                                log = log
                            )

                        _ = if (jsonLDObject.value.get(JsonLDConstants.ID).nonEmpty) {
                            throw BadRequestException("The @id of a value cannot be given in a request to create the value")
                        }

                        maybePermissions: Option[String] = jsonLDObject.maybeStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions, stringFormatter.toSparqlEncodedString)
                    } yield CreateValueV2(
                        resourceIri = resourceIri.toString,
                        resourceClassIri = resourceClassIri,
                        propertyIri = propertyIri,
                        valueContent = valueContent,
                        permissions = maybePermissions
                    )
            }
        } yield CreateValueRequestV2(
            createValue = createValue,
            apiRequestID = apiRequestID,
            requestingUser = requestingUser
        )
    }
}

/**
  * Represents a successful response to a [[CreateValueRequestV2]].
  *
  * @param valueIri  the IRI of the value that was created.
  * @param valueType the type of the value that was created.
  */
case class CreateValueResponseV2(valueIri: IRI,
                                 valueType: SmartIri) extends KnoraResponseV2 {
    override def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        if (targetSchema != ApiV2WithValueObjects) {
            throw AssertionException(s"CreateValueResponseV2 can only be returned in the complex schema")
        }

        JsonLDDocument(
            body = JsonLDObject(
                Map(
                    JsonLDConstants.ID -> JsonLDString(valueIri),
                    JsonLDConstants.TYPE -> JsonLDString(valueType.toOntologySchema(ApiV2WithValueObjects).toString)
                )
            )
        )
    }
}

/**
  * Requests an update to a value, i.e. the creation of a new version of an existing value.
  *
  * @param updateValue    an [[UpdateValueV2]] representing the new version of the value. A successful response will be
  *                       an [[UpdateValueResponseV2]].
  * @param requestingUser the user making the request.
  * @param apiRequestID   the API request ID.
  */
case class UpdateValueRequestV2(updateValue: UpdateValueV2,
                                requestingUser: UserADM,
                                apiRequestID: UUID) extends ValuesResponderRequestV2

/**
  * Constructs [[UpdateValueRequestV2]] instances based on JSON-LD input.
  */
object UpdateValueRequestV2 extends KnoraJsonLDRequestReaderV2[UpdateValueRequestV2] {
    /**
      * Converts JSON-LD input to a [[CreateValueRequestV2]].
      *
      * @param jsonLDDocument   the JSON-LD input.
      * @param apiRequestID     the UUID of the API request.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a case class instance representing the input.
      */
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            requestingUser: UserADM,
                            responderManager: ActorSelection,
                            log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[UpdateValueRequestV2] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        for {
            // Get the IRI of the resource that the value is to be created in.
            resourceIri: SmartIri <- Future(jsonLDDocument.getIDAsKnoraDataIri)

            // Get the resource class.
            resourceClassIri: SmartIri = jsonLDDocument.getTypeAsKnoraTypeIri

            // Get the resource property and the new value version.
            updateValue: UpdateValueV2 <- jsonLDDocument.getResourcePropertyValue match {
                case (propertyIri: SmartIri, jsonLDObject: JsonLDObject) =>
                    for {
                        valueContent: ValueContentV2 <-
                            ValueContentV2.fromJsonLDObject(
                                jsonLDObject = jsonLDObject,
                                requestingUser = requestingUser,
                                responderManager = responderManager,
                                log = log
                            )

                        valueIri = jsonLDObject.getIDAsKnoraDataIri
                        maybePermissions: Option[String] = jsonLDObject.maybeStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions, stringFormatter.toSparqlEncodedString)
                    } yield UpdateValueV2(
                        resourceIri = resourceIri.toString,
                        resourceClassIri = resourceClassIri,
                        propertyIri = propertyIri,
                        valueIri = valueIri.toString,
                        valueContent = valueContent,
                        permissions = maybePermissions
                    )
            }
        } yield UpdateValueRequestV2(
            updateValue = updateValue,
            apiRequestID = apiRequestID,
            requestingUser = requestingUser
        )
    }
}

/**
  * Represents a successful response to an [[UpdateValueRequestV2]].
  *
  * @param valueIri the IRI of the value version that was created.
  */
case class UpdateValueResponseV2(valueIri: IRI,
                                 valueType: SmartIri) extends KnoraResponseV2 {
    override def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        if (targetSchema != ApiV2WithValueObjects) {
            throw AssertionException(s"UpdateValueResponseV2 can only be returned in the complex schema")
        }

        JsonLDDocument(
            body = JsonLDObject(
                Map(
                    JsonLDConstants.ID -> JsonLDString(valueIri),
                    JsonLDConstants.TYPE -> JsonLDString(valueType.toOntologySchema(ApiV2WithValueObjects).toString)
                )
            )
        )
    }
}

/**
  * Requests that a value is marked as deleted. A successful response will be a [[SuccessResponseV2]].
  *
  * @param resourceIri    the IRI of the containing resource.
  * @param propertyIri    the IRI of the property pointing to the value to be marked as deleted.
  * @param valueIri       the IRI of the value to be marked as deleted.
  * @param deleteComment  an optional comment explaining why the value is being marked as deleted.
  * @param requestingUser the user making the request.
  * @param apiRequestID   the API request ID.
  */
case class DeleteValueRequestV2(resourceIri: IRI,
                                propertyIri: SmartIri,
                                valueIri: IRI,
                                deleteComment: Option[String] = None,
                                requestingUser: UserADM,
                                apiRequestID: UUID) extends ValuesResponderRequestV2

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
  *
  * @param resourceIri      the IRI of the resource in which values are to be created.
  * @param resourceClassIri the IRI of the resource class.
  * @param projectIri       the project the values belong to.
  * @param propertyValues   a map of property IRIs to the values to be added for each property.
  * @param currentTime      an xsd:dateTimeStamp that will be attached to the values.
  * @param requestingUser   the user that is creating the values.
  * @param apiRequestID     the API request ID.
  */
case class GenerateSparqlToCreateMultipleValuesRequestV2(resourceIri: IRI,
                                                         resourceClassIri: SmartIri,
                                                         projectIri: IRI,
                                                         propertyValues: Map[SmartIri, Seq[CreateValueV2]],
                                                         currentTime: Instant,
                                                         requestingUser: UserADM,
                                                         apiRequestID: UUID) extends ValuesResponderRequestV2 {
    lazy val values: Iterable[CreateValueV2] = propertyValues.values.flatten
}

/**
  * Represents a response to a [[GenerateSparqlToCreateMultipleValuesRequestV2]], providing a string that can be
  * included in the `INSERT DATA` clause of a SPARQL update operation to create the requested values.
  *
  * @param insertSparql     a string containing statements that must be inserted into the INSERT clause of the SPARQL
  *                         update that will create the values.
  * @param unverifiedValues a map of property IRIs to [[UnverifiedValueV2]] objects describing
  *                         the values that should have been created.
  */
case class GenerateSparqlToCreateMultipleValuesResponseV2(insertSparql: String,
                                                          unverifiedValues: Map[SmartIri, Seq[UnverifiedValueV2]])

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
  * @param deleteDate    the date when the resource or value was deleted.
  * @param deleteComment the reason why the resource or value was deleted.
  */
case class DeletionInfo(deleteDate: Instant,
                        deleteComment: String) {
    def toJsonLDFields(targetSchema: ApiV2Schema): Map[IRI, JsonLDValue] = {
        if (targetSchema != ApiV2WithValueObjects) {
            throw AssertionException("DeletionInfo is available in JSON-LD only in the complex schema")
        }

        Map(
            OntologyConstants.KnoraApiV2WithValueObjects.IsDeleted -> JsonLDBoolean(true),
            OntologyConstants.KnoraApiV2WithValueObjects.DeleteDate -> JsonLDObject(
                Map(
                    JsonLDConstants.TYPE -> JsonLDString(OntologyConstants.Xsd.DateTimeStamp),
                    JsonLDConstants.VALUE -> JsonLDString(deleteDate.toString)
                )
            ),
            OntologyConstants.KnoraApiV2WithValueObjects.DeleteComment -> JsonLDString(deleteComment)
        )
    }
}

/**
  * A value of a Knora property, as read from the triplestore.
  *
  * @param valueIri         the IRI of the value.
  * @param attachedToUser   the user that created the value.
  * @param permissions      the permissions that the value grants to user groups.
  * @param valueContent     the content of the value.
  * @param previousValueIri the IRI of the previous version of this value. Not returned in API responses, but needed
  *                         here for testing.
  * @param valueHasRefCount if this is a link value, its reference count.  Not returned in API responses, but needed
  *                         here for testing.
  * @param deletionInfo     if this value has been marked as deleted, provides the date when it was
  *                         deleted and the reason why it was deleted.
  */
case class ReadValueV2(valueIri: IRI,
                       attachedToUser: IRI,
                       permissions: String,
                       valueCreationDate: Instant,
                       valueContent: ValueContentV2,
                       previousValueIri: Option[IRI] = None,
                       valueHasRefCount: Option[Int] = None,
                       deletionInfo: Option[DeletionInfo]) extends IOValueV2 with KnoraReadV2[ReadValueV2] {
    // TODO: consider paramaterising ReadValueV2 with the subtype of its ValueContentV2. This would make
    // it possible to write a method that, e.g., is declared as returning a ReadValueV2[LinkValueContentV2] (like
    // ValuesResponderV2.findLinkValue). The challenge would be dealing with methods like ReadValueV2.toOntologySchema
    // and ValueContentV2.toOntologySchema, which would have to return instances of subtypes.
    // See <https://tpolecat.github.io/2015/04/29/f-bounds.html>.

    /**
      * Converts this value to the specified ontology schema.
      *
      * @param targetSchema the target schema.
      */
    override def toOntologySchema(targetSchema: ApiV2Schema): ReadValueV2 = {
        copy(valueContent = valueContent.toOntologySchema(targetSchema))
    }

    /**
      * Converts this value to JSON-LD.
      *
      * @param targetSchema the target schema.
      * @param settings     the application settings.
      * @return a JSON-LD representation of this value.
      */
    def toJsonLD(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        val valueContentAsJsonLD = valueContent.toJsonLDValue(targetSchema, settings)

        // In the complex schema, add the value's IRI and type to the JSON-LD object that represents it.
        targetSchema match {
            case ApiV2WithValueObjects =>
                // In the complex schema, the value must be represented as a JSON-LD object.
                valueContentAsJsonLD match {
                    case jsonLDObject: JsonLDObject =>
                        // Add the value's metadata.

                        val requiredMetadata = Map(
                            JsonLDConstants.ID -> JsonLDString(valueIri),
                            JsonLDConstants.TYPE -> JsonLDString(valueContent.valueType.toString),
                            OntologyConstants.KnoraApiV2WithValueObjects.AttachedToUser -> JsonLDUtil.iriToJsonLDObject(attachedToUser),
                            OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions -> JsonLDString(permissions)
                        )

                        val valueHasCommentAsJsonLD: Option[(IRI, JsonLDValue)] = valueContent.comment.map {
                            definedComment => OntologyConstants.KnoraApiV2WithValueObjects.ValueHasComment -> JsonLDString(definedComment)
                        }

                        val deletionInfoAsJsonLD: Map[IRI, JsonLDValue] = deletionInfo match {
                            case Some(definedDeletionInfo) => definedDeletionInfo.toJsonLDFields(ApiV2WithValueObjects)
                            case None => Map.empty[IRI, JsonLDValue]
                        }

                        JsonLDObject(jsonLDObject.value ++ requiredMetadata ++ valueHasCommentAsJsonLD ++ deletionInfoAsJsonLD)

                    case other =>
                        throw AssertionException(s"Expected value $valueIri to be a represented as a JSON-LD object in the complex schema, but found $other")
                }

            case ApiV2Simple => valueContentAsJsonLD
        }
    }
}

/**
  * The value of a Knora property sent to Knora to be created in an existing resource.
  *
  * @param resourceIri      the resource the new value should be attached to.
  * @param resourceClassIri the resource class that the client believes the resource belongs to.
  * @param propertyIri      the property of the new value. If the client wants to create a link, this must be a link value property.
  * @param valueContent     the content of the new value. If the client wants to create a link, this must be a [[LinkValueContentV2]].
  * @param permissions      the permissions to be given to the new value. If not provided, these will be taken from defaults.
  */
case class CreateValueV2(resourceIri: IRI,
                         resourceClassIri: SmartIri,
                         propertyIri: SmartIri,
                         valueContent: ValueContentV2,
                         permissions: Option[String] = None) extends IOValueV2

/**
  * A new version of a value of a Knora property to be created.
  *
  * @param resourceIri      the resource that the current value version is attached to.
  * @param resourceClassIri the resource class that the client believes the resource belongs to.
  * @param propertyIri      the property that the client believes points to the value. If the value is a link value,
  *                         this must be a link value property.
  * @param valueIri         the IRI of the value to be updated.
  * @param valueContent     the content of the new version of the value.
  */
case class UpdateValueV2(resourceIri: IRI,
                         resourceClassIri: SmartIri,
                         propertyIri: SmartIri,
                         valueIri: IRI,
                         valueContent: ValueContentV2,
                         permissions: Option[String] = None) extends IOValueV2

/**
  * The IRI and content of a new value or value version whose existence in the triplestore needs to be verified.
  *
  * @param newValueIri the IRI that was assigned to the new value.
  * @param value       the content of the new value.
  */
case class UnverifiedValueV2(newValueIri: IRI, value: ValueContentV2)

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
      * A comment on this `ValueContentV2`, if any.
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
    def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue

    /**
      * Undoes the SPARQL-escaping of strings in this [[ValueContentV2]].
      *
      * @return the same [[ValueContentV2]] with its strings unescaped.
      */
    def unescape: ValueContentV2

    /**
      * Returns `true` if creating this [[ValueContentV2]] as a new value would duplicate the specified other value.
      * This means that if resource `R` has property `P` with value `V1`, and `V1` would dupliate `V2`, the API server
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
      * @param jsonLDObject     the JSON-LD object.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a subclass of [[ValueContentV2]].
      */
    def fromJsonLDObject(jsonLDObject: JsonLDObject,
                         requestingUser: UserADM,
                         responderManager: ActorSelection,
                         log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[C]

    protected def getComment(jsonLDObject: JsonLDObject)(implicit stringFormatter: StringFormatter): Option[String] = {
        jsonLDObject.maybeStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.ValueHasComment, stringFormatter.toSparqlEncodedString)
    }
}

/**
  * Generates instances of value content classes (subclasses of [[ValueContentV2]]) from JSON-LD input.
  */
object ValueContentV2 extends ValueContentReaderV2[ValueContentV2] {
    /**
      * Converts a JSON-LD object to a [[ValueContentV2]].
      *
      * @param jsonLDObject     a JSON-LD object representing a value.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[ValueContentV2]].
      */
    override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                  requestingUser: UserADM,
                                  responderManager: ActorSelection,
                                  log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ValueContentV2] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        for {
            valueType: SmartIri <- Future(jsonLDObject.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr))

            valueContent <- valueType.toString match {
                case OntologyConstants.KnoraApiV2WithValueObjects.TextValue =>
                    TextValueContentV2.fromJsonLDObject(jsonLDObject = jsonLDObject, requestingUser = requestingUser, responderManager = responderManager, log = log)

                case OntologyConstants.KnoraApiV2WithValueObjects.IntValue =>
                    IntegerValueContentV2.fromJsonLDObject(jsonLDObject = jsonLDObject, requestingUser = requestingUser, responderManager = responderManager, log = log)

                case OntologyConstants.KnoraApiV2WithValueObjects.DecimalValue =>
                    DecimalValueContentV2.fromJsonLDObject(jsonLDObject = jsonLDObject, requestingUser = requestingUser, responderManager = responderManager, log = log)

                case OntologyConstants.KnoraApiV2WithValueObjects.BooleanValue =>
                    BooleanValueContentV2.fromJsonLDObject(jsonLDObject = jsonLDObject, requestingUser = requestingUser, responderManager = responderManager, log = log)

                case OntologyConstants.KnoraApiV2WithValueObjects.DateValue =>
                    DateValueContentV2.fromJsonLDObject(jsonLDObject = jsonLDObject, requestingUser = requestingUser, responderManager = responderManager, log = log)

                case OntologyConstants.KnoraApiV2WithValueObjects.GeomValue =>
                    GeomValueContentV2.fromJsonLDObject(jsonLDObject = jsonLDObject, requestingUser = requestingUser, responderManager = responderManager, log = log)

                case OntologyConstants.KnoraApiV2WithValueObjects.IntervalValue =>
                    IntervalValueContentV2.fromJsonLDObject(jsonLDObject = jsonLDObject, requestingUser = requestingUser, responderManager = responderManager, log = log)

                case OntologyConstants.KnoraApiV2WithValueObjects.LinkValue =>
                    LinkValueContentV2.fromJsonLDObject(jsonLDObject = jsonLDObject, requestingUser = requestingUser, responderManager = responderManager, log = log)

                case OntologyConstants.KnoraApiV2WithValueObjects.ListValue =>
                    HierarchicalListValueContentV2.fromJsonLDObject(jsonLDObject = jsonLDObject, requestingUser = requestingUser, responderManager = responderManager, log = log)

                case OntologyConstants.KnoraApiV2WithValueObjects.UriValue =>
                    UriValueContentV2.fromJsonLDObject(jsonLDObject = jsonLDObject, requestingUser = requestingUser, responderManager = responderManager, log = log)

                case OntologyConstants.KnoraApiV2WithValueObjects.GeonameValue =>
                    GeonameValueContentV2.fromJsonLDObject(jsonLDObject = jsonLDObject, requestingUser = requestingUser, responderManager = responderManager, log = log)

                case OntologyConstants.KnoraApiV2WithValueObjects.ColorValue =>
                    ColorValueContentV2.fromJsonLDObject(jsonLDObject = jsonLDObject, requestingUser = requestingUser, responderManager = responderManager, log = log)

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
  * @param comment                a comment on this `DateValueContentV2`, if any.
  */
case class DateValueContentV2(ontologySchema: OntologySchema,
                              valueHasStartJDN: Int,
                              valueHasEndJDN: Int,
                              valueHasStartPrecision: KnoraPrecisionV1.Value,
                              valueHasEndPrecision: KnoraPrecisionV1.Value,
                              valueHasCalendar: KnoraCalendarV1.Value,
                              comment: Option[String] = None) extends ValueContentV2 {
    override def valueType: SmartIri = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        OntologyConstants.KnoraBase.DateValue.toSmartIri.toOntologySchema(ontologySchema)
    }

    // We compute valueHasString instead of taking it from the triplestore, because the
    // string literal in the triplestore isn't in API v2 format.
    override lazy val valueHasString: String = {
        val startDate = DateUtilV2.jdnToDateYearMonthDay(
            julianDayNumber = valueHasStartJDN,
            precision = valueHasStartPrecision,
            calendar = valueHasCalendar
        )

        val endDate = DateUtilV2.jdnToDateYearMonthDay(
            julianDayNumber = valueHasEndJDN,
            precision = valueHasEndPrecision,
            calendar = valueHasCalendar
        )

        DateUtilV2.dateRangeToString(
            startDate = startDate,
            endDate = endDate,
            calendar = valueHasCalendar
        )
    }

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = copy(ontologySchema = targetSchema)

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple =>
                JsonLDUtil.datatypeValueToJsonLDObject(
                    value = valueHasString,
                    datatype = OntologyConstants.KnoraApiV2Simple.Date.toSmartIri
                )

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(
                    OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> JsonLDString(valueHasString),
                    OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar -> JsonLDString(valueHasCalendar.toString)
                ) ++ toComplexDateValueAssertions)
        }
    }

    /**
      * Create knora-api assertions.
      *
      * @return a Map of [[ApiV2WithValueObjects]] value properties to numbers (year, month, day) representing the date value.
      */
    def toComplexDateValueAssertions: Map[IRI, JsonLDValue] = {

        val startDateConversion: DateYearMonthDay = DateUtilV2.jdnToDateYearMonthDay(valueHasStartJDN, valueHasStartPrecision, valueHasCalendar)

        val startDateAssertions = startDateConversion.toStartDateAssertions.map {
            case (k: IRI, v: Int) => (k, JsonLDInt(v))

        } ++ startDateConversion.toStartEraAssertion.map {

            case (k: IRI, v: String) => (k, JsonLDString(v))
        }
        val endDateConversion: DateYearMonthDay = DateUtilV2.jdnToDateYearMonthDay(valueHasEndJDN, valueHasEndPrecision, valueHasCalendar)

        val endDateAssertions = endDateConversion.toEndDateAssertions.map {
            case (k: IRI, v: Int) => (k, JsonLDInt(v))

        } ++ endDateConversion.toEndEraAssertion.map {

            case (k: IRI, v: String) => (k, JsonLDString(v))
        }

        startDateAssertions ++ endDateAssertions
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
      * Converts a JSON-LD object to a [[DateValueContentV2]].
      *
      * @param jsonLDObject     the JSON-LD object.
      * @param responderManager a reference to the responder manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[DateValueContentV2]].
      */
    override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                  requestingUser: UserADM,
                                  responderManager: ActorSelection,
                                  log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[DateValueContentV2] = {
        Future(fromJsonLDObjectSync(jsonLDObject))
    }

    private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): DateValueContentV2 = {

        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val calendar: KnoraCalendarV1.Value = jsonLDObject.requireStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar, stringFormatter.validateCalendar)

        val dateValueHasStartYear: Int = jsonLDObject.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear)
        val maybeDateValueHasStartMonth: Option[Int] = jsonLDObject.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth)
        val maybeDateValueHasStartDay: Option[Int] = jsonLDObject.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay)
        val maybeDateValueHasStartEra: Option[KnoraEraV2.Value] = jsonLDObject.maybeStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra, stringFormatter.validateEra)

        val dateValueHasEndYear: Int = jsonLDObject.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear)
        val maybeDateValueHasEndMonth: Option[Int] = jsonLDObject.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth)
        val maybeDateValueHasEndDay: Option[Int] = jsonLDObject.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay)
        val maybeDateValueHasEndEra: Option[KnoraEraV2.Value] = jsonLDObject.maybeStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra, stringFormatter.validateEra)

        val startDate = DateYearMonthDay(
            year = dateValueHasStartYear,
            maybeMonth = maybeDateValueHasStartMonth,
            maybeDay = maybeDateValueHasStartDay,
            era = maybeDateValueHasStartEra.getOrElse(KnoraEraV2.CE)
        )

        val endDate = DateYearMonthDay(
            year = dateValueHasEndYear,
            maybeMonth = maybeDateValueHasEndMonth,
            maybeDay = maybeDateValueHasEndDay,
            era = maybeDateValueHasEndEra.getOrElse(KnoraEraV2.CE)
        )

        // TODO: convert the date range to start and end JDNs without first converting it to a string (#928).

        val dateRangeStr = DateUtilV2.dateRangeToString(
            startDate = startDate,
            endDate = endDate,
            calendar = calendar
        )

        val julianDateRange: JulianDayNumberValueV1 = DateUtilV1.createJDNValueV1FromDateString(dateRangeStr)

        DateValueContentV2(
            ontologySchema = ApiV2WithValueObjects,
            valueHasStartJDN = julianDateRange.dateval1,
            valueHasEndJDN = julianDateRange.dateval2,
            valueHasStartPrecision = startDate.getPrecision,
            valueHasEndPrecision = endDate.getPrecision,
            valueHasCalendar = calendar,
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
case class CreateStandoffTagV2InTriplestore(standoffNode: StandoffTagV2, standoffTagInstanceIri: IRI, startParentIri: Option[IRI] = None, endParentIri: Option[IRI] = None)


/**
  * Represents a Knora text value.
  *
  * @param valueHasString     the string representation of the text (without markup).
  * @param standoffAndMapping a [[StandoffAndMapping]], if any.
  * @param comment            a comment on this `TextValueContentV2`, if any.
  */
case class TextValueContentV2(ontologySchema: OntologySchema,
                              valueHasString: String,
                              valueHasLanguage: Option[String] = None,
                              standoffAndMapping: Option[StandoffAndMapping] = None,
                              comment: Option[String] = None) extends ValueContentV2 {
    private val knoraIdUtil = new KnoraIdUtil

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
        standoffAndMapping match {
            case Some(definedStandoffAndMapping) =>
                definedStandoffAndMapping.standoff.foldLeft(Set.empty[StandoffTagIriAttributeV2]) {
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

            case None => Set.empty[StandoffTagIriAttributeV2]
        }
    }

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = copy(ontologySchema = targetSchema)

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple =>
                valueHasLanguage match {
                    case Some(lang) =>
                        // In the simple schema, if this text value specifies a language, return it using a JSON-LD
                        // @language key as per <https://json-ld.org/spec/latest/json-ld/#string-internationalization>.
                        JsonLDUtil.objectWithLangToJsonLDObject(
                            obj = valueHasString,
                            lang = lang
                        )

                    case None => JsonLDString(valueHasString)
                }

            case ApiV2WithValueObjects =>
                val objectMap: Map[IRI, JsonLDValue] = if (standoffAndMapping.nonEmpty) {

                    val xmlFromStandoff = StandoffTagUtilV2.convertStandoffTagV2ToXML(valueHasString, standoffAndMapping.get.standoff, standoffAndMapping.get.mapping)

                    // check if there is an XSL transformation
                    if (standoffAndMapping.get.xslt.nonEmpty) {

                        val xmlTransformed: String = XMLUtil.applyXSLTransformation(xmlFromStandoff, standoffAndMapping.get.xslt.get)

                        // the xml was converted to HTML
                        Map(OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsHtml -> JsonLDString(xmlTransformed))
                    } else {
                        // xml is returned
                        Map(
                            OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsXml -> JsonLDString(xmlFromStandoff),
                            OntologyConstants.KnoraApiV2WithValueObjects.TextValueHasMapping -> JsonLDString(standoffAndMapping.get.mappingIri)
                        )
                    }

                } else {
                    // no markup given
                    Map(OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> JsonLDString(valueHasString))
                }

                // In the complex schema, if this text value specifies a language, return it using the predicate
                // knora-api:textValueHasLanguage.
                val objectMapWithLanguage: Map[IRI, JsonLDValue] = valueHasLanguage match {
                    case Some(lang) =>
                        objectMap + (OntologyConstants.KnoraApiV2WithValueObjects.TextValueHasLanguage -> JsonLDString(lang))
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

        standoffAndMapping match {
            case Some(definedStandoffAndMapping) =>
                // create an IRI for each standoff tag
                // internal references to XML ids are not resolved yet
                val standoffTagsWithOriginalXMLIDs: Seq[CreateStandoffTagV2InTriplestore] = definedStandoffAndMapping.standoff.map {
                    standoffNode: StandoffTagV2 =>
                        CreateStandoffTagV2InTriplestore(
                            standoffNode = standoffNode,
                            standoffTagInstanceIri = knoraIdUtil.makeRandomStandoffTagIri(valueIri) // generate IRI for new standoff node
                        )
                }

                // collect all the standoff tags that contain XML ids and
                // map the XML ids to standoff node Iris
                val iDsToStandoffNodeIris: Map[IRI, IRI] = standoffTagsWithOriginalXMLIDs.filter {
                    standoffTag: CreateStandoffTagV2InTriplestore =>
                        // filter those tags out that have an XML id
                        standoffTag.standoffNode.originalXMLID.isDefined
                }.map {
                    standoffTagWithID: CreateStandoffTagV2InTriplestore =>
                        // return the XML id as a key and the standoff IRI as the value
                        standoffTagWithID.standoffNode.originalXMLID.get -> standoffTagWithID.standoffTagInstanceIri
                }.toMap

                // Map the start index of each tag to its IRI, so we can resolve references to parent tags as references to
                // tag IRIs. We only care about start indexes here, because only hierarchical tags can be parents, and
                // hierarchical tags don't have end indexes.
                val startIndexesToStandoffNodeIris: Map[Int, IRI] = standoffTagsWithOriginalXMLIDs.map {
                    tagWithIndex => tagWithIndex.standoffNode.startIndex -> tagWithIndex.standoffTagInstanceIri
                }.toMap

                // resolve the original XML ids to standoff Iris every the `StandoffTagInternalReferenceAttributeV1`
                val standoffTagsWithNodeReferences: Seq[CreateStandoffTagV2InTriplestore] = standoffTagsWithOriginalXMLIDs.map {
                    standoffTag: CreateStandoffTagV2InTriplestore =>

                        // resolve original XML ids to standoff node Iris for `StandoffTagInternalReferenceAttributeV1`
                        val attributesWithStandoffNodeIriReferences: Seq[StandoffTagAttributeV2] = standoffTag.standoffNode.attributes.map {
                            attributeWithOriginalXMLID: StandoffTagAttributeV2 =>
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

            case None => Seq.empty[CreateStandoffTagV2InTriplestore]

        }

    }

    override def unescape: ValueContentV2 = {
        copy(
            valueHasString = stringFormatter.fromSparqlEncodedString(valueHasString),
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

                // compare standoff nodes (sort them first by index) and the XML-to-standoff mapping IRI
                val standoffIdentical: Boolean = (standoffAndMapping, thatTextValue.standoffAndMapping) match {
                    case (Some(thisStandoffAndMapping), Some(thatStandoffAndMapping)) =>
                        val thisComparableStandoff = StandoffTagUtilV2.makeComparableStandoffCollection(thisStandoffAndMapping.standoff)
                        val thatComparableStandoff = StandoffTagUtilV2.makeComparableStandoffCollection(thatStandoffAndMapping.standoff)
                        thisComparableStandoff == thatComparableStandoff && thisStandoffAndMapping.mappingIri == thatStandoffAndMapping.mappingIri

                    case (None, None) => true

                    case _ => false
                }

                valueHasStringIdentical && standoffIdentical && comment == thatTextValue.comment

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
      * @param jsonLDObject     the JSON-LD object.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[TextValueContentV2]].
      */
    override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                  requestingUser: UserADM,
                                  responderManager: ActorSelection,
                                  log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[TextValueContentV2] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        for {
            maybeValueAsString: Option[String] <- Future(jsonLDObject.maybeStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString, stringFormatter.toSparqlEncodedString))
            maybeValueHasLanguage: Option[String] = jsonLDObject.maybeStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.TextValueHasLanguage, stringFormatter.toSparqlEncodedString)
            maybeTextValueAsXml: Option[String] = jsonLDObject.maybeString(OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsXml)
            maybeMappingIri: Option[IRI] = jsonLDObject.maybeIriInObject(OntologyConstants.KnoraApiV2WithValueObjects.TextValueHasMapping, stringFormatter.validateAndEscapeIri)

            // If the client supplied the IRI of a standoff-to-XML mapping, get the mapping.

            maybeMappingFuture: Option[Future[GetMappingResponseV2]] = maybeMappingIri.map {
                mappingIri =>
                    for {
                        mappingResponse: GetMappingResponseV2 <- (responderManager ? GetMappingRequestV2(mappingIri = mappingIri, requestingUser = requestingUser)).mapTo[GetMappingResponseV2]
                    } yield mappingResponse
            }

            maybeMappingResponse: Option[GetMappingResponseV2] <- ActorUtil.optionFuture2FutureOption(maybeMappingFuture)

            // Did the client submit text with or without standoff markup?
            textValue: TextValueContentV2 = (maybeValueAsString, maybeTextValueAsXml, maybeMappingResponse) match {
                case (Some(valueAsString), None, None) =>
                    // Text without standoff.
                    TextValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasString = valueAsString,
                        comment = getComment(jsonLDObject)
                    )

                case (None, Some(textValueAsXml), Some(mappingResponse)) =>
                    // Text with standoff.

                    val textWithStandoffTags: TextWithStandoffTagsV2 = StandoffTagUtilV2.convertXMLtoStandoffTagV2(
                        xml = textValueAsXml,
                        mapping = mappingResponse,
                        acceptStandoffLinksToClientIDs = false,
                        log = log
                    )

                    val standoffAndMapping = StandoffAndMapping(
                        standoff = textWithStandoffTags.standoffTagV2,
                        mappingIri = mappingResponse.mappingIri,
                        mapping = mappingResponse.mapping,
                        xslt = None
                    )

                    TextValueContentV2(
                        ontologySchema = ApiV2WithValueObjects,
                        valueHasString = stringFormatter.toSparqlEncodedString(textWithStandoffTags.text, throw BadRequestException("Text value contains invalid characters")),
                        valueHasLanguage = maybeValueHasLanguage,
                        standoffAndMapping = Some(standoffAndMapping),
                        comment = getComment(jsonLDObject)
                    )

                case _ => throw BadRequestException(s"Invalid combination of knora-api:valueHasString, knora-api:textValueAsXml, and/or knora-api:textValueHasMapping")
            }

        } yield textValue
    }
}

/**
  * Represents standoff and the corresponding mapping.
  * May include an XSL transformation.
  *
  * @param standoff   a sequence of [[StandoffTagV2]].
  * @param mappingIri the IRI of the mapping
  * @param mapping    a mapping between XML and standoff.
  * @param xslt       an XSL transformation.
  */
case class StandoffAndMapping(standoff: Seq[StandoffTagV2], mappingIri: IRI, mapping: MappingXMLtoStandoff, xslt: Option[String] = None)

/**
  * Represents a Knora integer value.
  *
  * @param valueHasInteger the integer value.
  * @param comment         a comment on this `IntegerValueContentV2`, if any.
  */
case class IntegerValueContentV2(ontologySchema: OntologySchema,
                                 valueHasInteger: Int,
                                 comment: Option[String] = None) extends ValueContentV2 {
    override def valueType: SmartIri = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        OntologyConstants.KnoraBase.IntValue.toSmartIri.toOntologySchema(ontologySchema)
    }

    override lazy val valueHasString: String = valueHasInteger.toString

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = copy(ontologySchema = targetSchema)

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple => JsonLDInt(valueHasInteger)

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.IntValueAsInt -> JsonLDInt(valueHasInteger)))

        }
    }

    override def unescape: ValueContentV2 = {
        copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        that match {
            case thatIntegerValue: IntegerValueContentV2 => valueHasInteger == thatIntegerValue.valueHasInteger
            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
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
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return an [[IntegerValueContentV2]].
      */
    override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                  requestingUser: UserADM,
                                  responderManager: ActorSelection,
                                  log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[IntegerValueContentV2] = {
        Future(fromJsonLDObjectSync(jsonLDObject))
    }

    private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): IntegerValueContentV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val intValueAsInt: Int = jsonLDObject.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.IntValueAsInt)

        IntegerValueContentV2(
            ontologySchema = ApiV2WithValueObjects,
            valueHasInteger = intValueAsInt,
            comment = getComment(jsonLDObject)
        )
    }
}

/**
  * Represents a Knora decimal value.
  *
  * @param valueHasDecimal the decimal value.
  * @param comment         a comment on this `DecimalValueContentV2`, if any.
  */
case class DecimalValueContentV2(ontologySchema: OntologySchema,
                                 valueHasDecimal: BigDecimal,
                                 comment: Option[String] = None) extends ValueContentV2 {
    override def valueType: SmartIri = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        OntologyConstants.KnoraBase.DecimalValue.toSmartIri.toOntologySchema(ontologySchema)
    }

    override lazy val valueHasString: String = valueHasDecimal.toString

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = copy(ontologySchema = targetSchema)

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        val decimalValueAsJsonLDObject = JsonLDUtil.datatypeValueToJsonLDObject(
            value = valueHasDecimal.toString,
            datatype = OntologyConstants.Xsd.Decimal.toSmartIri
        )

        targetSchema match {
            case ApiV2Simple => decimalValueAsJsonLDObject

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.DecimalValueAsDecimal -> decimalValueAsJsonLDObject))
        }
    }

    override def unescape: ValueContentV2 = {
        copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        that match {
            case thatDecimalValue: DecimalValueContentV2 => valueHasDecimal == thatDecimalValue.valueHasDecimal
            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
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
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return an [[DecimalValueContentV2]].
      */
    override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                  requestingUser: UserADM,
                                  responderManager: ActorSelection,
                                  log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[DecimalValueContentV2] = {
        Future(fromJsonLDObjectSync(jsonLDObject))
    }

    private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): DecimalValueContentV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val decimalValueAsDecimal: BigDecimal = jsonLDObject.requireDatatypeValueInObject(
            key = OntologyConstants.KnoraApiV2WithValueObjects.DecimalValueAsDecimal,
            expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
            validationFun = stringFormatter.validateBigDecimal
        )

        DecimalValueContentV2(
            ontologySchema = ApiV2WithValueObjects,
            valueHasDecimal = decimalValueAsDecimal,
            comment = getComment(jsonLDObject)
        )
    }
}

/**
  * Represents a Boolean value.
  *
  * @param valueHasBoolean the Boolean value.
  * @param comment         a comment on this `BooleanValueContentV2`, if any.
  */
case class BooleanValueContentV2(ontologySchema: OntologySchema,
                                 valueHasBoolean: Boolean,
                                 comment: Option[String] = None) extends ValueContentV2 {
    override def valueType: SmartIri = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        OntologyConstants.KnoraBase.BooleanValue.toSmartIri.toOntologySchema(ontologySchema)
    }

    override lazy val valueHasString: String = valueHasBoolean.toString

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = copy(ontologySchema = targetSchema)

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple => JsonLDBoolean(valueHasBoolean)

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.BooleanValueAsBoolean -> JsonLDBoolean(valueHasBoolean)))
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
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return an [[BooleanValueContentV2]].
      */
    override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                  requestingUser: UserADM,
                                  responderManager: ActorSelection,
                                  log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[BooleanValueContentV2] = {
        Future(fromJsonLDObjectSync(jsonLDObject))
    }

    private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): BooleanValueContentV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val booleanValueAsBoolean: Boolean = jsonLDObject.requireBoolean(OntologyConstants.KnoraApiV2WithValueObjects.BooleanValueAsBoolean)

        BooleanValueContentV2(
            ontologySchema = ApiV2WithValueObjects,
            valueHasBoolean = booleanValueAsBoolean,
            comment = getComment(jsonLDObject)
        )
    }
}

/**
  * Represents a Knora geometry value (a 2D-shape).
  *
  * @param valueHasGeometry JSON representing a 2D geometrical shape.
  * @param comment          a comment on this `GeomValueContentV2`, if any.
  */
case class GeomValueContentV2(ontologySchema: OntologySchema,
                              valueHasGeometry: String,
                              comment: Option[String] = None) extends ValueContentV2 {
    override def valueType: SmartIri = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        OntologyConstants.KnoraBase.GeomValue.toSmartIri.toOntologySchema(ontologySchema)
    }

    override def valueHasString: String = valueHasGeometry

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = copy(ontologySchema = targetSchema)

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple =>
                JsonLDUtil.datatypeValueToJsonLDObject(
                    value = valueHasGeometry,
                    datatype = OntologyConstants.KnoraApiV2Simple.Geom.toSmartIri
                )

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.GeometryValueAsGeometry -> JsonLDString(valueHasGeometry)))
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
            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
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
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return an [[GeomValueContentV2]].
      */
    override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                  requestingUser: UserADM,
                                  responderManager: ActorSelection,
                                  log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[GeomValueContentV2] = {
        Future(fromJsonLDObjectSync(jsonLDObject))
    }

    private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): GeomValueContentV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val geometryValueAsGeometry: String = jsonLDObject.requireStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.GeometryValueAsGeometry, stringFormatter.validateGeometryString)

        GeomValueContentV2(
            ontologySchema = ApiV2WithValueObjects,
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
  * @param comment               a comment on this `IntervalValueContentV2`, if any.
  */
case class IntervalValueContentV2(ontologySchema: OntologySchema,
                                  valueHasIntervalStart: BigDecimal,
                                  valueHasIntervalEnd: BigDecimal,
                                  comment: Option[String] = None) extends ValueContentV2 {
    override def valueType: SmartIri = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        OntologyConstants.KnoraBase.IntervalValue.toSmartIri.toOntologySchema(ontologySchema)
    }

    override lazy val valueHasString: String = s"$valueHasIntervalStart - $valueHasIntervalEnd"

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = copy(ontologySchema = targetSchema)

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple =>
                JsonLDUtil.datatypeValueToJsonLDObject(
                    value = valueHasString,
                    datatype = OntologyConstants.KnoraApiV2Simple.Interval.toSmartIri
                )

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(
                    OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasStart ->
                        JsonLDUtil.datatypeValueToJsonLDObject(
                            value = valueHasIntervalStart.toString,
                            datatype = OntologyConstants.Xsd.Decimal.toSmartIri
                        ),
                    OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasEnd ->
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
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return an [[IntervalValueContentV2]].
      */
    override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                  requestingUser: UserADM,
                                  responderManager: ActorSelection,
                                  log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[IntervalValueContentV2] = {
        Future(fromJsonLDObjectSync(jsonLDObject))
    }

    private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): IntervalValueContentV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val intervalValueHasStart: BigDecimal = jsonLDObject.requireDatatypeValueInObject(
            OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasStart,
            OntologyConstants.Xsd.Decimal.toSmartIri,
            stringFormatter.validateBigDecimal
        )

        val intervalValueHasEnd: BigDecimal = jsonLDObject.requireDatatypeValueInObject(
            OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasEnd,
            OntologyConstants.Xsd.Decimal.toSmartIri,
            stringFormatter.validateBigDecimal
        )

        IntervalValueContentV2(
            ontologySchema = ApiV2WithValueObjects,
            valueHasIntervalStart = intervalValueHasStart,
            valueHasIntervalEnd = intervalValueHasEnd,
            comment = getComment(jsonLDObject)
        )
    }
}

/**
  * Represents a value pointing to a Knora hierarchical list node.
  *
  * @param valueHasListNode the IRI of the hierarchical list node pointed to.
  * @param listNodeLabel    the label of the hierarchical list node pointed to.
  * @param comment          a comment on this `HierarchicalListValueContentV2`, if any.
  */
case class HierarchicalListValueContentV2(ontologySchema: OntologySchema,
                                          valueHasListNode: IRI,
                                          listNodeLabel: Option[String] = None,
                                          comment: Option[String] = None) extends ValueContentV2 {
    override def valueType: SmartIri = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        OntologyConstants.KnoraBase.ListValue.toSmartIri.toOntologySchema(ontologySchema)
    }

    override def valueHasString: String = valueHasListNode

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = copy(ontologySchema = targetSchema)

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple =>
                listNodeLabel match {
                    case Some(labelStr) => JsonLDString(labelStr)
                    case None => throw AssertionException("Can't convert list value to simple schema because it has no list node label")

                }

            case ApiV2WithValueObjects =>
                JsonLDObject(
                    Map(
                        OntologyConstants.KnoraApiV2WithValueObjects.ListValueAsListNode -> JsonLDUtil.iriToJsonLDObject(valueHasListNode)
                    ) ++ listNodeLabel.map(labelStr => OntologyConstants.KnoraApiV2WithValueObjects.ListValueAsListNodeLabel -> JsonLDString(labelStr))
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
            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
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
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[HierarchicalListValueContentV2]].
      */
    override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                  requestingUser: UserADM,
                                  responderManager: ActorSelection,
                                  log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[HierarchicalListValueContentV2] = {
        Future(fromJsonLDObjectSync(jsonLDObject))
    }

    private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): HierarchicalListValueContentV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val listValueAsListNode: SmartIri = jsonLDObject.requireIriInObject(OntologyConstants.KnoraApiV2WithValueObjects.ListValueAsListNode, stringFormatter.toSmartIriWithErr)

        if (!listValueAsListNode.isKnoraDataIri) {
            throw BadRequestException(s"List node IRI <$listValueAsListNode> is not a Knora data IRI")
        }

        HierarchicalListValueContentV2(
            ontologySchema = ApiV2WithValueObjects,
            valueHasListNode = listValueAsListNode.toString,
            comment = getComment(jsonLDObject)
        )
    }
}


/**
  * Represents a Knora color value.
  *
  * @param valueHasColor a hexadecimal string containing the RGB color value
  * @param comment       a comment on this `ColorValueContentV2`, if any.
  */
case class ColorValueContentV2(ontologySchema: OntologySchema,
                               valueHasColor: String,
                               comment: Option[String] = None) extends ValueContentV2 {
    override def valueType: SmartIri = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        OntologyConstants.KnoraBase.ColorValue.toSmartIri.toOntologySchema(ontologySchema)
    }

    override def valueHasString: String = valueHasColor

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = copy(ontologySchema = targetSchema)

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple =>
                JsonLDUtil.datatypeValueToJsonLDObject(
                    value = valueHasColor,
                    datatype = OntologyConstants.KnoraApiV2Simple.Color.toSmartIri
                )

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.ColorValueAsColor -> JsonLDString(valueHasColor)))
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
            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
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
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[ColorValueContentV2]].
      */
    override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                  requestingUser: UserADM,
                                  responderManager: ActorSelection,
                                  log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ColorValueContentV2] = {
        Future(fromJsonLDObjectSync(jsonLDObject))
    }

    private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): ColorValueContentV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val colorValueAsColor: String = jsonLDObject.requireStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.ColorValueAsColor, stringFormatter.toSparqlEncodedString)

        ColorValueContentV2(
            ontologySchema = ApiV2WithValueObjects,
            valueHasColor = colorValueAsColor,
            comment = getComment(jsonLDObject)
        )
    }
}

/**
  * Represents a Knora URI value.
  *
  * @param valueHasUri the URI value.
  * @param comment     a comment on this `UriValueContentV2`, if any.
  */
case class UriValueContentV2(ontologySchema: OntologySchema,
                             valueHasUri: String,
                             comment: Option[String] = None) extends ValueContentV2 {
    override def valueType: SmartIri = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        OntologyConstants.KnoraBase.UriValue.toSmartIri.toOntologySchema(ontologySchema)
    }

    override def valueHasString: String = valueHasUri

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = copy(ontologySchema = targetSchema)

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        val uriAsJsonLDObject = JsonLDUtil.datatypeValueToJsonLDObject(
            value = valueHasUri,
            datatype = OntologyConstants.Xsd.Uri.toSmartIri
        )

        targetSchema match {
            case ApiV2Simple => uriAsJsonLDObject

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.UriValueAsUri -> uriAsJsonLDObject))
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
            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
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
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[UriValueContentV2]].
      */
    override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                  requestingUser: UserADM,
                                  responderManager: ActorSelection,
                                  log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[UriValueContentV2] = {
        Future(fromJsonLDObjectSync(jsonLDObject))
    }

    private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): UriValueContentV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val uriValueAsUri: String = jsonLDObject.requireDatatypeValueInObject(
            key = OntologyConstants.KnoraApiV2WithValueObjects.UriValueAsUri,
            expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
            validationFun = stringFormatter.toSparqlEncodedString
        )

        UriValueContentV2(
            ontologySchema = ApiV2WithValueObjects,
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
  * @param comment             a comment on this `GeonameValueContentV2`, if any.
  */
case class GeonameValueContentV2(ontologySchema: OntologySchema,
                                 valueHasGeonameCode: String,
                                 comment: Option[String] = None) extends ValueContentV2 {
    override def valueType: SmartIri = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        OntologyConstants.KnoraBase.GeonameValue.toSmartIri.toOntologySchema(ontologySchema)
    }

    override def valueHasString: String = valueHasGeonameCode

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = copy(ontologySchema = targetSchema)

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple =>
                JsonLDUtil.datatypeValueToJsonLDObject(
                    value = valueHasGeonameCode,
                    datatype = OntologyConstants.KnoraApiV2Simple.Geoname.toSmartIri
                )

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.GeonameValueAsGeonameCode -> JsonLDString(valueHasGeonameCode)))
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
            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
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
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[GeonameValueContentV2]].
      */
    override def fromJsonLDObject(jsonLDObject: JsonLDObject,
                                  requestingUser: UserADM,
                                  responderManager: ActorSelection,
                                  log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[GeonameValueContentV2] = {
        Future(fromJsonLDObjectSync(jsonLDObject))
    }

    private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): GeonameValueContentV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val geonameValueAsGeonameCode: String = jsonLDObject.requireStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.GeonameValueAsGeonameCode, stringFormatter.toSparqlEncodedString)

        GeonameValueContentV2(
            ontologySchema = ApiV2WithValueObjects,
            valueHasGeonameCode = geonameValueAsGeonameCode,
            comment = getComment(jsonLDObject)
        )
    }
}

/**
  * An abstract trait representing any file value.
  */
sealed trait FileValueContentV2 {
    val internalMimeType: String
    val internalFilename: String
    val originalFilename: String
    val originalMimeType: Option[String]

    protected def toJsonLDValueinSimpleSchema(imagePath: String): JsonLDObject = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        JsonLDUtil.datatypeValueToJsonLDObject(
            value = imagePath,
            datatype = OntologyConstants.KnoraApiV2Simple.File.toSmartIri
        )
    }
}

/**
  * Represents an image file. Please note that the file itself is managed by Sipi.
  *
  * @param internalMimeType the mime type of the file corresponding to this image file value.
  * @param internalFilename the name of the file corresponding to this image file value.
  * @param originalFilename the original mime type of the image file before importing it.
  * @param originalMimeType the original name of the image file before importing it.
  * @param dimX             the with of the the image file corresponding to this file value in pixels.
  * @param dimY             the height of the the image file corresponding to this file value in pixels.
  * @param qualityLevel     the quality (resolution) of the the image file corresponding to this file value (scale 10-100)
  * @param isPreview        indicates if the file value represents a preview image (thumbnail).
  * @param comment          a comment on this `StillImageFileValueContentV2`, if any.
  */
case class StillImageFileValueContentV2(ontologySchema: OntologySchema,
                                        internalMimeType: String,
                                        internalFilename: String,
                                        originalFilename: String,
                                        originalMimeType: Option[String],
                                        dimX: Int,
                                        dimY: Int,
                                        qualityLevel: Int,
                                        isPreview: Boolean,
                                        comment: Option[String] = None) extends FileValueContentV2 with ValueContentV2 {
    override def valueType: SmartIri = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        OntologyConstants.KnoraBase.StillImageFileValue.toSmartIri.toOntologySchema(ontologySchema)
    }

    override def valueHasString: String = internalFilename

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = copy(ontologySchema = targetSchema)

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        val imagePath: String = s"${settings.externalSipiIIIFGetUrl}/$internalFilename/full/$dimX,$dimY/0/default.jpg"

        targetSchema match {
            case ApiV2Simple => toJsonLDValueinSimpleSchema(imagePath)

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(
                    OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl -> JsonLDString(imagePath),
                    OntologyConstants.KnoraApiV2WithValueObjects.FileValueIsPreview -> JsonLDBoolean(isPreview),
                    OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasDimX -> JsonLDInt(dimX),
                    OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasDimY -> JsonLDInt(dimY),
                    OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename -> JsonLDString(internalFilename),
                    OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasIIIFBaseUrl -> JsonLDString(settings.externalSipiIIIFGetUrl)
                ))
        }
    }

    override def unescape: ValueContentV2 = {
        copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        that match {
            case thatStillImage: StillImageFileValueContentV2 =>
                internalMimeType == thatStillImage.internalMimeType &&
                    internalFilename == thatStillImage.internalFilename &&
                    originalFilename == thatStillImage.originalFilename &&
                    originalMimeType == thatStillImage.originalMimeType &&
                    dimX == thatStillImage.dimX &&
                    dimY == thatStillImage.dimY &&
                    qualityLevel == thatStillImage.qualityLevel &&
                    isPreview == thatStillImage.isPreview

            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
        }
    }

    override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
        currentVersion match {
            case thatStillImage: StillImageFileValueContentV2 =>
                internalMimeType == thatStillImage.internalMimeType &&
                    internalFilename == thatStillImage.internalFilename &&
                    originalFilename == thatStillImage.originalFilename &&
                    originalMimeType == thatStillImage.originalMimeType &&
                    dimX == thatStillImage.dimX &&
                    dimY == thatStillImage.dimY &&
                    qualityLevel == thatStillImage.qualityLevel &&
                    isPreview == thatStillImage.isPreview &&
                    comment == thatStillImage.comment

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
                                  responderManager: ActorSelection,
                                  log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[StillImageFileValueContentV2] = {
        // TODO
        throw NotImplementedException(s"Reading of ${getClass.getName} from JSON-LD input not implemented")
    }
}

/**
  * Represents a text file value. Please note that the file itself is managed by Sipi.
  *
  * @param internalMimeType the mime type of the file corresponding to this text file value.
  * @param internalFilename the name of the file corresponding to this text file value.
  * @param originalFilename the original mime type of the text file before importing it.
  * @param originalMimeType the original name of the text file before importing it.
  * @param comment          a comment on this `TextFileValueContentV2`, if any.
  */
case class TextFileValueContentV2(ontologySchema: OntologySchema,
                                  internalMimeType: String,
                                  internalFilename: String,
                                  originalFilename: String,
                                  originalMimeType: Option[String],
                                  comment: Option[String] = None) extends FileValueContentV2 with ValueContentV2 {
    override def valueType: SmartIri = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        OntologyConstants.KnoraBase.TextFileValue.toSmartIri.toOntologySchema(ontologySchema)
    }

    override def valueHasString: String = internalFilename

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = copy(ontologySchema = targetSchema)

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        val imagePath: String = s"${settings.externalSipiFileServerGetUrl}/$internalFilename"

        targetSchema match {
            case ApiV2Simple => toJsonLDValueinSimpleSchema(imagePath)

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(
                    OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename -> JsonLDString(internalFilename),
                    OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl -> JsonLDString(imagePath)
                ))
        }
    }

    override def unescape: ValueContentV2 = {
        copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        that match {
            case thatTextFile: TextFileValueContentV2 =>
                internalMimeType == thatTextFile.internalMimeType &&
                    internalFilename == thatTextFile.internalFilename &&
                    originalFilename == thatTextFile.originalFilename &&
                    originalMimeType == thatTextFile.originalMimeType

            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
        }
    }

    override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
        currentVersion match {
            case thatTextFile: TextFileValueContentV2 =>
                internalMimeType == thatTextFile.internalMimeType &&
                    internalFilename == thatTextFile.internalFilename &&
                    originalFilename == thatTextFile.originalFilename &&
                    originalMimeType == thatTextFile.originalMimeType &&
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
                                  responderManager: ActorSelection,
                                  log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[TextFileValueContentV2] = {
        // TODO
        throw NotImplementedException(s"Reading of ${getClass.getName} from JSON-LD input not implemented")
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
                              comment: Option[String] = None) extends ValueContentV2 {
    override def valueType: SmartIri = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        OntologyConstants.KnoraBase.LinkValue.toSmartIri.toOntologySchema(ontologySchema)
    }

    override def valueHasString: String = referredResourceIri

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = {
        val convertedNestedResource = nestedResource.map {
            nested =>
                val targetApiSchema: ApiV2Schema = targetSchema match {
                    case apiSchema: ApiV2Schema => apiSchema
                    case _ => throw AssertionException(s"Can't convert a nested resource to $targetSchema")
                }

                nested.toOntologySchema(targetApiSchema)
        }

        copy(
            ontologySchema = targetSchema,
            nestedResource = convertedNestedResource
        )
    }

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple => JsonLDUtil.iriToJsonLDObject(referredResourceIri)

            case ApiV2WithValueObjects =>
                // check if the referred resource has to be included in the JSON response
                val objectMap: Map[IRI, JsonLDValue] = nestedResource match {
                    case Some(targetResource: ReadResourceV2) =>
                        // include the nested resource in the response
                        val referredResourceAsJsonLDValue: JsonLDObject = targetResource.toJsonLD(
                            targetSchema = targetSchema,
                            settings = settings
                        )

                        // check whether the nested resource is the target or the source of the link
                        if (!isIncomingLink) {
                            Map(OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasTarget -> referredResourceAsJsonLDValue)
                        } else {
                            Map(OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasSource -> referredResourceAsJsonLDValue)
                        }
                    case None =>
                        // check whether it is an outgoing or incoming link
                        if (!isIncomingLink) {
                            Map(OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasTargetIri -> JsonLDUtil.iriToJsonLDObject(referredResourceIri))
                        } else {
                            Map(OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasSourceIri -> JsonLDUtil.iriToJsonLDObject(referredResourceIri))
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
                                  responderManager: ActorSelection,
                                  log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[LinkValueContentV2] = {
        Future(fromJsonLDObjectSync(jsonLDObject))
    }

    private def fromJsonLDObjectSync(jsonLDObject: JsonLDObject): LinkValueContentV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val targetIri: SmartIri = jsonLDObject.requireIriInObject(OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasTargetIri, stringFormatter.toSmartIriWithErr)

        if (!targetIri.isKnoraDataIri) {
            throw BadRequestException(s"Link target IRI <$targetIri> is not a Knora data IRI")
        }

        LinkValueContentV2(
            ontologySchema = ApiV2WithValueObjects,
            referredResourceIri = targetIri.toString,
            comment = getComment(jsonLDObject)
        )
    }
}
