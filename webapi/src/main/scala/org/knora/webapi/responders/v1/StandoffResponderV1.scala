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

package org.knora.webapi.responders.v1

import java.io.{File, IOException, StringReader}
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.{Schema, SchemaFactory, Validator => JValidator}

import akka.actor.Status
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCodes, _}
import akka.pattern._
import akka.stream.ActorMaterializer
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoByIRIGetRequestV1, ProjectInfoResponseV1}
import org.knora.webapi.messages.v1.responder.resourcemessages.{LocationV1, ResourceFullGetRequestV1, ResourceFullResponseV1}
import org.knora.webapi.messages.v1.responder.standoffmessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.twirl.{MappingElement, MappingStandoffDatatypeClass, MappingXMLAttribute}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.standoff.StandoffTagUtilV1.XMLTagItem
import org.knora.webapi.util.standoff._
import org.knora.webapi.util.{CacheUtil, KnoraIdUtil, MessageUtil, StringFormatter}
import org.knora.webapi.{BadRequestException, _}
import org.xml.sax.SAXException

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.xml.{Node, NodeSeq, XML}


/**
  * Responds to requests relating to the creation of mappings from XML elements and attributes to standoff classes and properties.
  */
class StandoffResponderV1 extends Responder {

    implicit val materializer = ActorMaterializer()

    // Converts SPARQL query results to ApiValueV1 objects.
    val valueUtilV1 = new ValueUtilV1(settings)

    /**
      * Receives a message of type [[StandoffResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case CreateMappingRequestV1(xml, label, projectIri, mappingName, userProfile, uuid) => future2Message(sender(), createMappingV1(xml, label, projectIri, mappingName, userProfile, uuid), log)
        case GetMappingRequestV1(mappingIri, userProfile) => future2Message(sender(), getMappingV1(mappingIri, userProfile), log)
        case GetXSLTransformationRequestV1(xsltTextReprIri, userProfile) => future2Message(sender(), getXSLTransformation(xsltTextReprIri, userProfile), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }


    val xsltCacheName = "xsltCache"

    /**
      * Retrieves a `knora-base:XSLTransformation` in the triplestore and requests the corresponding XSL file from Sipi.
      *
      * @param xslTransformationIri The IRI of the resource representing the XSL Transformation (a [[OntologyConstants.KnoraBase.XSLTransformation]]).
      * @param userProfile          The client making the request.
      * @return a [[GetXSLTransformationResponseV1]].
      */
    private def getXSLTransformation(xslTransformationIri: IRI, userProfile: UserProfileV1): Future[GetXSLTransformationResponseV1] = {

        val textLocationFuture: Future[LocationV1] = for {
            // get the `LocationV1` representing XSL transformation
            textRepresentationResponse: ResourceFullResponseV1 <- (responderManager ? ResourceFullGetRequestV1(iri = xslTransformationIri, userProfile = userProfile, getIncoming = false)).mapTo[ResourceFullResponseV1]

            textLocation: LocationV1 = textRepresentationResponse match {
                case textRepr: ResourceFullResponseV1 if textRepr.resinfo.isDefined && textRepr.resinfo.get.restype_id == OntologyConstants.KnoraBase.XSLTransformation =>
                    val locations: Seq[LocationV1] = textRepr.resinfo.get.locations.getOrElse(throw BadRequestException(s"no location given for $xslTransformationIri"))

                    locations.headOption.getOrElse(throw BadRequestException(s"no location given for $xslTransformationIri"))

                case other => throw BadRequestException(s"$xslTransformationIri is not an ${OntologyConstants.KnoraBase.XSLTransformation}")
            }

            // check if `textLocation` represents an XSL transformation
            _ = if (!(textLocation.format_name == "XML" && textLocation.origname.endsWith(".xsl"))) {
                throw BadRequestException(s"$xslTransformationIri does not have a file value referring to a XSL transformation")
            }
        } yield textLocation

        val recoveredTextLocationFuture = textLocationFuture.recover {
            case notFound: NotFoundException => throw BadRequestException(s"XSL transformation $xslTransformationIri not found: ${notFound.message}")
        }

        for {

            // check if the XSL transformation is in the cache
            textLocation <- recoveredTextLocationFuture

            // for PI to be able to communicate with SIPI, we need to use SIPI's internal url
            internalTextLocationPath = textLocation.path.replace(settings.externalSipiBaseUrl, settings.internalSipiBaseUrl)
            _ = println("StandoffResponderV1 - getXSLTransformation - original textLocation.path: {}", textLocation.path)
            _ = println("StandoffResponderV1 - getXSLTransformation - internalTextLocationPath: {}", internalTextLocationPath)

            xsltMaybe: Option[String] = CacheUtil.get[String](cacheName = xsltCacheName, key = internalTextLocationPath)

            xslt: String <- if (xsltMaybe.nonEmpty) {
                // XSL transformation is cached
                Future(xsltMaybe.get)
            } else {
                // ask SIPI to return the XSL transformation
                val sipiResponseFuture: Future[HttpResponse] = for {

                    // ask Sipi to return the XSL transformation file
                    response: HttpResponse <- Http().singleRequest(
                        HttpRequest(
                            method = HttpMethods.GET,
                            uri = internalTextLocationPath
                        )
                    )

                } yield response

                val sipiResponseFutureRecovered: Future[HttpResponse] = sipiResponseFuture.recoverWith {

                    case noResponse: akka.stream.scaladsl.TcpIdleTimeoutException =>
                        // this problem is hardly the user's fault. Create a SipiException
                        throw SipiException(message = "Sipi not reachable", e = noResponse, log = log)


                    // TODO: what other exceptions have to be handled here?
                    // if Exception is used, also previous errors are caught here

                }

                for {

                    sipiResponseRecovered: HttpResponse <- sipiResponseFutureRecovered

                    httpStatusCode: StatusCode = sipiResponseRecovered.status

                    messageBody <- sipiResponseRecovered.entity.toStrict(5.seconds)

                    _ = if (httpStatusCode != StatusCodes.OK) {
                        throw SipiException(s"Sipi returned status code ${httpStatusCode.intValue} with msg '${messageBody.data.decodeString("UTF-8")}'")
                    }

                    // get the XSL transformation
                    xslt: String = messageBody.data.decodeString("UTF-8")

                    textLocation <- textLocationFuture

                    _ = CacheUtil.put(cacheName = xsltCacheName, key = textLocation.path, value = xslt)

                } yield xslt
            }

        } yield GetXSLTransformationResponseV1(xslt = xslt)

    }

    /**
      * Creates a mapping between XML elements and attributes to standoff classes and properties.
      * The mapping is used to convert XML documents to [[TextValueV1]] and back.
      *
      * @param xml         the provided mapping.
      * @param userProfile the client that made the request.
      */
    private def createMappingV1(xml: String, label: String, projectIri: IRI, mappingName: String, userProfile: UserProfileV1, apiRequestID: UUID): Future[CreateMappingResponseV1] = {

        def createMappingAndCheck(xml: String, label: String, mappingIri: IRI, namedGraph: String, userProfile: UserProfileV1): Future[CreateMappingResponseV1] = {

            val knoraIdUtil = new KnoraIdUtil

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

                // the mapping conforms to the XML schema "src/main/resources/mappingXMLToStandoff.xsd"
                mappingXML = XML.loadString(xml)

                // get the default XSL transformation, if given (optional)
                defaultXSLTransformation: Option[IRI] <- mappingXML \ "defaultXSLTransformation" match {
                    case defaultTrans: NodeSeq if defaultTrans.length == 1 =>

                        // check if the IRI is valid
                        val transIri = stringFormatter.validateAndEscapeIri(defaultTrans.headOption.getOrElse(throw BadRequestException("could not access <defaultXSLTransformation>")).text, throw BadRequestException(s"XSL transformation ${defaultTrans.head.text} is not a valid IRI"))

                        // try to obtain the XSL transformation to make sure that it really exists
                        // TODO: add a test to the integration tests
                        for {
                            transform: GetXSLTransformationResponseV1 <- getXSLTransformation(transIri, userProfile)
                        } yield Some(transIri)
                    case _ => Future(None)
                }

                // create a collection of a all elements mappingElement
                mappingElementsXML: NodeSeq = mappingXML \ "mappingElement"

                mappingElements: Seq[MappingElement] = mappingElementsXML.map {

                    curMappingEle: Node =>

                        // get the name of the XML tag
                        val tagName = (curMappingEle \ "tag" \ "name").headOption.getOrElse(throw BadRequestException(s"no '<name>' given for node $curMappingEle")).text

                        // get the namespace the tag is defined in
                        val tagNamespace = (curMappingEle \ "tag" \ "namespace").headOption.getOrElse(throw BadRequestException(s"no '<namespace>' given for node $curMappingEle")).text

                        // get the class the tag is combined with
                        val className = (curMappingEle \ "tag" \ "class").headOption.getOrElse(throw BadRequestException(s"no '<classname>' given for node $curMappingEle")).text

                        // get the boolean indicating if the element requires a separator in the text once it is converted to standoff
                        val separatorBooleanAsString = (curMappingEle \ "tag" \ "separatesWords").headOption.getOrElse(throw BadRequestException(s"no '<separatesWords>' given for node $curMappingEle")).text

                        val separatorRequired: Boolean = stringFormatter.validateBoolean(separatorBooleanAsString, throw BadRequestException(s"<separatesWords> could not be converted to Boolean: $separatorBooleanAsString"))

                        // get the standoff class IRI
                        val standoffClassIri = (curMappingEle \ "standoffClass" \ "classIri").headOption.getOrElse(throw BadRequestException(s"no '<classIri>' given for node $curMappingEle")).text

                        // get a collection containing all the attributes
                        val attributeNodes: NodeSeq = curMappingEle \ "standoffClass" \ "attributes" \ "attribute"

                        val attributes: Seq[MappingXMLAttribute] = attributeNodes.map {

                            curAttributeNode =>

                                // get the current attribute's name
                                val attrName = (curAttributeNode \ "attributeName").headOption.getOrElse(throw BadRequestException(s"no '<attributeName>' given for attribute $curAttributeNode")).text

                                val attributeNamespace = (curAttributeNode \ "namespace").headOption.getOrElse(throw BadRequestException(s"no '<namespace>' given for attribute $curAttributeNode")).text

                                // get the standoff property IRI for the current attribute
                                val propIri = (curAttributeNode \ "propertyIri").headOption.getOrElse(throw BadRequestException(s"no '<propertyIri>' given for attribute $curAttributeNode")).text

                                MappingXMLAttribute(
                                    attributeName = stringFormatter.toSparqlEncodedString(attrName, throw BadRequestException(s"tagname $attrName contains invalid characters")),
                                    namespace = stringFormatter.toSparqlEncodedString(attributeNamespace, throw BadRequestException(s"tagname $attributeNamespace contains invalid characters")),
                                    standoffProperty = stringFormatter.validateAndEscapeIri(propIri, throw BadRequestException(s"standoff class IRI $standoffClassIri is not a valid IRI")),
                                    mappingXMLAttributeElementIri = knoraIdUtil.makeRandomMappingElementIri(mappingIri)
                                )

                        }

                        // get the optional element datatype
                        val datatypeMaybe: NodeSeq = curMappingEle \ "standoffClass" \ "datatype"

                        // if "datatype" is given, get the the standoff class data type and the name of the XML data type attribute
                        val standoffDataTypeOption: Option[MappingStandoffDatatypeClass] = if (datatypeMaybe.nonEmpty) {
                            val dataTypeXML = (datatypeMaybe \ "type").headOption.getOrElse(throw BadRequestException(s"no '<type>' given for datatype")).text

                            val dataType: StandoffDataTypeClasses.Value = StandoffDataTypeClasses.lookup(dataTypeXML, throw BadRequestException(s"Invalid data type provided for $tagName"))
                            val dataTypeAttribute: String = (datatypeMaybe \ "attributeName").headOption.getOrElse(throw BadRequestException(s"no '<attributeName>' given for datatype")).text

                            Some(MappingStandoffDatatypeClass(
                                datatype = dataType.toString, // safe because it is an enumeration
                                attributeName = stringFormatter.toSparqlEncodedString(dataTypeAttribute, throw BadRequestException(s"tagname $dataTypeAttribute contains invalid characters")),
                                mappingStandoffDataTypeClassElementIri = knoraIdUtil.makeRandomMappingElementIri(mappingIri)
                            ))
                        } else {
                            None
                        }

                        MappingElement(
                            tagName = stringFormatter.toSparqlEncodedString(tagName, throw BadRequestException(s"tagname $tagName contains invalid characters")),
                            namespace = stringFormatter.toSparqlEncodedString(tagNamespace, throw BadRequestException(s"namespace $tagNamespace contains invalid characters")),
                            className = stringFormatter.toSparqlEncodedString(className, throw BadRequestException(s"classname $className contains invalid characters")),
                            standoffClass = stringFormatter.validateAndEscapeIri(standoffClassIri, throw BadRequestException(s"standoff class IRI $standoffClassIri is not a valid IRI")),
                            attributes = attributes,
                            standoffDataTypeClass = standoffDataTypeOption,
                            mappingElementIri = knoraIdUtil.makeRandomMappingElementIri(mappingIri),
                            separatorRequired = separatorRequired
                        )


                }

                // transform mappingElements to the structure that is used internally to convert to or from standoff
                // in order to check for duplicates (checks are done during transformation)
                // TODO: find a way to provide a default XSL transformation when the mapping is created
                mappingXMLToStandoff: MappingXMLtoStandoff = transformMappingElementsToMappingXMLtoStandoff(mappingElements, None)

                // get the standoff entities used in the mapping
                // checks if the standoff classes exist in the ontology
                // checks if the standoff properties exist in the ontology
                // checks if the attributes defined for XML elements have cardinalities for the standoff properties defined on the standoff class
                _ <- getStandoffEntitiesFromMappingV1(mappingXMLToStandoff, userProfile)

                // check if the mapping IRI already exists
                getExistingMappingSparql = queries.sparql.v1.txt.getMapping(
                    triplestore = settings.triplestoreType,
                    mappingIri = mappingIri
                ).toString()
                existingMappingResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(getExistingMappingSparql)).mapTo[SparqlConstructResponse]

                _ = if (existingMappingResponse.statements.nonEmpty) {
                    throw BadRequestException(s"mapping IRI $mappingIri already exists")
                }

                createNewMappingSparql = queries.sparql.v1.txt.createNewMapping(
                    triplestore = settings.triplestoreType,
                    dataNamedGraph = namedGraph,
                    mappingIri = mappingIri,
                    label = label,
                    defaultXSLTransformation = defaultXSLTransformation,
                    mappingElements = mappingElements
                ).toString()

                // Do the update.
                createResourceResponse <- (storeManager ? SparqlUpdateRequest(createNewMappingSparql)).mapTo[SparqlUpdateResponse]

                // check if the mapping has been created
                newMappingResponse <- (storeManager ? SparqlConstructRequest(getExistingMappingSparql)).mapTo[SparqlConstructResponse]

                _ = if (newMappingResponse.statements.isEmpty) {
                    log.error(s"Attempted a SPARQL update to create a new resource, but it inserted no rows:\n\n$newMappingResponse")
                    throw UpdateNotPerformedException(s"Resource $mappingIri was not created. Please report this as a possible bug.")
                }

                // get the mapping from the triplestore and cache it thereby
                _ = getMappingFromTriplestore(mappingIri, userProfile)


            } yield {
                CreateMappingResponseV1(mappingIri = mappingIri)
            }


            createMappingFuture.recover {
                case validationException: SAXException => throw BadRequestException(s"the provided mapping is invalid: ${validationException.getMessage}")

                case ioException: IOException => throw NotFoundException(s"The schema could not be found")

                case unknown: Exception => throw BadRequestException(s"the provided mapping could not be handled correctly: ${unknown.getMessage}")

            }

        }

        for {
            // Don't allow anonymous users to create a mapping.
            userIri: IRI <- Future {
                userProfile.userData.user_id match {
                    case Some(iri) => iri
                    case None => throw ForbiddenException("Anonymous users aren't allowed to create mappings")
                }
            }

            // check if the given project IRI represents an actual project
            projectInfo: ProjectInfoResponseV1 <- (responderManager ? ProjectInfoByIRIGetRequestV1(
                iri = projectIri,
                Some(userProfile)
            )).mapTo[ProjectInfoResponseV1]

            knoraIdUtil = new KnoraIdUtil

            // TODO: make sure that has sufficient permissions to create a mapping in the given project

            // create the mapping IRI from the project IRI and the name provided by the user
            mappingIri = knoraIdUtil.makeProjectMappingIri(projectIri, mappingName)

            // put the mapping into the named graph of the project
            namedGraph = StringFormatter.getGeneralInstance.projectDataNamedGraph(projectInfo.project_info)

            result: CreateMappingResponseV1 <- IriLocker.runWithIriLock(
                apiRequestID,
                knoraIdUtil.createMappingLockIriForProject(projectIri), // use a special project specific IRI to lock the creation of mappings for the given project
                () => createMappingAndCheck(
                    xml = xml,
                    label = label,
                    mappingIri = mappingIri,
                    namedGraph = namedGraph,
                    userProfile = userProfile
                )
            )

        } yield result


    }

    /**
      * Transforms a mapping represented as a Seq of [[MappingElement]] to a [[MappingXMLtoStandoff]].
      * This method is called when reading a mapping back from the triplestore.
      *
      * @param mappingElements the Seq of MappingElement to be transformed.
      * @return a [[MappingXMLtoStandoff]].
      */
    private def transformMappingElementsToMappingXMLtoStandoff(mappingElements: Seq[MappingElement], defaultXSLTransformation: Option[IRI]): MappingXMLtoStandoff = {

        val mappingXMLToStandoff = mappingElements.foldLeft(MappingXMLtoStandoff(namespace = Map.empty[String, Map[String, Map[String, XMLTag]]], defaultXSLTransformation = None)) {
            case (acc: MappingXMLtoStandoff, curEle: MappingElement) =>

                // get the name of the XML tag
                val tagname = curEle.tagName

                // get the namespace the tag is defined in
                val namespace = curEle.namespace

                // get the class the tag is combined with
                val classname = curEle.className

                // get tags from this namespace if already existent, otherwise create an empty map
                val namespaceMap: Map[String, Map[String, XMLTag]] = acc.namespace.getOrElse(namespace, Map.empty[String, Map[String, XMLTag]])

                // get the standoff class IRI
                val standoffClassIri = curEle.standoffClass

                // get a collection containing all the attributes
                val attributeNodes: Seq[MappingXMLAttribute] = curEle.attributes

                // group attributes by their namespace
                val attributeNodesByNamespace: Map[String, Seq[MappingXMLAttribute]] = attributeNodes.groupBy {
                    (attr: MappingXMLAttribute) =>
                        attr.namespace
                }

                // create attribute entries for each given namespace
                val attributes: Map[String, Map[String, IRI]] = attributeNodesByNamespace.map {
                    case (namespace: String, attrNodes: Seq[MappingXMLAttribute]) =>

                        // collect all the attributes for the current namespace
                        val attributesInNamespace: Map[String, IRI] = attrNodes.foldLeft(Map.empty[String, IRI]) {
                            case (acc: Map[String, IRI], attrEle: MappingXMLAttribute) =>

                                // get the current attribute's name
                                val attrName = attrEle.attributeName

                                // check if the current attribute already exists in this namespace
                                if (acc.get(attrName).nonEmpty) {
                                    throw BadRequestException("Duplicate attribute name in namespace")
                                }

                                // get the standoff property IRI for the current attribute
                                val propIri = attrEle.standoffProperty

                                // add the current attribute to the collection
                                acc + (attrName -> propIri)
                        }

                        namespace -> attributesInNamespace
                }

                // if "datatype" is given, create a `XMLStandoffDataTypeClass`
                val dataTypeOption: Option[XMLStandoffDataTypeClass] = curEle.standoffDataTypeClass match {

                    case Some(dataTypeClass: MappingStandoffDatatypeClass) =>

                        val dataType = StandoffDataTypeClasses.lookup(dataTypeClass.datatype, throw BadRequestException(s"Invalid data type provided for $tagname"))

                        val dataTypeAttribute = dataTypeClass.attributeName

                        Some(XMLStandoffDataTypeClass(
                            standoffDataTypeClass = dataType,
                            dataTypeXMLAttribute = dataTypeAttribute
                        ))

                    case None => None
                }

                // add the current tag to the map
                val newNamespaceMap: Map[String, Map[String, XMLTag]] = namespaceMap.get(tagname) match {
                    case Some(tagMap: Map[String, XMLTag]) =>
                        tagMap.get(classname) match {
                            case Some(existingClassname) => throw BadRequestException("Duplicate tag and classname combination in the same namespace")
                            case None =>
                                // create the definition for the current element
                                val xmlElementDef = XMLTag(name = tagname, mapping = XMLTagToStandoffClass(standoffClassIri = standoffClassIri, attributesToProps = attributes, dataType = dataTypeOption), separatorRequired = curEle.separatorRequired)

                                // combine the definition for the this classname with the existing definitions beloning to the same element
                                val combinedClassDef: Map[String, XMLTag] = namespaceMap(tagname) + (classname -> xmlElementDef)

                                // combine all elements for this namespace
                                namespaceMap + (tagname -> combinedClassDef)

                        }
                    case None =>
                        namespaceMap + (tagname -> Map(classname -> XMLTag(name = tagname, mapping = XMLTagToStandoffClass(standoffClassIri = standoffClassIri, attributesToProps = attributes, dataType = dataTypeOption), separatorRequired = curEle.separatorRequired)))
                }

                // recreate the whole structure for all namespaces
                MappingXMLtoStandoff(
                    namespace = acc.namespace + (namespace -> newNamespaceMap),
                    defaultXSLTransformation = defaultXSLTransformation
                )

        }

        // invert mapping in order to run checks for duplicate use of
        // standoff class Iris and property Iris in the attributes for a standoff class
        StandoffTagUtilV1.invertXMLToStandoffMapping(mappingXMLToStandoff)

        mappingXMLToStandoff

    }

    /**
      * The name of the mapping cache.
      */
    val mappingCacheName = "mappingCache"

    /**
      * Gets a mapping either from the cache or by making a request to the triplestore.
      *
      * @param mappingIri  the IRI of the mapping to retrieve.
      * @param userProfile the user making the request.
      * @return a [[MappingXMLtoStandoff]].
      */
    private def getMappingV1(mappingIri: IRI, userProfile: UserProfileV1): Future[GetMappingResponseV1] = {

        for {

            mapping: GetMappingResponseV1 <- CacheUtil.get[MappingXMLtoStandoff](cacheName = mappingCacheName, key = mappingIri) match {
                case Some(mapping: MappingXMLtoStandoff) =>

                    for {

                        entities: StandoffEntityInfoGetResponseV1 <- getStandoffEntitiesFromMappingV1(mapping, userProfile)

                    } yield GetMappingResponseV1(
                        mappingIri = mappingIri,
                        mapping = mapping,
                        standoffEntities = entities
                    )

                case None =>

                    for {
                        mapping: MappingXMLtoStandoff <- getMappingFromTriplestore(mappingIri, userProfile)

                        entities: StandoffEntityInfoGetResponseV1 <- getStandoffEntitiesFromMappingV1(mapping, userProfile)

                    } yield GetMappingResponseV1(
                        mappingIri = mappingIri,
                        mapping = mapping,
                        standoffEntities = entities
                    )
            }

        } yield mapping

    }

    /**
      *
      * Gets a mapping from the triplestore.
      *
      * @param mappingIri  the IRI of the mapping to retrieve.
      * @param userProfile the user making the request.
      * @return a [[MappingXMLtoStandoff]].
      */
    private def getMappingFromTriplestore(mappingIri: IRI, userProfile: UserProfileV1): Future[MappingXMLtoStandoff] = {

        val getMappingSparql = queries.sparql.v1.txt.getMapping(
            triplestore = settings.triplestoreType,
            mappingIri = mappingIri
        ).toString()

        for {

            mappingResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(getMappingSparql)).mapTo[SparqlConstructResponse]

            // if the result is empty, the mapping does not exist
            _ = if (mappingResponse.statements.isEmpty) {
                throw BadRequestException(s"mapping $mappingIri does not exist")
            }

            // separate MappingElements from other statements (attributes and datatypes)
            (mappingElementStatements: Map[IRI, Seq[(IRI, String)]], otherStatements: Map[IRI, Seq[(IRI, String)]]) = mappingResponse.statements.partition {
                case (subjectIri: IRI, assertions: Seq[(IRI, String)]) =>

                    assertions.contains((OntologyConstants.Rdf.Type, OntologyConstants.KnoraBase.MappingElement))
            }

            mappingElements: Seq[MappingElement] = mappingElementStatements.map {
                case (subjectIri: IRI, assertions: Seq[(IRI, String)]) =>

                    // for convenience (works only for props with cardinality one)
                    val assertionsAsMap: Map[IRI, String] = assertions.toMap

                    // check for attributes
                    val attributes: Seq[MappingXMLAttribute] = assertions.filter {
                        case (propIri, obj) =>
                            propIri == OntologyConstants.KnoraBase.mappingHasXMLAttribute
                    }.map {
                        case (attrProp: IRI, attributeElementIri: String) =>

                            val attributeStatementsAsMap: Map[IRI, String] = otherStatements(attributeElementIri).toMap

                            MappingXMLAttribute(
                                attributeName = attributeStatementsAsMap(OntologyConstants.KnoraBase.mappingHasXMLAttributename),
                                namespace = attributeStatementsAsMap(OntologyConstants.KnoraBase.mappingHasXMLNamespace),
                                standoffProperty = attributeStatementsAsMap(OntologyConstants.KnoraBase.mappingHasStandoffProperty),
                                mappingXMLAttributeElementIri = attributeElementIri
                            )
                    }

                    // check for standoff data type class
                    val dataTypeOption: Option[IRI] = assertionsAsMap.get(OntologyConstants.KnoraBase.mappingHasStandoffDataTypeClass)

                    MappingElement(
                        tagName = assertionsAsMap(OntologyConstants.KnoraBase.mappingHasXMLTagname),
                        namespace = assertionsAsMap(OntologyConstants.KnoraBase.mappingHasXMLNamespace),
                        className = assertionsAsMap(OntologyConstants.KnoraBase.mappingHasXMLClass),
                        standoffClass = assertionsAsMap(OntologyConstants.KnoraBase.mappingHasStandoffClass),
                        mappingElementIri = subjectIri,
                        standoffDataTypeClass = dataTypeOption match {
                            case Some(dataTypeElementIri: IRI) =>

                                val dataTypeAssertionsAsMap: Map[IRI, String] = otherStatements(dataTypeElementIri).toMap

                                Some(MappingStandoffDatatypeClass(
                                    datatype = dataTypeAssertionsAsMap(OntologyConstants.KnoraBase.mappingHasStandoffClass),
                                    attributeName = dataTypeAssertionsAsMap(OntologyConstants.KnoraBase.mappingHasXMLAttributename),
                                    mappingStandoffDataTypeClassElementIri = dataTypeElementIri
                                ))
                            case None => None
                        },
                        attributes = attributes,
                        separatorRequired = assertionsAsMap(OntologyConstants.KnoraBase.mappingElementRequiresSeparator).toBoolean
                    )

            }.toSeq

            // check if there is a default XSL transformation
            defaultXSLTransformationOption: Option[IRI] = otherStatements(mappingIri).find {
                case (pred: IRI, obj: String) =>
                    pred == OntologyConstants.KnoraBase.mappingHasDefaultXSLTransformation
            }.map {
                case (hasDefaultTransformation: IRI, xslTransformationIri: IRI) =>
                    xslTransformationIri
            }

            mappingXMLToStandoff = transformMappingElementsToMappingXMLtoStandoff(mappingElements, defaultXSLTransformationOption)

            // add the mapping to the cache
            _ = CacheUtil.put(cacheName = mappingCacheName, key = mappingIri, value = mappingXMLToStandoff)

        } yield mappingXMLToStandoff

    }

    /**
      * Gets the required standoff entities (classes and properties) from the mapping and requests information about these entities from the ontology responder.
      *
      * @param mappingXMLtoStandoff the mapping to be used.
      * @param userProfile          the client that made the request.
      * @return a [[StandoffEntityInfoGetResponseV1]] holding information about standoff classes and properties.
      */
    private def getStandoffEntitiesFromMappingV1(mappingXMLtoStandoff: MappingXMLtoStandoff, userProfile: UserProfileV1): Future[StandoffEntityInfoGetResponseV1] = {

        // invert the mapping so standoff class Iris become keys
        val mappingStandoffToXML: Map[IRI, XMLTagItem] = StandoffTagUtilV1.invertXMLToStandoffMapping(mappingXMLtoStandoff)

        // collect standoff class Iris from the mapping
        val standoffTagIrisFromMapping = mappingStandoffToXML.keySet

        // collect all the standoff property Iris from the mapping
        val standoffPropertyIrisFromMapping = mappingStandoffToXML.values.foldLeft(Set.empty[IRI]) {
            (acc: Set[IRI], xmlTag: XMLTagItem) =>
                acc ++ xmlTag.attributes.keySet
        }

        // make sure that the mapping does not contain system or data type standoff properties as attributes
        // these standoff properties can only be used via the standoff base tag and standoff data type classes
        val systemOrDatatypePropsAsAttr: Set[IRI] = standoffPropertyIrisFromMapping.intersect(StandoffProperties.systemProperties ++ StandoffProperties.dataTypeProperties)
        if (systemOrDatatypePropsAsAttr.nonEmpty) throw InvalidStandoffException(s"attempt to define attributes for system or data type properties: ${systemOrDatatypePropsAsAttr.mkString(", ")}")

        for {

            // request information about standoff classes that should be created
            standoffClassEntities: StandoffEntityInfoGetResponseV1 <- (responderManager ? StandoffEntityInfoGetRequestV1(standoffClassIris = standoffTagIrisFromMapping, userProfile = userProfile)).mapTo[StandoffEntityInfoGetResponseV1]

            // check that the ontology responder returned the information for all the standoff classes it was asked for
            // if the ontology responder does not return a standoff class it was asked for, then this standoff class does not exist
            _ = if (standoffTagIrisFromMapping != standoffClassEntities.standoffClassInfoMap.keySet) {
                throw NotFoundException(s"the ontology responder could not find information about these standoff classes: ${(standoffTagIrisFromMapping -- standoffClassEntities.standoffClassInfoMap.keySet).mkString(", ")}")
            }

            // get the property Iris that are defined on the standoff classes returned by the ontology responder
            standoffPropertyIrisFromOntologyResponder = standoffClassEntities.standoffClassInfoMap.foldLeft(Set.empty[IRI]) {
                case (acc, (standoffClassIri, standoffClassEntity)) =>
                    val props = standoffClassEntity.allCardinalities.keySet
                    acc ++ props
            }

            // request information about the standoff properties
            standoffPropertyEntities: StandoffEntityInfoGetResponseV1 <- (responderManager ? StandoffEntityInfoGetRequestV1(standoffPropertyIris = standoffPropertyIrisFromOntologyResponder, userProfile = userProfile)).mapTo[StandoffEntityInfoGetResponseV1]

            // check that the ontology responder returned the information for all the standoff properties it was asked for
            // if the ontology responder does not return a standoff property it was asked for, then this standoff property does not exist
            propertyDefinitionsFromMappingFoundInOntology: Set[IRI] = standoffPropertyEntities.standoffPropertyInfoMap.keySet.intersect(standoffPropertyIrisFromMapping)

            _ = if (standoffPropertyIrisFromMapping != propertyDefinitionsFromMappingFoundInOntology) {
                throw NotFoundException(s"the ontology responder could not find information about these standoff properties: " +
                    s"${(standoffPropertyIrisFromMapping -- propertyDefinitionsFromMappingFoundInOntology).mkString(", ")}")
            }

            // check that for each standoff property defined in the mapping element for a standoff class, a corresponding cardinality exists in the ontology
            _ = mappingStandoffToXML.foreach {
                case (standoffClass: IRI, xmlTag: XMLTagItem) =>
                    // collect all the standoff properties defined for this standoff class
                    val standoffPropertiesForStandoffClass: Set[IRI] = xmlTag.attributes.keySet

                    // check that the current standoff class has cardinalities for all the properties defined
                    val cardinalitiesFound = standoffClassEntities.standoffClassInfoMap(standoffClass).allCardinalities.keySet.intersect(standoffPropertiesForStandoffClass)

                    if (standoffPropertiesForStandoffClass != cardinalitiesFound) {
                        throw NotFoundException(s"the following standoff properties have no cardinality for $standoffClass: ${(standoffPropertiesForStandoffClass -- cardinalitiesFound).mkString(", ")}")
                    }

                    // collect the required standoff properties for the standoff class
                    val requiredPropsForClass = standoffClassEntities.standoffClassInfoMap(standoffClass).allCardinalities.filter {
                        case (property: IRI, card: KnoraCardinalityInfo) =>
                            card.cardinality == Cardinality.MustHaveOne || card.cardinality == Cardinality.MustHaveSome
                    }.keySet -- StandoffProperties.systemProperties -- StandoffProperties.dataTypeProperties

                    // check that all the required standoff properties exist in the mapping
                    if (standoffPropertiesForStandoffClass.intersect(requiredPropsForClass) != requiredPropsForClass) {
                        throw NotFoundException(s"the following required standoff properties are not defined for the standoff class $standoffClass: ${(requiredPropsForClass -- standoffPropertiesForStandoffClass).mkString(", ")}")
                    }

                    // check if the standoff class's data type is correct in the mapping
                    standoffClassEntities.standoffClassInfoMap(standoffClass).standoffDataType match {
                        case Some(dataType: StandoffDataTypeClasses.Value) =>
                            // check if this corresponds to the datatype in the mapping
                            val dataTypeFromMapping: XMLStandoffDataTypeClass = xmlTag.tagItem.mapping.dataType.getOrElse(throw InvalidStandoffException(s"no data type provided for $standoffClass, but $dataType required"))
                            if (dataTypeFromMapping.standoffDataTypeClass != dataType) {
                                throw InvalidStandoffException(s"wrong data type ${dataTypeFromMapping.standoffDataTypeClass} provided for $standoffClass, but $dataType required")
                            }
                        case None =>
                            if (xmlTag.tagItem.mapping.dataType.nonEmpty) {
                                throw InvalidStandoffException(s"no data type expected for $standoffClass, but ${xmlTag.tagItem.mapping.dataType.get.standoffDataTypeClass} given")
                            }
                    }

            }


        } yield StandoffEntityInfoGetResponseV1(
            standoffClassInfoMap = standoffClassEntities.standoffClassInfoMap,
            standoffPropertyInfoMap = standoffPropertyEntities.standoffPropertyInfoMap
        )

    }

}