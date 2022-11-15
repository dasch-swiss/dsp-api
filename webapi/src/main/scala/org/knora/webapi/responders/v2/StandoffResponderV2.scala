/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import akka.pattern._
import akka.util.Timeout
import org.xml.sax.SAXException

import java.io._
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory
import javax.xml.validation.{Validator => JValidator}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import scala.xml.XML

import dsp.errors._
import dsp.schema.domain.Cardinality._
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileRequest
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileResponse
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.twirl.MappingElement
import org.knora.webapi.messages.twirl.MappingStandoffDatatypeClass
import org.knora.webapi.messages.twirl.MappingXMLAttribute
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2.XMLTagItem
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality._
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.StandoffEntityInfoGetRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.StandoffEntityInfoGetResponseV2
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.Responder.handleUnexpectedMessage
import org.knora.webapi.util._
import org.knora.webapi.util.cache.CacheUtil

/**
 * Responds to requests relating to the creation of mappings from XML elements and attributes to standoff classes and properties.
 */
class StandoffResponderV2(responderData: ResponderData) extends Responder(responderData) {

  private def xmlMimeTypes = Set(
    "text/xml",
    "application/xml"
  )

  /**
   * Receives a message of type [[StandoffResponderRequestV2]], and returns an appropriate response message.
   */
  def receive(msg: StandoffResponderRequestV2) = msg match {
    case getStandoffPageRequestV2: GetStandoffPageRequestV2 => getStandoffV2(getStandoffPageRequestV2)
    case getRemainingStandoffFromTextValueRequestV2: GetRemainingStandoffFromTextValueRequestV2 =>
      getRemainingStandoffFromTextValueV2(getRemainingStandoffFromTextValueRequestV2)
    case CreateMappingRequestV2(metadata, xml, requestingUser, uuid) =>
      createMappingV2(
        xml.xml,
        metadata.label,
        metadata.projectIri,
        metadata.mappingName,
        requestingUser,
        uuid
      )
    case GetMappingRequestV2(mappingIri, requestingUser) =>
      getMappingV2(mappingIri, requestingUser)
    case GetXSLTransformationRequestV2(xsltTextReprIri, requestingUser) =>
      getXSLTransformation(xsltTextReprIri, requestingUser)
    case other => handleUnexpectedMessage(other, log, this.getClass.getName)
  }

  private val xsltCacheName = "xsltCache"

  private def getStandoffV2(getStandoffRequestV2: GetStandoffPageRequestV2): Future[GetStandoffResponseV2] = {
    val requestMaxStartIndex = getStandoffRequestV2.offset + responderData.appConfig.standoffPerPage - 1

    for {
      resourceRequestSparql <- Future(
                                 org.knora.webapi.messages.twirl.queries.sparql.v2.txt
                                   .getResourcePropertiesAndValues(
                                     resourceIris = Seq(getStandoffRequestV2.resourceIri),
                                     preview = false,
                                     withDeleted = false,
                                     maybePropertyIri = None,
                                     maybeVersionDate = None,
                                     queryAllNonStandoff = false,
                                     maybeValueIri = Some(getStandoffRequestV2.valueIri),
                                     maybeStandoffMinStartIndex = Some(getStandoffRequestV2.offset),
                                     maybeStandoffMaxStartIndex = Some(requestMaxStartIndex),
                                     stringFormatter = stringFormatter
                                   )
                                   .toString()
                               )

      // _ = println("=================================")
      // _ = println(resourceRequestSparql)

      // standoffPageStartTime = System.currentTimeMillis()

      resourceRequestResponse: SparqlExtendedConstructResponse <-
        appActor
          .ask(
            SparqlExtendedConstructRequest(
              sparql = resourceRequestSparql
            )
          )
          .mapTo[SparqlExtendedConstructResponse]

      // standoffPageEndTime = System.currentTimeMillis()

      // _ = println(s"Got a page of standoff in ${standoffPageEndTime - standoffPageStartTime} ms")

      // separate resources and values
      mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
        ConstructResponseUtilV2
          .splitMainResourcesAndValueRdfData(
            constructQueryResults = resourceRequestResponse,
            requestingUser = getStandoffRequestV2.requestingUser
          )

      readResourcesSequenceV2: ReadResourcesSequenceV2 <- ConstructResponseUtilV2.createApiResponse(
                                                            mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                                                            orderByResourceIri = Seq(getStandoffRequestV2.resourceIri),
                                                            pageSizeBeforeFiltering =
                                                              1, // doesn't matter because we're not doing paging
                                                            mappings = Map.empty,
                                                            queryStandoff = false,
                                                            calculateMayHaveMoreResults = false,
                                                            versionDate = None,
                                                            appActor = appActor,
                                                            targetSchema = getStandoffRequestV2.targetSchema,
                                                            appConfig = responderData.appConfig,
                                                            requestingUser = getStandoffRequestV2.requestingUser
                                                          )

      readResourceV2 = readResourcesSequenceV2.toResource(getStandoffRequestV2.resourceIri)

      valueObj: ReadValueV2 =
        readResourceV2.values.values.flatten
          .find(_.valueIri == getStandoffRequestV2.valueIri)
          .getOrElse(
            throw NotFoundException(
              s"Value <${getStandoffRequestV2.valueIri}> not found in resource <${getStandoffRequestV2.resourceIri}> (maybe you do not have permission to see it, or it is marked as deleted)"
            )
          )

      textValueObj: ReadTextValueV2 = valueObj match {
                                        case textVal: ReadTextValueV2 => textVal
                                        case _ =>
                                          throw BadRequestException(
                                            s"Value <${getStandoffRequestV2.valueIri}> not found in resource <${getStandoffRequestV2.resourceIri}> is not a text value"
                                          )
                                      }

      nextOffset: Option[Int] = textValueObj.valueHasMaxStandoffStartIndex match {
                                  case Some(definedMaxIndex) =>
                                    if (requestMaxStartIndex >= definedMaxIndex) {
                                      None
                                    } else {
                                      Some(requestMaxStartIndex + 1)
                                    }

                                  case None => None
                                }
    } yield GetStandoffResponseV2(
      valueIri = textValueObj.valueIri,
      standoff = textValueObj.valueContent.standoff,
      nextOffset = nextOffset
    )
  }

  /**
   * If not already in the cache, retrieves a `knora-base:XSLTransformation` in the triplestore and requests the corresponding XSL transformation file from Sipi.
   *
   * @param xslTransformationIri the IRI of the resource representing the XSL Transformation (a [[OntologyConstants.KnoraBase.XSLTransformation]]).
   *
   * @param requestingUser       the user making the request.
   * @return a [[GetXSLTransformationResponseV2]].
   */
  private def getXSLTransformation(
    xslTransformationIri: IRI,
    requestingUser: UserADM
  ): Future[GetXSLTransformationResponseV2] = {

    val xsltUrlFuture = for {

      textRepresentationResponseV2: ReadResourcesSequenceV2 <-
        appActor
          .ask(
            ResourcesGetRequestV2(
              resourceIris = Vector(xslTransformationIri),
              targetSchema = ApiV2Complex,
              requestingUser = requestingUser
            )
          )
          .mapTo[ReadResourcesSequenceV2]

      resource = textRepresentationResponseV2.toResource(xslTransformationIri)

      _ = if (resource.resourceClassIri.toString != OntologyConstants.KnoraBase.XSLTransformation) {
            throw BadRequestException(
              s"Resource $xslTransformationIri is not a ${OntologyConstants.KnoraBase.XSLTransformation}"
            )
          }

      (fileValueIri: IRI, xsltFileValueContent: TextFileValueContentV2) =
        resource.values.get(
          OntologyConstants.KnoraBase.HasTextFileValue.toSmartIri
        ) match {
          case Some(values: Seq[ReadValueV2]) if values.size == 1 =>
            values.head match {
              case value: ReadValueV2 =>
                value.valueContent match {
                  case textRepr: TextFileValueContentV2 => (value.valueIri, textRepr)
                  case _ =>
                    throw InconsistentRepositoryDataException(
                      s"${OntologyConstants.KnoraBase.XSLTransformation} $xslTransformationIri is supposed to have exactly one value of type ${OntologyConstants.KnoraBase.TextFileValue}"
                    )
                }
            }

          case _ =>
            throw InconsistentRepositoryDataException(
              s"${OntologyConstants.KnoraBase.XSLTransformation} has no or more than one property ${OntologyConstants.KnoraBase.HasTextFileValue}"
            )
        }

      // check if xsltFileValueContent represents an XSL transformation
      _ = if (!xmlMimeTypes.contains(xsltFileValueContent.fileValue.internalMimeType)) {
            throw BadRequestException(
              s"Expected $fileValueIri to be an XML file referring to an XSL transformation, but it has MIME type ${xsltFileValueContent.fileValue.internalMimeType}"
            )
          }

      xsltUrl: String =
        s"${responderData.appConfig.sipi.internalBaseUrl}/${resource.projectADM.shortcode}/${xsltFileValueContent.fileValue.internalFilename}/file"

    } yield xsltUrl

    val recoveredXsltUrlFuture = xsltUrlFuture.recover { case notFound: NotFoundException =>
      throw BadRequestException(s"XSL transformation $xslTransformationIri not found: ${notFound.message}")
    }

    for {

      // check if the XSL transformation is in the cache
      xsltFileUrl <- recoveredXsltUrlFuture

      xsltMaybe: Option[String] = CacheUtil.get[String](cacheName = xsltCacheName, key = xsltFileUrl)

      xslt: String <-
        if (xsltMaybe.nonEmpty) {
          // XSL transformation is cached
          Future(xsltMaybe.get)
        } else {
          for {
            response: SipiGetTextFileResponse <- appActor
                                                   .ask(
                                                     SipiGetTextFileRequest(
                                                       fileUrl = xsltFileUrl,
                                                       requestingUser = KnoraSystemInstances.Users.SystemUser,
                                                       senderName = this.getClass.getName
                                                     )
                                                   )
                                                   .mapTo[SipiGetTextFileResponse]
            _ = CacheUtil.put(cacheName = xsltCacheName, key = xsltFileUrl, value = response.content)
          } yield response.content
        }

    } yield GetXSLTransformationResponseV2(xslt = xslt)

  }

  /**
   * Creates a mapping between XML elements and attributes to standoff classes and properties.
   * The mapping is used to convert XML documents to texts with standoff and back.
   *
   * @param xml                  the provided mapping.
   *
   * @param requestingUser       the client that made the request.
   */
  private def createMappingV2(
    xml: String,
    label: String,
    projectIri: SmartIri,
    mappingName: String,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Future[CreateMappingResponseV2] = {

    def createMappingAndCheck(
      xml: String,
      label: String,
      mappingIri: IRI,
      namedGraph: String,
      requestingUser: UserADM
    ): Future[CreateMappingResponseV2] = {

      val createMappingFuture = for {

        factory <- Future(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI))

        // get the schema the mapping has to be validated against
        schemaFile: String = FileUtil.readTextResource("mappingXMLToStandoff.xsd")

        schemaSource: StreamSource = new StreamSource(new StringReader(schemaFile))

        // create a schema instance
        schemaInstance: Schema = factory.newSchema(schemaSource)
        validator: JValidator  = schemaInstance.newValidator()

        // validate the provided mapping
        _ = validator.validate(new StreamSource(new StringReader(xml)))

        // the mapping conforms to the XML schema "src/main/resources/mappingXMLToStandoff.xsd"
        mappingXML: Elem = XML.loadString(xml)

        // get the default XSL transformation, if given (optional)
        defaultXSLTransformation: Option[IRI] <- mappingXML \ "defaultXSLTransformation" match {
                                                   case defaultTrans: NodeSeq if defaultTrans.length == 1 =>
                                                     // check if the IRI is valid
                                                     val transIri = stringFormatter.validateAndEscapeIri(
                                                       defaultTrans.headOption
                                                         .getOrElse(
                                                           throw BadRequestException(
                                                             "could not access <defaultXSLTransformation>"
                                                           )
                                                         )
                                                         .text,
                                                       throw BadRequestException(
                                                         s"XSL transformation ${defaultTrans.head.text} is not a valid IRI"
                                                       )
                                                     )

                                                     // try to obtain the XSL transformation to make sure that it really exists
                                                     for {
                                                       transform: GetXSLTransformationResponseV2 <-
                                                         getXSLTransformation(
                                                           xslTransformationIri = transIri,
                                                           requestingUser = requestingUser
                                                         )
                                                     } yield Some(transIri)
                                                   case _ => Future(None)
                                                 }

        // create a collection of a all elements mappingElement
        mappingElementsXML: NodeSeq = mappingXML \ "mappingElement"

        mappingElements: Seq[MappingElement] = mappingElementsXML.map { curMappingEle: Node =>
                                                 // get the name of the XML tag
                                                 val tagName = (curMappingEle \ "tag" \ "name").headOption
                                                   .getOrElse(
                                                     throw BadRequestException(
                                                       s"no '<name>' given for node $curMappingEle"
                                                     )
                                                   )
                                                   .text

                                                 // get the namespace the tag is defined in
                                                 val tagNamespace = (curMappingEle \ "tag" \ "namespace").headOption
                                                   .getOrElse(
                                                     throw BadRequestException(
                                                       s"no '<namespace>' given for node $curMappingEle"
                                                     )
                                                   )
                                                   .text

                                                 // get the class the tag is combined with
                                                 val className = (curMappingEle \ "tag" \ "class").headOption
                                                   .getOrElse(
                                                     throw BadRequestException(
                                                       s"no '<classname>' given for node $curMappingEle"
                                                     )
                                                   )
                                                   .text

                                                 // get the boolean indicating if the element requires a separator in the text once it is converted to standoff
                                                 val separatorBooleanAsString =
                                                   (curMappingEle \ "tag" \ "separatesWords").headOption
                                                     .getOrElse(
                                                       throw BadRequestException(
                                                         s"no '<separatesWords>' given for node $curMappingEle"
                                                       )
                                                     )
                                                     .text

                                                 val separatorRequired: Boolean = stringFormatter.validateBoolean(
                                                   separatorBooleanAsString,
                                                   throw BadRequestException(
                                                     s"<separatesWords> could not be converted to Boolean: $separatorBooleanAsString"
                                                   )
                                                 )

                                                 // get the standoff class IRI
                                                 val standoffClassIri =
                                                   (curMappingEle \ "standoffClass" \ "classIri").headOption
                                                     .getOrElse(
                                                       throw BadRequestException(
                                                         s"no '<classIri>' given for node $curMappingEle"
                                                       )
                                                     )
                                                     .text

                                                 // get a collection containing all the attributes
                                                 val attributeNodes: NodeSeq =
                                                   curMappingEle \ "standoffClass" \ "attributes" \ "attribute"

                                                 val attributes: Seq[MappingXMLAttribute] = attributeNodes.map {
                                                   curAttributeNode =>
                                                     // get the current attribute's name
                                                     val attrName = (curAttributeNode \ "attributeName").headOption
                                                       .getOrElse(
                                                         throw BadRequestException(
                                                           s"no '<attributeName>' given for attribute $curAttributeNode"
                                                         )
                                                       )
                                                       .text

                                                     val attributeNamespace =
                                                       (curAttributeNode \ "namespace").headOption
                                                         .getOrElse(
                                                           throw BadRequestException(
                                                             s"no '<namespace>' given for attribute $curAttributeNode"
                                                           )
                                                         )
                                                         .text

                                                     // get the standoff property IRI for the current attribute
                                                     val propIri = (curAttributeNode \ "propertyIri").headOption
                                                       .getOrElse(
                                                         throw BadRequestException(
                                                           s"no '<propertyIri>' given for attribute $curAttributeNode"
                                                         )
                                                       )
                                                       .text

                                                     MappingXMLAttribute(
                                                       attributeName = stringFormatter.toSparqlEncodedString(
                                                         attrName,
                                                         throw BadRequestException(
                                                           s"tagname $attrName contains invalid characters"
                                                         )
                                                       ),
                                                       namespace = stringFormatter.toSparqlEncodedString(
                                                         attributeNamespace,
                                                         throw BadRequestException(
                                                           s"tagname $attributeNamespace contains invalid characters"
                                                         )
                                                       ),
                                                       standoffProperty = stringFormatter.validateAndEscapeIri(
                                                         propIri,
                                                         throw BadRequestException(
                                                           s"standoff class IRI $standoffClassIri is not a valid IRI"
                                                         )
                                                       ),
                                                       mappingXMLAttributeElementIri =
                                                         stringFormatter.makeRandomMappingElementIri(mappingIri)
                                                     )

                                                 }

                                                 // get the optional element datatype
                                                 val datatypeMaybe: NodeSeq =
                                                   curMappingEle \ "standoffClass" \ "datatype"

                                                 // if "datatype" is given, get the the standoff class data type and the name of the XML data type attribute
                                                 val standoffDataTypeOption: Option[MappingStandoffDatatypeClass] =
                                                   if (datatypeMaybe.nonEmpty) {
                                                     val dataTypeXML = (datatypeMaybe \ "type").headOption
                                                       .getOrElse(
                                                         throw BadRequestException(s"no '<type>' given for datatype")
                                                       )
                                                       .text

                                                     val dataType: StandoffDataTypeClasses.Value =
                                                       StandoffDataTypeClasses.lookup(
                                                         dataTypeXML,
                                                         throw BadRequestException(
                                                           s"Invalid data type provided for $tagName"
                                                         )
                                                       )
                                                     val dataTypeAttribute: String =
                                                       (datatypeMaybe \ "attributeName").headOption
                                                         .getOrElse(
                                                           throw BadRequestException(
                                                             s"no '<attributeName>' given for datatype"
                                                           )
                                                         )
                                                         .text

                                                     Some(
                                                       MappingStandoffDatatypeClass(
                                                         datatype =
                                                           dataType.toString, // safe because it is an enumeration
                                                         attributeName = stringFormatter.toSparqlEncodedString(
                                                           dataTypeAttribute,
                                                           throw BadRequestException(
                                                             s"tagname $dataTypeAttribute contains invalid characters"
                                                           )
                                                         ),
                                                         mappingStandoffDataTypeClassElementIri =
                                                           stringFormatter.makeRandomMappingElementIri(mappingIri)
                                                       )
                                                     )
                                                   } else {
                                                     None
                                                   }

                                                 MappingElement(
                                                   tagName = stringFormatter.toSparqlEncodedString(
                                                     tagName,
                                                     throw BadRequestException(
                                                       s"tagname $tagName contains invalid characters"
                                                     )
                                                   ),
                                                   namespace = stringFormatter.toSparqlEncodedString(
                                                     tagNamespace,
                                                     throw BadRequestException(
                                                       s"namespace $tagNamespace contains invalid characters"
                                                     )
                                                   ),
                                                   className = stringFormatter.toSparqlEncodedString(
                                                     className,
                                                     throw BadRequestException(
                                                       s"classname $className contains invalid characters"
                                                     )
                                                   ),
                                                   standoffClass = stringFormatter.validateAndEscapeIri(
                                                     standoffClassIri,
                                                     throw BadRequestException(
                                                       s"standoff class IRI $standoffClassIri is not a valid IRI"
                                                     )
                                                   ),
                                                   attributes = attributes,
                                                   standoffDataTypeClass = standoffDataTypeOption,
                                                   mappingElementIri =
                                                     stringFormatter.makeRandomMappingElementIri(mappingIri),
                                                   separatorRequired = separatorRequired
                                                 )

                                               }

        // transform mappingElements to the structure that is used internally to convert to or from standoff
        // in order to check for duplicates (checks are done during transformation)
        mappingXMLToStandoff: MappingXMLtoStandoff = transformMappingElementsToMappingXMLtoStandoff(
                                                       mappingElements,
                                                       None
                                                     )

        // get the standoff entities used in the mapping
        // checks if the standoff classes exist in the ontology
        // checks if the standoff properties exist in the ontology
        // checks if the attributes defined for XML elements have cardinalities for the standoff properties defined on the standoff class
        _ <- getStandoffEntitiesFromMappingV2(mappingXMLToStandoff, requestingUser)

        // check if the mapping IRI already exists
        getExistingMappingSparql = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
                                     .getMapping(
                                       mappingIri = mappingIri
                                     )
                                     .toString()

        existingMappingResponse: SparqlConstructResponse <- appActor
                                                              .ask(
                                                                SparqlConstructRequest(
                                                                  sparql = getExistingMappingSparql
                                                                )
                                                              )
                                                              .mapTo[SparqlConstructResponse]

        _ = if (existingMappingResponse.statements.nonEmpty) {
              throw BadRequestException(s"mapping IRI $mappingIri already exists")
            }

        createNewMappingSparql = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
                                   .createNewMapping(
                                     dataNamedGraph = namedGraph,
                                     mappingIri = mappingIri,
                                     label = label,
                                     defaultXSLTransformation = defaultXSLTransformation,
                                     mappingElements = mappingElements
                                   )
                                   .toString()

        // Do the update.
        createResourceResponse: SparqlUpdateResponse <- appActor
                                                          .ask(SparqlUpdateRequest(createNewMappingSparql))
                                                          .mapTo[SparqlUpdateResponse]

        // check if the mapping has been created
        newMappingResponse <- appActor
                                .ask(
                                  SparqlConstructRequest(
                                    sparql = getExistingMappingSparql
                                  )
                                )
                                .mapTo[SparqlConstructResponse]

        _ = if (newMappingResponse.statements.isEmpty) {
              log.error(
                s"Attempted a SPARQL update to create a new resource, but it inserted no rows:\n\n$newMappingResponse"
              )
              throw UpdateNotPerformedException(
                s"Resource $mappingIri was not created. Please report this as a possible bug."
              )
            }

        // get the mapping from the triplestore and cache it thereby
        _ = getMappingFromTriplestore(
              mappingIri = mappingIri,
              requestingUser = requestingUser
            )
      } yield {
        CreateMappingResponseV2(
          mappingIri = mappingIri,
          label = label,
          projectIri = projectIri
        )
      }

      createMappingFuture.recover {
        case validationException: SAXException =>
          throw BadRequestException(s"the provided mapping is invalid: ${validationException.getMessage}")

        case _: IOException => throw NotFoundException(s"The schema could not be found")

        case unknown: Exception =>
          throw BadRequestException(s"the provided mapping could not be handled correctly: ${unknown.getMessage}")

      }

    }

    for {
      // Don't allow anonymous users to create a mapping.
      userIri: IRI <- Future {
                        if (requestingUser.isAnonymousUser) {
                          throw ForbiddenException("Anonymous users aren't allowed to create mappings")
                        } else {
                          requestingUser.id
                        }
                      }

      // check if the given project IRI represents an actual project
      projectInfoMaybe: Option[ProjectADM] <-
        appActor
          .ask(
            ProjectGetADM(
              identifier = ProjectIdentifierADM.Iri
                .fromString(projectIri.toString)
                .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
            )
          )
          .mapTo[Option[ProjectADM]]

      // TODO: make sure that has sufficient permissions to create a mapping in the given project

      // create the mapping IRI from the project IRI and the name provided by the user
      mappingIri = stringFormatter.makeProjectMappingIri(projectIri.toString, mappingName)

      _ = if (projectInfoMaybe.isEmpty)
            throw BadRequestException(s"Project with Iri ${projectIri.toString} does not exist")

      // put the mapping into the named graph of the project
      namedGraph = StringFormatter.getGeneralInstance.projectDataNamedGraphV2(projectInfoMaybe.get)

      result: CreateMappingResponseV2 <- IriLocker.runWithIriLock(
                                           apiRequestID,
                                           stringFormatter
                                             .createMappingLockIriForProject(
                                               projectIri.toString
                                             ), // use a special project specific IRI to lock the creation of mappings for the given project
                                           () =>
                                             createMappingAndCheck(
                                               xml = xml,
                                               label = label,
                                               mappingIri = mappingIri,
                                               namedGraph = namedGraph,
                                               requestingUser = requestingUser
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
  private def transformMappingElementsToMappingXMLtoStandoff(
    mappingElements: Seq[MappingElement],
    defaultXSLTransformation: Option[IRI]
  ): MappingXMLtoStandoff = {

    val mappingXMLToStandoff = mappingElements.foldLeft(
      MappingXMLtoStandoff(
        namespace = Map.empty[String, Map[String, Map[String, XMLTag]]],
        defaultXSLTransformation = None
      )
    ) { case (acc: MappingXMLtoStandoff, curEle: MappingElement) =>
      // get the name of the XML tag
      val tagname = curEle.tagName

      // get the namespace the tag is defined in
      val namespace = curEle.namespace

      // get the class the tag is combined with
      val classname = curEle.className

      // get tags from this namespace if already existent, otherwise create an empty map
      val namespaceMap: Map[String, Map[String, XMLTag]] =
        acc.namespace.getOrElse(namespace, Map.empty[String, Map[String, XMLTag]])

      // get the standoff class IRI
      val standoffClassIri = curEle.standoffClass

      // get a collection containing all the attributes
      val attributeNodes: Seq[MappingXMLAttribute] = curEle.attributes

      // group attributes by their namespace
      val attributeNodesByNamespace: Map[String, Seq[MappingXMLAttribute]] = attributeNodes.groupBy {
        attr: MappingXMLAttribute =>
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
          val dataType =
            StandoffDataTypeClasses.lookup(
              dataTypeClass.datatype,
              throw BadRequestException(s"Invalid data type provided for $tagname")
            )

          val dataTypeAttribute = dataTypeClass.attributeName

          Some(
            XMLStandoffDataTypeClass(
              standoffDataTypeClass = dataType,
              dataTypeXMLAttribute = dataTypeAttribute
            )
          )

        case None => None
      }

      // add the current tag to the map
      val newNamespaceMap: Map[String, Map[String, XMLTag]] = namespaceMap.get(tagname) match {
        case Some(tagMap: Map[String, XMLTag]) =>
          tagMap.get(classname) match {
            case Some(_) =>
              throw BadRequestException("Duplicate tag and classname combination in the same namespace")
            case None =>
              // create the definition for the current element
              val xmlElementDef = XMLTag(
                name = tagname,
                mapping = XMLTagToStandoffClass(
                  standoffClassIri = standoffClassIri,
                  attributesToProps = attributes,
                  dataType = dataTypeOption
                ),
                separatorRequired = curEle.separatorRequired
              )

              // combine the definition for the this classname with the existing definitions beloning to the same element
              val combinedClassDef: Map[String, XMLTag] = namespaceMap(tagname) + (classname -> xmlElementDef)

              // combine all elements for this namespace
              namespaceMap + (tagname -> combinedClassDef)

          }
        case None =>
          namespaceMap + (tagname -> Map(
            classname -> XMLTag(
              name = tagname,
              mapping = XMLTagToStandoffClass(
                standoffClassIri = standoffClassIri,
                attributesToProps = attributes,
                dataType = dataTypeOption
              ),
              separatorRequired = curEle.separatorRequired
            )
          ))
      }

      // recreate the whole structure for all namespaces
      MappingXMLtoStandoff(
        namespace = acc.namespace + (namespace -> newNamespaceMap),
        defaultXSLTransformation = defaultXSLTransformation
      )

    }

    // invert mapping in order to run checks for duplicate use of
    // standoff class Iris and property Iris in the attributes for a standoff class
    StandoffTagUtilV2.invertXMLToStandoffMapping(mappingXMLToStandoff)

    mappingXMLToStandoff

  }

  /**
   * The name of the mapping cache.
   */
  val mappingCacheName = "mappingCache"

  /**
   * Gets a mapping either from the cache or by making a request to the triplestore.
   *
   * @param mappingIri           the IRI of the mapping to retrieve.
   *
   * @param requestingUser       the user making the request.
   * @return a [[MappingXMLtoStandoff]].
   */
  private def getMappingV2(
    mappingIri: IRI,
    requestingUser: UserADM
  ): Future[GetMappingResponseV2] = {

    val mappingFuture: Future[GetMappingResponseV2] =
      CacheUtil.get[MappingXMLtoStandoff](cacheName = mappingCacheName, key = mappingIri) match {
        case Some(mapping: MappingXMLtoStandoff) =>
          for {

            entities: StandoffEntityInfoGetResponseV2 <- getStandoffEntitiesFromMappingV2(mapping, requestingUser)

          } yield GetMappingResponseV2(
            mappingIri = mappingIri,
            mapping = mapping,
            standoffEntities = entities
          )

        case None =>
          for {
            mapping: MappingXMLtoStandoff <- getMappingFromTriplestore(
                                               mappingIri = mappingIri,
                                               requestingUser = requestingUser
                                             )

            entities: StandoffEntityInfoGetResponseV2 <- getStandoffEntitiesFromMappingV2(mapping, requestingUser)

          } yield GetMappingResponseV2(
            mappingIri = mappingIri,
            mapping = mapping,
            standoffEntities = entities
          )
      }

    val mappingRecovered: Future[GetMappingResponseV2] = mappingFuture.recover { case e: Exception =>
      throw BadRequestException(s"An error occurred when requesting mapping $mappingIri: ${e.getMessage}")
    }

    for {
      mapping <- mappingRecovered
    } yield mapping

  }

  /**
   * Gets a mapping from the triplestore.
   *
   * @param mappingIri           the IRI of the mapping to retrieve.
   *
   * @param requestingUser       the user making the request.
   * @return a [[MappingXMLtoStandoff]].
   */
  private def getMappingFromTriplestore(
    mappingIri: IRI,
    requestingUser: UserADM
  ): Future[MappingXMLtoStandoff] = {

    val getMappingSparql = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
      .getMapping(
        mappingIri = mappingIri
      )
      .toString()

    for {

      mappingResponse: SparqlConstructResponse <- appActor
                                                    .ask(
                                                      SparqlConstructRequest(
                                                        sparql = getMappingSparql
                                                      )
                                                    )
                                                    .mapTo[SparqlConstructResponse]

      // if the result is empty, the mapping does not exist
      _ = if (mappingResponse.statements.isEmpty) {
            throw BadRequestException(s"mapping $mappingIri does not exist in triplestore")
          }

      // separate MappingElements from other statements (attributes and datatypes)
      (mappingElementStatements: Map[IRI, Seq[(IRI, String)]], otherStatements: Map[IRI, Seq[(IRI, String)]]) =
        mappingResponse.statements.partition { case (_: IRI, assertions: Seq[(IRI, String)]) =>
          assertions.contains((OntologyConstants.Rdf.Type, OntologyConstants.KnoraBase.MappingElement))
        }

      mappingElements: Seq[MappingElement] = mappingElementStatements.map {
                                               case (subjectIri: IRI, assertions: Seq[(IRI, String)]) =>
                                                 // for convenience (works only for props with cardinality one)
                                                 val assertionsAsMap: Map[IRI, String] = assertions.toMap

                                                 // check for attributes
                                                 val attributes: Seq[MappingXMLAttribute] = assertions.filter {
                                                   case (propIri, _) =>
                                                     propIri == OntologyConstants.KnoraBase.MappingHasXMLAttribute
                                                 }.map { case (_: IRI, attributeElementIri: String) =>
                                                   val attributeStatementsAsMap: Map[IRI, String] =
                                                     otherStatements(attributeElementIri).toMap

                                                   MappingXMLAttribute(
                                                     attributeName = attributeStatementsAsMap(
                                                       OntologyConstants.KnoraBase.MappingHasXMLAttributename
                                                     ),
                                                     namespace = attributeStatementsAsMap(
                                                       OntologyConstants.KnoraBase.MappingHasXMLNamespace
                                                     ),
                                                     standoffProperty = attributeStatementsAsMap(
                                                       OntologyConstants.KnoraBase.MappingHasStandoffProperty
                                                     ),
                                                     mappingXMLAttributeElementIri = attributeElementIri
                                                   )
                                                 }

                                                 // check for standoff data type class
                                                 val dataTypeOption: Option[IRI] =
                                                   assertionsAsMap.get(
                                                     OntologyConstants.KnoraBase.MappingHasStandoffDataTypeClass
                                                   )

                                                 MappingElement(
                                                   tagName =
                                                     assertionsAsMap(OntologyConstants.KnoraBase.MappingHasXMLTagname),
                                                   namespace = assertionsAsMap(
                                                     OntologyConstants.KnoraBase.MappingHasXMLNamespace
                                                   ),
                                                   className =
                                                     assertionsAsMap(OntologyConstants.KnoraBase.MappingHasXMLClass),
                                                   standoffClass = assertionsAsMap(
                                                     OntologyConstants.KnoraBase.MappingHasStandoffClass
                                                   ),
                                                   mappingElementIri = subjectIri,
                                                   standoffDataTypeClass = dataTypeOption match {
                                                     case Some(dataTypeElementIri: IRI) =>
                                                       val dataTypeAssertionsAsMap: Map[IRI, String] =
                                                         otherStatements(dataTypeElementIri).toMap

                                                       Some(
                                                         MappingStandoffDatatypeClass(
                                                           datatype = dataTypeAssertionsAsMap(
                                                             OntologyConstants.KnoraBase.MappingHasStandoffClass
                                                           ),
                                                           attributeName = dataTypeAssertionsAsMap(
                                                             OntologyConstants.KnoraBase.MappingHasXMLAttributename
                                                           ),
                                                           mappingStandoffDataTypeClassElementIri = dataTypeElementIri
                                                         )
                                                       )
                                                     case None => None
                                                   },
                                                   attributes = attributes,
                                                   separatorRequired = assertionsAsMap(
                                                     OntologyConstants.KnoraBase.MappingElementRequiresSeparator
                                                   ).toBoolean
                                                 )

                                             }.toSeq

      // check if there is a default XSL transformation
      defaultXSLTransformationOption: Option[IRI] = otherStatements(mappingIri).find { case (pred: IRI, _: String) =>
                                                      pred == OntologyConstants.KnoraBase.MappingHasDefaultXSLTransformation
                                                    }.map { case (_: IRI, xslTransformationIri: IRI) =>
                                                      xslTransformationIri
                                                    }

      mappingXMLToStandoff = transformMappingElementsToMappingXMLtoStandoff(
                               mappingElements,
                               defaultXSLTransformationOption
                             )

      // add the mapping to the cache
      _ = CacheUtil.put(cacheName = mappingCacheName, key = mappingIri, value = mappingXMLToStandoff)

    } yield mappingXMLToStandoff

  }

  /**
   * Gets the required standoff entities (classes and properties) from the mapping and requests information about these entities from the ontology responder.
   *
   * @param mappingXMLtoStandoff the mapping to be used.
   * @param requestingUser       the client that made the request.
   * @return a [[StandoffEntityInfoGetResponseV2]] holding information about standoff classes and properties.
   */
  private def getStandoffEntitiesFromMappingV2(
    mappingXMLtoStandoff: MappingXMLtoStandoff,
    requestingUser: UserADM
  ): Future[StandoffEntityInfoGetResponseV2] = {

    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    // TODO: think about refactoring the mapping so it uses SmartIris (that would also have to include `StandoffProperties`)

    // invert the mapping so standoff class Iris become keys
    val mappingStandoffToXML: Map[IRI, XMLTagItem] = StandoffTagUtilV2.invertXMLToStandoffMapping(mappingXMLtoStandoff)

    // collect standoff class Iris from the mapping
    val standoffTagIrisFromMapping: Set[IRI] = mappingStandoffToXML.keySet

    // collect all the standoff property Iris from the mapping
    val standoffPropertyIrisFromMapping: Set[IRI] = mappingStandoffToXML.values.foldLeft(Set.empty[IRI]) {
      (acc: Set[IRI], xmlTag: XMLTagItem) =>
        acc ++ xmlTag.attributes.keySet
    }

    // make sure that the mapping does not contain system or data type standoff properties as attributes
    // these standoff properties can only be used via the standoff base tag and standoff data type classes
    val systemOrDatatypePropsAsAttr: Set[IRI] = standoffPropertyIrisFromMapping.intersect(
      StandoffProperties.systemProperties ++ StandoffProperties.dataTypeProperties
    )
    if (systemOrDatatypePropsAsAttr.nonEmpty)
      throw InvalidStandoffException(
        s"attempt to define attributes for system or data type properties: ${systemOrDatatypePropsAsAttr.mkString(", ")}"
      )

    for {

      // request information about standoff classes that should be created
      standoffClassEntities: StandoffEntityInfoGetResponseV2 <-
        appActor
          .ask(
            StandoffEntityInfoGetRequestV2(
              standoffClassIris = standoffTagIrisFromMapping.map(_.toSmartIri),
              requestingUser = requestingUser
            )
          )
          .mapTo[StandoffEntityInfoGetResponseV2]

      // check that the ontology responder returned the information for all the standoff classes it was asked for
      // if the ontology responder does not return a standoff class it was asked for, then this standoff class does not exist
      _ = if (standoffTagIrisFromMapping.map(_.toSmartIri) != standoffClassEntities.standoffClassInfoMap.keySet) {
            throw NotFoundException(
              s"the ontology responder could not find information about these standoff classes: ${(standoffTagIrisFromMapping
                  .map(_.toSmartIri) -- standoffClassEntities.standoffClassInfoMap.keySet).mkString(", ")}"
            )
          }

      // get the property Iris that are defined on the standoff classes returned by the ontology responder
      standoffPropertyIrisFromOntologyResponder: Set[SmartIri] =
        standoffClassEntities.standoffClassInfoMap.foldLeft(
          Set.empty[SmartIri]
        ) { case (acc, (_, standoffClassEntity: ReadClassInfoV2)) =>
          val props = standoffClassEntity.allCardinalities.keySet
          acc ++ props
        }

      // request information about the standoff properties
      standoffPropertyEntities: StandoffEntityInfoGetResponseV2 <-
        appActor
          .ask(
            StandoffEntityInfoGetRequestV2(
              standoffPropertyIris = standoffPropertyIrisFromOntologyResponder,
              requestingUser = requestingUser
            )
          )
          .mapTo[StandoffEntityInfoGetResponseV2]

      // check that the ontology responder returned the information for all the standoff properties it was asked for
      // if the ontology responder does not return a standoff property it was asked for, then this standoff property does not exist
      propertyDefinitionsFromMappingFoundInOntology: Set[SmartIri] =
        standoffPropertyEntities.standoffPropertyInfoMap.keySet
          .intersect(standoffPropertyIrisFromMapping.map(_.toSmartIri))

      _ = if (standoffPropertyIrisFromMapping.map(_.toSmartIri) != propertyDefinitionsFromMappingFoundInOntology) {
            throw NotFoundException(
              s"the ontology responder could not find information about these standoff properties: " +
                s"${(standoffPropertyIrisFromMapping.map(_.toSmartIri) -- propertyDefinitionsFromMappingFoundInOntology)
                    .mkString(", ")}"
            )
          }

      // check that for each standoff property defined in the mapping element for a standoff class, a corresponding cardinality exists in the ontology
      _ = mappingStandoffToXML.foreach { case (standoffClass: IRI, xmlTag: XMLTagItem) =>
            // collect all the standoff properties defined for this standoff class
            val standoffPropertiesForStandoffClass: Set[SmartIri] = xmlTag.attributes.keySet.map(_.toSmartIri)

            // check that the current standoff class has cardinalities for all the properties defined
            val cardinalitiesFound = standoffClassEntities
              .standoffClassInfoMap(standoffClass.toSmartIri)
              .allCardinalities
              .keySet
              .intersect(standoffPropertiesForStandoffClass)

            if (standoffPropertiesForStandoffClass != cardinalitiesFound) {
              throw NotFoundException(
                s"the following standoff properties have no cardinality for $standoffClass: ${(standoffPropertiesForStandoffClass -- cardinalitiesFound)
                    .mkString(", ")}"
              )
            }

            // collect the required standoff properties for the standoff class
            val requiredPropsForClass: Set[SmartIri] = standoffClassEntities
              .standoffClassInfoMap(standoffClass.toSmartIri)
              .allCardinalities
              .filter { case (property: SmartIri, card: KnoraCardinalityInfo) =>
                card.cardinality == MustHaveOne || card.cardinality == MustHaveSome
              }
              .keySet -- StandoffProperties.systemProperties.map(_.toSmartIri) -- StandoffProperties.dataTypeProperties
              .map(_.toSmartIri)

            // check that all the required standoff properties exist in the mapping
            if (standoffPropertiesForStandoffClass.intersect(requiredPropsForClass) != requiredPropsForClass) {
              throw NotFoundException(
                s"the following required standoff properties are not defined for the standoff class $standoffClass: ${(requiredPropsForClass -- standoffPropertiesForStandoffClass)
                    .mkString(", ")}"
              )
            }

            // check if the standoff class's data type is correct in the mapping
            standoffClassEntities.standoffClassInfoMap(standoffClass.toSmartIri).standoffDataType match {
              case Some(dataType: StandoffDataTypeClasses.Value) =>
                // check if this corresponds to the datatype in the mapping
                val dataTypeFromMapping: XMLStandoffDataTypeClass = xmlTag.tagItem.mapping.dataType.getOrElse(
                  throw InvalidStandoffException(s"no data type provided for $standoffClass, but $dataType required")
                )
                if (dataTypeFromMapping.standoffDataTypeClass != dataType) {
                  throw InvalidStandoffException(
                    s"wrong data type ${dataTypeFromMapping.standoffDataTypeClass} provided for $standoffClass, but $dataType required"
                  )
                }
              case None =>
                if (xmlTag.tagItem.mapping.dataType.nonEmpty) {
                  throw InvalidStandoffException(
                    s"no data type expected for $standoffClass, but ${xmlTag.tagItem.mapping.dataType.get.standoffDataTypeClass} given"
                  )
                }
            }

          }

    } yield StandoffEntityInfoGetResponseV2(
      standoffClassInfoMap = standoffClassEntities.standoffClassInfoMap,
      standoffPropertyInfoMap = standoffPropertyEntities.standoffPropertyInfoMap
    )

  }

  /**
   * A [[TaskResult]] containing a page of standoff queried from a text value.
   *
   * @param underlyingResult the underlying standoff result.
   * @param nextTask         the next task, or `None` if there is no more standoff to query in the text value.
   */
  case class StandoffTaskResult(underlyingResult: StandoffTaskUnderlyingResult, nextTask: Option[GetStandoffTask])
      extends TaskResult[StandoffTaskUnderlyingResult]

  /**
   * The underlying result type contained in a [[StandoffTaskResult]].
   *
   * @param standoff the standoff that was queried.
   */
  case class StandoffTaskUnderlyingResult(standoff: Vector[StandoffTagV2])

  /**
   * A task that gets a page of standoff from a text value.
   *
   * @param resourceIri          the IRI of the resource containing the value.
   * @param valueIri             the IRI of the value.
   * @param offset               the start index of the first standoff tag to be returned.
   *
   * @param requestingUser       the user making the request.
   */
  case class GetStandoffTask(
    resourceIri: IRI,
    valueIri: IRI,
    offset: Int,
    requestingUser: UserADM
  ) extends Task[StandoffTaskUnderlyingResult] {
    override def runTask(
      previousResult: Option[TaskResult[StandoffTaskUnderlyingResult]]
    )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[TaskResult[StandoffTaskUnderlyingResult]] =
      for {
        // Get a page of standoff.
        standoffResponse <- getStandoffV2(
                              GetStandoffPageRequestV2(
                                resourceIri = resourceIri,
                                valueIri = valueIri,
                                offset = offset,
                                targetSchema = ApiV2Complex,
                                requestingUser = requestingUser
                              )
                            )

        // Add it to the standoff that has already been collected.
        collectedStandoff = previousResult match {
                              case Some(definedPreviousResult) =>
                                definedPreviousResult.underlyingResult.standoff ++ standoffResponse.standoff
                              case None => standoffResponse.standoff.toVector
                            }
      } yield standoffResponse.nextOffset match {
        case Some(definedNextOffset) =>
          // There is more standoff to query. Return the collected standoff and the next task.
          StandoffTaskResult(
            underlyingResult = StandoffTaskUnderlyingResult(collectedStandoff),
            nextTask = Some(copy(offset = definedNextOffset))
          )

        case None =>
          // There is no more standoff to query. Just return the collected standoff.
          StandoffTaskResult(
            underlyingResult = StandoffTaskUnderlyingResult(collectedStandoff),
            nextTask = None
          )
      }
  }

  /**
   * Returns all pages of standoff markup from a text value, except for the first page.
   *
   * @param getRemainingStandoffFromTextValueRequestV2 the request message.
   * @return the text value's standoff markup.
   */
  private def getRemainingStandoffFromTextValueV2(
    getRemainingStandoffFromTextValueRequestV2: GetRemainingStandoffFromTextValueRequestV2
  ): Future[GetStandoffResponseV2] = {
    val firstTask = GetStandoffTask(
      resourceIri = getRemainingStandoffFromTextValueRequestV2.resourceIri,
      valueIri = getRemainingStandoffFromTextValueRequestV2.valueIri,
      offset = responderData.appConfig.standoffPerPage, // the offset of the second page
      requestingUser = getRemainingStandoffFromTextValueRequestV2.requestingUser
    )

    for {
      result: TaskResult[StandoffTaskUnderlyingResult] <- ActorUtil.runTasks(firstTask)
    } yield GetStandoffResponseV2(
      valueIri = getRemainingStandoffFromTextValueRequestV2.valueIri,
      standoff = result.underlyingResult.standoff,
      nextOffset = None
    )
  }
}
