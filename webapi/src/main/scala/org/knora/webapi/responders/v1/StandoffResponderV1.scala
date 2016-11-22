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

package org.knora.webapi.responders.v1

import java.util.UUID

import akka.actor.Status
import akka.pattern._
import akka.stream.ActorMaterializer
import org.knora.webapi.messages.v1.responder.ontologymessages.{Cardinality, StandoffEntityInfoGetRequestV1, StandoffEntityInfoGetResponseV1}
import org.knora.webapi.messages.v1.responder.standoffmessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.valuemessages.{CreateValueRequestV1, CreateValueResponseV1, TextValueV1}
import org.knora.webapi.twirl._
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.standoff._
import org.knora.webapi.util.{DateUtilV1, InputValidation}
import org.knora.webapi.{BadRequestException, _}

import scala.concurrent.Future

/**
  * Responds to requests for information about binary representations of resources, and returns responses in Knora API
  * v1 format.
  */
class StandoffResponderV1 extends ResponderV1 {

    implicit val materializer = ActorMaterializer()

    // Converts SPARQL query results to ApiValueV1 objects.
    val valueUtilV1 = new ValueUtilV1(settings)

    /**
      * Receives a message of type [[StandoffResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case CreateStandoffRequestV1(projIri, resIri, propIri, xml, userProfile, uuid) => future2Message(sender(), createStandoff(projIri, resIri, propIri, xml, userProfile, uuid), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    // represents the standoff properties defined on the base standoff tag
    val systemStandoffProperties = Set(
        OntologyConstants.KnoraBase.StandoffTagHasStart,
        OntologyConstants.KnoraBase.StandoffTagHasEnd,
        OntologyConstants.KnoraBase.StandoffTagHasStartIndex,
        OntologyConstants.KnoraBase.StandoffTagHasEndIndex,
        OntologyConstants.KnoraBase.StandoffTagHasStartParentIndex,
        OntologyConstants.KnoraBase.StandoffTagHasEndParentIndex,
        OntologyConstants.KnoraBase.StandoffTagHasUUID
    )

    // represents the possible data types of a standoff class
    object dataTypes extends Enumeration {
        val date = Value(0, "date")
        val uri = Value(1, "uri")
        val color = Value(2, "color")
        val integer = Value(3, "integer")
        val decimal = Value(4, "decimal")
        val interval = Value(5, "interval")
        val boolean = Value(6, "boolean")
        val link = Value(7, "link")

        val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

        /**
          * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
          * [[BadRequestException]].
          *
          * @param name the name of the value.
          * @return the requested value.
          */
        def lookup(name: String): Value = {
            valueMap.get(name) match {
                case Some(value) => value
                case None => throw BadRequestException(s"requested data type not supported: $name")
            }
        }
    }

    /**
      * Tries to find a data type attribute in the XML attributes of a given standoff node. Throws an appropriate error if information is inconsistent or missing.
      *
      * @param XMLtoStandoffMapping the mapping from XML to standoff classes and properties for the given standoff node.
      * @param dataType the expected data type of the given standoff node.
      * @param standoffNodeFromXML the given standoff node.
      * @return the value of the attribute.
      */
    private def getDataTypeAttribute(XMLtoStandoffMapping: MapXMLTagToStandoffClass, dataType: dataTypes.Value, standoffNodeFromXML: StandoffTag): String = {

        if (XMLtoStandoffMapping.dataType.isEmpty || XMLtoStandoffMapping.dataType.get != dataType) {
            throw BadRequestException(s"wrong data type definition provided in mapping for standoff class ${XMLtoStandoffMapping.standoffClassIri}")
        }

        val attrName = XMLtoStandoffMapping.dataTypeXMLAttribute.getOrElse(throw BadRequestException(s"no data type attribute definition provided in mapping for ${XMLtoStandoffMapping.standoffClassIri}"))

        val attrStringOption: Option[StandoffTagAttribute] = standoffNodeFromXML.attributes.find(attr => attr.key == attrName)

        if (attrStringOption.isEmpty) {
            throw BadRequestException(s"required data type attribute '$attrName' could not be found for a $dataType value")
        } else {
            attrStringOption.get.value
        }

    }

    /**
      * Creates a sequence of [[StandoffTagAttributeV1]] for the given standoff node.
      *
      * @param XMLtoStandoffMapping the mapping from XML to standoff classes and properties for the given standoff node.
      * @param classSpecificProps the properties that may or have to be created (cardinalities) for the given standoff node.
      * @param standoffNodeFromXML the given standoff node.
      * @param standoffPropertyEntities the ontology information about the standoff properties.
      * @return a sequence of [[StandoffTagAttributeV1]]
      */
    private def createAttributes(XMLtoStandoffMapping: MapXMLTagToStandoffClass, classSpecificProps: Map[IRI, Cardinality.Value], standoffNodeFromXML: StandoffTag, standoffPropertyEntities: StandoffEntityInfoGetResponseV1): Seq[StandoffTagAttributeV1] = {

        if (classSpecificProps.nonEmpty) {
            // additional standoff properties are required

            val XMLAttributeMapping: Map[String, IRI] = XMLtoStandoffMapping.attributesToProps

            // map over all non data type attributes
            val attrs: Seq[StandoffTagAttributeV1] = standoffNodeFromXML.attributes.filterNot(attr => XMLtoStandoffMapping.dataType.nonEmpty && XMLtoStandoffMapping.dataTypeXMLAttribute.nonEmpty && XMLtoStandoffMapping.dataTypeXMLAttribute.get == attr.key).map {
                attr: StandoffTagAttribute =>
                    // get the standoff property Iri for this XML attribute
                    val standoffTagPropIri = XMLAttributeMapping.getOrElse(attr.key, throw BadRequestException(s"mapping for attr '${attr.key}' not provided"))

                    // check if a cardinality exists for the current attribute
                    if (classSpecificProps.get(standoffTagPropIri).isEmpty) {
                        throw BadRequestException(s"no cardinility defined for attr '${attr.key}'")
                    }

                    // check if the object datatype constraint is respected for the current property
                    val propDataType = standoffPropertyEntities.standoffPropertyEntityInfoMap(standoffTagPropIri).predicates(OntologyConstants.KnoraBase.ObjectDatatypeConstraint)

                    propDataType.objects.headOption match {
                        case Some(OntologyConstants.Xsd.String) =>
                            StandoffTagStringAttributeV1(standoffPropertyIri = standoffTagPropIri, value = InputValidation.toSparqlEncodedString(attr.value, () => throw BadRequestException(s"Invalid string attribute: '${attr.value}'")))

                        case Some(OntologyConstants.Xsd.Integer) =>
                            StandoffTagIntegerAttributeV1(standoffPropertyIri = standoffTagPropIri, value = InputValidation.toInt(attr.value, () => throw BadRequestException(s"Invalid integer attribute: '${attr.value}'")))

                        case Some(OntologyConstants.Xsd.Decimal) =>
                            StandoffTagDecimalAttributeV1(standoffPropertyIri = standoffTagPropIri, value = InputValidation.toBigDecimal(attr.value, () => throw BadRequestException(s"Invalid decimal attribute: '${attr.value}'")))

                        case Some(OntologyConstants.Xsd.Boolean) =>
                            StandoffTagBooleanAttributeV1(standoffPropertyIri = standoffTagPropIri, value = InputValidation.toBoolean(attr.value, () => throw BadRequestException(s"Invalid boolean attribute: '${attr.value}'")))

                        case None => throw InconsistentTriplestoreDataException(s"did not find ${OntologyConstants.KnoraBase.ObjectDatatypeConstraint} for $standoffTagPropIri")

                        case other => throw InconsistentTriplestoreDataException(s"triplestore returned unknown ${OntologyConstants.KnoraBase.ObjectDatatypeConstraint} '$other' for $standoffTagPropIri")

                    }

            }.toList

            val attrsGroupedByPropIri: Map[IRI, Seq[StandoffTagAttributeV1]] = attrs.groupBy(attr => attr.standoffPropertyIri)

            // filter all the required props
            val mustExistOnce: Set[IRI] = classSpecificProps.filter {
                case (propIri, card) =>
                    card == Cardinality.MustHaveOne || card == Cardinality.MustHaveSome
            }.keySet

            // check if all the min cardinalities are respected
            mustExistOnce.map {
                propIri =>
                    attrsGroupedByPropIri.get(propIri) match {
                        case Some(attrs: Seq[StandoffTagAttributeV1]) => ()

                        case None => throw BadRequestException(s"the min cardinalities were not respected for $propIri")
                    }
            }

            // filter all the props that have a limited occurrence
            val mayExistOnce = classSpecificProps.filter {
                case (propIri, card) =>
                    card == Cardinality.MustHaveOne || card == Cardinality.MayHaveOne
            }.keySet

            // check if all the max cardinalities are respected
            mayExistOnce.map {
                propIri =>
                    attrsGroupedByPropIri.get(propIri) match {
                        case Some(attrs: Seq[StandoffTagAttributeV1]) =>
                            if (attrs.size > 1) {
                                throw BadRequestException(s"the max cardinalities were not respected for $propIri")
                            }
                        case None => ()
                    }
            }

            attrs

        } else {
            // only system props are required
            Seq.empty[StandoffTagAttributeV1]
        }

    }

    case class MapXMLTagToStandoffClass(standoffClassIri: IRI, attributesToProps: Map[String, IRI] = Map.empty[String, IRI], dataType: Option[dataTypes.Value] = None, dataTypeXMLAttribute: Option[String] = None)

    /**
      * Creates standoff from a given XML.
      *
      * @param xml the xml file sent by the client.
      * @param userProfile the client that made the request.
      * @return a [[CreateStandoffResponseV1]]
      */
    def createStandoff(projectIri: IRI, resourceIri: IRI, propertyIRI: IRI, xml: String, userProfile: UserProfileV1, apiRequestID: UUID): Future[CreateStandoffResponseV1] = {

        val mappingXMLTags2StandoffTags = Map(
            "text" -> MapXMLTagToStandoffClass(standoffClassIri = OntologyConstants.KnoraBase.StandoffRootTag, attributesToProps = Map("documentType" -> "http://www.knora.org/ontology/knora-base#standoffRootTagHasDocumentType")),
            "p" -> MapXMLTagToStandoffClass(standoffClassIri = OntologyConstants.KnoraBase.StandoffParagraphTag),
            "i" -> MapXMLTagToStandoffClass(standoffClassIri = OntologyConstants.KnoraBase.StandoffItalicTag),
            "birthday" -> MapXMLTagToStandoffClass(standoffClassIri = "http://www.knora.org/ontology/knora-base#StandoffBirthdayTag", dataType = Some(dataTypes.date), dataTypeXMLAttribute = Some("date")),
            "interval" -> MapXMLTagToStandoffClass(standoffClassIri = "http://www.knora.org/ontology/knora-base#StandoffRangeTag", dataType = Some(dataTypes.interval), dataTypeXMLAttribute = Some("range"), attributesToProps = Map("unsure" -> "http://www.knora.org/ontology/knora-base#standoffTagIsUnsure"))
        )

        val standoffUtil = new StandoffUtil()

        val textWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(xml)

        // get Iris of standoff classes that should be created
        val standoffTagIris = mappingXMLTags2StandoffTags.values.map(row => row.standoffClassIri).toSet

        for {
            // request information about standoff classes that should be created
            standoffClassEntities: StandoffEntityInfoGetResponseV1 <- (responderManager ? StandoffEntityInfoGetRequestV1(standoffClassIris = standoffTagIris, userProfile = userProfile)).mapTo[StandoffEntityInfoGetResponseV1]

            // get the property Iris that are defined on the standoff classes returned by the ontology responder
            standoffPropertyIris = standoffClassEntities.standoffClassEntityInfoMap.foldLeft(Set.empty[IRI]) {
                case (acc, (standoffClassIri, standoffClassEntity)) =>
                    val props = standoffClassEntity.cardinalities.keySet
                    acc ++ props
            }

            // request information about the standoff properties
            standoffPropertyEntities: StandoffEntityInfoGetResponseV1 <- (responderManager ? StandoffEntityInfoGetRequestV1(standoffPropertyIris = standoffPropertyIris, userProfile = userProfile)).mapTo[StandoffEntityInfoGetResponseV1]

            // loop over the standoff nodes returned by the StandoffUtil and map them to type safe case classes
            standoffNodesToCreate: Seq[StandoffTagV1] = textWithStandoff.standoff.map {
                case (standoffNodeFromXML: StandoffTag) =>

                    val standoffDefFromMapping: MapXMLTagToStandoffClass = mappingXMLTags2StandoffTags.getOrElse(standoffNodeFromXML.tagName, throw BadRequestException(s"the standoff class for the tag '${standoffNodeFromXML.tagName}' could not be found in the provided mapping"))

                    val standoffClassIri: IRI = standoffDefFromMapping.standoffClassIri

                    // get the cardinalities of the current standoff class
                    val cardinalities: Map[IRI, Cardinality.Value] = standoffClassEntities.standoffClassEntityInfoMap.getOrElse(standoffClassIri, throw NotFoundException(s"information about standoff class $standoffClassIri was not found in ontology")).cardinalities

                    // create a standoff base tag with the information available from standoff util
                    val standoffBaseTagV1: StandoffBaseTagV1 = standoffNodeFromXML match {
                        case hierarchicalStandoffTag: HierarchicalStandoffTag =>
                            StandoffBaseTagV1(
                                name = standoffClassIri,
                                startPosition = hierarchicalStandoffTag.startPosition,
                                endPosition = hierarchicalStandoffTag.endPosition,
                                uuid = hierarchicalStandoffTag.uuid,
                                startIndex = hierarchicalStandoffTag.index,
                                endIndex = None,
                                startParentIndex = hierarchicalStandoffTag.parentIndex,
                                endParentIndex = None,
                                attributes = Seq.empty[StandoffTagAttributeV1]
                            )
                        case freeStandoffTag: FreeStandoffTag =>
                            StandoffBaseTagV1(
                                name = standoffClassIri,
                                startPosition = freeStandoffTag.startPosition,
                                endPosition = freeStandoffTag.endPosition,
                                uuid = freeStandoffTag.uuid,
                                startIndex = freeStandoffTag.startIndex,
                                endIndex = Some(freeStandoffTag.endIndex),
                                startParentIndex = freeStandoffTag.startParentIndex,
                                endParentIndex = freeStandoffTag.endParentIndex,
                                attributes = Seq.empty[StandoffTagAttributeV1]
                            )
                    }

                    // check the data type of the given standoff class
                    standoffClassEntities.standoffClassEntityInfoMap(standoffClassIri).dataType match {

                        case Some(OntologyConstants.KnoraBase.StandoffLinkTag) =>

                            val linkString: String = getDataTypeAttribute(standoffDefFromMapping, dataTypes.link, standoffNodeFromXML)

                            val classSpecificProps = cardinalities -- systemStandoffProperties -- Set(OntologyConstants.KnoraBase.StandoffTagHasLink)

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            StandoffLinkTagV1(
                                name = standoffBaseTagV1.name,
                                startPosition = standoffBaseTagV1.startPosition,
                                endPosition = standoffBaseTagV1.endPosition,
                                uuid = standoffBaseTagV1.uuid,
                                startIndex = standoffBaseTagV1.startIndex,
                                endIndex = standoffBaseTagV1.endIndex,
                                startParentIndex = standoffBaseTagV1.startParentIndex,
                                endParentIndex = standoffBaseTagV1.endParentIndex,
                                attributes = attributesV1,
                                standoffTagHasLink = InputValidation.toIri(linkString, () =>  throw BadRequestException(s"Iri invalid: $linkString"))
                            )

                        case Some(OntologyConstants.KnoraBase.StandoffColorTag) =>

                            val colorString: String = getDataTypeAttribute(standoffDefFromMapping, dataTypes.color, standoffNodeFromXML)

                            val classSpecificProps = cardinalities -- systemStandoffProperties -- Set(OntologyConstants.KnoraBase.ValueHasColor)

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            StandoffColorTagV1(
                                name = standoffBaseTagV1.name,
                                startPosition = standoffBaseTagV1.startPosition,
                                endPosition = standoffBaseTagV1.endPosition,
                                uuid = standoffBaseTagV1.uuid,
                                startIndex = standoffBaseTagV1.startIndex,
                                endIndex = standoffBaseTagV1.endIndex,
                                startParentIndex = standoffBaseTagV1.startParentIndex,
                                endParentIndex = standoffBaseTagV1.endParentIndex,
                                attributes = attributesV1,
                                valueHasColor = InputValidation.toColor(colorString, () => throw BadRequestException(s"Color invalid: $colorString"))
                            )

                        case Some(OntologyConstants.KnoraBase.StandoffUriTag) =>

                            val uriString: String = getDataTypeAttribute(standoffDefFromMapping, dataTypes.uri, standoffNodeFromXML)

                            val classSpecificProps = cardinalities -- systemStandoffProperties -- Set(OntologyConstants.KnoraBase.ValueHasUri)

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            StandoffUriTagV1(
                                name = standoffBaseTagV1.name,
                                startPosition = standoffBaseTagV1.startPosition,
                                endPosition = standoffBaseTagV1.endPosition,
                                uuid = standoffBaseTagV1.uuid,
                                startIndex = standoffBaseTagV1.startIndex,
                                endIndex = standoffBaseTagV1.endIndex,
                                startParentIndex = standoffBaseTagV1.startParentIndex,
                                endParentIndex = standoffBaseTagV1.endParentIndex,
                                attributes = attributesV1,
                                valueHasUri = InputValidation.toIri(uriString, () =>  throw BadRequestException(s"Iri invalid: $uriString"))
                            )



                        case Some(OntologyConstants.KnoraBase.StandoffIntegerTag) =>

                            val integerString: String = getDataTypeAttribute(standoffDefFromMapping, dataTypes.integer, standoffNodeFromXML)

                            val classSpecificProps = cardinalities -- systemStandoffProperties -- Set(OntologyConstants.KnoraBase.ValueHasInteger)

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            StandoffIntegerTagV1(
                                name = standoffBaseTagV1.name,
                                startPosition = standoffBaseTagV1.startPosition,
                                endPosition = standoffBaseTagV1.endPosition,
                                uuid = standoffBaseTagV1.uuid,
                                startIndex = standoffBaseTagV1.startIndex,
                                endIndex = standoffBaseTagV1.endIndex,
                                startParentIndex = standoffBaseTagV1.startParentIndex,
                                endParentIndex = standoffBaseTagV1.endParentIndex,
                                attributes = attributesV1,
                                valueHasInteger = InputValidation.toInt(integerString, () => throw BadRequestException(s"Integer value invalid: $integerString"))
                            )

                        case Some(OntologyConstants.KnoraBase.StandoffDecimalTag) =>

                            val decimalString: String = getDataTypeAttribute(standoffDefFromMapping, dataTypes.decimal, standoffNodeFromXML)

                            val classSpecificProps = cardinalities -- systemStandoffProperties -- Set(OntologyConstants.KnoraBase.ValueHasDecimal)

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            StandoffIntegerTagV1(
                                name = standoffBaseTagV1.name,
                                startPosition = standoffBaseTagV1.startPosition,
                                endPosition = standoffBaseTagV1.endPosition,
                                uuid = standoffBaseTagV1.uuid,
                                startIndex = standoffBaseTagV1.startIndex,
                                endIndex = standoffBaseTagV1.endIndex,
                                startParentIndex = standoffBaseTagV1.startParentIndex,
                                endParentIndex = standoffBaseTagV1.endParentIndex,
                                attributes = attributesV1,
                                valueHasInteger = InputValidation.toInt(decimalString, () => throw BadRequestException(s"Integer value invalid: $decimalString"))
                            )

                        case Some(OntologyConstants.KnoraBase.StandoffBooleanTag) =>

                            val booleanString: String = getDataTypeAttribute(standoffDefFromMapping, dataTypes.boolean, standoffNodeFromXML)

                            val classSpecificProps = cardinalities -- systemStandoffProperties -- Set(OntologyConstants.KnoraBase.ValueHasBoolean)

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            StandoffBooleanTagV1(
                                name = standoffBaseTagV1.name,
                                startPosition = standoffBaseTagV1.startPosition,
                                endPosition = standoffBaseTagV1.endPosition,
                                uuid = standoffBaseTagV1.uuid,
                                startIndex = standoffBaseTagV1.startIndex,
                                endIndex = standoffBaseTagV1.endIndex,
                                startParentIndex = standoffBaseTagV1.startParentIndex,
                                endParentIndex = standoffBaseTagV1.endParentIndex,
                                attributes = attributesV1,
                                valueHasBoolean = InputValidation.toBoolean(booleanString, () => throw BadRequestException(s"Integer value invalid: $booleanString"))
                            )

                        case Some(OntologyConstants.KnoraBase.StandoffIntervalTag) =>

                            val intervalString: String = getDataTypeAttribute(standoffDefFromMapping, dataTypes.interval, standoffNodeFromXML)

                            // interval String contains two decimals separated by a comma
                            val interval: Array[String] = intervalString.split(",")
                            if (interval.size != 2) {
                                throw BadRequestException(s"interval string $intervalString is invalid, it should contain two decimals separated by a comma")
                            }

                            val start: String = interval(0)

                            val classSpecificProps = cardinalities -- systemStandoffProperties -- Set(OntologyConstants.KnoraBase.ValueHasIntervalStart, OntologyConstants.KnoraBase.ValueHasIntervalEnd)

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            StandoffIntervalTagV1(
                                name = standoffBaseTagV1.name,
                                startPosition = standoffBaseTagV1.startPosition,
                                endPosition = standoffBaseTagV1.endPosition,
                                uuid = standoffBaseTagV1.uuid,
                                startIndex = standoffBaseTagV1.startIndex,
                                endIndex = standoffBaseTagV1.endIndex,
                                startParentIndex = standoffBaseTagV1.startParentIndex,
                                endParentIndex = standoffBaseTagV1.endParentIndex,
                                attributes = attributesV1,
                                valueHasIntervalStart = InputValidation.toBigDecimal(interval(0), () => throw BadRequestException(s"Decimal value invalid: ${interval(0)}")),
                                valueHasIntervalEnd = InputValidation.toBigDecimal(interval(1), () => throw BadRequestException(s"Decimal value invalid: ${interval(1)}"))
                            )

                        case Some(OntologyConstants.KnoraBase.StandoffDateTag) =>

                            val dateString: String = getDataTypeAttribute(standoffDefFromMapping, dataTypes.date, standoffNodeFromXML)

                            val dateValue = DateUtilV1.createJDCValueV1FromDateString(dateString)

                            val classSpecificProps = cardinalities -- systemStandoffProperties -- Set(OntologyConstants.KnoraBase.ValueHasCalendar, OntologyConstants.KnoraBase.ValueHasStartJDC, OntologyConstants.KnoraBase.ValueHasEndJDC, OntologyConstants.KnoraBase.ValueHasStartPrecision, OntologyConstants.KnoraBase.ValueHasEndPrecision)

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            StandoffDateTagV1(
                                name = standoffBaseTagV1.name,
                                startPosition = standoffBaseTagV1.startPosition,
                                endPosition = standoffBaseTagV1.endPosition,
                                uuid = standoffBaseTagV1.uuid,
                                startIndex = standoffBaseTagV1.startIndex,
                                endIndex = standoffBaseTagV1.endIndex,
                                startParentIndex = standoffBaseTagV1.startParentIndex,
                                endParentIndex = standoffBaseTagV1.endParentIndex,
                                valueHasCalendar = dateValue.calendar,
                                valueHasStartJDC = dateValue.dateval1,
                                valueHasEndJDC = dateValue.dateval2,
                                valueHasStartPrecision = dateValue.dateprecision1,
                                valueHasEndPrecision = dateValue.dateprecision2,
                                attributes = attributesV1
                            )

                        case None =>

                            // ignore the system properties since they are provided by StandoffUtil
                            val classSpecificProps: Map[IRI, Cardinality.Value] = cardinalities -- systemStandoffProperties

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            standoffBaseTagV1.copy(
                                attributes = attributesV1
                            )


                        case unknownDataType => throw InconsistentTriplestoreDataException(s"the triplestore returned the data type $unknownDataType for $standoffClassIri that could be handled")

                    }




            }

            // _ = println(ScalaPrettyPrinter.prettyPrint(standoffNodesToCreate))

            createValueResponse: CreateValueResponseV1 <- (responderManager ? CreateValueRequestV1(projectIri = projectIri, resourceIri = resourceIri, propertyIri = propertyIRI, value = TextValueV1(utf8str = "gaga"), userProfile = userProfile, apiRequestID = apiRequestID)).mapTo[CreateValueResponseV1]

            // TODO: send the list of standoff case classes and the text to value responder in order to create the triples

        } yield CreateStandoffResponseV1(userdata = userProfile.userData)

    }

}