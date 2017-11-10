/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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
import org.knora.webapi.messages.v1.responder.ontologymessages.StandoffEntityInfoGetResponseV1
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.{IRI, OntologyConstants}
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
  * @param xml         the mapping in XML.
  * @param projectIri  the IRI of the project the mapping belongs to.
  * @param mappingName the name of the mapping to be created.
  * @param userProfile the profile of the user making the request.
  */
case class CreateMappingRequestV1(xml: String, label: String, projectIri: IRI, mappingName: String, userProfile: UserProfileV1, apiRequestID: UUID) extends StandoffResponderRequestV1

/**
  * Provides the IRI of the created mapping.
  *
  * @param mappingIri the IRI of the resource (knora-base:XMLToStandoffMapping) representing the mapping that has been created.
  */
case class CreateMappingResponseV1(mappingIri: IRI) extends KnoraResponseV1 {
    def toJsValue = RepresentationV1JsonProtocol.createMappingResponseV1Format.write(this)
}

/**
  * Represents a request to get a mapping from XML elements and attributes to standoff entities.
  *
  * @param mappingIri  the IRI of the mapping.
  * @param userProfile the profile of the user making the request.
  */
case class GetMappingRequestV1(mappingIri: IRI, userProfile: UserProfileV1) extends StandoffResponderRequestV1

/**
  * Represents a response to a [[GetMappingRequestV1]].
  *
  * @param mappingIri       the IRI of the requested mapping.
  * @param mapping          the requested mapping.
  * @param standoffEntities the standoff entities referred to in the mapping.
  */
case class GetMappingResponseV1(mappingIri: IRI, mapping: MappingXMLtoStandoff, standoffEntities: StandoffEntityInfoGetResponseV1)

/**
  * Represents a request that gets an XSL Transformation represented by a `knora-base:XSLTransformation`.
  *
  * @param xsltTextRepresentationIri the IRI of the `knora-base:XSLTransformation`.
  * @param userProfile               the profile of the user making the request.
  */
case class GetXSLTransformationRequestV1(xsltTextRepresentationIri: IRI, userProfile: UserProfileV1) extends StandoffResponderRequestV1

/**
  * Represents a response to a [[GetXSLTransformationRequestV1]].
  *
  * @param xslt the XSLT to be applied to the XML created from standoff.
  */
case class GetXSLTransformationResponseV1(xslt: String)

/**
  * Represents a mapping between XML tags and standoff entities (classes and properties).
  *
  * Example:
  *
  * namespace = Map("myXMLNamespace" -> Map("myXMLTagName" -> Map("myXMLClassname" -> XMLTag(...))))
  *
  * The class names allow for the ruse of the same tag name. This is important when using HTML since the tag set is very limited.
  *
  * @param namespace                a Map of XML namespaces and a Map of tag names and [[XMLTag]].
  * @param defaultXSLTransformation the IRI of the default XSL transformation for the resulting XML, if any.
  */
case class MappingXMLtoStandoff(namespace: Map[String, Map[String, Map[String, XMLTag]]], defaultXSLTransformation: Option[IRI])

/**
  * Represents a mapping between an XML tag and standoff entities (classes and properties).
  *
  * @param name              the tag name.
  * @param mapping           the corresponding standoff entities.
  * @param separatorRequired indicates if the element requires a separator in the text once the markup has been converted to standoff.
  */
case class XMLTag(name: String, mapping: XMLTagToStandoffClass, separatorRequired: Boolean)

/**
  * Represents standoff entities referred to in the mapping.
  * The attributes are represented as a Map of namespaces and a Map of attribute names and standoff properties.
  *
  * Example for attributesToProps:
  *
  * attributesToProps = Map("myXMLNamespace" -> Map("myXMLAttributeName" -> "standoffPropertyIri"))
  *
  * @param standoffClassIri  the IRI of the standoff class.
  * @param attributesToProps a mapping between XML namespaces and attribute names and standoff properties.
  * @param dataType          the data type of the standoff class (e.g., a date).
  */
case class XMLTagToStandoffClass(standoffClassIri: IRI, attributesToProps: Map[String, Map[String, IRI]] = Map.empty[String, Map[String, IRI]], dataType: Option[XMLStandoffDataTypeClass])

/**
  * Represents a data type standoff class in mapping for an XML element.
  *
  * @param standoffDataTypeClass the data type of the standoff class (e.g., a date).
  * @param dataTypeXMLAttribute  the XML attribute holding the information needed for the standoff class data type (e.g., a date string).
  */
case class XMLStandoffDataTypeClass(standoffDataTypeClass: StandoffDataTypeClasses.Value, dataTypeXMLAttribute: String)

/**
  * Represents an API request to create a mapping.
  *
  * @param project_id  the project in which the mapping is to be added.
  * @param label       the label describing the mapping.
  * @param mappingName the name of the mapping (will be appended to the mapping IRI).
  */
case class CreateMappingApiRequestV1(project_id: IRI, label: String, mappingName: String) {

    def toJsValue = RepresentationV1JsonProtocol.createMappingApiRequestV1Format.write(this)

}

/**
  * Represents the data types of standoff classes.
  */
object StandoffDataTypeClasses extends Enumeration {

    val StandoffLinkTag: Value = Value(OntologyConstants.KnoraBase.StandoffLinkTag)

    val StandoffDateTag: Value = Value(OntologyConstants.KnoraBase.StandoffDateTag)

    val StandoffUriTag: Value = Value(OntologyConstants.KnoraBase.StandoffUriTag)

    val StandoffColorTag: Value = Value(OntologyConstants.KnoraBase.StandoffColorTag)

    val StandoffIntegerTag: Value = Value(OntologyConstants.KnoraBase.StandoffIntegerTag)

    val StandoffDecimalTag: Value = Value(OntologyConstants.KnoraBase.StandoffDecimalTag)

    val StandoffIntervalTag: Value = Value(OntologyConstants.KnoraBase.StandoffIntervalTag)

    val StandoffBooleanTag: Value = Value(OntologyConstants.KnoraBase.StandoffBooleanTag)

    val StandoffInternalReferenceTag: Value = Value(OntologyConstants.KnoraBase.StandoffInternalReferenceTag)

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

    /**
      * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
      * exception.
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
        OntologyConstants.KnoraBase.StandoffTagHasStartParent,
        OntologyConstants.KnoraBase.StandoffTagHasEndParent,
        OntologyConstants.KnoraBase.StandoffTagHasUUID,
        OntologyConstants.KnoraBase.StandoffTagHasOriginalXMLID
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

    // represents the standoff properties defined on the internal reference standoff tag
    val internalReferenceProperties = Set(OntologyConstants.KnoraBase.StandoffTagHasInternalReference)

    val dataTypeProperties: Set[IRI] = dateProperties ++ intervalProperties ++ booleanProperties ++ decimalProperties ++ integerProperties ++ uriProperties ++ colorProperties ++ linkProperties ++ internalReferenceProperties
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON for standoff handling.
  */
object RepresentationV1JsonProtocol extends DefaultJsonProtocol with NullOptions with SprayJsonSupport {

    implicit val createMappingApiRequestV1Format: RootJsonFormat[CreateMappingApiRequestV1] = jsonFormat3(CreateMappingApiRequestV1)
    implicit val createMappingResponseV1Format: RootJsonFormat[CreateMappingResponseV1] = jsonFormat1(CreateMappingResponseV1)
}