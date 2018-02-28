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

package org.knora.webapi.util.standoff

import java.util.UUID

import akka.event.LoggingAdapter
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages.{PropertyInfoV1, StandoffEntityInfoGetResponseV1}
import org.knora.webapi.messages.v1.responder.standoffmessages._
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo
import org.knora.webapi.twirl._
import org.knora.webapi.util.{DateUtilV1, StringFormatter}

object StandoffTagUtilV1 {

    // string constant used to mark the absence of an XML namespace in the mapping definitioon of an XML element
    private val noNamespace = "noNamespace"

    // string constant used to mark the absence of a classname in the mapping definition of an XML element
    private val noClass = "noClass"

    // name of class attribute (used to combine elements and classes in mappings)
    private val classAttribute = "class"

    // an internal Link in an XML document begins with this character
    val internalLinkMarker = '#'

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
      * @param xmlToStandoffMapping     the mapping from XML to standoff classes and properties for the given standoff node.
      * @param classSpecificProps       the properties that may or have to be created (cardinalities) for the given standoff node.
      * @param standoffNodeFromXML      the given standoff node.
      * @param standoffPropertyEntities the ontology information about the standoff properties.
      * @return a sequence of [[StandoffTagAttributeV1]]
      */
    private def createAttributes(xmlToStandoffMapping: XMLTagToStandoffClass, classSpecificProps: Map[IRI, KnoraCardinalityInfo], existingXMLIDs: Seq[String], standoffNodeFromXML: StandoffTag, standoffPropertyEntities: Map[IRI, PropertyInfoV1]): Seq[StandoffTagAttributeV1] = {
        val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        if (classSpecificProps.nonEmpty) {
            // this standoff class requires additional standoff properties to the standoff data type properties (contained in `StandoffProperties.dataTypeProperties`).

            // map over all non data type attributes, ignore the "class" attribute ("class" is only used in the mapping to allow for the reuse of the same tag name, not to store actual data).
            val attrs: Seq[StandoffTagAttributeV1] = standoffNodeFromXML.attributes.filterNot(attr => (xmlToStandoffMapping.dataType.nonEmpty && xmlToStandoffMapping.dataType.get.dataTypeXMLAttribute == attr.key) || attr.key == classAttribute).map {
                attr: StandoffTagAttribute =>
                    // get the standoff property IRI for this XML attribute

                    val xmlNamespace = attr.xmlNamespace match {
                        case None => noNamespace
                        case Some(namespace) => namespace
                    }

                    val standoffTagPropIri = xmlToStandoffMapping.attributesToProps.getOrElse(xmlNamespace, throw BadRequestException(s"namespace $xmlNamespace unknown for attribute ${attr.key} in mapping")).getOrElse(attr.key, throw BadRequestException(s"mapping for attr '${attr.key}' not provided"))
                    val propPredicates = standoffPropertyEntities(standoffTagPropIri).predicates

                    // check if a cardinality exists for the current attribute
                    if (classSpecificProps.get(standoffTagPropIri).isEmpty) {
                        throw BadRequestException(s"no cardinality defined for attr '${attr.key}'")
                    }

                    if (propPredicates.get(OntologyConstants.KnoraBase.ObjectDatatypeConstraint).isDefined) {
                        // property is a data type property

                        val propDatatypeConstraint = propPredicates(OntologyConstants.KnoraBase.ObjectDatatypeConstraint)

                        propDatatypeConstraint.objects.headOption match {
                            case Some(OntologyConstants.Xsd.String) =>
                                StandoffTagStringAttributeV1(standoffPropertyIri = standoffTagPropIri, value = stringFormatter.toSparqlEncodedString(attr.value, throw BadRequestException(s"Invalid string attribute: '${attr.value}'")))

                            case Some(OntologyConstants.Xsd.Integer) =>
                                StandoffTagIntegerAttributeV1(standoffPropertyIri = standoffTagPropIri, value = stringFormatter.validateInt(attr.value, throw BadRequestException(s"Invalid integer attribute: '${attr.value}'")))

                            case Some(OntologyConstants.Xsd.Decimal) =>
                                StandoffTagDecimalAttributeV1(standoffPropertyIri = standoffTagPropIri, value = stringFormatter.validateBigDecimal(attr.value, throw BadRequestException(s"Invalid decimal attribute: '${attr.value}'")))

                            case Some(OntologyConstants.Xsd.Boolean) =>
                                StandoffTagBooleanAttributeV1(standoffPropertyIri = standoffTagPropIri, value = stringFormatter.validateBoolean(attr.value, throw BadRequestException(s"Invalid boolean attribute: '${attr.value}'")))

                            case None => throw InconsistentTriplestoreDataException(s"did not find ${OntologyConstants.KnoraBase.ObjectDatatypeConstraint} for $standoffTagPropIri")

                            case other => throw InconsistentTriplestoreDataException(s"triplestore returned unknown ${OntologyConstants.KnoraBase.ObjectDatatypeConstraint} '$other' for $standoffTagPropIri")

                        }
                    } else {
                        // only properties with a `ObjectDatatypeConstraint` are allowed here (linking properties have to be created via data type standoff classes)
                        throw InconsistentTriplestoreDataException(s"no ${OntologyConstants.KnoraBase.ObjectDatatypeConstraint} given for property '$standoffTagPropIri'")
                    }

            }.toList

            val attrsGroupedByPropIri: Map[IRI, Seq[StandoffTagAttributeV1]] = attrs.groupBy(attr => attr.standoffPropertyIri)

            // filter all the required props
            val mustExistOnce: Set[IRI] = classSpecificProps.filter {
                case (propIri, card) =>
                    card.cardinality == Cardinality.MustHaveOne || card.cardinality == Cardinality.MustHaveSome
            }.keySet

            // check if all the min cardinalities are respected
            mustExistOnce.foreach {
                propIri =>
                    attrsGroupedByPropIri.get(propIri) match {
                        case Some(attrs: Seq[StandoffTagAttributeV1]) => ()

                        case None => throw BadRequestException(s"the min cardinalities were not respected for the property $propIri (missing attribute for element ${standoffNodeFromXML.tagName})")
                    }
            }

            // filter all the props that have a limited occurrence
            val mayExistOnce = classSpecificProps.filter {
                case (propIri, card) =>
                    card.cardinality == Cardinality.MustHaveOne || card.cardinality == Cardinality.MayHaveOne
            }.keySet

            // check if all the max cardinalities are respected
            mayExistOnce.foreach {
                propIri =>
                    attrsGroupedByPropIri.get(propIri) match {
                        case Some(attrs: Seq[StandoffTagAttributeV1]) =>
                            if (attrs.size > 1) {
                                throw BadRequestException(s"the max cardinalities were not respected for $propIri (for element ${standoffNodeFromXML.tagName})")
                            }
                        case None => ()
                    }
            }

            attrs

        } else {
            // possibly data type properties are required, but no other standoff properties

            // check that there no other attributes than data type attributes and 'class'
            val unsupportedAttributes: Set[String] = standoffNodeFromXML.attributes.filterNot {
                attr => (xmlToStandoffMapping.dataType.nonEmpty && xmlToStandoffMapping.dataType.get.dataTypeXMLAttribute == attr.key) || attr.key == classAttribute
            }.map(attr => attr.key)

            if (unsupportedAttributes.nonEmpty) throw BadRequestException(s"Attributes found that are not defined in the mapping: ${unsupportedAttributes.mkString(", ")}")

            Seq.empty[StandoffTagAttributeV1]
        }

    }

    /**
      * Represents a text with standoff markup including the mapping.
      *
      * @param text          the text as a mere sequence of characters.
      * @param standoffTagV1 the text's standoff markup.
      */
    case class TextWithStandoffTagsV1(text: String, standoffTagV1: Seq[StandoffTagV1], mapping: GetMappingResponseV1)

    /**
      * Converts XML to a [[TextWithStandoffTagsV1]].
      *
      * @param xml                            the XML representing text with markup.
      * @param mapping                        the mapping used to convert XML to standoff.
      * @param acceptStandoffLinksToClientIDs if `true`, allow standoff link tags to use the client's IDs for target
      *                                       resources. In a bulk import, this allows standoff links to resources
      *                                       that are to be created by the import.
      * @return a [[TextWithStandoffTagsV1]].
      */
    def convertXMLtoStandoffTagV1(xml: String, mapping: GetMappingResponseV1, acceptStandoffLinksToClientIDs: Boolean, log: LoggingAdapter): TextWithStandoffTagsV1 = {

        // collect all the `XMLTag` from the given mapping that require a separator
        // and create a `XMLTagSeparatorRequired` for each of them
        //
        // namespace = Map("myXMLNamespace" -> Map("myXMLTagName" -> Map("myXMLClassname" -> XMLTag(...))))
        val elementsSeparatorRequired: Vector[XMLTagSeparatorRequired] = mapping.mapping.namespace.flatMap {
            case (namespace: String, elesForNamespace: Map[String, Map[String, XMLTag]]) =>
                elesForNamespace.flatMap {
                    case (tagname: String, classWithTag: Map[String, XMLTag]) =>

                        val tagsWithSeparator: Iterable[XMLTagSeparatorRequired] = classWithTag.filter {
                            // filter out all `XMLTag` that require a separator
                            case (classname: String, tag: XMLTag) =>
                                tag.separatorRequired
                        }.map {
                            case (classname: String, tag: XMLTag) =>

                                // create a `XMLTagSeparatorRequired` with the current's element
                                // namespace and class, if any
                                XMLTagSeparatorRequired(
                                    maybeNamespace = namespace match {
                                        // check for namespace
                                        case `noNamespace` => None
                                        case nspace: String => Some(nspace)
                                    },
                                    tagname = tag.name,
                                    maybeClassname = classname match {
                                        // check for classname
                                        case `noClass` => None
                                        case clname: String => Some(clname)
                                    }
                                )
                        }
                        tagsWithSeparator
                }
        }.toVector

        val xmlStandoffUtil = new XMLToStandoffUtil()
        val textWithStandoff: TextWithStandoff = xmlStandoffUtil.xml2TextWithStandoff(xml, elementsSeparatorRequired, log)

        val standoffTagsV1: Seq[StandoffTagV1] = convertXMLStandoffTagToStandoffTagV1(
            textWithStandoff = textWithStandoff,
            mappingXMLtoStandoff = mapping.mapping,
            standoffEntities = mapping.standoffEntities,
            acceptStandoffLinksToClientIDs = acceptStandoffLinksToClientIDs
        )

        TextWithStandoffTagsV1(
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
      * @param textWithStandoff               sequence of [[StandoffTag]] returned by [[XMLToStandoffUtil.xml2TextWithStandoff]].
      * @param mappingXMLtoStandoff           the mapping to be used.
      * @param standoffEntities               the standoff entities (classes and properties) to be used.
      * @param acceptStandoffLinksToClientIDs if `true`, allow standoff link tags to use the client's IDs for target
      *                                       resources. In a bulk import, this allows standoff links to resources
      *                                       that are to be created by the import.
      * @return a sequence of [[StandoffTagV1]].
      */
    private def convertXMLStandoffTagToStandoffTagV1(textWithStandoff: TextWithStandoff,
                                                     mappingXMLtoStandoff: MappingXMLtoStandoff,
                                                     standoffEntities: StandoffEntityInfoGetResponseV1,
                                                     acceptStandoffLinksToClientIDs: Boolean): Seq[StandoffTagV1] = {
        val stringFormatter = StringFormatter.getGeneralInstance

        // collect al the existing ids from the standoff tags
        val existingXMLIDs: Seq[String] = textWithStandoff.standoff.filter((standoffTag: StandoffTag) => standoffTag.originalID.isDefined).map {
            standoffTagWithID =>
                standoffTagWithID.originalID.get
        }

        // get the id of an XML target element from an internal reference
        // assumes that an internal references starts with a "#"
        def getTargetIDFromInternalReference(internalReference: String) = {
            // make sure that the internal reference starts with a '#'
            if (internalReference.charAt(0) != internalLinkMarker) throw BadRequestException(s"invalid internal reference (should start with a $internalLinkMarker): '$internalReference'")

            val refTarget = internalReference.substring(1)

            // make sure that he target of the reference exists in this context
            if (!existingXMLIDs.contains(refTarget)) throw BadRequestException(s"invalid internal reference: target '$internalReference' unknown")

            refTarget

        }

        textWithStandoff.standoff.map {
            case (standoffNodeFromXML: StandoffTag) =>

                val xmlNamespace = standoffNodeFromXML.xmlNamespace match {
                    case None => noNamespace
                    case Some(namespace) => namespace
                }

                val classname: String = standoffNodeFromXML.attributes.find(_.key == classAttribute) match {
                    case None => noClass
                    case Some(classAttribute: StandoffTagAttribute) => classAttribute.value
                }

                // get the mapping corresponding to the given namespace and tagname
                val standoffDefFromMapping = mappingXMLtoStandoff.namespace
                    .getOrElse(xmlNamespace, throw BadRequestException(s"namespace $xmlNamespace not defined in mapping"))
                    .getOrElse(standoffNodeFromXML.tagName, throw BadRequestException(s"the standoff class for the tag '${standoffNodeFromXML.tagName}' could not be found in the provided mapping"))
                    .getOrElse(classname, throw BadRequestException(s"the standoff class for the classname $classname in combination with the tag '${standoffNodeFromXML.tagName}' could not be found in the provided mapping")).mapping

                val standoffClassIri: IRI = standoffDefFromMapping.standoffClassIri

                // get the cardinalities of the current standoff class
                val cardinalities: Map[IRI, KnoraCardinalityInfo] = standoffEntities.standoffClassInfoMap.getOrElse(standoffClassIri, throw NotFoundException(s"information about standoff class $standoffClassIri was not found in ontology")).knoraResourceCardinalities

                // create a standoff base tag with the information available from standoff util
                val standoffBaseTagV1: StandoffTagV1 = standoffNodeFromXML match {
                    case hierarchicalStandoffTag: HierarchicalStandoffTag =>
                        StandoffTagV1(
                            standoffTagClassIri = standoffClassIri,
                            startPosition = hierarchicalStandoffTag.startPosition,
                            endPosition = hierarchicalStandoffTag.endPosition,
                            uuid = hierarchicalStandoffTag.uuid.toString,
                            originalXMLID = hierarchicalStandoffTag.originalID match {
                                case Some(id: String) => Some(stringFormatter.toSparqlEncodedString(id, throw BadRequestException(s"XML id $id cannot be converted to a Sparql conform string")))
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
                                case Some(id: String) => Some(stringFormatter.toSparqlEncodedString(id, throw BadRequestException(s"XML id $id cannot be converted to a Sparql conform string")))
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
                standoffEntities.standoffClassInfoMap(standoffClassIri).standoffDataType match {

                    case Some(StandoffDataTypeClasses.StandoffLinkTag) =>

                        val linkString: String = getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffLinkTag, standoffNodeFromXML)

                        val internalLink: StandoffTagAttributeV1 = StandoffTagIriAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink, value = stringFormatter.validateStandoffLinkResourceReference(linkString, acceptStandoffLinksToClientIDs, throw BadRequestException(s"Invalid standoff resource reference: $linkString")))

                        val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.linkProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, existingXMLIDs, standoffNodeFromXML, standoffEntities.standoffPropertyInfoMap)

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

                    case Some(StandoffDataTypeClasses.StandoffInternalReferenceTag) =>

                        val internalReferenceString: String = getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffInternalReferenceTag, standoffNodeFromXML)

                        val internalReference = StandoffTagInternalReferenceAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasInternalReference, value = getTargetIDFromInternalReference(internalReferenceString))

                        val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.internalReferenceProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, existingXMLIDs, standoffNodeFromXML, standoffEntities.standoffPropertyInfoMap)

                        StandoffTagV1(
                            dataType = Some(StandoffDataTypeClasses.StandoffInternalReferenceTag),
                            standoffTagClassIri = standoffBaseTagV1.standoffTagClassIri,
                            startPosition = standoffBaseTagV1.startPosition,
                            endPosition = standoffBaseTagV1.endPosition,
                            uuid = standoffBaseTagV1.uuid,
                            originalXMLID = standoffBaseTagV1.originalXMLID,
                            startIndex = standoffBaseTagV1.startIndex,
                            endIndex = standoffBaseTagV1.endIndex,
                            startParentIndex = standoffBaseTagV1.startParentIndex,
                            endParentIndex = standoffBaseTagV1.endParentIndex,
                            attributes = attributesV1 :+ internalReference
                        )


                    case Some(StandoffDataTypeClasses.StandoffColorTag) =>

                        val colorString: String = getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffColorTag, standoffNodeFromXML)

                        val colorValue = StandoffTagStringAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasColor, value = stringFormatter.validateColor(colorString, throw BadRequestException(s"Color invalid: $colorString")))

                        val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.colorProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, existingXMLIDs, standoffNodeFromXML, standoffEntities.standoffPropertyInfoMap)

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

                        val uriValue = StandoffTagUriAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasUri, value = stringFormatter.validateAndEscapeIri(uriString, throw BadRequestException(s"URI invalid: $uriString")))

                        val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.uriProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, existingXMLIDs, standoffNodeFromXML, standoffEntities.standoffPropertyInfoMap)

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

                        val integerValue = StandoffTagIntegerAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasInteger, value = stringFormatter.validateInt(integerString, throw BadRequestException(s"Integer value invalid: $integerString")))

                        val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.integerProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, existingXMLIDs, standoffNodeFromXML, standoffEntities.standoffPropertyInfoMap)

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

                        val decimalValue = StandoffTagDecimalAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasDecimal, value = stringFormatter.validateBigDecimal(decimalString, throw BadRequestException(s"Decimal value invalid: $decimalString")))

                        val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.decimalProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, existingXMLIDs, standoffNodeFromXML, standoffEntities.standoffPropertyInfoMap)

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

                        val booleanValue = StandoffTagBooleanAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasBoolean, value = stringFormatter.validateBoolean(booleanString, throw BadRequestException(s"Boolean value invalid: $booleanString")))

                        val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.booleanProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, existingXMLIDs, standoffNodeFromXML, standoffEntities.standoffPropertyInfoMap)

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

                        val intervalStart = StandoffTagDecimalAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasIntervalStart, value = stringFormatter.validateBigDecimal(interval(0), throw BadRequestException(s"Decimal value invalid: ${interval(0)}")))

                        val intervalEnd = StandoffTagDecimalAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasIntervalEnd, value = stringFormatter.validateBigDecimal(interval(1), throw BadRequestException(s"Decimal value invalid: ${interval(1)}")))

                        val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.intervalProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, existingXMLIDs, standoffNodeFromXML, standoffEntities.standoffPropertyInfoMap)

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

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, existingXMLIDs, standoffNodeFromXML, standoffEntities.standoffPropertyInfoMap)

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
                        val classSpecificProps: Map[IRI, KnoraCardinalityInfo] = cardinalities -- StandoffProperties.systemProperties

                        val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, existingXMLIDs, standoffNodeFromXML, standoffEntities.standoffPropertyInfoMap)

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
            throw BadRequestException("the same standoff class IRI is used more than once in the mapping")
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
                                    throw BadRequestException(s"the same property IRI is used more than once for the attributes mapping for tag $tagname")
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

                                // standoff class IRI -> XMLTagItem(... attributes -> attrItems)
                                tagItem.mapping.standoffClassIri -> XMLTagItem(namespace = tagNamespace, tagname = tagname, classname = classname, tagItem = tagItem, attributes = attrItems)
                        }

                }

        }

    }

    /**
      * Create a sequence of [[StandoffTagV1]] from the given standoff nodes (Sparql results).
      *
      * @param standoffEntities   information about the standoff entities (from the ontology).
      * @param standoffAssertions standoff assertions to be converted into [[StandoffTagV1]]
      * @return a sequence of [[StandoffTagV1]].
      */
    def createStandoffTagsV1FromSparqlResults(standoffEntities: StandoffEntityInfoGetResponseV1, standoffAssertions: Map[IRI, Map[IRI, String]]): Vector[StandoffTagV1] = {

        standoffAssertions.map {

            case (standoffNodeIri: IRI, standoffNodes: Map[IRI, String]) =>

                // create a sequence of `StandoffTagAttributeV1` from the given attributes
                val attributes: Seq[StandoffTagAttributeV1] = (standoffNodes -- StandoffProperties.systemProperties - OntologyConstants.Rdf.Type).map {
                    case (propIri, value) =>

                        val propPredicates = standoffEntities.standoffPropertyInfoMap(propIri).predicates

                        // check if the given property has an object type constraint (linking property) or an object data type constraint
                        if (propPredicates.get(OntologyConstants.KnoraBase.ObjectClassConstraint).isDefined) {

                            // it is a linking property
                            // check if it refers to a resource or a standoff node

                            if (standoffEntities.standoffPropertyInfoMap(propIri).isStandoffInternalReferenceProperty) {
                                // it refers to a standoff node, recreate the original id

                                // value points to another standoff node
                                // get this standoff node and access its original id
                                val originalId: String = standoffAssertions(value).getOrElse(OntologyConstants.KnoraBase.StandoffTagHasOriginalXMLID, throw InconsistentTriplestoreDataException(s"referred standoff $value node has no original XML id"))

                                // recreate the original id reference
                                StandoffTagStringAttributeV1(standoffPropertyIri = propIri, value = StandoffTagUtilV1.internalLinkMarker + originalId)
                            } else {
                                // it refers to a knora resource
                                StandoffTagIriAttributeV1(standoffPropertyIri = propIri, value = value)
                            }
                        } else if (propPredicates.get(OntologyConstants.KnoraBase.ObjectDatatypeConstraint).isDefined) {

                            // it is a data type property (literal)
                            val propDataType = propPredicates(OntologyConstants.KnoraBase.ObjectDatatypeConstraint)

                            propDataType.objects.headOption match {
                                case Some(OntologyConstants.Xsd.String) =>
                                    StandoffTagStringAttributeV1(standoffPropertyIri = propIri, value = value)

                                case Some(OntologyConstants.Xsd.Integer) =>
                                    StandoffTagIntegerAttributeV1(standoffPropertyIri = propIri, value = value.toInt)

                                case Some(OntologyConstants.Xsd.Decimal) =>
                                    StandoffTagDecimalAttributeV1(standoffPropertyIri = propIri, value = BigDecimal(value))

                                case Some(OntologyConstants.Xsd.Boolean) =>
                                    StandoffTagBooleanAttributeV1(standoffPropertyIri = propIri, value = value.toBoolean)

                                case Some(OntologyConstants.Xsd.Uri) => StandoffTagIriAttributeV1(standoffPropertyIri = propIri, value = value)

                                case None => throw InconsistentTriplestoreDataException(s"did not find ${OntologyConstants.KnoraBase.ObjectDatatypeConstraint} for $propIri")

                                case other => throw InconsistentTriplestoreDataException(s"triplestore returned unknown ${OntologyConstants.KnoraBase.ObjectDatatypeConstraint} '$other' for $propIri")

                            }
                        } else {
                            throw InconsistentTriplestoreDataException(s"no object class or data type constraint found for property '$propIri'")
                        }

                }.toVector

                StandoffTagV1(
                    standoffTagClassIri = standoffNodes(OntologyConstants.Rdf.Type),
                    startPosition = standoffNodes(OntologyConstants.KnoraBase.StandoffTagHasStart).toInt,
                    endPosition = standoffNodes(OntologyConstants.KnoraBase.StandoffTagHasEnd).toInt,
                    dataType = standoffEntities.standoffClassInfoMap(standoffNodes(OntologyConstants.Rdf.Type)).standoffDataType,
                    startIndex = standoffNodes(OntologyConstants.KnoraBase.StandoffTagHasStartIndex).toInt,
                    endIndex = standoffNodes.get(OntologyConstants.KnoraBase.StandoffTagHasEndIndex) match {
                        case Some(endIndex: String) => Some(endIndex.toInt)
                        case None => None
                    },
                    uuid = standoffNodes(OntologyConstants.KnoraBase.StandoffTagHasUUID),
                    originalXMLID = standoffNodes.get(OntologyConstants.KnoraBase.StandoffTagHasOriginalXMLID),
                    startParentIndex = standoffNodes.get(OntologyConstants.KnoraBase.StandoffTagHasStartParent) match {
                        // translate standoff node IRI to index
                        case Some(startParentIri: IRI) => Some(standoffAssertions(startParentIri)(OntologyConstants.KnoraBase.StandoffTagHasStartIndex).toInt)
                        case None => None
                    },
                    endParentIndex = standoffNodes.get(OntologyConstants.KnoraBase.StandoffTagHasEndParent) match {
                        // translate standoff node IRI to index
                        case Some(endParentIri: IRI) => Some(standoffAssertions(endParentIri)(OntologyConstants.KnoraBase.StandoffTagHasStartIndex).toInt)
                        case None => None
                    },
                    attributes = attributes
                )
        }.toVector

    }

    /**
      * Converts a sequence of [[StandoffTagAttributeV1]] to a sequence of [[StandoffTagAttribute]].
      *
      * @param mapping    the mapping used to convert standoff property IRIs to XML attribute names.
      * @param attributes the standoff properties to be converted to XML attributes.
      * @return a sequence of [[StandoffTagAttribute]].
      */
    private def convertStandoffAttributeTags(mapping: Map[IRI, XMLAttrItem], attributes: Seq[StandoffTagAttributeV1]): Seq[StandoffTagAttribute] = {
        attributes.map {
            attr =>

                val attrItem: XMLAttrItem = mapping.getOrElse(attr.standoffPropertyIri, throw NotFoundException(s"property IRI ${attr.standoffPropertyIri} could not be found in mapping"))

                StandoffTagAttribute(
                    key = attrItem.attrname,
                    xmlNamespace = attrItem.namespace match {
                        case `noNamespace` => None
                        case namespace => Some(namespace)
                    },
                    value = attr.stringValue
                )
        }
    }

    /**
      * Converts a text value with standoff to an XML String.
      *
      * @param utf8str              the string representation of the text value (`valueHasString`).
      * @param standoff             the standoff representing the markup.
      * @param mappingXMLtoStandoff the mapping used to convert standoff to XML markup.
      * @return a String representing an XML document.
      */
    def convertStandoffTagV1ToXML(utf8str: String, standoff: Seq[StandoffTagV1], mappingXMLtoStandoff: MappingXMLtoStandoff): String = {

        // inverts the mapping and makes standoff class Iris keys (for tags)
        val mappingStandoffToXML: Map[IRI, XMLTagItem] = invertXMLToStandoffMapping(mappingXMLtoStandoff)

        val standoffUtil = new XMLToStandoffUtil(writeUuidsToXml = false)

        val standoffTags: Seq[StandoffTag] = standoff.map {
            (standoffTagV1: StandoffTagV1) =>

                val xmlItemForStandoffClass: XMLTagItem = mappingStandoffToXML.getOrElse(standoffTagV1.standoffTagClassIri, throw NotFoundException(s"standoff class IRI ${standoffTagV1.standoffTagClassIri} not found in mapping"))

                // recreate data type specific attributes (optional)
                val attributes: Seq[StandoffTagAttribute] = standoffTagV1.dataType match {

                    case Some(StandoffDataTypeClasses.StandoffLinkTag) =>
                        val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}")).dataTypeXMLAttribute

                        val linkIri = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.StandoffTagHasLink).get.stringValue

                        val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.linkProperties.contains(attr.standoffPropertyIri))

                        convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = linkIri, xmlNamespace = None)

                    case Some(StandoffDataTypeClasses.StandoffInternalReferenceTag) =>
                        val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}")).dataTypeXMLAttribute

                        val internalRefTarget = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.StandoffTagHasInternalReference).get.stringValue

                        val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.internalReferenceProperties.contains(attr.standoffPropertyIri))

                        convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = internalRefTarget, xmlNamespace = None)

                    case Some(StandoffDataTypeClasses.StandoffColorTag) =>
                        val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}")).dataTypeXMLAttribute

                        val colorString = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasColor).get.stringValue

                        val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.colorProperties.contains(attr.standoffPropertyIri))

                        convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = colorString, xmlNamespace = None)

                    case Some(StandoffDataTypeClasses.StandoffUriTag) =>
                        val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}")).dataTypeXMLAttribute

                        val uriRef = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasUri).get.stringValue

                        val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.uriProperties.contains(attr.standoffPropertyIri))

                        convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = uriRef, xmlNamespace = None)

                    case Some(StandoffDataTypeClasses.StandoffIntegerTag) =>
                        val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}")).dataTypeXMLAttribute

                        val integerString = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasInteger).get.stringValue

                        val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.integerProperties.contains(attr.standoffPropertyIri))

                        convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = integerString, xmlNamespace = None)

                    case Some(StandoffDataTypeClasses.StandoffDecimalTag) =>
                        val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}")).dataTypeXMLAttribute

                        val decimalString = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasDecimal).get.stringValue

                        val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.decimalProperties.contains(attr.standoffPropertyIri))

                        convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = decimalString, xmlNamespace = None)

                    case Some(StandoffDataTypeClasses.StandoffBooleanTag) =>
                        val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}")).dataTypeXMLAttribute

                        val booleanString = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasBoolean).get.stringValue

                        val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.booleanProperties.contains(attr.standoffPropertyIri))

                        convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = booleanString, xmlNamespace = None)

                    case Some(StandoffDataTypeClasses.StandoffIntervalTag) =>
                        val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}")).dataTypeXMLAttribute

                        val intervalString = Vector(standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasIntervalStart).get.stringValue, standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasIntervalEnd).get.stringValue).mkString(",")

                        val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.intervalProperties.contains(attr.standoffPropertyIri))

                        convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = intervalString, xmlNamespace = None)

                    case Some(StandoffDataTypeClasses.StandoffDateTag) =>
                        // create one attribute from date properties
                        val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}")).dataTypeXMLAttribute

                        val calendar = KnoraCalendarV1.lookup(standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasCalendar).get.stringValue)

                        val julianDayCountValueV1: UpdateValueV1 = JulianDayNumberValueV1(
                            dateval1 = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasStartJDN).get.stringValue.toInt,
                            dateval2 = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasEndJDN).get.stringValue.toInt,
                            dateprecision1 = KnoraPrecisionV1.lookup(standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasStartPrecision).get.stringValue),
                            dateprecision2 = KnoraPrecisionV1.lookup(standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasEndPrecision).get.stringValue),
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
                            key = classAttribute,
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


        val textWithStandoff = TextWithStandoff(text = utf8str, standoff = standoffTags)
        standoffUtil.textWithStandoff2Xml(textWithStandoff)
    }

}