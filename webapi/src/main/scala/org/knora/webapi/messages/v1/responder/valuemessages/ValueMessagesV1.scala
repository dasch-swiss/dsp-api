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

package org.knora.webapi.messages.v1.responder.valuemessages

import java.io.File
import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.resourcemessages.LocationV1
import org.knora.webapi.messages.v1.responder.sipimessages.SipiResponderConversionRequestV1
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.twirl.{StandoffTagAttributeV2, StandoffTagInternalReferenceAttributeV2, StandoffTagV2}
import org.knora.webapi.util.standoff.StandoffTagUtilV2
import org.knora.webapi.util.{DateUtilV1, KnoraIdUtil, StringFormatter}
import org.knora.webapi._
import spray.json._


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * Represents an API request payload that asks the Knora API server to create a new value of a resource property
  * (as opposed to a new version of an existing value).
  *
  * @param res_id         the IRI of the resource in which the value is to be added.
  * @param prop           the property that is to receive the value.
  * @param richtext_value a rich-text object to be used in the value.
  * @param int_value      an integer literal to be used in the value.
  * @param decimal_value  a decimal literal to be used in the value.
  * @param date_value     a date object to be used in the value.
  * @param color_value    a colour literal to be used in the value.
  * @param geom_value     a geometry literal to be used in the value.
  * @param comment        a comment to add to the value.
  */
case class CreateValueApiRequestV1(res_id: IRI,
                                   prop: IRI,
                                   richtext_value: Option[CreateRichtextV1] = None,
                                   link_value: Option[IRI] = None,
                                   int_value: Option[Int] = None,
                                   decimal_value: Option[BigDecimal] = None,
                                   boolean_value: Option[Boolean] = None,
                                   uri_value: Option[String] = None,
                                   date_value: Option[String] = None,
                                   color_value: Option[String] = None,
                                   geom_value: Option[String] = None,
                                   hlist_value: Option[IRI] = None,
                                   interval_value: Option[Seq[BigDecimal]] = None,
                                   geoname_value: Option[String] = None,
                                   comment: Option[String] = None) {

    // Make sure only one value is given.
    if (List(
        richtext_value,
        link_value,
        int_value,
        decimal_value,
        boolean_value,
        uri_value,
        date_value,
        color_value,
        geom_value,
        hlist_value,
        interval_value,
        geoname_value).flatten.size > 1) {
        throw BadRequestException(s"Different value types were submitted for property $prop")
    }

    /**
      * Returns the type of the given value.
      *
      * @return a value type IRI.
      */
    def getValueClassIri: IRI = {
        if (richtext_value.nonEmpty) OntologyConstants.KnoraBase.TextValue
        else if (link_value.nonEmpty) OntologyConstants.KnoraBase.LinkValue
        else if (int_value.nonEmpty) OntologyConstants.KnoraBase.IntValue
        else if (decimal_value.nonEmpty) OntologyConstants.KnoraBase.DecimalValue
        else if (boolean_value.nonEmpty) OntologyConstants.KnoraBase.BooleanValue
        else if (uri_value.nonEmpty) OntologyConstants.KnoraBase.UriValue
        else if (date_value.nonEmpty) OntologyConstants.KnoraBase.DateValue
        else if (color_value.nonEmpty) OntologyConstants.KnoraBase.ColorValue
        else if (geom_value.nonEmpty) OntologyConstants.KnoraBase.GeomValue
        else if (hlist_value.nonEmpty) OntologyConstants.KnoraBase.ListValue
        else if (interval_value.nonEmpty) OntologyConstants.KnoraBase.IntervalValue
        else if (geoname_value.nonEmpty) OntologyConstants.KnoraBase.GeonameValue
        else throw BadRequestException("No value specified")
    }

}

/**
  * Represents a richtext object consisting of text, text attributes and resource references.
  *
  * @param utf8str    a mere string in case of a text without any markup.
  * @param xml        xml in case of a text with markup.
  * @param mapping_id IRI of the mapping used to transform XML to standoff.
  */
case class CreateRichtextV1(utf8str: Option[String] = None,
                            language: Option[String] = None,
                            xml: Option[String] = None,
                            mapping_id: Option[IRI] = None) {

    def toJsValue = ApiValueV1JsonProtocol.createRichtextV1Format.write(this)
}

/**
  * Represents a file value to be added to a Knora resource.
  *
  * @param originalFilename the original name of the file.
  * @param originalMimeType the original mime type of the file.
  * @param filename         the name of the file to be attached to a Knora-resource (file is temporarily stored by SIPI).
  */
case class CreateFileV1(originalFilename: String,
                        originalMimeType: String,
                        filename: String) {

    def toJsValue = ApiValueV1JsonProtocol.createFileV1Format.write(this)

}

/**
  * Represents a file on disk to be added to a Knora resource in the context of a bulk import.
  *
  * @param file     the file.
  * @param mimeType the file's MIME type.
  */
case class ReadFileV1(file: File, mimeType: String)

/**
  * Represents a quality level of a file value to added to a Knora resource.
  *
  * @param path     the path to the file.
  * @param mimeType the mime type of the file.
  * @param dimX     the x dimension of the file, if given (e.g. an image).
  * @param dimY     the y dimension of the file, if given (e.g. an image).
  */
case class CreateFileQualityLevelV1(path: String,
                                    mimeType: String,
                                    dimX: Option[Int] = None,
                                    dimY: Option[Int] = None) {

    def toJsValue = ApiValueV1JsonProtocol.createFileQualityLevelFormat.write(this)
}

/**
  * Represents an API request payload that asks the Knora API server to change a value of a resource property (i.e. to
  * update its version history).
  *
  * @param richtext_value a rich-text object to be used in the value.
  * @param int_value      an integer literal to be used in the value.
  * @param decimal_value  a decimal literal to be used in the value.
  * @param date_value     a date object to be used in the value.
  * @param color_value    a colour literal to be used in the value.
  * @param geom_value     a geometry literal to be used in the value.
  * @param comment        a comment to add to the value.
  */
case class ChangeValueApiRequestV1(richtext_value: Option[CreateRichtextV1] = None,
                                   link_value: Option[IRI] = None,
                                   int_value: Option[Int] = None,
                                   decimal_value: Option[BigDecimal] = None,
                                   boolean_value: Option[Boolean] = None,
                                   uri_value: Option[String] = None,
                                   date_value: Option[String] = None,
                                   color_value: Option[String] = None,
                                   geom_value: Option[String] = None,
                                   hlist_value: Option[IRI] = None,
                                   interval_value: Option[Seq[BigDecimal]] = None,
                                   geoname_value: Option[String] = None,
                                   comment: Option[String] = None) {

    /**
      * Returns the type of the given value.
      *
      * TODO: make sure that only one value is given.
      *
      * @return a value type IRI.
      */
    def getValueClassIri: IRI = {
        if (richtext_value.nonEmpty) OntologyConstants.KnoraBase.TextValue
        else if (link_value.nonEmpty) OntologyConstants.KnoraBase.LinkValue
        else if (int_value.nonEmpty) OntologyConstants.KnoraBase.IntValue
        else if (decimal_value.nonEmpty) OntologyConstants.KnoraBase.DecimalValue
        else if (boolean_value.nonEmpty) OntologyConstants.KnoraBase.BooleanValue
        else if (uri_value.nonEmpty) OntologyConstants.KnoraBase.UriValue
        else if (date_value.nonEmpty) OntologyConstants.KnoraBase.DateValue
        else if (color_value.nonEmpty) OntologyConstants.KnoraBase.ColorValue
        else if (geom_value.nonEmpty) OntologyConstants.KnoraBase.GeomValue
        else if (hlist_value.nonEmpty) OntologyConstants.KnoraBase.ListValue
        else if (interval_value.nonEmpty) OntologyConstants.KnoraBase.IntervalValue
        else if (geoname_value.nonEmpty) OntologyConstants.KnoraBase.GeonameValue
        else throw BadRequestException("No value specified")
    }


}

/**
  * Represents an API request payload that asks the Knora API server to change the file attached to a resource
  * (i. e. to create a new version of its file values).
  *
  * @param file the new file to be attached to the resource (GUI-case).
  */
case class ChangeFileValueApiRequestV1(file: CreateFileV1) {

    def toJsValue = ApiValueV1JsonProtocol.changeFileValueApiRequestV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing a message that can be sent to [[org.knora.webapi.responders.v1.ValuesResponderV1]].
  */
sealed trait ValuesResponderRequestV1 extends KnoraRequestV1

/**
  * Represents a request for a (current) value. A successful response will be a [[ValueGetResponseV1]].
  *
  * @param valueIri    the IRI of the value requested.
  * @param userProfile the profile of the user making the request.
  */
case class ValueGetRequestV1(valueIri: IRI, userProfile: UserADM) extends ValuesResponderRequestV1

/**
  * Represents a request for the details of a reification node describing a direct link between two resources.
  * A successful response will be a [[ValueGetResponseV1]] containing a [[LinkValueV1]].
  *
  * @param subjectIri   the IRI of the resource that is the source of the link.
  * @param predicateIri the IRI of the property that links the two resources.
  * @param objectIri    the IRI of the resource that is the target of the link.
  * @param userProfile  the profile of the user making the request.
  */
case class LinkValueGetRequestV1(subjectIri: IRI, predicateIri: IRI, objectIri: IRI, userProfile: UserADM) extends ValuesResponderRequestV1

/**
  * Provides details of a Knora value. A successful response will be a [[ValueGetResponseV1]].
  *
  * @param value             the single requested value.
  * @param valuetype         the IRI of the value's type.
  * @param valuecreator      the username of the user who created the value.
  * @param valuecreatorname  the name of the user who created the value.
  * @param valuecreationdate the date when the value was created.
  * @param comment           the comment on the value, if any.
  * @param rights            the user's permission on the value.
  */
case class ValueGetResponseV1(valuetype: IRI,
                              value: ApiValueV1,
                              valuecreator: String,
                              valuecreatorname: String,
                              valuecreationdate: String,
                              comment: Option[String] = None,
                              rights: Int) extends KnoraResponseV1 {
    def toJsValue = ApiValueV1JsonProtocol.valueGetResponseV1Format.write(this)
}

/**
  * Represents a request for the version history of a value. A successful response will be a [[ValueVersionHistoryGetResponseV1]].
  *
  * @param resourceIri     the IRI of the resource that the value belongs to.
  * @param propertyIri     the IRI of the property that points to the value.
  * @param currentValueIri the IRI of the current version of the value.
  * @param userProfile     the profile of the user making the request.
  */
case class ValueVersionHistoryGetRequestV1(resourceIri: IRI,
                                           propertyIri: IRI,
                                           currentValueIri: IRI,
                                           userProfile: UserADM) extends ValuesResponderRequestV1

/**
  * Provides the version history of a value.
  *
  * @param valueVersions a list of the versions of the value, from newest to oldest.
  */
case class ValueVersionHistoryGetResponseV1(valueVersions: Seq[ValueVersionV1]) extends KnoraResponseV1 {
    def toJsValue = ApiValueV1JsonProtocol.valueVersionHistoryGetResponseV1Format.write(this)
}

/**
  * Represents a request to add a new value of a resource property (as opposed to a new version of an existing value). A
  * successful response will be an [[CreateValueResponseV1]].
  *
  * @param resourceIndex the index of the resource
  * @param resourceIri   the IRI of the resource to which the value should be added.
  * @param propertyIri   the IRI of the property that should receive the value.
  * @param value         the value to be added.
  * @param comment       an optional comment on the value.
  * @param userProfile   the profile of the user making the request.
  * @param apiRequestID  the ID of this API request.
  */
case class CreateValueRequestV1(resourceIndex: Int = 0,
                                resourceIri: IRI,
                                propertyIri: IRI,
                                value: UpdateValueV1,
                                comment: Option[String] = None,
                                userProfile: UserADM,
                                apiRequestID: UUID) extends ValuesResponderRequestV1

/**
  * Represents a response to a [[CreateValueRequestV1]].
  *
  * @param value   the value that was added.
  * @param comment an optional comment on the value.
  * @param id      the IRI of the value that was added.
  * @param rights  a code representing the requesting user's permissions on the value.
  */
case class CreateValueResponseV1(value: ApiValueV1,
                                 comment: Option[String] = None,
                                 id: IRI,
                                 rights: Int) extends KnoraResponseV1 {
    def toJsValue = ApiValueV1JsonProtocol.createValueResponseV1Format.write(this)
}

/**
  * Represents a value that should have been created using the SPARQL returned in a
  * [[GenerateSparqlToCreateMultipleValuesResponseV1]]. To verify that the value was in fact created, send a
  * [[VerifyMultipleValueCreationRequestV1]].
  *
  * @param newValueIri the IRI of the value that should have been created.
  * @param value       an [[UpdateValueV1]] representing the value that should have been created.
  */
case class UnverifiedValueV1(newValueIri: IRI, value: UpdateValueV1)

/**
  * Requests verification that new values were created.
  *
  * @param resourceIri      the IRI of the resource in which the values should have been created.
  * @param unverifiedValues a [[Map]] of property IRIs to [[UnverifiedValueV1]] objects
  *                         describing the values that should have been created for each property.
  * @param userProfile      the profile of the user making the request.
  */
case class VerifyMultipleValueCreationRequestV1(resourceIri: IRI,
                                                unverifiedValues: Map[IRI, Seq[UnverifiedValueV1]],
                                                userProfile: UserADM) extends ValuesResponderRequestV1

/**
  * In response to a [[VerifyMultipleValueCreationRequestV1]], indicates that all requested values were
  * created successfully.
  *
  * @param verifiedValues information about the values that were created.
  */
case class VerifyMultipleValueCreationResponseV1(verifiedValues: Map[IRI, Seq[CreateValueResponseV1]])

/**
  * A holder for an [[UpdateValueV1]] along with an optional comment.
  *
  * @param updateValueV1 the [[UpdateValueV1]].
  * @param comment       an optional comment on the value.
  */
case class CreateValueV1WithComment(updateValueV1: UpdateValueV1, comment: Option[String] = None)

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
  * In the collection of values to be created, standoff links in text values are allowed to point either to the IRIs
  * of resources that already exist in the triplestore, or to the client's IDs for resources that are being created
  * as part of a bulk import. If client resource IDs are used in standoff links, `clientResourceIDsToResourceIris`
  * must map those IDs to the real  IRIs of the resources that are to be created.
  *
  * @param projectIri                       the project the values belong to.
  * @param resourceIri                      the resource the values will be attached to.
  * @param resourceClassIri                 the IRI of the resource's OWL class.
  * @param defaultPropertyAccessPermissions the default object access permissions of each property attached to the resource class.
  * @param values                           the values to be added, with optional comments.
  * @param clientResourceIDsToResourceIris  a map of client resource IDs (which may appear in standoff link tags
  *                                         in values) to the IRIs that will be used for those resources.
  * @param currentTime                      an xsd:dateTimeStamp that will be attached to the values.
  * @param userProfile                      the user that is creating the values.
  */
case class GenerateSparqlToCreateMultipleValuesRequestV1(projectIri: IRI,
                                                         resourceIri: IRI,
                                                         resourceClassIri: IRI,
                                                         defaultPropertyAccessPermissions: Map[IRI, String],
                                                         values: Map[IRI, Seq[CreateValueV1WithComment]],
                                                         clientResourceIDsToResourceIris: Map[String, IRI],
                                                         currentTime: String,
                                                         userProfile: UserADM,
                                                         apiRequestID: UUID) extends ValuesResponderRequestV1


/**
  * Represents a response to a [[GenerateSparqlToCreateMultipleValuesRequestV1]], providing a string that can be included
  * in the `INSERT DATA` clause of a SPARQL update operation to create the requested values.
  *
  * After executing the SPARQL update, the receiver can check whether the values were actually created by sending a
  * [[VerifyMultipleValueCreationRequestV1]].
  *
  * @param insertSparql     a string containing statements that must be inserted into the INSERT clause of the SPARQL
  *                         update that will create the values.
  * @param unverifiedValues a map of property IRIs to [[UnverifiedValueV1]] objects describing
  *                         the values that should have been created.
  */
case class GenerateSparqlToCreateMultipleValuesResponseV1(insertSparql: String,
                                                          unverifiedValues: Map[IRI, Seq[UnverifiedValueV1]])


/**
  * Represents a request to change the value of a property (by updating its version history). A successful response will
  * be a [[ChangeValueResponseV1]].
  *
  * @param valueIri     the IRI of the current value.
  * @param value        the new value, or [[None]] if only the value's comment is being changed.
  * @param comment      an optional comment on the value.
  * @param userProfile  the profile of the user making the request.
  * @param apiRequestID the ID of this API request.
  */
case class ChangeValueRequestV1(valueIri: IRI,
                                value: UpdateValueV1,
                                comment: Option[String] = None,
                                userProfile: UserADM,
                                apiRequestID: UUID) extends ValuesResponderRequestV1

/**
  * Represents a request to change the comment on a value. A successful response will be a [[ChangeValueResponseV1]].
  *
  * @param valueIri     the IRI of the current value.
  * @param comment      the comment to be added to the new version of the value.
  * @param userProfile  the profile of the user making the request.
  * @param apiRequestID the ID of this API request.
  */
case class ChangeCommentRequestV1(valueIri: IRI,
                                  comment: Option[String],
                                  userProfile: UserADM,
                                  apiRequestID: UUID) extends ValuesResponderRequestV1

/**
  * Represents a response to an [[ChangeValueRequestV1]].
  *
  * @param value   the value that was added.
  * @param comment an optional comment on the value.
  * @param id      the IRI of the value that was added.
  */
case class ChangeValueResponseV1(value: ApiValueV1,
                                 comment: Option[String] = None,
                                 id: IRI,
                                 rights: Int) extends KnoraResponseV1 {
    def toJsValue = ApiValueV1JsonProtocol.changeValueResponseV1Format.write(this)
}

/**
  * Represents a request to mark a value as deleted.
  *
  * @param valueIri      the IRI of the value to be marked as deleted.
  * @param deleteComment an optional comment explaining why the value is being deleted.
  * @param userProfile   the profile of the user making the request.
  * @param apiRequestID  the ID of this API request.
  */
case class DeleteValueRequestV1(valueIri: IRI,
                                deleteComment: Option[String] = None,
                                userProfile: UserADM,
                                apiRequestID: UUID) extends ValuesResponderRequestV1

/**
  * Represents a response to a [[DeleteValueRequestV1]].
  *
  * @param id the IRI of the value that was marked as deleted. If this was a `LinkValue`, a new version of it
  *           will have been created, and `id` will the IRI of that new version. Otherwise, `id` will be the IRI
  *           submitted in the [[DeleteValueRequestV1]]. For an explanation of this behaviour, see the chapter
  *           ''Triplestore Updates'' in the Knora API server design documentation.
  */
case class DeleteValueResponseV1(id: IRI) extends KnoraResponseV1 {
    def toJsValue = ApiValueV1JsonProtocol.deleteValueResponseV1Format.write(this)
}

/**
  * Represents a request to change (update) the file value(s) of a given resource.
  * In case of an image, two file valueshave to be changed: thumbnail and full quality.
  *
  * @param resourceIri the resource whose files value(s) should be changed.
  * @param file        the file to be created and added.
  */
case class ChangeFileValueRequestV1(resourceIri: IRI, file: SipiResponderConversionRequestV1, apiRequestID: UUID, userProfile: UserADM) extends ValuesResponderRequestV1

/**
  * Represents a response to a [[ChangeFileValueRequestV1]].
  * Possibly, two file values have been changed (thumb and full quality).
  *
  * @param locations the updated file value(s).
  */
case class ChangeFileValueResponseV1(locations: Vector[LocationV1]) extends KnoraResponseV1 {
    def toJsValue = ApiValueV1JsonProtocol.changeFileValueresponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * The value of a Knora property, either as represented internally by Knora or as returned to clients in
  * Knora API v1.
  */
sealed trait ValueV1 {
    /**
      * The IRI of the Knora value type corresponding to the type of this `ValueV1`.
      */
    def valueTypeIri: IRI
}

/**
  * The value of a Knora property as represented to clients in Knora API v1. An [[ApiValueV1]] can be serialised as
  * JSON for use in the API.
  */
sealed trait ApiValueV1 extends ValueV1 with Jsonable

/**
  * The value of a Knora property as represented in an update request.
  */
sealed trait UpdateValueV1 extends ValueV1 {
    /**
      * Returns `true` if creating this [[UpdateValueV1]] as a new value would duplicate the specified other value.
      * This means that if resource `R` has property `P` with value `V1`, and `V1` is a duplicate of `V2`, the API server
      * should not add another instance of property `P` with value `V2`. It does not necessarily mean that `V1 == V2`.
      *
      * @param other another [[ValueV1]].
      * @return `true` if `other` is a duplicate of `this`.
      */
    def isDuplicateOfOtherValue(other: ApiValueV1): Boolean

    /**
      * Returns `true` if this [[UpdateValueV1]] would be redundant as a new version of an existing value. This means
      * that if resource `R` has property `P` with value `V1`, and `V2` is redundant given `V1`, we should not `V2`
      * as a new version of `V1`. It does not necessarily mean that `V1 == V2`.
      *
      * @param currentVersion the current version of the value.
      * @return `true` if this [[UpdateValueV1]] is redundant given `currentVersion`.
      */
    def isRedundant(currentVersion: ApiValueV1): Boolean
}

/**
  * Represents a Knora API v1 property value object and some associated information.
  *
  * @param valueObjectIri the IRI of the value object.
  * @param valueV1        a [[ApiValueV1]] containing the object's literal value.
  */
case class ValueObjectV1(valueObjectIri: IRI,
                         valueV1: ApiValueV1,
                         valuePermission: Option[Int] = None,
                         comment: Option[String] = None,
                         order: Int = 0)

/**
  * An enumeration of the types of calendars Knora supports. Note: do not use the `withName` method to get instances
  * of the values of this enumeration; use `lookup` instead, because it reports errors better.
  */
object KnoraCalendarV1 extends Enumeration {
    val JULIAN: Value = Value(0, "JULIAN")
    val GREGORIAN: Value = Value(1, "GREGORIAN")
    val JEWISH: Value = Value(2, "JEWISH")
    val REVOLUTIONARY: Value = Value(3, "REVOLUTIONARY")

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

    /**
      * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
      * [[InconsistentTriplestoreDataException]].
      *
      * @param name the name of the value.
      * @return the requested value.
      */
    def lookup(name: String): Value = {
        valueMap.get(name) match {
            case Some(value) => value
            case None => throw InconsistentTriplestoreDataException(s"Calendar type not supported: $name")
        }
    }
}

/**
  * An enumeration of the types of calendar precisions Knora supports. Note: do not use the `withName` method to get instances
  * of the values of this enumeration; use `lookup` instead, because it reports errors better.
  */
object KnoraPrecisionV1 extends Enumeration {
    val DAY = Value(0, "DAY")
    val MONTH = Value(1, "MONTH")
    val YEAR = Value(2, "YEAR")

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

    /**
      * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
      * [[InconsistentTriplestoreDataException]].
      *
      * @param name the name of the value.
      * @return the requested value.
      */
    def lookup(name: String): Value = {
        valueMap.get(name) match {
            case Some(value) => value
            case None => throw InconsistentTriplestoreDataException(s"Calendar precision not supported: $name")
        }
    }
}

/**
  *
  * Represents a [[StandoffTagV2]] for a standoff tag of a certain type (standoff tag class) that is about to be created in the triplestore.
  *
  * @param standoffNode           the standoff node to be created.
  * @param standoffTagInstanceIri the standoff node's IRI.
  * @param startParentIri         the IRI of the parent of the start tag.
  * @param endParentIri           the IRI of the parent of the end tag, if any.
  */
case class CreateStandoffTagV1InTriplestore(standoffNode: StandoffTagV2, standoffTagInstanceIri: IRI, startParentIri: Option[IRI] = None, endParentIri: Option[IRI] = None)

sealed trait TextValueV1 {

    def utf8str: String

    def language: Option[String]

}

/**
  * Represents a text value with standoff markup.
  *
  * @param utf8str            text in mere utf8 representation (including newlines and carriage returns).
  * @param language           the language of the text, if known.
  * @param standoff           attributes of the text in standoff format. For each attribute, several ranges may be given (a list of [[StandoffTagV2]]).
  * @param resource_reference referred Knora resources.
  * @param mapping            the mapping used to create standoff from another format.
  */
case class TextValueWithStandoffV1(utf8str: String,
                                   language: Option[String] = None,
                                   standoff: Seq[StandoffTagV2],
                                   resource_reference: Set[IRI] = Set.empty[IRI],
                                   mappingIri: IRI,
                                   mapping: MappingXMLtoStandoff) extends TextValueV1 with UpdateValueV1 with ApiValueV1 {

    private val knoraIdUtil = new KnoraIdUtil
    private val stringFormatter = StringFormatter.getGeneralInstance

    def valueTypeIri = OntologyConstants.KnoraBase.TextValue

    def toJsValue = {
        // TODO: depending on the given mapping, decide how serialize the text with standoff markup

        val xml = StandoffTagUtilV2.convertStandoffTagV2ToXML(utf8str, standoff, mapping)

        language match {
            case Some(lang) =>
                JsObject(
                    "xml" -> JsString(xml),
                    "mapping_id" -> JsString(mappingIri),
                    "language" -> JsString(lang)
                )


            case None =>
                JsObject(
                    "xml" -> JsString(xml),
                    "mapping_id" -> JsString(mappingIri)
                )
        }

    }

    /**
      * A convenience method that creates an IRI for each [[StandoffTagV2]] and resolves internal references to standoff node Iris.
      *
      * @return a list of [[CreateStandoffTagV1InTriplestore]] each representing a [[StandoffTagV2]] object
      *         along with is standoff tag class and IRI that is going to identify it in the triplestore.
      */
    def prepareForSparqlInsert(valueIri: IRI): Seq[CreateStandoffTagV1InTriplestore] = {

        // create an IRI for each standoff tag
        // internal references to XML ids are not resolved yet
        val standoffTagsWithOriginalXMLIDs: Seq[CreateStandoffTagV1InTriplestore] = standoff.map {
            case (standoffNode: StandoffTagV2) =>
                CreateStandoffTagV1InTriplestore(
                    standoffNode = standoffNode,
                    standoffTagInstanceIri = knoraIdUtil.makeRandomStandoffTagIri(valueIri) // generate IRI for new standoff node
                )
        }

        // collect all the standoff tags that contain XML ids and
        // map the XML ids to standoff node Iris
        val iDsToStandoffNodeIris: Map[IRI, IRI] = standoffTagsWithOriginalXMLIDs.filter {
            (standoffTag: CreateStandoffTagV1InTriplestore) =>
                // filter those tags out that have an XML id
                standoffTag.standoffNode.originalXMLID.isDefined
        }.map {
            (standoffTagWithID: CreateStandoffTagV1InTriplestore) =>
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
        val standoffTagsWithNodeReferences: Seq[CreateStandoffTagV1InTriplestore] = standoffTagsWithOriginalXMLIDs.map {
            (standoffTag: CreateStandoffTagV1InTriplestore) =>

                // resolve original XML ids to standoff node Iris for `StandoffTagInternalReferenceAttributeV1`
                val attributesWithStandoffNodeIriReferences: Seq[StandoffTagAttributeV2] = standoffTag.standoffNode.attributes.map {
                    (attributeWithOriginalXMLID: StandoffTagAttributeV2) =>
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
    }

    /**
      * Returns `true` if the specified object is a [[TextValueV1]] and has the same `utf8str` as this one. We
      * assume that it doesn't make sense for a resource to have two different text values associated with the
      * same property, containing the same text but different markup.
      *
      * @param other another [[ValueV1]].
      * @return `true` if `other` is a duplicate of `this`.
      */
    override def isDuplicateOfOtherValue(other: ApiValueV1): Boolean = {

        other match {
            case otherText: TextValueV1 =>

                // unescape utf8str since it contains escaped sequences while the string returned by the triplestore does not
                stringFormatter.fromSparqlEncodedString(utf8str) == otherText.utf8str
            case otherValue => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${otherValue.valueTypeIri}")
        }
    }

    override def toString = utf8str

    /**
      * It's OK to add a new version of a text value as long as something has been changed in it, even if it's only the markup.
      *
      * @param currentVersion the current version of the value.
      * @return `true` if this [[UpdateValueV1]] is redundant given `currentVersion`.
      */
    override def isRedundant(currentVersion: ApiValueV1): Boolean = {

        currentVersion match {
            case _: TextValueSimpleV1 => false

            case textValueWithStandoffV1: TextValueWithStandoffV1 =>

                // compare utf8str (unescape utf8str since it contains escaped sequences while the string returned by the triplestore does not)
                val utf8strIdentical: Boolean = stringFormatter.fromSparqlEncodedString(utf8str) == textValueWithStandoffV1.utf8str

                // Compare standoff tags.
                val thisComparableStandoff = StandoffTagUtilV2.makeComparableStandoffCollection(standoff)
                val thatComparableStandoff = StandoffTagUtilV2.makeComparableStandoffCollection(textValueWithStandoffV1.standoff)
                val standoffIdentical: Boolean = thisComparableStandoff == thatComparableStandoff

                utf8strIdentical && standoffIdentical && textValueWithStandoffV1.mappingIri == this.mappingIri

            case other => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${other.valueTypeIri}")
        }
    }

}

/**
  * Represents a text value without standoff markup.
  *
  * @param utf8str  the text.
  * @param language the language of the text, if known.
  */
case class TextValueSimpleV1(utf8str: String, language: Option[String] = None) extends TextValueV1 with UpdateValueV1 with ApiValueV1 {

    def valueTypeIri = OntologyConstants.KnoraBase.TextValue

    def toJsValue = {
        language match {
            case Some(lang) =>
                JsObject(
                    "utf8str" -> JsString(utf8str),
                    "language" -> JsString(lang)
                )

            case None =>
                JsObject("utf8str" -> JsString(utf8str))
        }
    }

    /**
      * Returns `true` if the specified object is a [[TextValueV1]] and has the same `utf8str` as this one. We
      * assume that it doesn't make sense for a resource to have two different text values associated with the
      * same property, containing the same text but different markup.
      *
      * @param other another [[ValueV1]].
      * @return `true` if `other` is a duplicate of `this`.
      */
    override def isDuplicateOfOtherValue(other: ApiValueV1): Boolean = {
        other match {
            case otherText: TextValueV1 => otherText.utf8str == utf8str
            case otherValue => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${otherValue.valueTypeIri}")
        }
    }

    override def toString = utf8str


    /**
      * It's OK to add a new version of a text value as long as something has been changed in it, even if it's only the markup.
      *
      * @param currentVersion the current version of the value.
      * @return `true` if this [[UpdateValueV1]] is redundant given `currentVersion`.
      */
    override def isRedundant(currentVersion: ApiValueV1): Boolean = {
        currentVersion match {
            case textValueSimpleV1: TextValueSimpleV1 => textValueSimpleV1 == this
            case textValueWithStandoffV1: TextValueWithStandoffV1 => false
            case other => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${other.valueTypeIri}")
        }
    }

}

/**
  * Represents a direct link from one resource to another.
  *
  * @param targetResourceIri       the IRI of the resource that the link points to.
  * @param valueLabel              the `rdfs:label` of the resource referred to.
  * @param valueResourceClass      the IRI of the OWL class of the resource that the link points to.
  * @param valueResourceClassLabel the label of the OWL class of the resource that the link points to.
  * @param valueResourceClassIcon  the icon of the OWL class of the resource that the link points to.
  */
case class LinkV1(targetResourceIri: IRI,
                  valueLabel: Option[String] = None,
                  valueResourceClass: Option[IRI] = None,
                  valueResourceClassLabel: Option[String] = None,
                  valueResourceClassIcon: Option[String] = None) extends ApiValueV1 {

    def valueTypeIri = OntologyConstants.KnoraBase.LinkValue

    override def toString = targetResourceIri

    def toJsValue = JsString(targetResourceIri)
}

/**
  * Represents a `knora-base:LinkValue`, i.e. a reification of a link between two resources.
  *
  * @param subjectIri     the IRI of the resource that is the source of the link.
  * @param predicateIri   the IRI of the property that links the two resources.
  * @param objectIri      the IRI of the resource that is the target of the link.
  * @param referenceCount the reference count of the `LinkValue`. If the link property is `knora-base:hasStandoffLinkTo`,
  *                       the reference count can be any integer greater than or equal to 0. Otherwise, the reference
  *                       count can only be 0 or 1.
  */
case class LinkValueV1(subjectIri: IRI,
                       predicateIri: IRI,
                       objectIri: IRI,
                       referenceCount: Int) extends ApiValueV1 {
    def valueTypeIri = OntologyConstants.KnoraBase.LinkValue

    override def toJsValue = ApiValueV1JsonProtocol.linkValueV1Format.write(this)
}

/**
  * Represents a request to update a link.
  *
  * @param targetResourceIri the IRI of the resource that the link should point to.
  * @param targetExists      `true` if the link target already exists, `false` if it is going to be created in the
  *                          same transaction.
  */
case class LinkUpdateV1(targetResourceIri: IRI, targetExists: Boolean = true) extends UpdateValueV1 {
    def valueTypeIri = OntologyConstants.KnoraBase.LinkValue

    /**
      * It doesn't make sense to add a link to a resource when we already have a link to the same resource.
      *
      * @param other another [[ValueV1]].
      * @return `true` if `other` is a duplicate of `this`.
      */
    override def isDuplicateOfOtherValue(other: ApiValueV1): Boolean = {

        other match {
            case linkV1: LinkV1 => targetResourceIri == linkV1.targetResourceIri
            case linkValueV1: LinkValueV1 => targetResourceIri == linkValueV1.objectIri
            case otherValue => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${otherValue.valueTypeIri}")
        }
    }

    override def toString = targetResourceIri

    /**
      * A link isn't really changed if the new version points to the same resource as the old version.
      *
      * @param currentVersion the current version of the value.
      * @return `true` if this [[UpdateValueV1]] is redundant given `currentVersion`.
      */
    override def isRedundant(currentVersion: ApiValueV1): Boolean = isDuplicateOfOtherValue(currentVersion)
}

/**
  * Represents a request to create a link to a resource that hasn't been created yet, and is known only
  * by the ID that the client has provided for it. Instances of this class will be replaced by instances
  * of [[LinkUpdateV1]] during the preparation for the update.
  *
  * @param clientIDForTargetResource the client's ID for the target resource.
  */
case class LinkToClientIDUpdateV1(clientIDForTargetResource: String) extends UpdateValueV1 {
    def valueTypeIri = OntologyConstants.KnoraBase.LinkValue

    override def isDuplicateOfOtherValue(other: ApiValueV1): Boolean = false

    override def toString = clientIDForTargetResource

    override def isRedundant(currentVersion: ApiValueV1): Boolean = false
}

/**
  * Represents the IRI of a Knora hierarchical list.
  *
  * @param hierarchicalListIri the IRI of the hierarchical list.
  */
case class HierarchicalListValueV1(hierarchicalListIri: IRI) extends UpdateValueV1 with ApiValueV1 {

    def valueTypeIri = OntologyConstants.KnoraBase.ListValue

    def toJsValue = JsString(hierarchicalListIri)

    override def toString = {
        // TODO: implement this correctly

        // the string representation is the rdfs:label of the list node

        hierarchicalListIri
    }

    /**
      * Checks if a new list value would duplicate an existing list value.
      *
      * @param other another [[ValueV1]].
      * @return `true` if `other` is a duplicate of `this`.
      */
    override def isDuplicateOfOtherValue(other: ApiValueV1): Boolean = {
        other match {
            case listValueV1: HierarchicalListValueV1 => listValueV1 == this
            case otherValue => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${otherValue.valueTypeIri}")
        }
    }

    /**
      * Checks if a new version of a list value would be redundant given the current version of the value.
      *
      * @param currentVersion the current version of the value.
      * @return `true` if this [[UpdateValueV1]] is redundant given `currentVersion`.
      */
    override def isRedundant(currentVersion: ApiValueV1): Boolean = {
        currentVersion match {
            case listValueV1: HierarchicalListValueV1 => listValueV1 == this
            case other => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${other.valueTypeIri}")
        }
    }
}

/**
  * Represents an integer value.
  *
  * @param ival the integer value.
  */
case class IntegerValueV1(ival: Int) extends UpdateValueV1 with ApiValueV1 {

    def valueTypeIri = OntologyConstants.KnoraBase.IntValue

    def toJsValue = JsNumber(ival)

    override def toString = ival.toString

    /**
      * Checks if a new integer value would duplicate an existing integer value.
      *
      * @param other another [[ValueV1]].
      * @return `true` if `other` is a duplicate of `this`.
      */
    override def isDuplicateOfOtherValue(other: ApiValueV1): Boolean = {
        other match {
            case integerValueV1: IntegerValueV1 => integerValueV1 == this
            case otherValue => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${otherValue.valueTypeIri}")
        }
    }

    /**
      * Checks if a new version of an integer value would be redundant given the current version of the value.
      *
      * @param currentVersion the current version of the value.
      * @return `true` if this [[UpdateValueV1]] is redundant given `currentVersion`.
      */
    override def isRedundant(currentVersion: ApiValueV1): Boolean = {
        currentVersion match {
            case integerValueV1: IntegerValueV1 => integerValueV1 == this
            case other => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${other.valueTypeIri}")
        }
    }
}

/**
  * Represents a boolean value.
  *
  * @param bval the boolean value.
  */
case class BooleanValueV1(bval: Boolean) extends UpdateValueV1 with ApiValueV1 {

    def valueTypeIri = OntologyConstants.KnoraBase.BooleanValue

    def toJsValue = JsBoolean(bval)

    override def toString = bval.toString

    /**
      * Checks if a new boolean value would duplicate an existing boolean value. Always returns `true`, because it
      * does not make sense to have two instances of the same boolean property.
      *
      * @param other another [[ValueV1]].
      * @return `true` if `other` is a duplicate of `this`.
      */
    override def isDuplicateOfOtherValue(other: ApiValueV1): Boolean = true

    /**
      * Checks if a new version of an boolean value would be redundant given the current version of the value.
      *
      * @param currentVersion the current version of the value.
      * @return `true` if this [[UpdateValueV1]] is redundant given `currentVersion`.
      */
    override def isRedundant(currentVersion: ApiValueV1): Boolean = {
        currentVersion match {
            case booleanValueV1: BooleanValueV1 => booleanValueV1 == this
            case other => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${other.valueTypeIri}")
        }
    }
}

/**
  * Represents a URI value.
  *
  * @param uri the URI value.
  */
case class UriValueV1(uri: String) extends UpdateValueV1 with ApiValueV1 {

    def valueTypeIri = OntologyConstants.KnoraBase.UriValue

    def toJsValue = JsString(uri)

    override def toString = uri

    /**
      * Checks if a new URI value would duplicate an existing URI value.
      *
      * @param other another [[ValueV1]].
      * @return `true` if `other` is a duplicate of `this`.
      */
    override def isDuplicateOfOtherValue(other: ApiValueV1): Boolean = {
        other match {
            case uriValueV1: UriValueV1 => uriValueV1 == this
            case otherValue => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${otherValue.valueTypeIri}")
        }
    }

    /**
      * Checks if a new version of an integer value would be redundant given the current version of the value.
      *
      * @param currentVersion the current version of the value.
      * @return `true` if this [[UpdateValueV1]] is redundant given `currentVersion`.
      */
    override def isRedundant(currentVersion: ApiValueV1): Boolean = {
        currentVersion match {
            case uriValueV1: UriValueV1 => uriValueV1 == this
            case other => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${other.valueTypeIri}")
        }
    }
}

/**
  * Represents an arbitrary-precision decimal value.
  *
  * @param dval the decimal value.
  */
case class DecimalValueV1(dval: BigDecimal) extends UpdateValueV1 with ApiValueV1 {
    def valueTypeIri = OntologyConstants.KnoraBase.DecimalValue

    def toJsValue = JsNumber(dval)

    override def toString = dval.toString

    /**
      * Checks if a new decimal value would duplicate an existing decimal value.
      *
      * @param other another [[ValueV1]].
      * @return `true` if `other` is a duplicate of `this`.
      */
    override def isDuplicateOfOtherValue(other: ApiValueV1): Boolean = {
        other match {
            case decimalValueV1: DecimalValueV1 => decimalValueV1 == this
            case otherValue => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${otherValue.valueTypeIri}")
        }
    }

    /**
      * Checks if a new version of a decimal value would be redundant given the current version of the value.
      *
      * @param currentVersion the current version of the value.
      * @return `true` if this [[UpdateValueV1]] is redundant given `currentVersion`.
      */
    override def isRedundant(currentVersion: ApiValueV1): Boolean = {
        currentVersion match {
            case decimalValueV1: DecimalValueV1 => decimalValueV1 == this
            case other => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${other.valueTypeIri}")
        }
    }

}

/**
  * Represents a time interval value.
  *
  * @param timeval1 an `xsd:decimal` representing the beginning of the interval.
  * @param timeval2 an `xsd:decimal` representing the end of the interval.
  */
case class IntervalValueV1(timeval1: BigDecimal, timeval2: BigDecimal) extends UpdateValueV1 with ApiValueV1 {

    def valueTypeIri = OntologyConstants.KnoraBase.IntervalValue

    def toJsValue = JsObject(
        "timeval1" -> JsNumber(timeval1),
        "timeval2" -> JsNumber(timeval2)
    )

    override def toString = s"$timeval1 - $timeval2"

    /**
      * Checks if a new interval value would duplicate an existing interval value.
      *
      * @param other another [[ValueV1]].
      * @return `true` if `other` is a duplicate of `this`.
      */
    override def isDuplicateOfOtherValue(other: ApiValueV1): Boolean = {
        other match {
            case intervalValueV1: IntervalValueV1 => intervalValueV1 == this
            case otherValue => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${otherValue.valueTypeIri}")
        }
    }

    /**
      * Checks if a new version of this interval value would be redundant given the current version of the value.
      *
      * @param currentVersion the current version of the value.
      * @return `true` if this [[UpdateValueV1]] is redundant given `currentVersion`.
      */
    override def isRedundant(currentVersion: ApiValueV1): Boolean = {
        currentVersion match {
            case intervalValueV1: IntervalValueV1 => intervalValueV1 == this
            case other => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${other.valueTypeIri}")
        }
    }

}

/**
  * Represents a date value as a period bounded by Julian Day Numbers. Knora stores dates internally in this format.
  *
  * @param dateval1       the beginning of the date (a Julian day number).
  * @param dateval2       the end of the date (a Julian day number).
  * @param calendar       the preferred calendar for representing the date.
  * @param dateprecision1 the precision of the beginning of the date.
  * @param dateprecision2 the precision of the end of the date.
  */
case class JulianDayNumberValueV1(dateval1: Int,
                                  dateval2: Int,
                                  calendar: KnoraCalendarV1.Value,
                                  dateprecision1: KnoraPrecisionV1.Value,
                                  dateprecision2: KnoraPrecisionV1.Value) extends UpdateValueV1 {

    def valueTypeIri = OntologyConstants.KnoraBase.DateValue

    override def isDuplicateOfOtherValue(other: ApiValueV1): Boolean = {
        other match {
            case dateValueV1: DateValueV1 => DateUtilV1.julianDayNumberValueV1ToDateValueV1(this) == other
            case otherValue => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${otherValue.valueTypeIri}")
        }
    }

    override def isRedundant(currentVersion: ApiValueV1): Boolean = isDuplicateOfOtherValue(currentVersion)

    // value for String representation of a date in templates.
    override def toString = {
        // use only precision DAY: either the date is exact (a certain day)
        // or it is a period expressed as a range from one day to another.
        val date1 = DateUtilV1.julianDayNumber2DateString(dateval1, calendar, KnoraPrecisionV1.DAY)
        val date2 = DateUtilV1.julianDayNumber2DateString(dateval2, calendar, KnoraPrecisionV1.DAY)

        // if date1 and date2 are identical, it's not a period.
        if (date1 == date2) {
            // one exact day
            date1
        } else {
            // period: from to
            date1 + " - " + date2
        }
    }
}

/**
  * Represents a date value as represented in Knora API v1.
  *
  * A [[DateValueV1]] can represent either single date or a period with start and end dates (`dateval1` and `dateval2`).
  * If it represents a single date, `dateval1` will have a value but `dateval2` will be `None`. Both `dateval1` and `dateval2`
  * can indicate degrees of uncertainty, using the following formats:
  *
  * - `YYYY-MM-DD` specifies a particular day, with no uncertainty.
  * - `YYYY-MM` indicates that the year and the month are known, but that the day of the month is uncertain. In effect, this specifies a range of possible dates, from the first day of the month to the last day of the month.
  * - `YYYY` indicates that only the year is known. In effect, this specifies a range of possible dates, from the first day of the year to the last day of the year.
  *
  * The year and month values refer to years and months in the calendar specified by `calendar`.
  *
  * @param dateval1 the start date of the period.
  * @param dateval2 the end date of the period, if any.
  * @param calendar the type of calendar used in the date.
  */
case class DateValueV1(dateval1: String,
                       dateval2: String,
                       era1: String,
                       era2: String,
                       calendar: KnoraCalendarV1.Value) extends ApiValueV1 {

    def valueTypeIri = OntologyConstants.KnoraBase.DateValue

    override def toString = {


        // if date1 and date2 are identical, it's not a period.
        if (dateval1 == dateval2) {
            // one exact day
            dateval1 + " " + era1
        } else {
            // period: from to
            dateval1 + " " + era1 + " - " + dateval2 + " " + era2
        }

    }

    def toJsValue = ApiValueV1JsonProtocol.dateValueV1Format.write(this)
}

/**
  * Represents an RGB color value.
  *
  * @param color a hexadecimal string containing the RGB color value.
  */
case class ColorValueV1(color: String) extends UpdateValueV1 with ApiValueV1 {

    def valueTypeIri = OntologyConstants.KnoraBase.ColorValue

    def toJsValue = JsString(color)

    override def toString = color

    /**
      * Checks if a new color value would equal an existing color value.
      *
      * @param other another [[ValueV1]].
      * @return `true` if `other` is a duplicate of `this`.
      */
    override def isDuplicateOfOtherValue(other: ApiValueV1): Boolean = {
        other match {
            case colorValueV1: ColorValueV1 => colorValueV1 == this
            case otherValue => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${otherValue.valueTypeIri}")
        }
    }

    /**
      * Checks if a new version of this color value would equal the existing version of this color value.
      *
      * @param currentVersion the current version of the value.
      * @return `true` if this [[UpdateValueV1]] is redundant given `currentVersion`.
      */
    override def isRedundant(currentVersion: ApiValueV1): Boolean = {
        currentVersion match {
            case colorValueV1: ColorValueV1 => colorValueV1 == this
            case other => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${other.valueTypeIri}")
        }
    }
}

/**
  * Represents a geometric shape.
  *
  * @param geom A string containing JSON that describes the shape. TODO: don't use JSON for this (issue 169).
  */
case class GeomValueV1(geom: String) extends UpdateValueV1 with ApiValueV1 {

    def valueTypeIri = OntologyConstants.KnoraBase.GeomValue

    def toJsValue = JsString(geom)

    override def toString = geom

    /**
      * Checks if a new geom value would duplicate an existing geom value.
      *
      * @param other another [[ValueV1]].
      * @return `true` if `other` is a duplicate of `this`.
      */
    override def isDuplicateOfOtherValue(other: ApiValueV1): Boolean = {
        other match {
            case geomValueV1: GeomValueV1 => geomValueV1 == this
            case otherValue => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${otherValue.valueTypeIri}")
        }
    }

    /**
      * Checks if a new version of a geom value would be redundant given the current version of the value.
      *
      * @param currentVersion the current version of the value.
      * @return `true` if this [[UpdateValueV1]] is redundant given `currentVersion`.
      */
    override def isRedundant(currentVersion: ApiValueV1): Boolean = {
        currentVersion match {
            case geomValueV1: GeomValueV1 => geomValueV1 == this
            case other => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${other.valueTypeIri}")
        }
    }
}

/**
  * Represents a [[http://www.geonames.org/ GeoNames]] code.
  *
  * @param geonameCode a string representing the GeoNames code.
  */
case class GeonameValueV1(geonameCode: String) extends UpdateValueV1 with ApiValueV1 {

    def valueTypeIri = OntologyConstants.KnoraBase.GeonameValue

    def toJsValue = JsString(geonameCode)

    override def toString = geonameCode

    /**
      * Checks if a new GeoName value would duplicate an existing GeoName value.
      *
      * @param other another [[ValueV1]].
      * @return `true` if `other` is a duplicate of `this`.
      */
    override def isDuplicateOfOtherValue(other: ApiValueV1): Boolean = {
        other match {
            case geonameValueV1: GeonameValueV1 => geonameValueV1 == this
            case otherValue => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${otherValue.valueTypeIri}")
        }
    }

    /**
      * Checks if a new version of a GeoName value would be redundant given the current version of the value.
      *
      * @param currentVersion the current version of the value.
      * @return `true` if this [[UpdateValueV1]] is redundant given `currentVersion`.
      */
    override def isRedundant(currentVersion: ApiValueV1): Boolean = {
        currentVersion match {
            case geonameValueV1: GeonameValueV1 => geonameValueV1 == this
            case other => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${other.valueTypeIri}")
        }
    }
}

/**
  * The data describing a binary file of any type that can be sent to Knora.
  */
sealed trait FileValueV1 extends UpdateValueV1 with ApiValueV1 {
    val internalMimeType: String
    val internalFilename: String
    val originalFilename: String
    val originalMimeType: Option[String]
}

/**
  * A representation of a digital image.
  *
  * @param internalMimeType the MIME-type of the internal representation.
  * @param internalFilename the internal filename of the object.
  * @param originalFilename the original filename of the object at the time of the import.
  * @param dimX             the X dimension of the object.
  * @param dimY             the Y dimension of the object.
  * @param qualityLevel     the quality level of this image (higher values mean higher resolutions).
  * @param qualityName      a string representation of the qualityLevel
  * @param isPreview        indicates if the file value is used as a preview (thumbnail)
  */
case class StillImageFileValueV1(internalMimeType: String,
                                 internalFilename: String,
                                 originalFilename: String,
                                 originalMimeType: Option[String] = None,
                                 dimX: Int,
                                 dimY: Int,
                                 qualityLevel: Int,
                                 qualityName: Option[String] = None,
                                 isPreview: Boolean = false) extends FileValueV1 {

    def valueTypeIri = OntologyConstants.KnoraBase.StillImageFileValue

    def toJsValue = ApiValueV1JsonProtocol.stillImageFileValueV1Format.write(this)

    override def toString = originalFilename

    /**
      * Checks if a new still image file value would duplicate an existing still image file value.
      *
      * @param other another [[ValueV1]].
      * @return `true` if `other` is a duplicate of `this`.
      */
    override def isDuplicateOfOtherValue(other: ApiValueV1): Boolean = {
        other match {
            case stillImageFileValueV1: StillImageFileValueV1 => stillImageFileValueV1 == this
            case otherValue => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${otherValue.valueTypeIri}")
        }
    }

    /**
      * Checks if a new version of a still image file value would be redundant given the current version of the value.
      *
      * @param currentVersion the current version of the value.
      * @return `true` if this [[UpdateValueV1]] is redundant given `currentVersion`.
      */
    override def isRedundant(currentVersion: ApiValueV1): Boolean = {
        currentVersion match {
            case stillImageFileValueV1: StillImageFileValueV1 => stillImageFileValueV1 == this
            case other => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${other.valueTypeIri}")
        }
    }
}

case class MovingImageFileValueV1(internalMimeType: String,
                                  internalFilename: String,
                                  originalFilename: String,
                                  originalMimeType: Option[String] = None) extends FileValueV1 {

    def valueTypeIri = OntologyConstants.KnoraBase.MovingImageFileValue

    def toJsValue = ApiValueV1JsonProtocol.movingImageFileValueV1Format.write(this)

    override def toString = originalFilename

    /**
      * Checks if a new moving image file value would duplicate an existing moving image file value.
      *
      * @param other another [[ValueV1]].
      * @return `true` if `other` is a duplicate of `this`.
      */
    override def isDuplicateOfOtherValue(other: ApiValueV1): Boolean = {
        other match {
            case movingImageFileValueV1: MovingImageFileValueV1 => movingImageFileValueV1 == this
            case otherValue => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${otherValue.valueTypeIri}")
        }
    }

    /**
      * Checks if a new version of a moving image file value would be redundant given the current version of the value.
      *
      * @param currentVersion the current version of the value.
      * @return `true` if this [[UpdateValueV1]] is redundant given `currentVersion`.
      */
    override def isRedundant(currentVersion: ApiValueV1): Boolean = {
        currentVersion match {
            case movingImageFileValueV1: MovingImageFileValueV1 => movingImageFileValueV1 == this
            case other => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${other.valueTypeIri}")
        }
    }

}

case class TextFileValueV1(internalMimeType: String,
                           internalFilename: String,
                           originalFilename: String,
                           originalMimeType: Option[String] = None) extends FileValueV1 {

    def valueTypeIri = OntologyConstants.KnoraBase.TextFileValue

    def toJsValue = ApiValueV1JsonProtocol.textFileValueV1Format.write(this)

    override def toString = originalFilename

    /**
      * Checks if a new text file value would duplicate an existing text file value.
      *
      * @param other another [[ValueV1]].
      * @return `true` if `other` is a duplicate of `this`.
      */
    override def isDuplicateOfOtherValue(other: ApiValueV1): Boolean = {
        other match {
            case textFileValueV1: TextFileValueV1 => textFileValueV1 == this
            case otherValue => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${otherValue.valueTypeIri}")
        }
    }

    /**
      * Checks if a new version of a text file value would be redundant given the current version of the value.
      *
      * @param currentVersion the current version of the value.
      * @return `true` if this [[UpdateValueV1]] is redundant given `currentVersion`.
      */
    override def isRedundant(currentVersion: ApiValueV1): Boolean = {
        currentVersion match {
            case textFileValueV1: TextFileValueV1 => textFileValueV1 == this
            case other => throw InconsistentTriplestoreDataException(s"Cannot compare a $valueTypeIri to a ${other.valueTypeIri}")
        }
    }

}


/**
  * Represents information about a version of a value.
  *
  * @param valueObjectIri    the IRI of the version.
  * @param valueCreationDate the timestamp of the version.
  * @param previousValue     the IRI of the previous version.
  */
case class ValueVersionV1(valueObjectIri: IRI,
                          valueCreationDate: Option[String],
                          previousValue: Option[IRI]) extends ApiValueV1 {
    def valueTypeIri = OntologyConstants.KnoraBase.LinkValue

    def toJsValue = ApiValueV1JsonProtocol.valueVersionV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON for property values.
  */
object ApiValueV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceV1JsonProtocol._

    /**
      * Converts between [[KnoraCalendarV1]] objects and [[JsValue]] objects.
      */
    implicit object KnoraCalendarV1JsonFormat extends JsonFormat[KnoraCalendarV1.Value] {
        def read(jsonVal: JsValue): KnoraCalendarV1.Value = jsonVal match {
            case JsString(str) => KnoraCalendarV1.lookup(str)
            case _ => throw BadRequestException(s"Invalid calendar in JSON: $jsonVal")
        }

        def write(calendarV1Value: KnoraCalendarV1.Value): JsValue = JsString(calendarV1Value.toString)
    }

    /**
      * Converts between [[KnoraPrecisionV1]] objects and [[JsValue]] objects.
      */
    implicit object KnoraPrecisionV1JsonFormat extends JsonFormat[KnoraPrecisionV1.Value] {
        def read(jsonVal: JsValue): KnoraPrecisionV1.Value = jsonVal match {
            case JsString(str) => KnoraPrecisionV1.lookup(str)
            case _ => throw BadRequestException(s"Invalid precision in JSON: $jsonVal")
        }

        def write(precisionV1Value: KnoraPrecisionV1.Value): JsValue = JsString(precisionV1Value.toString)
    }

    /**
      * Converts between [[ApiValueV1]] objects and [[JsValue]] objects.
      */
    implicit object ValueV1JsonFormat extends JsonFormat[ApiValueV1] {
        /**
          * Not implemented.
          */
        def read(jsonVal: JsValue) = ???

        /**
          * Converts an [[ApiValueV1]] to a [[JsValue]].
          *
          * @param valueV1 a [[ApiValueV1]]
          * @return a [[JsValue]].
          */
        def write(valueV1: ApiValueV1): JsValue = valueV1.toJsValue
    }

    implicit val createFileQualityLevelFormat: RootJsonFormat[CreateFileQualityLevelV1] = jsonFormat4(CreateFileQualityLevelV1)
    implicit val createFileV1Format: RootJsonFormat[CreateFileV1] = jsonFormat3(CreateFileV1)
    implicit val valueGetResponseV1Format: RootJsonFormat[ValueGetResponseV1] = jsonFormat7(ValueGetResponseV1)
    implicit val dateValueV1Format: JsonFormat[DateValueV1] = jsonFormat5(DateValueV1)
    implicit val stillImageFileValueV1Format: JsonFormat[StillImageFileValueV1] = jsonFormat9(StillImageFileValueV1)
    implicit val textFileValueV1Format: JsonFormat[TextFileValueV1] = jsonFormat4(TextFileValueV1)
    implicit val movingImageFileValueV1Format: JsonFormat[MovingImageFileValueV1] = jsonFormat4(MovingImageFileValueV1)
    implicit val valueVersionV1Format: JsonFormat[ValueVersionV1] = jsonFormat3(ValueVersionV1)
    implicit val linkValueV1Format: JsonFormat[LinkValueV1] = jsonFormat4(LinkValueV1)
    implicit val valueVersionHistoryGetResponseV1Format: RootJsonFormat[ValueVersionHistoryGetResponseV1] = jsonFormat1(ValueVersionHistoryGetResponseV1)
    implicit val createRichtextV1Format: RootJsonFormat[CreateRichtextV1] = jsonFormat4(CreateRichtextV1)
    implicit val createValueApiRequestV1Format: RootJsonFormat[CreateValueApiRequestV1] = jsonFormat15(CreateValueApiRequestV1)
    implicit val createValueResponseV1Format: RootJsonFormat[CreateValueResponseV1] = jsonFormat4(CreateValueResponseV1)
    implicit val changeValueApiRequestV1Format: RootJsonFormat[ChangeValueApiRequestV1] = jsonFormat13(ChangeValueApiRequestV1)
    implicit val changeValueResponseV1Format: RootJsonFormat[ChangeValueResponseV1] = jsonFormat4(ChangeValueResponseV1)
    implicit val deleteValueResponseV1Format: RootJsonFormat[DeleteValueResponseV1] = jsonFormat1(DeleteValueResponseV1)
    implicit val changeFileValueApiRequestV1Format: RootJsonFormat[ChangeFileValueApiRequestV1] = jsonFormat1(ChangeFileValueApiRequestV1)
    implicit val changeFileValueresponseV1Format: RootJsonFormat[ChangeFileValueResponseV1] = jsonFormat1(ChangeFileValueResponseV1)
}
