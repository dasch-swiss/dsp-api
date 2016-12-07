/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.messages.v1.responder.standoffmessages

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.{IRI, OntologyConstants}
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import spray.json._

import scala.collection.immutable.SortedSet


/**
  * An abstract trait representing a Knora v1 API request message that can be sent to `StandoffResponderV1`.
  */
sealed trait StandoffResponderRequestV1 extends KnoraRequestV1

/**
  * Represents a request to create a mapping between XML elements and attributes and standoff classes and properties.
  * A successful response will be a [[CreateMappingResponseV1]].
  *
  * @param xml the mapping in XML.
  * @param projectIri the IRI of the project the mapping belongs to.
  * @param userProfile the profile of the user making the request.
  */
case class CreateMappingRequestV1(xml: String, projectIri: IRI, userProfile: UserProfileV1) extends StandoffResponderRequestV1

/**
  * Provides the name of the file containing the mapping.
  *
  * @param resourceIri the Iri of the resource (knora-base:XMLToStandoffMapping) representing the mapping that has been created.
  * @param userdata information about the user that made the request.
  */
case class CreateMappingResponseV1(resourceIri: IRI, userdata: UserDataV1) extends KnoraResponseV1 {
    def toJsValue = RepresentationV1JsonProtocol.createMappingResponseV1Format.write(this)
}

/**
  * Represents a mapping between XML tags and standoff entities (classes and properties).
  * The map's keys are XML namespaces, the values are Maps themselves that have XML tag names as keys.
  *
  * Example:
  *
  * namespace = Map("myXMLNamespace" -> Map("myXMLTagName" -> XMLTag(...)))
  *
  * @param namespace a Map of XML namespaces and a Map of tag names and [[XMLTag]].
  */
case class MappingXMLtoStandoff(namespace: Map[String, Map[String, XMLTag]])

/**
  * Represents a mapping between an XML tag and standoff entities (classes and properties).
  *
  * @param name the tag name.
  * @param mapping the corresponding standoff entities.
  */
case class XMLTag(name: String, mapping: XMLTagToStandoffClass)

/**
  * Represents standoff entities referred to in the mapping.
  * The attributes are represented as a Map of namespaces and a Map of attribute names and standoff properties.
  *
  * Example for attributesToProps:
  *
  * attributesToProps = Map("myXMLNamespace" -> Map("myXMLAttributeName" -> "standoffPropertyIri"))
  *
  * @param standoffClassIri the Iri of the standoff class.
  * @param attributesToProps a mapping between XML namespaces and attribute names and standoff properties.
  * @param dataType the data type of the standoff class (e.g., a date).
  * @param dataTypeXMLAttribute the XML attribute holding the information needed for the standoff class data type (e.g., a date string).
  */
case class XMLTagToStandoffClass(standoffClassIri: IRI, attributesToProps: Map[String, Map[String, IRI]] = Map.empty[String, Map[String, IRI]], dataType: Option[StandoffDataTypeClasses.Value] = None, dataTypeXMLAttribute: Option[String] = None)


/**
  * Represents a request to add a text value containing standoff to a resource.
  * A successful response will be an [[CreateStandoffResponseV1]].
  *
  * @param projectIri   the project in which the text value is to be added.
  * @param resourceIri  the IRI of the resource to which the text value should be added.
  * @param propertyIri  the IRI of the property that should receive the text value.
  * @param mappingIri   the IRI of the mapping resource that is used to turn the XML into standoff.
  * @param xml          the xml representing the text with markup.
  * @param userProfile  the profile of the user making the request.
  * @param apiRequestID the ID of this API request.
  */
case class CreateStandoffRequestV1(projectIri: IRI, resourceIri: IRI, propertyIri: IRI, mappingIri: IRI, xml: String, userProfile: UserProfileV1, apiRequestID: UUID) extends StandoffResponderRequestV1

/**
  *
  * @param id       the Iri of the new text value.compile
  * @param userdata information about the user that made the request.
  */
case class CreateStandoffResponseV1(id: IRI, userdata: UserDataV1) extends KnoraResponseV1 {
    def toJsValue = RepresentationV1JsonProtocol.createStandoffResponseV1Format.write(this)
}

/**
  * Represents a request for a text value. A successful response will be a [[StandoffGetResponseV1]].
  *
  * @param valueIri    the IRI of the value requested.
  * @param userProfile the profile of the user making the request.
  */
case class StandoffGetRequestV1(valueIri: IRI, userProfile: UserProfileV1) extends StandoffResponderRequestV1

/**
  * Provides a text value in XML.
  *
  * @param xml      the XML file representing the text value.
  * @param userdata information about the user that made the request.
  */
case class StandoffGetResponseV1(xml: String, userdata: UserDataV1) extends KnoraResponseV1 {
    def toJsValue = RepresentationV1JsonProtocol.standoffGetResponseV1Format.write(this)
}

/**
  * Represents the data types of standoff classes.
  */
object StandoffDataTypeClasses extends Enumeration {

    val StandoffLinkTag = Value(OntologyConstants.KnoraBase.StandoffLinkTag)

    val StandoffDateTag = Value(OntologyConstants.KnoraBase.StandoffDateTag)

    val StandoffUriTag = Value(OntologyConstants.KnoraBase.StandoffUriTag)

    val StandoffColorTag = Value(OntologyConstants.KnoraBase.StandoffColorTag)

    val StandoffIntegerTag = Value(OntologyConstants.KnoraBase.StandoffIntegerTag)

    val StandoffDecimalTag = Value(OntologyConstants.KnoraBase.StandoffDecimalTag)

    val StandoffIntervalTag = Value(OntologyConstants.KnoraBase.StandoffIntervalTag)

    val StandoffBooleanTag = Value(OntologyConstants.KnoraBase.StandoffBooleanTag)

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

    /**
      * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
      * [[org.knora.webapi.BadRequestException]].
      *
      * @param name     the name of the value.
      * @param errorFun the function to be called in case of an error.
      * @return the requested value.
      */
    def lookup(name: String, errorFun: () => Nothing): Value = {
        valueMap.get(name) match {
            case Some(value) => value
            case None => errorFun()
        }
    }

    def getStandoffClassIris: SortedSet[IRI] = StandoffDataTypeClasses.values.map(_.toString)

}

/**
  * Represents collections of standoff properties.
  */
object StandoffProperties {

    // represents the standoff properties defined on the base standoff tag
    val systemProperties = Set(
        OntologyConstants.KnoraBase.StandoffTagHasStart,
        OntologyConstants.KnoraBase.StandoffTagHasEnd,
        OntologyConstants.KnoraBase.StandoffTagHasStartIndex,
        OntologyConstants.KnoraBase.StandoffTagHasEndIndex,
        OntologyConstants.KnoraBase.StandoffTagHasStartParentIndex,
        OntologyConstants.KnoraBase.StandoffTagHasEndParentIndex,
        OntologyConstants.KnoraBase.StandoffTagHasUUID
    )

    // represents the standoff properties defined on the date standoff tag
    val dateProperties = Set(
        OntologyConstants.KnoraBase.ValueHasCalendar,
        OntologyConstants.KnoraBase.ValueHasStartJDN,
        OntologyConstants.KnoraBase.ValueHasEndJDN,
        OntologyConstants.KnoraBase.ValueHasStartPrecision,
        OntologyConstants.KnoraBase.ValueHasEndPrecision
    )

    // represents the standoff properties defined on the interval standoff tag
    val intervalProperties = Set(
        OntologyConstants.KnoraBase.ValueHasIntervalStart,
        OntologyConstants.KnoraBase.ValueHasIntervalEnd
    )

    // represents the standoff properties defined on the boolean standoff tag
    val booleanProperties = Set(OntologyConstants.KnoraBase.ValueHasBoolean)

    // represents the standoff properties defined on the decimal standoff tag
    val decimalProperties = Set(OntologyConstants.KnoraBase.ValueHasDecimal)

    // represents the standoff properties defined on the integer standoff tag
    val integerProperties = Set(OntologyConstants.KnoraBase.ValueHasInteger)

    // represents the standoff properties defined on the uri standoff tag
    val uriProperties = Set(OntologyConstants.KnoraBase.ValueHasUri)

    // represents the standoff properties defined on the color standoff tag
    val colorProperties = Set(OntologyConstants.KnoraBase.ValueHasColor)

    // represents the standoff properties defined on the link standoff tag
    val linkProperties = Set(OntologyConstants.KnoraBase.StandoffTagHasLink)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON for standoff handling.
  */
object RepresentationV1JsonProtocol extends DefaultJsonProtocol with NullOptions with SprayJsonSupport {

    import org.knora.webapi.messages.v1.responder.usermessages.UserDataV1JsonProtocol._

    implicit val createStandoffResponseV1Format: RootJsonFormat[CreateStandoffResponseV1] = jsonFormat2(CreateStandoffResponseV1)
    implicit val standoffGetResponseV1Format: RootJsonFormat[StandoffGetResponseV1] = jsonFormat2(StandoffGetResponseV1)
    implicit val createMappingResponseV1Format: RootJsonFormat[CreateMappingResponseV1] = jsonFormat2(CreateMappingResponseV1)
}