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

package org.knora.webapi.util.standoff

import java.util.UUID

import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages.{Cardinality, StandoffEntityInfoGetResponseV1, StandoffPropertyEntityInfoV1}
import org.knora.webapi.messages.v1.responder.standoffmessages._
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.twirl._
import org.knora.webapi.util.{DateUtilV1, InputValidation}

object StandoffTagUtilV1 {

    // string constant used to mark the absence of an XML namespace in the mapping definitioon of an XML element
    private val noNamespace = "noNamespace"

    // string constant used to mark the absence of a classname in the mapping definition of an XML element
    private val noClass = "noClass"

    /**
      * Tries to find a data type attribute in the XML attributes of a given standoff node. Throws an appropriate error if information is inconsistent or missing.
      *
      * @param XMLtoStandoffMapping the mapping from XML to standoff classes and properties for the given standoff node.
      * @param dataType             the expected data type of the given standoff node.
      * @param standoffNodeFromXML  the given standoff node.
      * @return the value of the attribute.
      */
    private def getDataTypeAttribute(XMLtoStandoffMapping: XMLTagToStandoffClass, dataType: StandoffDataTypeClasses.Value, standoffNodeFromXML: StandoffTag): String = {

        if (XMLtoStandoffMapping.dataType.isEmpty || XMLtoStandoffMapping.dataType.get.standoffDataTypeClass != dataType) {
            throw BadRequestException(s"no or wrong data type definition provided in mapping for standoff class ${XMLtoStandoffMapping.standoffClassIri}")
        }

        val attrName = XMLtoStandoffMapping.dataType.get.dataTypeXMLAttribute

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
      * @param XMLtoStandoffMapping     the mapping from XML to standoff classes and properties for the given standoff node.
      * @param classSpecificProps       the properties that may or have to be created (cardinalities) for the given standoff node.
      * @param standoffNodeFromXML      the given standoff node.
      * @param standoffPropertyEntities the ontology information about the standoff properties.
      * @return a sequence of [[StandoffTagAttributeV1]]
      */
    private def createAttributes(XMLtoStandoffMapping: XMLTagToStandoffClass, classSpecificProps: Map[IRI, Cardinality.Value], standoffNodeFromXML: StandoffTag, IDsToUUIDs: Map[IRI, UUID], standoffPropertyEntities: Map[IRI, StandoffPropertyEntityInfoV1]): Seq[StandoffTagAttributeV1] = {

        // assumes that internal references start with a "#"
        def getTargetIDFromInternalReference(internalReference: String) = {
            if (internalReference.charAt(0) != '#') throw BadRequestException(s"invalid internal reference: $internalReference")

            internalReference.substring(1)
        }

        if (classSpecificProps.nonEmpty) {
            // additional standoff properties are required

            // map over all non data type attributes, ignore the "class" attribute ("class" is only used in the mapping to allow for the reuse of the same tag name, not to store actual data).
            val attrs: Seq[StandoffTagAttributeV1] = standoffNodeFromXML.attributes.filterNot(attr => (XMLtoStandoffMapping.dataType.nonEmpty && XMLtoStandoffMapping.dataType.get.dataTypeXMLAttribute == attr.key) || attr.key == "class").map {
                attr: StandoffTagAttribute =>
                    // get the standoff property Iri for this XML attribute

                    val xmlNamespace = attr.xmlNamespace match {
                        case None => noNamespace
                        case Some(namespace) => namespace
                    }

                    val standoffTagPropIri = XMLtoStandoffMapping.attributesToProps.getOrElse(xmlNamespace, throw BadRequestException(s"namespace $xmlNamespace unknown for attribute ${attr.key} in mapping")).getOrElse(attr.key, throw BadRequestException(s"mapping for attr '${attr.key}' not provided"))

                    // check if a cardinality exists for the current attribute
                    if (classSpecificProps.get(standoffTagPropIri).isEmpty) {
                        throw BadRequestException(s"no cardinality defined for attr '${attr.key}'")
                    }

                    if (standoffPropertyEntities(standoffTagPropIri).predicates.get(OntologyConstants.KnoraBase.ObjectDatatypeConstraint).isDefined) {
                        // property is a datatype property

                        val propDatatypeConstraint = standoffPropertyEntities(standoffTagPropIri).predicates(OntologyConstants.KnoraBase.ObjectDatatypeConstraint)

                        propDatatypeConstraint.objects.headOption match {
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
                    } else if (standoffPropertyEntities(standoffTagPropIri).predicates.get(OntologyConstants.KnoraBase.ObjectClassConstraint).isDefined) {

                        // property is an object property

                        // we expect a property of type http://www.knora.org/ontology/knora-base#standoffTagHasInternalReference
                        if (!standoffPropertyEntities(standoffTagPropIri).isSubPropertyOf.contains(OntologyConstants.KnoraBase.StandoffTagHasInternalReference)) {
                            throw BadRequestException(s"wrong type given for ${standoffTagPropIri}: a standoff object property is expected to be a subproperty of ${OntologyConstants.KnoraBase.StandoffTagHasInternalReference}")
                        }

                        StandoffTagInternalReferenceAttributeV1(standoffPropertyIri = standoffTagPropIri, value = IDsToUUIDs.getOrElse(getTargetIDFromInternalReference(attr.value), throw BadRequestException(s"internal reference is invalid: ${attr.value}")))

                    } else {
                        throw InconsistentTriplestoreDataException(s"no ${OntologyConstants.KnoraBase.ObjectDatatypeConstraint} or ${OntologyConstants.KnoraBase.ObjectClassConstraint} given for property '$standoffTagPropIri'")
                    }


            }.toList

            val attrsGroupedByPropIri: Map[IRI, Seq[StandoffTagAttributeV1]] = attrs.groupBy(attr => attr.standoffPropertyIri)

            // filter all the required props
            val mustExistOnce: Set[IRI] = classSpecificProps.filter {
                case (propIri, card) =>
                    card == Cardinality.MustHaveOne || card == Cardinality.MustHaveSome
            }.keySet

            // check if all the min cardinalities are respected
            mustExistOnce.foreach {
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
            mayExistOnce.foreach {
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

            // TODO: check if there are superfluous attributes defined and throw an error if so

            Seq.empty[StandoffTagAttributeV1]
        }

    }

    /**
      * Represents a text with standoff markup including the mapping.
      *
      * @param text          the text as a mere sequence of characters.
      * @param standoffTagV1 the text's standoff markup.
      */
    case class TextWithStandoffTagV1(text: String, standoffTagV1: Seq[StandoffTagV1], mapping: GetMappingResponseV1)

    /**
      * Converts XML to a [[TextWithStandoffTagV1]].
      *
      * @param xml     the XML representing text with markup.
      * @param mapping the mapping used to convert XML to standoff.
      * @return a [[TextWithStandoffTagV1]].
      */
    def convertXMLtoStandoffTagV1(xml: String, mapping: GetMappingResponseV1): TextWithStandoffTagV1 = {

        val xmlStandoffUtil = new XMLToStandoffUtil()

        // FIXME: if the XML is not well formed, the error is not handled correctly in XMLToStandoffUtil
        val textWithStandoff: TextWithStandoff = try {
            xmlStandoffUtil.xml2TextWithStandoff(xml)
        } catch {
            case e: org.xml.sax.SAXParseException => throw BadRequestException(s"there was a problem parsing the provided XML: ${e.getMessage}")

            case other: Exception => throw BadRequestException(s"there was a problem processing the provided XML: ${other.getMessage}")
        }

        val standoffTagsV1: Seq[StandoffTagV1] = convertXMLToStandoffUtilStandoffTagToStandoffTagV1(
            textWithStandoff = textWithStandoff,
            mappingXMLtoStandoff = mapping.mapping,
            standoffEntities = mapping.standoffEntities
        )

        TextWithStandoffTagV1(
            text = textWithStandoff.text,
            standoffTagV1 = standoffTagsV1,
            mapping = mapping
        )

    }

    /**
      *
      * Turns a sequence of [[StandoffTag]] returned by [[XMLToStandoffUtil.xml2TextWithStandoff]] into a sequence of [[StandoffTagV1]].
      * This method handles the creation of data type specific properties (e.g. for a date value) on the basis of the provided mapping.
      *
      * @param textWithStandoff     sequence of [[StandoffTag]] returned by [[XMLToStandoffUtil.xml2TextWithStandoff]].
      * @param mappingXMLtoStandoff the mapping to be used.
      * @param standoffEntities     the standoff entities (classes and properties) to be used.
      * @return a sequence of [[StandoffTagV1]].
      */
    private def convertXMLToStandoffUtilStandoffTagToStandoffTagV1(textWithStandoff: TextWithStandoff, mappingXMLtoStandoff: MappingXMLtoStandoff, standoffEntities: StandoffEntityInfoGetResponseV1): Seq[StandoffTagV1] = {

        textWithStandoff.standoff.map {
            case (standoffNodeFromXML: StandoffTag) =>

                val xmlNamespace = standoffNodeFromXML.xmlNamespace match {
                    case None => noNamespace
                    case Some(namespace) => namespace
                }

                val classname: String = standoffNodeFromXML.attributes.find(_.key == "class") match {
                    case None => noClass
                    case Some(classAttribute: StandoffTagAttribute) => classAttribute.value
                }

                // get the mapping corresponding to the given namespace and tagname
                val standoffDefFromMapping = mappingXMLtoStandoff.namespace
                    .getOrElse(xmlNamespace, throw BadRequestException(s"namespace ${xmlNamespace} not defined in mapping"))
                    .getOrElse(standoffNodeFromXML.tagName, throw BadRequestException(s"the standoff class for the tag '${standoffNodeFromXML.tagName}' could not be found in the provided mapping"))
                    .getOrElse(classname, throw BadRequestException(s"the standoff class for the classname $classname in combination with the tag '${standoffNodeFromXML.tagName}' could not be found in the provided mapping")).mapping

                val standoffClassIri: IRI = standoffDefFromMapping.standoffClassIri

                // get the cardinalities of the current standoff class
                val cardinalities: Map[IRI, Cardinality.Value] = standoffEntities.standoffClassEntityInfoMap.getOrElse(standoffClassIri, throw NotFoundException(s"information about standoff class $standoffClassIri was not found in ontology")).cardinalities

                // TODO: resolve thhese references when the IRIs for the standoff nodes have been created
                val IDsToUUIDs: Map[IRI, UUID] = textWithStandoff.standoff.filter((standoffTag: StandoffTag) => standoffTag.originalID.isDefined).map {
                    standoffTagWithID =>
                        (standoffTagWithID.originalID.get, standoffTagWithID.uuid)
                }.toMap

                // create a standoff base tag with the information available from standoff util
                val standoffBaseTagV1: StandoffTagV1 = standoffNodeFromXML match {
                    case hierarchicalStandoffTag: HierarchicalStandoffTag =>
                        StandoffTagV1(
                            standoffTagClassIri = standoffClassIri,
                            startPosition = hierarchicalStandoffTag.startPosition,
                            endPosition = hierarchicalStandoffTag.endPosition,
                            uuid = hierarchicalStandoffTag.uuid.toString,
                            originalXMLID = hierarchicalStandoffTag.originalID match {
                                case Some(id: String) => Some(InputValidation.toSparqlEncodedString(id, () => throw BadRequestException(s"XML id $id cannot be converted to a Sparql conform string")))
                                case None => None
                            },
                            startIndex = hierarchicalStandoffTag.index,
                            endIndex = None,
                            startParentIndex = hierarchicalStandoffTag.parentIndex,
                            endParentIndex = None,
                            attributes = Seq.empty[StandoffTagAttributeV1]
                        )
                    case freeStandoffTag: FreeStandoffTag =>
                        StandoffTagV1(
                            standoffTagClassIri = standoffClassIri,
                            startPosition = freeStandoffTag.startPosition,
                            endPosition = freeStandoffTag.endPosition,
                            uuid = freeStandoffTag.uuid.toString,
                            originalXMLID = freeStandoffTag.originalID match {
                                case Some(id: String) => Some(InputValidation.toSparqlEncodedString(id, () => throw BadRequestException(s"XML id $id cannot be converted to a Sparql conform string")))
                                case None => None
                            },
                            startIndex = freeStandoffTag.startIndex,
                            endIndex = Some(freeStandoffTag.endIndex),
                            startParentIndex = freeStandoffTag.startParentIndex,
                            endParentIndex = freeStandoffTag.endParentIndex,
                            attributes = Seq.empty[StandoffTagAttributeV1]
                        )

                    case _ => throw InvalidStandoffException("StandoffUtil did neither return a HierarchicalStandoff tag nor a FreeStandoffTag")
                }

                // check the data type of the given standoff class
                standoffEntities.standoffClassEntityInfoMap(standoffClassIri).dataType match {

                    case Some(StandoffDataTypeClasses.StandoffLinkTag) =>

                        val linkString: String = getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffLinkTag, standoffNodeFromXML)

                        val internalLink: StandoffTagAttributeV1 = StandoffTagIriAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink, value = InputValidation.toIri(linkString, () => throw BadRequestException(s"Iri invalid: $linkString")))

                        val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.linkProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, IDsToUUIDs, standoffEntities.standoffPropertyEntityInfoMap)

                        StandoffTagV1(
                            dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
                            standoffTagClassIri = standoffBaseTagV1.standoffTagClassIri,
                            startPosition = standoffBaseTagV1.startPosition,
                            endPosition = standoffBaseTagV1.endPosition,
                            uuid = standoffBaseTagV1.uuid,
                            originalXMLID = standoffBaseTagV1.originalXMLID,
                            startIndex = standoffBaseTagV1.startIndex,
                            endIndex = standoffBaseTagV1.endIndex,
                            startParentIndex = standoffBaseTagV1.startParentIndex,
                            endParentIndex = standoffBaseTagV1.endParentIndex,
                            attributes = attributesV1 :+ internalLink
                        )

                    case Some(StandoffDataTypeClasses.StandoffColorTag) =>

                        val colorString: String = getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffColorTag, standoffNodeFromXML)

                        val colorValue = StandoffTagStringAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasColor, value = InputValidation.toColor(colorString, () => throw BadRequestException(s"Color invalid: $colorString")))

                        val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.colorProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, IDsToUUIDs, standoffEntities.standoffPropertyEntityInfoMap)

                        StandoffTagV1(
                            dataType = Some(StandoffDataTypeClasses.StandoffColorTag),
                            standoffTagClassIri = standoffBaseTagV1.standoffTagClassIri,
                            startPosition = standoffBaseTagV1.startPosition,
                            endPosition = standoffBaseTagV1.endPosition,
                            uuid = standoffBaseTagV1.uuid,
                            originalXMLID = standoffBaseTagV1.originalXMLID,
                            startIndex = standoffBaseTagV1.startIndex,
                            endIndex = standoffBaseTagV1.endIndex,
                            startParentIndex = standoffBaseTagV1.startParentIndex,
                            endParentIndex = standoffBaseTagV1.endParentIndex,
                            attributes = attributesV1 :+ colorValue
                        )

                    case Some(StandoffDataTypeClasses.StandoffUriTag) =>

                        val uriString: String = getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffUriTag, standoffNodeFromXML)

                        val uriValue = StandoffTagIriAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasUri, value = InputValidation.toIri(uriString, () => throw BadRequestException(s"Iri invalid: $uriString")))

                        val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.uriProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, IDsToUUIDs, standoffEntities.standoffPropertyEntityInfoMap)

                        StandoffTagV1(
                            dataType = Some(StandoffDataTypeClasses.StandoffUriTag),
                            standoffTagClassIri = standoffBaseTagV1.standoffTagClassIri,
                            startPosition = standoffBaseTagV1.startPosition,
                            endPosition = standoffBaseTagV1.endPosition,
                            uuid = standoffBaseTagV1.uuid,
                            originalXMLID = standoffBaseTagV1.originalXMLID,
                            startIndex = standoffBaseTagV1.startIndex,
                            endIndex = standoffBaseTagV1.endIndex,
                            startParentIndex = standoffBaseTagV1.startParentIndex,
                            endParentIndex = standoffBaseTagV1.endParentIndex,
                            attributes = attributesV1 :+ uriValue
                        )


                    case Some(StandoffDataTypeClasses.StandoffIntegerTag) =>

                        val integerString: String = getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffIntegerTag, standoffNodeFromXML)

                        val integerValue = StandoffTagIntegerAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasInteger, value = InputValidation.toInt(integerString, () => throw BadRequestException(s"Integer value invalid: $integerString")))

                        val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.integerProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, IDsToUUIDs, standoffEntities.standoffPropertyEntityInfoMap)

                        StandoffTagV1(
                            dataType = Some(StandoffDataTypeClasses.StandoffIntegerTag),
                            standoffTagClassIri = standoffBaseTagV1.standoffTagClassIri,
                            startPosition = standoffBaseTagV1.startPosition,
                            endPosition = standoffBaseTagV1.endPosition,
                            uuid = standoffBaseTagV1.uuid,
                            originalXMLID = standoffBaseTagV1.originalXMLID,
                            startIndex = standoffBaseTagV1.startIndex,
                            endIndex = standoffBaseTagV1.endIndex,
                            startParentIndex = standoffBaseTagV1.startParentIndex,
                            endParentIndex = standoffBaseTagV1.endParentIndex,
                            attributes = attributesV1 :+ integerValue
                        )

                    case Some(StandoffDataTypeClasses.StandoffDecimalTag) =>

                        val decimalString: String = getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffDecimalTag, standoffNodeFromXML)

                        val decimalValue = StandoffTagDecimalAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasDecimal, value = InputValidation.toBigDecimal(decimalString, () => throw BadRequestException(s"Decimal value invalid: $decimalString")))

                        val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.decimalProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, IDsToUUIDs, standoffEntities.standoffPropertyEntityInfoMap)

                        StandoffTagV1(
                            dataType = Some(StandoffDataTypeClasses.StandoffDecimalTag),
                            standoffTagClassIri = standoffBaseTagV1.standoffTagClassIri,
                            startPosition = standoffBaseTagV1.startPosition,
                            endPosition = standoffBaseTagV1.endPosition,
                            uuid = standoffBaseTagV1.uuid,
                            originalXMLID = standoffBaseTagV1.originalXMLID,
                            startIndex = standoffBaseTagV1.startIndex,
                            endIndex = standoffBaseTagV1.endIndex,
                            startParentIndex = standoffBaseTagV1.startParentIndex,
                            endParentIndex = standoffBaseTagV1.endParentIndex,
                            attributes = attributesV1 :+ decimalValue
                        )

                    case Some(StandoffDataTypeClasses.StandoffBooleanTag) =>

                        val booleanString: String = getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffBooleanTag, standoffNodeFromXML)

                        val booleanValue = StandoffTagBooleanAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasBoolean, value = InputValidation.toBoolean(booleanString, () => throw BadRequestException(s"Boolean value invalid: $booleanString")))

                        val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.booleanProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, IDsToUUIDs, standoffEntities.standoffPropertyEntityInfoMap)

                        StandoffTagV1(
                            dataType = Some(StandoffDataTypeClasses.StandoffBooleanTag),
                            standoffTagClassIri = standoffBaseTagV1.standoffTagClassIri,
                            startPosition = standoffBaseTagV1.startPosition,
                            endPosition = standoffBaseTagV1.endPosition,
                            uuid = standoffBaseTagV1.uuid,
                            originalXMLID = standoffBaseTagV1.originalXMLID,
                            startIndex = standoffBaseTagV1.startIndex,
                            endIndex = standoffBaseTagV1.endIndex,
                            startParentIndex = standoffBaseTagV1.startParentIndex,
                            endParentIndex = standoffBaseTagV1.endParentIndex,
                            attributes = attributesV1 :+ booleanValue
                        )

                    case Some(StandoffDataTypeClasses.StandoffIntervalTag) =>

                        val intervalString: String = getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffIntervalTag, standoffNodeFromXML)

                        // interval String contains two decimals separated by a comma
                        val interval: Array[String] = intervalString.split(",")
                        if (interval.length != 2) {
                            throw BadRequestException(s"interval string $intervalString is invalid, it should contain two decimals separated by a comma")
                        }

                        val intervalStart = StandoffTagDecimalAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasIntervalStart, value = InputValidation.toBigDecimal(interval(0), () => throw BadRequestException(s"Decimal value invalid: ${interval(0)}")))

                        val intervalEnd = StandoffTagDecimalAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasIntervalEnd, value = InputValidation.toBigDecimal(interval(1), () => throw BadRequestException(s"Decimal value invalid: ${interval(1)}")))

                        val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.intervalProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, IDsToUUIDs, standoffEntities.standoffPropertyEntityInfoMap)

                        StandoffTagV1(
                            dataType = Some(StandoffDataTypeClasses.StandoffIntervalTag),
                            standoffTagClassIri = standoffBaseTagV1.standoffTagClassIri,
                            startPosition = standoffBaseTagV1.startPosition,
                            endPosition = standoffBaseTagV1.endPosition,
                            uuid = standoffBaseTagV1.uuid,
                            originalXMLID = standoffBaseTagV1.originalXMLID,
                            startIndex = standoffBaseTagV1.startIndex,
                            endIndex = standoffBaseTagV1.endIndex,
                            startParentIndex = standoffBaseTagV1.startParentIndex,
                            endParentIndex = standoffBaseTagV1.endParentIndex,
                            attributes = attributesV1 ++ List(intervalStart, intervalEnd)
                        )

                    case Some(StandoffDataTypeClasses.StandoffDateTag) =>

                        val dateString: String = getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffDateTag, standoffNodeFromXML)

                        val dateValue = DateUtilV1.createJDNValueV1FromDateString(dateString)

                        val dateCalendar = StandoffTagStringAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasCalendar, value = dateValue.calendar.toString)

                        val dateStart = StandoffTagIntegerAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasStartJDN, value = dateValue.dateval1)

                        val dateEnd = StandoffTagIntegerAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasEndJDN, value = dateValue.dateval2)

                        val dateStartPrecision = StandoffTagStringAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasStartPrecision, value = dateValue.dateprecision1.toString)

                        val dateEndPrecision = StandoffTagStringAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasEndPrecision, value = dateValue.dateprecision2.toString)

                        val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.dateProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, IDsToUUIDs, standoffEntities.standoffPropertyEntityInfoMap)

                        StandoffTagV1(
                            dataType = Some(StandoffDataTypeClasses.StandoffDateTag),
                            standoffTagClassIri = standoffBaseTagV1.standoffTagClassIri,
                            startPosition = standoffBaseTagV1.startPosition,
                            endPosition = standoffBaseTagV1.endPosition,
                            uuid = standoffBaseTagV1.uuid,
                            originalXMLID = standoffBaseTagV1.originalXMLID,
                            startIndex = standoffBaseTagV1.startIndex,
                            endIndex = standoffBaseTagV1.endIndex,
                            startParentIndex = standoffBaseTagV1.startParentIndex,
                            endParentIndex = standoffBaseTagV1.endParentIndex,
                            attributes = attributesV1 ++ List(dateCalendar, dateStart, dateEnd, dateStartPrecision, dateEndPrecision)
                        )

                    case None =>

                        // ignore the system properties since they are provided by StandoffUtil
                        val classSpecificProps: Map[IRI, Cardinality.Value] = cardinalities -- StandoffProperties.systemProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, IDsToUUIDs, standoffEntities.standoffPropertyEntityInfoMap)

                        standoffBaseTagV1.copy(
                            attributes = attributesV1
                        )


                    case unknownDataType => throw InconsistentTriplestoreDataException(s"the triplestore returned the data type $unknownDataType for $standoffClassIri that could be handled")

                }
        }
    }

    // maps a standoff class to an XML tag with attributes
    case class XMLTagItem(namespace: String, tagname: String, classname: String, tagItem: XMLTag, attributes: Map[IRI, XMLAttrItem])

    // maps a standoff property to XML attributes
    case class XMLAttrItem(namespace: String, attrname: String)

    /**
      * Inverts a [[MappingXMLtoStandoff]] and makes standoff class Iris keys.
      * This is makes it easier to map standoff classes back to XML tags (recreating XML from standoff).
      *
      * This method also checks for duplicate usage of standoff classes and properties in the attribute mapping of a tag.
      *
      * @param mappingXMLtoStandoff mapping from XML to standoff.
      * @return a Map standoff class Iris to [[XMLTagItem]].
      */
    def invertXMLToStandoffMapping(mappingXMLtoStandoff: MappingXMLtoStandoff): Map[IRI, XMLTagItem] = {

        // check for duplicate standoff class Iris
        val classIris: Iterable[IRI] = mappingXMLtoStandoff.namespace.values.flatten.flatMap {
            case (tagname: String, tagItem: Map[String, XMLTag]) =>
                tagItem.values.map(_.mapping.standoffClassIri)
        }

        // check for duplicate standoff class Iris
        if (classIris.size != classIris.toSet.size) {
            throw BadRequestException("the same standoff class Iri is used more than once in the mapping")
        }

        mappingXMLtoStandoff.namespace.flatMap {
            case (tagNamespace: String, tagMapping: Map[String, Map[String, XMLTag]]) =>

                tagMapping.flatMap {
                    case (tagname: String, classnameMapping: Map[String, XMLTag]) =>

                        classnameMapping.map {
                            case (classname: String, tagItem: XMLTag) =>

                                // collect all the property Iris defined in the attributes for the current tag
                                // over all namespaces
                                val propIris: Iterable[IRI] = tagItem.mapping.attributesToProps.values.flatten.map {
                                    case (attrName, propIri) =>
                                        propIri
                                }

                                // check for duplicate property Iris
                                if (propIris.size != propIris.toSet.size) {
                                    throw BadRequestException(s"the same property Iri is used more than once for the attributes mapping for tag $tagname")
                                }

                                // inverts the mapping and makes standoff property Iris keys (for attributes)
                                // this is makes it easier to map standoff properties back to XML attributes
                                val attrItems: Map[IRI, XMLAttrItem] = tagItem.mapping.attributesToProps.flatMap {
                                    case (attrNamespace: String, attrMappings: Map[String, IRI]) =>

                                        attrMappings.map {
                                            case (attrName, propIri) =>
                                                propIri -> XMLAttrItem(attrNamespace, attrName)
                                        }
                                }

                                // standoff class Iri -> XMLTagItem(... attributes -> attrItems)
                                tagItem.mapping.standoffClassIri -> XMLTagItem(namespace = tagNamespace, tagname = tagname, classname = classname, tagItem = tagItem, attributes = attrItems)
                        }

                }

        }

    }

    // converts a sequence of `StandoffTagAttributeV1` to a sequence of `StandoffTagAttribute`
    private def convertStandoffAttributeTags(mapping: Map[IRI, XMLAttrItem], attributes: Seq[StandoffTagAttributeV1]): Seq[StandoffTagAttribute] = {
        attributes.map {
            attr =>

                val attrItem: XMLAttrItem = mapping.getOrElse(attr.standoffPropertyIri, throw NotFoundException(s"property Iri ${attr.standoffPropertyIri} could not be found in mapping"))

                StandoffTagAttribute(
                    key = attrItem.attrname,
                    xmlNamespace = attrItem.namespace match {
                        case `noNamespace` => None
                        case namespace => Some(namespace)
                    },
                    value = attr.stringValue()
                )
        }
    }

    def convertStandoffTagV1ToXML(utf8str: String, standoff: Seq[StandoffTagV1], mappingXMLtoStandoff: MappingXMLtoStandoff): String = {

        // inverts the mapping and makes standoff class Iris keys (for tags)
        val mappingStandoffToXML: Map[IRI, XMLTagItem] = invertXMLToStandoffMapping(mappingXMLtoStandoff)

        val standoffUtil = new XMLToStandoffUtil(writeUuidsToXml = false)

        val standoffTags: Seq[StandoffTag] = standoff.map {
            (standoffTagV1: StandoffTagV1) =>

                val xmlItemForStandoffClass: XMLTagItem = mappingStandoffToXML.getOrElse(standoffTagV1.standoffTagClassIri, throw NotFoundException(s"standoff class Iri ${standoffTagV1.standoffTagClassIri} not found in mapping"))

                // recreate data type specific attributes (optional)
                val attributes: Seq[StandoffTagAttribute] = standoffTagV1.dataType match {

                    case Some(StandoffDataTypeClasses.StandoffLinkTag) =>
                        val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}")).dataTypeXMLAttribute

                        val linkIri = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.StandoffTagHasLink).get.stringValue()

                        val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.linkProperties.contains(attr.standoffPropertyIri))

                        convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = linkIri, xmlNamespace = None)

                    case Some(StandoffDataTypeClasses.StandoffColorTag) =>
                        val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}")).dataTypeXMLAttribute

                        val colorString = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasColor).get.stringValue()

                        val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.colorProperties.contains(attr.standoffPropertyIri))

                        convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = colorString, xmlNamespace = None)

                    case Some(StandoffDataTypeClasses.StandoffUriTag) =>
                        val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}")).dataTypeXMLAttribute

                        val uriRef = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasUri).get.stringValue()

                        val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.uriProperties.contains(attr.standoffPropertyIri))

                        convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = uriRef, xmlNamespace = None)

                    case Some(StandoffDataTypeClasses.StandoffIntegerTag) =>
                        val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}")).dataTypeXMLAttribute

                        val integerString = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasInteger).get.stringValue()

                        val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.integerProperties.contains(attr.standoffPropertyIri))

                        convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = integerString, xmlNamespace = None)

                    case Some(StandoffDataTypeClasses.StandoffDecimalTag) =>
                        val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}")).dataTypeXMLAttribute

                        val decimalString = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasDecimal).get.stringValue()

                        val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.decimalProperties.contains(attr.standoffPropertyIri))

                        convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = decimalString, xmlNamespace = None)

                    case Some(StandoffDataTypeClasses.StandoffBooleanTag) =>
                        val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}")).dataTypeXMLAttribute

                        val booleanString = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasBoolean).get.stringValue()

                        val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.booleanProperties.contains(attr.standoffPropertyIri))

                        convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = booleanString, xmlNamespace = None)

                    case Some(StandoffDataTypeClasses.StandoffIntervalTag) =>
                        val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}")).dataTypeXMLAttribute

                        val intervalString = Vector(standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasIntervalStart).get.stringValue(), standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasIntervalEnd).get.stringValue()).mkString(",")

                        val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.intervalProperties.contains(attr.standoffPropertyIri))

                        convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = intervalString, xmlNamespace = None)

                    case Some(StandoffDataTypeClasses.StandoffDateTag) =>
                        // create one attribute from date properties
                        val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}")).dataTypeXMLAttribute

                        val calendar = KnoraCalendarV1.lookup(standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasCalendar).get.stringValue())

                        val julianDayCountValueV1: UpdateValueV1 = JulianDayNumberValueV1(
                            dateval1 = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasStartJDN).get.stringValue().toInt,
                            dateval2 = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasEndJDN).get.stringValue().toInt,
                            dateprecision1 = KnoraPrecisionV1.lookup(standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasStartPrecision).get.stringValue()),
                            dateprecision2 = KnoraPrecisionV1.lookup(standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasEndPrecision).get.stringValue()),
                            calendar = calendar
                        )

                        val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.dateProperties.contains(attr.standoffPropertyIri))

                        convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = Vector(calendar.toString, julianDayCountValueV1.toString).mkString(":"), xmlNamespace = None)

                    case None => convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, standoffTagV1.attributes)

                    case unknownDataType => throw InconsistentTriplestoreDataException(s"the triplestore returned an unknown data type for ${standoffTagV1.standoffTagClassIri} that could not be handled")
                }


                // check in mapping if the XML element has an attribute class to be recreated
                val classAttributeFromMapping = xmlItemForStandoffClass.classname match {
                    case `noClass` => Seq.empty[StandoffTagAttribute]
                    case classname => Vector(
                        StandoffTagAttribute(
                            key = "class",
                            value = classname,
                            xmlNamespace = None
                        )
                    )
                }

                val attributesWithClass = attributes ++ classAttributeFromMapping

                if (standoffTagV1.endIndex.isDefined) {
                    // it is a free standoff tag
                    FreeStandoffTag(
                        originalID = standoffTagV1.originalXMLID,
                        tagName = xmlItemForStandoffClass.tagname,
                        xmlNamespace = xmlItemForStandoffClass.namespace match {
                            case `noNamespace` => None
                            case namespace => Some(namespace)
                        },
                        uuid = UUID.fromString(standoffTagV1.uuid),
                        startPosition = standoffTagV1.startPosition,
                        endPosition = standoffTagV1.endPosition,
                        startIndex = standoffTagV1.startIndex,
                        endIndex = standoffTagV1.endIndex.getOrElse(throw InconsistentTriplestoreDataException(s"end index is missing for a free standoff tag")),
                        startParentIndex = standoffTagV1.startParentIndex,
                        endParentIndex = standoffTagV1.endParentIndex,
                        attributes = attributesWithClass.toSet
                    )
                } else {
                    // it is a hierarchical standoff tag
                    HierarchicalStandoffTag(
                        originalID = standoffTagV1.originalXMLID,
                        tagName = xmlItemForStandoffClass.tagname,
                        xmlNamespace = xmlItemForStandoffClass.namespace match {
                            case `noNamespace` => None
                            case namespace => Some(namespace)
                        },
                        uuid = UUID.fromString(standoffTagV1.uuid),
                        startPosition = standoffTagV1.startPosition,
                        endPosition = standoffTagV1.endPosition,
                        index = standoffTagV1.startIndex,
                        parentIndex = standoffTagV1.startParentIndex,
                        attributes = attributesWithClass.toSet
                    )
                }

        }


        // TODO: Does the string need to be unescaped?
        val textWithStandoff = TextWithStandoff(text = utf8str, standoff = standoffTags)

        val xml = standoffUtil.textWithStandoff2Xml(textWithStandoff)

        xml

    }

}