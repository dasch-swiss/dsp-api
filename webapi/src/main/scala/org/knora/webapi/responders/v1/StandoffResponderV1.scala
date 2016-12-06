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

import java.io.{File, IOException, StringReader}
import java.nio.file.Files
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.{Schema, SchemaFactory, Validator => JValidator}

import akka.actor.Status
import akka.pattern._
import akka.stream.ActorMaterializer
import org.knora.webapi.messages.v1.responder.ontologymessages.{Cardinality, StandoffEntityInfoGetRequestV1, StandoffEntityInfoGetResponseV1}
import org.knora.webapi.messages.v1.responder.sipimessages.{SipiResponderConversionPathRequestV1, SipiResponderConversionResponseV1}
import org.knora.webapi.messages.v1.responder.standoffmessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.twirl._
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.standoff._
import org.knora.webapi.util.{DateUtilV1, InputValidation, ScalaPrettyPrinter}
import org.knora.webapi.{BadRequestException, _}
import org.xml.sax.SAXException

import scala.concurrent.Future
import scala.xml.{Node, NodeSeq, XML}

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
        case CreateStandoffRequestV1(projIri, resIri, propIri, xml, userProfile, uuid) => future2Message(sender(), createStandoffV1(projIri, resIri, propIri, xml, userProfile, uuid), log)
        case StandoffGetRequestV1(valueIri, userProfile) => future2Message(sender(), getStandoffV1(valueIri, userProfile), log)
        case CreateMappingRequestV1(xml, userProfile) => future2Message(sender(), createMappingV1(xml, userProfile), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * Creates a mapping from XML elements and attributes to standoff classes and properties.
      *
      * @param xml         the provided mapping.
      * @param userProfile the client that made the request.
      */
    private def createMappingV1(xml: String, userProfile: UserProfileV1): Future[CreateMappingResponseV1] = {

        val createMappingFuture = for {

            factory <- Future(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI))

            // get the schema the mapping has to be validated against
            schemaFile: File = new File("src/main/resources/mappingXMLToStandoff.xsd")

            schemaSource: StreamSource = new StreamSource(schemaFile)

            // create a schema instance
            schemaInstance: Schema = factory.newSchema(schemaSource)
            validator: JValidator = schemaInstance.newValidator()

            // validate the provided mapping
            _ = validator.validate(new StreamSource(new StringReader(xml)))

            // TODO: if the validation is successful, store the mapping using Sipi
            tmpFile: File = InputValidation.createTempFile(settings)
            _ = Files.write(tmpFile.toPath(), xml.getBytes());

            sipiResponse: SipiResponderConversionResponseV1 <- (responderManager ? SipiResponderConversionPathRequestV1(
                originalFilename = "mapping.xml",
                originalMimeType = "application/xml",
                source = tmpFile,
                userProfile = userProfile)).mapTo[SipiResponderConversionResponseV1]

            _ = println(sipiResponse)

        } yield CreateMappingResponseV1(filename = "", userdata = userProfile.userData)

        createMappingFuture.recoverWith {
            case validationException: SAXException => throw BadRequestException(s"the provided mapping is invalid: ${validationException.getMessage}")

            case ioException: IOException => throw NotFoundException(s"The schema could not be found")

            case unknown: Exception => throw BadRequestException(s"the provided mapping could not be handled correctly: ${unknown.getMessage}")
        }
    }

    // string constant used to mark the absence of an XML namespace
    val noNamespace = "noNamespace"

    // TODO call this method when creating the mapping and cache it
    private def getMapping(mappingFileValueIri: IRI): MappingXMLtoStandoff = {

        // TODO: get mapping from file value
        val mappingString =
            """<?xml version="1.0" encoding="UTF-8"?>
              |<mapping xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="mapping.xsd">
              |  <mappingElement>
              |    <tag><name>text</name><namespace>noNamespace</namespace></tag>
              |    <standoffClass>
              |      <classIri>http://www.knora.org/ontology/knora-base#StandoffRootTag</classIri>
              |      <attributes>
              |        <attribute><attributeName>documentType</attributeName><namespace>noNamespace</namespace><propertyIri>http://www.knora.org/ontology/knora-base#standoffRootTagHasDocumentType</propertyIri></attribute>
              |      </attributes>
              |    </standoffClass>
              |  </mappingElement>
              |
              |  <mappingElement>
              |    <tag>
              |      <name>p</name>
              |      <namespace>noNamespace</namespace>
              |    </tag>
              |    <standoffClass>
              |      <classIri>http://www.knora.org/ontology/knora-base#StandoffParagraphTag</classIri>
              |    </standoffClass>
              |  </mappingElement>
              |
              |  <mappingElement>
              |    <tag>
              |      <name>i</name>
              |      <namespace>noNamespace</namespace>
              |    </tag>
              |    <standoffClass>
              |      <classIri>http://www.knora.org/ontology/knora-base#StandoffItalicTag</classIri>
              |    </standoffClass>
              |  </mappingElement>
              |
              |  <mappingElement>
              |    <tag>
              |      <name>b</name>
              |      <namespace>noNamespace</namespace>
              |    </tag>
              |    <standoffClass>
              |      <classIri>http://www.knora.org/ontology/knora-base#StandoffBoldTag</classIri>
              |    </standoffClass>
              |  </mappingElement>
              |
              |  <mappingElement>
              |    <tag>
              |      <name>interval</name>
              |      <namespace>noNamespace</namespace>
              |    </tag>
              |    <standoffClass>
              |      <classIri>http://www.knora.org/ontology/knora-base#StandoffRangeTag</classIri>
              |      <attributes>
              |      <attribute><attributeName>unsure</attributeName><namespace>noNamespace</namespace><propertyIri>http://www.knora.org/ontology/knora-base#standoffTagIsUnsure</propertyIri></attribute>
              |      </attributes>
              |      <datatype>
              |        <type>http://www.knora.org/ontology/knora-base#StandoffIntervalTag</type>
              |        <attributeName>range</attributeName>
              |      </datatype>
              |    </standoffClass>
              |  </mappingElement>
              |
              | <mappingElement>
              |    <tag><name>birthday</name><namespace>noNamespace</namespace></tag>
              |    <standoffClass>
              |      <classIri>http://www.knora.org/ontology/knora-base#StandoffBirthdayTag</classIri>
              |      <datatype><type>http://www.knora.org/ontology/knora-base#StandoffDateTag</type>
              |        <attributeName>date</attributeName>
              |      </datatype>
              |    </standoffClass>
              |  </mappingElement>
              |
              |
              |
              |
              |</mapping>""".stripMargin

        // the mapping conforms to the XML schema "src/main/resources/mappingXMLToStandoff.xsd"
        val mappingXML = XML.loadString(mappingString)

        val mappingElements: NodeSeq = mappingXML \ "mappingElement"

        val mappingXMLToStandoff: MappingXMLtoStandoff = mappingElements.foldLeft(MappingXMLtoStandoff(namespace = Map.empty[String, Map[String, XMLTag]])) {
            case (acc: MappingXMLtoStandoff, curNode: Node) =>

                // get the name of the XML tag
                val tagname = (curNode \ "tag" \ "name").headOption.getOrElse(throw BadRequestException(s"no '<name>' given for node $curNode")).text

                // get the namespace the tag is defined in
                val namespace = (curNode \ "tag" \ "namespace").headOption.getOrElse(throw BadRequestException(s"no '<namespace>' given for node $curNode")).text

                // get tags from this namespace if already existent, otherwise create an empty map
                val namespaceMap = acc.namespace.getOrElse(namespace, Map.empty[String, XMLTag])

                // get the standoff class Iri
                val standoffClassIri = (curNode \ "standoffClass" \ "classIri").headOption.getOrElse(throw BadRequestException(s"no '<classIri>' given for node $curNode")).text

                // get a collection containing all the attributes
                val attributeNodes: NodeSeq = curNode \ "standoffClass" \ "attributes" \ "attribute"

                // group attributes by their namespace
                val attributeNodesByNamespace: Map[String, NodeSeq] = attributeNodes.groupBy {
                    (attrNode: Node) =>
                        (attrNode \ "namespace").headOption.getOrElse(throw BadRequestException(s"no '<namespace>' given for attribute $attrNode")).text
                }

                // create attribute entries for each given namespace
                val attributes: Map[String, Map[String, IRI]] = attributeNodesByNamespace.map {
                    case (namespace: String, attrNodes: NodeSeq) =>

                        // collect all the attributes for the current namespace
                        val attributesInNamespace: Map[String, IRI] = attrNodes.foldLeft(Map.empty[String, IRI]) {
                            case (acc: Map[String, IRI], attrNode: Node) =>

                                // get the current attribute's name
                                val attrName = (attrNode \ "attributeName").headOption.getOrElse(throw BadRequestException(s"no '<attributeName>' given for attribute $attrNode")).text

                                // check if the current attribute already exists in this namespace
                                if (acc.get(attrName).nonEmpty) {
                                    throw BadRequestException("Duplicate attribute name in namespace")
                                }

                                // get the standoff property Iri for the current attribute
                                val propIri = (attrNode \ "propertyIri").headOption.getOrElse(throw BadRequestException(s"no '<propertyIri>' given for attribute $attrNode")).text

                                // add the current attribute to the collection
                                acc + (attrName -> propIri)
                        }

                        namespace -> attributesInNamespace
                }

                // get the optional element datatype
                val datatypeMaybe: NodeSeq = curNode \ "standoffClass" \ "datatype"

                // if "datatype" is given, get the the standoff class data type and the name of the XML data type attribute
                val (dataTypeOption: Option[StandoffDataTypeClasses.Value], dataTypeAttributeOption: Option[String]) = if (datatypeMaybe.nonEmpty) {
                    val dataType = StandoffDataTypeClasses.lookup((datatypeMaybe \ "type").headOption.getOrElse(throw BadRequestException(s"no '<type>' given for datatype")).text, () => throw BadRequestException("Invalid data type provided"))
                    val dataTypeAttribute = (datatypeMaybe \ "attributeName").headOption.getOrElse(throw BadRequestException(s"no '<attributeName>' given for datatype")).text

                    (Some(dataType), Some(dataTypeAttribute))
                } else {
                    (None, None)
                }

                // add the current tag to the map
                val newNamespaceMap = namespaceMap.get(tagname) match {
                    case Some(tag) => throw BadRequestException("Duplicate tag name in namespace")
                    case None => namespaceMap + (tagname -> XMLTag(name = tagname, mapping = XMLTagToStandoffClass(standoffClassIri = standoffClassIri, attributesToProps = attributes, dataType = dataTypeOption, dataTypeXMLAttribute = dataTypeAttributeOption)))
                }

                // recreate the whole structure
                MappingXMLtoStandoff(
                    namespace = acc.namespace + (namespace -> newNamespaceMap)
                )
        }

        // invert mapping in order to run checks for duplicate use of
        // standoff class Iris and property Iris in the attributes for a standoff class
        invertXMLToStandoffMapping(mappingXMLToStandoff)

        mappingXMLToStandoff
    }


    /**
      * Tries to find a data type attribute in the XML attributes of a given standoff node. Throws an appropriate error if information is inconsistent or missing.
      *
      * @param XMLtoStandoffMapping the mapping from XML to standoff classes and properties for the given standoff node.
      * @param dataType             the expected data type of the given standoff node.
      * @param standoffNodeFromXML  the given standoff node.
      * @return the value of the attribute.
      */
    private def getDataTypeAttribute(XMLtoStandoffMapping: XMLTagToStandoffClass, dataType: StandoffDataTypeClasses.Value, standoffNodeFromXML: StandoffTag): String = {

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
      * @param XMLtoStandoffMapping     the mapping from XML to standoff classes and properties for the given standoff node.
      * @param classSpecificProps       the properties that may or have to be created (cardinalities) for the given standoff node.
      * @param standoffNodeFromXML      the given standoff node.
      * @param standoffPropertyEntities the ontology information about the standoff properties.
      * @return a sequence of [[StandoffTagAttributeV1]]
      */
    private def createAttributes(XMLtoStandoffMapping: XMLTagToStandoffClass, classSpecificProps: Map[IRI, Cardinality.Value], standoffNodeFromXML: StandoffTag, standoffPropertyEntities: StandoffEntityInfoGetResponseV1): Seq[StandoffTagAttributeV1] = {

        if (classSpecificProps.nonEmpty) {
            // additional standoff properties are required

            // map over all non data type attributes
            val attrs: Seq[StandoffTagAttributeV1] = standoffNodeFromXML.attributes.filterNot(attr => XMLtoStandoffMapping.dataType.nonEmpty && XMLtoStandoffMapping.dataTypeXMLAttribute.nonEmpty && XMLtoStandoffMapping.dataTypeXMLAttribute.get == attr.key).map {
                attr: StandoffTagAttribute =>
                    // get the standoff property Iri for this XML attribute

                    val xmlNamespace = attr.xmlNamespace match {
                    case None => noNamespace
                    case Some(namespace) => namespace
                }

                    val standoffTagPropIri = XMLtoStandoffMapping.attributesToProps.getOrElse(xmlNamespace, throw throw BadRequestException(s"namespace $xmlNamespace unknown for attribute ${attr.key}")).getOrElse(attr.key, throw BadRequestException(s"mapping for attr '${attr.key}' not provided"))

                    // check if a cardinality exists for the current attribute
                    if (classSpecificProps.get(standoffTagPropIri).isEmpty) {
                        throw BadRequestException(s"no cardinality defined for attr '${attr.key}'")
                    }

                    // check if the object datatype constraint is respected for the current property
                    val propDataType = standoffPropertyEntities.standoffPropertyEntityInfoMap(standoffTagPropIri).predicates.getOrElse(OntologyConstants.KnoraBase.ObjectDatatypeConstraint, throw InconsistentTriplestoreDataException(s"no ${OntologyConstants.KnoraBase.ObjectDatatypeConstraint} given for property '$standoffTagPropIri'"))

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

    /**
      * Creates standoff from a given XML file.
      *
      * @param xml         the xml file sent by the client.
      * @param userProfile the client that made the request.
      * @return a [[CreateStandoffResponseV1]]
      */
    private def createStandoffV1(projectIri: IRI, resourceIri: IRI, propertyIRI: IRI, xml: String, userProfile: UserProfileV1, apiRequestID: UUID): Future[CreateStandoffResponseV1] = {

        val standoffUtil = new StandoffUtil()

        val textWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(xml)

        // TODO: get the mapping that was used when creating the standoff values
        val mappingXMLtoStandoff: MappingXMLtoStandoff = getMapping("")

        // collect standoff classes Iris from mapping
        val standoffTagIris: Set[IRI] = mappingXMLtoStandoff.namespace.flatMap {
            case (namespace: String, mapping: Map[String, XMLTag]) =>
                mapping.map {
                    case (tagname: String, tagItem: XMLTag) =>
                        tagItem.mapping.standoffClassIri
                }
        }.toSet


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

                    val xmlNamespace = standoffNodeFromXML.xmlNamespace match {
                        case None => noNamespace
                        case Some(namespace) => namespace
                    }

                    // get the mapping corresponding to the given namespace and tagname
                    val standoffDefFromMapping = mappingXMLtoStandoff.namespace
                        .getOrElse(xmlNamespace, throw BadRequestException(s"namespace ${xmlNamespace} not defined in mapping"))
                        .getOrElse(standoffNodeFromXML.tagName, throw BadRequestException(s"the standoff class for the tag '${standoffNodeFromXML.tagName}' could not be found in the provided mapping")).mapping

                    val standoffClassIri: IRI = standoffDefFromMapping.standoffClassIri

                    // get the cardinalities of the current standoff class
                    val cardinalities: Map[IRI, Cardinality.Value] = standoffClassEntities.standoffClassEntityInfoMap.getOrElse(standoffClassIri, throw NotFoundException(s"information about standoff class $standoffClassIri was not found in ontology")).cardinalities

                    // create a standoff base tag with the information available from standoff util
                    val standoffBaseTagV1: StandoffTagV1 = standoffNodeFromXML match {
                        case hierarchicalStandoffTag: HierarchicalStandoffTag =>
                            StandoffTagV1(
                                standoffTagClassIri = standoffClassIri,
                                startPosition = hierarchicalStandoffTag.startPosition,
                                endPosition = hierarchicalStandoffTag.endPosition,
                                uuid = Some(hierarchicalStandoffTag.uuid.toString),
                                startIndex = Some(hierarchicalStandoffTag.index),
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
                                uuid = Some(freeStandoffTag.uuid.toString),
                                startIndex = Some(freeStandoffTag.startIndex),
                                endIndex = Some(freeStandoffTag.endIndex),
                                startParentIndex = freeStandoffTag.startParentIndex,
                                endParentIndex = freeStandoffTag.endParentIndex,
                                attributes = Seq.empty[StandoffTagAttributeV1]
                            )
                    }

                    // check the data type of the given standoff class
                    standoffClassEntities.standoffClassEntityInfoMap(standoffClassIri).dataType match {

                        case Some(StandoffDataTypeClasses.StandoffLinkTag) =>

                            val linkString: String = getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffLinkTag, standoffNodeFromXML)

                            val internalLink: StandoffTagAttributeV1 = StandoffTagIriAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink, value = InputValidation.toIri(linkString, () => throw BadRequestException(s"Iri invalid: $linkString")))

                            val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.linkProperties

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            StandoffTagV1(
                                dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
                                standoffTagClassIri = standoffBaseTagV1.standoffTagClassIri,
                                startPosition = standoffBaseTagV1.startPosition,
                                endPosition = standoffBaseTagV1.endPosition,
                                uuid = standoffBaseTagV1.uuid,
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

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            StandoffTagV1(
                                dataType = Some(StandoffDataTypeClasses.StandoffColorTag),
                                standoffTagClassIri = standoffBaseTagV1.standoffTagClassIri,
                                startPosition = standoffBaseTagV1.startPosition,
                                endPosition = standoffBaseTagV1.endPosition,
                                uuid = standoffBaseTagV1.uuid,
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

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            StandoffTagV1(
                                dataType = Some(StandoffDataTypeClasses.StandoffUriTag),
                                standoffTagClassIri = standoffBaseTagV1.standoffTagClassIri,
                                startPosition = standoffBaseTagV1.startPosition,
                                endPosition = standoffBaseTagV1.endPosition,
                                uuid = standoffBaseTagV1.uuid,
                                startIndex = standoffBaseTagV1.startIndex,
                                endIndex = standoffBaseTagV1.endIndex,
                                startParentIndex = standoffBaseTagV1.startParentIndex,
                                endParentIndex = standoffBaseTagV1.endParentIndex,
                                attributes = attributesV1 :+ uriValue
                            )


                        case Some(StandoffDataTypeClasses.StandoffIntegerTag) =>

                            val integerString: String = getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffIntegerTag, standoffNodeFromXML)

                            val integerValue = StandoffTagIntegerAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasInteger, value = InputValidation.toInt(integerString, () => throw BadRequestException(s"Integer value invalid: $integerString")))

                            val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.intervalProperties

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            StandoffTagV1(
                                dataType = Some(StandoffDataTypeClasses.StandoffIntegerTag),
                                standoffTagClassIri = standoffBaseTagV1.standoffTagClassIri,
                                startPosition = standoffBaseTagV1.startPosition,
                                endPosition = standoffBaseTagV1.endPosition,
                                uuid = standoffBaseTagV1.uuid,
                                startIndex = standoffBaseTagV1.startIndex,
                                endIndex = standoffBaseTagV1.endIndex,
                                startParentIndex = standoffBaseTagV1.startParentIndex,
                                endParentIndex = standoffBaseTagV1.endParentIndex,
                                attributes = attributesV1 :+ integerValue
                            )

                        case Some(StandoffDataTypeClasses.StandoffDecimalTag) =>

                            val decimalString: String = getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffDecimalTag, standoffNodeFromXML)

                            val decimalValue = StandoffTagDecimalAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasDecimal, value = InputValidation.toInt(decimalString, () => throw BadRequestException(s"Integer value invalid: $decimalString")))

                            val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.decimalProperties

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            StandoffTagV1(
                                dataType = Some(StandoffDataTypeClasses.StandoffDecimalTag),
                                standoffTagClassIri = standoffBaseTagV1.standoffTagClassIri,
                                startPosition = standoffBaseTagV1.startPosition,
                                endPosition = standoffBaseTagV1.endPosition,
                                uuid = standoffBaseTagV1.uuid,
                                startIndex = standoffBaseTagV1.startIndex,
                                endIndex = standoffBaseTagV1.endIndex,
                                startParentIndex = standoffBaseTagV1.startParentIndex,
                                endParentIndex = standoffBaseTagV1.endParentIndex,
                                attributes = attributesV1 :+ decimalValue
                            )

                        case Some(StandoffDataTypeClasses.StandoffBooleanTag) =>

                            val booleanString: String = getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffBooleanTag, standoffNodeFromXML)

                            val booleanValue = StandoffTagBooleanAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasBoolean, value = InputValidation.toBoolean(booleanString, () => throw BadRequestException(s"Integer value invalid: $booleanString")))

                            val classSpecificProps = cardinalities -- StandoffProperties.systemProperties -- StandoffProperties.booleanProperties

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            StandoffTagV1(
                                dataType = Some(StandoffDataTypeClasses.StandoffBooleanTag),
                                standoffTagClassIri = standoffBaseTagV1.standoffTagClassIri,
                                startPosition = standoffBaseTagV1.startPosition,
                                endPosition = standoffBaseTagV1.endPosition,
                                uuid = standoffBaseTagV1.uuid,
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

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            StandoffTagV1(
                                dataType = Some(StandoffDataTypeClasses.StandoffIntervalTag),
                                standoffTagClassIri = standoffBaseTagV1.standoffTagClassIri,
                                startPosition = standoffBaseTagV1.startPosition,
                                endPosition = standoffBaseTagV1.endPosition,
                                uuid = standoffBaseTagV1.uuid,
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

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            StandoffTagV1(
                                dataType = Some(StandoffDataTypeClasses.StandoffDateTag),
                                standoffTagClassIri = standoffBaseTagV1.standoffTagClassIri,
                                startPosition = standoffBaseTagV1.startPosition,
                                endPosition = standoffBaseTagV1.endPosition,
                                uuid = standoffBaseTagV1.uuid,
                                startIndex = standoffBaseTagV1.startIndex,
                                endIndex = standoffBaseTagV1.endIndex,
                                startParentIndex = standoffBaseTagV1.startParentIndex,
                                endParentIndex = standoffBaseTagV1.endParentIndex,
                                attributes = attributesV1 ++ List(dateCalendar, dateStart, dateEnd, dateStartPrecision, dateEndPrecision)
                            )

                        case None =>

                            // ignore the system properties since they are provided by StandoffUtil
                            val classSpecificProps: Map[IRI, Cardinality.Value] = cardinalities -- StandoffProperties.systemProperties

                            val attributesV1 = createAttributes(standoffDefFromMapping, classSpecificProps, standoffNodeFromXML, standoffPropertyEntities)

                            standoffBaseTagV1.copy(
                                attributes = attributesV1
                            )


                        case unknownDataType => throw InconsistentTriplestoreDataException(s"the triplestore returned the data type $unknownDataType for $standoffClassIri that could be handled")

                    }


            }

            // _ = println(ScalaPrettyPrinter.prettyPrint(standoffNodesToCreate))

            // collect the resource references from the linking standoff nodes
            resourceReferences: Set[IRI] = InputValidation.getResourceIrisFromStandoffTags(standoffNodesToCreate)

            createValueResponse: CreateValueResponseV1 <- (responderManager ? CreateValueRequestV1(
                projectIri = projectIri,
                resourceIri = resourceIri,
                propertyIri = propertyIRI,
                value = TextValueV1(
                    utf8str = textWithStandoff.text,
                    resource_reference = resourceReferences,
                    textattr = standoffNodesToCreate,
                    xml = Some(xml)),
                userProfile = userProfile,
                apiRequestID = apiRequestID)).mapTo[CreateValueResponseV1]


        } yield CreateStandoffResponseV1(id = createValueResponse.id, userdata = userProfile.userData)

    }

    private def changeStandoffV1() = ???

    // maps a standoff class to an XML tag with attributes
    case class XMLTagItem(namespace: String, tagname: String, tagItem: XMLTag, attributes: Map[IRI, XMLAttrItem])

    // maps a standoff property to XML attributes
    case class XMLAttrItem(namespace: String, attrname: String)

    /**
      *  Inverts a [[MappingXMLtoStandoff]] and makes standoff class Iris keys.
      *  This is makes it easier to map standoff classes back to XML tags (recreating XML from standoff).
      *
      *  This method also checks for duplicate usage of standoff classes and properties in the attribute mapping of a tag.
      *
      * @param mappingXMLtoStandoff  mapping from XML to standoff.
      * @return a Map standoff class Iris to [[XMLTagItem]].
      */
    private def invertXMLToStandoffMapping(mappingXMLtoStandoff: MappingXMLtoStandoff): Map[IRI, XMLTagItem] = {

        // check for duplicate standoff class Iris
        val classIris: Iterable[IRI] = mappingXMLtoStandoff.namespace.values.flatten.map {
            case (tagname, tagItem) =>
                tagItem.mapping.standoffClassIri
        }

        // check for duplicate standoff class Iris
        if (classIris.size != classIris.toSet.size) {
            throw BadRequestException("the same standoff class Iri is used more than once in the mapping")
        }

        mappingXMLtoStandoff.namespace.flatMap {
            case (tagNamespace: String, tagMappings: Map[String, XMLTag]) =>

                tagMappings.map {
                    case (tagname: String, tagItem: XMLTag) =>

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
                                        (propIri -> XMLAttrItem(attrNamespace, attrName))
                                }
                        }

                        // standoff class Iri -> XMLTagItem(... attributes -> attrItems)
                        (tagItem.mapping.standoffClassIri -> XMLTagItem(tagNamespace, tagname, tagItem, attributes = attrItems))

                }
        }


    }

    /**
      *
      * Queries a text value with standoff and returns it as XML.
      *
      * @param valueIri    the IRI of the text value to be queried.
      * @param userProfile the profile of the user making the request.
      * @return a [[StandoffGetResponseV1]].
      */
    private def getStandoffV1(valueIri: IRI, userProfile: UserProfileV1): Future[StandoffGetResponseV1] = {

        // converts a sequence of `StandoffTagAttributeV1` to a sequence of `StandoffTagAttribute`
        def convertStandoffAttributeTags(mapping: Map[IRI, XMLAttrItem], attributes: Seq[StandoffTagAttributeV1]): Seq[StandoffTagAttribute] = {
            attributes.map {
                attr =>

                    val attrItem: XMLAttrItem = mapping.getOrElse(attr.standoffPropertyIri, throw NotFoundException(s"property Iri ${attr.standoffPropertyIri} could not be found in mapping"))

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

        // TODO: get the mapping that was used when creating the standoff values
        val mappingXMLtoStandoff: MappingXMLtoStandoff = getMapping("")

        // inverts the mapping and makes standoff class Iris keys (for tags)
        val mappingStandoffToXML: Map[IRI, XMLTagItem] = invertXMLToStandoffMapping(mappingXMLtoStandoff)

        for {
        // ask the ValuesResponder to query the text value.
            value: ValueGetResponseV1 <- (responderManager ? ValueGetRequestV1(valueIri = valueIri, userProfile = userProfile)).mapTo[ValueGetResponseV1]

            // make sure it is a text value
            _ = if (value.valuetype != OntologyConstants.KnoraBase.TextValue) {
                throw BadRequestException(s"the requested value $valueIri is not a text value, but a ${value.valuetype}")
            }

            // create XML from the text value
            textValue: TextValueV1 = value.value match {
                case textValue: TextValueV1 => textValue
                case _ => throw BadRequestException(s"value could not be interpreted as a TextValueV1")
            }

            standoffUtil = new StandoffUtil()

            standoffTags: Seq[StandoffTag] = textValue.textattr.map {
                (standoffTagV1: StandoffTagV1) =>

                    val xmlItemForStandoffClass: XMLTagItem = mappingStandoffToXML.getOrElse(standoffTagV1.standoffTagClassIri, throw NotFoundException(s"standoff class Iri ${standoffTagV1.standoffTagClassIri} not found in mapping"))

                    // recreate data type specific attribute (optional)
                    val attributes: Seq[StandoffTagAttribute] = standoffTagV1.dataType match {

                        case Some(StandoffDataTypeClasses.StandoffLinkTag) =>
                            val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataTypeXMLAttribute.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"))

                            val linkIri = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.StandoffTagHasLink).get.stringValue

                            val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.linkProperties.contains(attr.standoffPropertyIri))

                            convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = linkIri, xmlNamespace = None)

                        case Some(StandoffDataTypeClasses.StandoffColorTag) =>
                            val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataTypeXMLAttribute.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"))

                            val colorString = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasColor).get.stringValue

                            val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.colorProperties.contains(attr.standoffPropertyIri))

                            convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = colorString, xmlNamespace = None)

                        case Some(StandoffDataTypeClasses.StandoffUriTag) =>
                            val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataTypeXMLAttribute.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"))

                            val uriRef = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasUri).get.stringValue

                            val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.uriProperties.contains(attr.standoffPropertyIri))

                            convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = uriRef, xmlNamespace = None)

                        case Some(StandoffDataTypeClasses.StandoffIntegerTag) =>
                            val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataTypeXMLAttribute.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"))

                            val integerString = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasInteger).get.stringValue

                            val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.integerProperties.contains(attr.standoffPropertyIri))

                            convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = integerString, xmlNamespace = None)

                        case Some(StandoffDataTypeClasses.StandoffDecimalTag) =>
                            val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataTypeXMLAttribute.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"))

                            val decimalString = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasDecimal).get.stringValue

                            val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.decimalProperties.contains(attr.standoffPropertyIri))

                            convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = decimalString, xmlNamespace = None)

                        case Some(StandoffDataTypeClasses.StandoffBooleanTag) =>
                            val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataTypeXMLAttribute.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"))

                            val booleanString = standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasBoolean).get.stringValue

                            val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.booleanProperties.contains(attr.standoffPropertyIri))

                            convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = booleanString, xmlNamespace = None)

                        case Some(StandoffDataTypeClasses.StandoffIntervalTag) =>
                            val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataTypeXMLAttribute.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"))

                            val intervalString = Vector(standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasIntervalStart).get.stringValue, standoffTagV1.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.ValueHasIntervalEnd).get.stringValue).mkString(",")

                            val conventionalAttributes = standoffTagV1.attributes.filterNot(attr => StandoffProperties.intervalProperties.contains(attr.standoffPropertyIri))

                            convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, conventionalAttributes) :+ StandoffTagAttribute(key = dataTypeAttrName, value = intervalString, xmlNamespace = None)

                        case Some(StandoffDataTypeClasses.StandoffDateTag) =>
                            // create one attribute from date properties
                            val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataTypeXMLAttribute.getOrElse(throw NotFoundException(s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"))

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


                    if (standoffTagV1.endIndex.isDefined) {
                        // it is a free standoff tag
                        FreeStandoffTag(
                            tagName = xmlItemForStandoffClass.tagname,
                            xmlNamespace = xmlItemForStandoffClass.namespace match {
                                case `noNamespace` => None
                                case namespace => Some(namespace)
                            },
                            uuid = UUID.fromString(standoffTagV1.uuid.get), // TODO: this is an option in knora-base! UUID should also be an option in StandoffUtil
                            startPosition = standoffTagV1.startPosition,
                            endPosition = standoffTagV1.endPosition,
                            startIndex = standoffTagV1.startIndex.getOrElse(throw InconsistentTriplestoreDataException(s"start index is missing for a free standoff tag belonging to $valueIri")),
                            endIndex = standoffTagV1.endIndex.getOrElse(throw InconsistentTriplestoreDataException(s"end index is missing for a free standoff tag belonging to $valueIri")),
                            startParentIndex = standoffTagV1.startParentIndex,
                            endParentIndex = standoffTagV1.endParentIndex,
                            attributes = attributes.toSet
                        )
                    } else {
                        // it is a hierarchical standoff tag
                        HierarchicalStandoffTag(
                            tagName = xmlItemForStandoffClass.tagname,
                            xmlNamespace = xmlItemForStandoffClass.namespace match {
                                case `noNamespace` => None
                                case namespace => Some(namespace)
                            },
                            uuid = UUID.fromString(standoffTagV1.uuid.get), // TODO: this is an option in  knora-base! UUID should also be an option in StandoffUtil
                            startPosition = standoffTagV1.startPosition,
                            endPosition = standoffTagV1.endPosition,
                            index = standoffTagV1.startIndex.getOrElse(throw InconsistentTriplestoreDataException(s"start index is missing for a hierarchical standoff tag belonging to $valueIri")),
                            parentIndex = standoffTagV1.startParentIndex,
                            attributes = attributes.toSet
                        )
                    }

            }

            textWithStandoff = TextWithStandoff(text = textValue.utf8str, standoff = standoffTags)

            xml = standoffUtil.textWithStandoff2Xml(textWithStandoff)

        } yield StandoffGetResponseV1(xml = xml, userdata = userProfile.userData)

    }

}