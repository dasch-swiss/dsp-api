/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.xml.sax.SAXException
import zio.*

import java.io.*
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import scala.xml.Node
import scala.xml.NodeSeq
import scala.xml.XML

import dsp.errors.*
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.messages.*
import org.knora.webapi.messages.v2.responder.standoffmessages.*
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.common.CreateMappingRequestV2
import org.knora.webapi.slice.common.StandoffMappingElementIri
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.resources.repo.CreateNewMappingQuery
import org.knora.webapi.slice.resources.repo.GetMappingQuery
import org.knora.webapi.slice.resources.repo.model.MappingElement
import org.knora.webapi.slice.resources.repo.model.MappingStandoffDatatypeClass
import org.knora.webapi.slice.resources.repo.model.MappingXMLAttribute
import org.knora.webapi.slice.standoff.service.StandoffMappingService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.util.FileUtil

/**
 * Handles creation of mappings from XML elements and attributes to standoff classes and properties.
 */
final class StandoffResponderV2(
  triplestore: TriplestoreService,
  projectService: ProjectService,
  standoffMappingService: StandoffMappingService,
)(implicit val stringFormatter: StringFormatter) {

  /**
   * Creates a mapping between XML elements and attributes to standoff classes and properties.
   * The mapping is used to convert XML documents to texts with standoff and back.
   *
   * @param request the mapping creation request.
   */
  def createMappingV2(
    request: CreateMappingRequestV2,
    uuid: UUID,
  ): Task[CreateMappingResponseV2] = {
    val createMappingAndCheck: Task[CreateMappingResponseV2] = (for {
      // check if the given project IRI represents an actual project
      project <- projectService
                   .findById(request.projectIri)
                   .someOrFail(BadRequestException(s"Project with Iri ${request.projectIri.toString} does not exist"))

      // create the mapping IRI from the project IRI and the name provided by the user
      mappingIri <- ZIO
                      .fromEither(StandoffMappingIri.from(request.projectIri, request.mappingName))
                      .mapError(BadRequestException.apply)
      // put the mapping into the named graph of the project
      namedGraph = ProjectService.projectDataNamedGraphV2(project).value

      factory <- ZIO.attempt(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI))

      // get the schema the mapping has to be validated against
      schemaFile = FileUtil.readTextResource("mappingXMLToStandoff.xsd")

      schemaSource = new StreamSource(new StringReader(schemaFile))

      // create a schema instance
      schemaInstance = factory.newSchema(schemaSource)
      validator      = schemaInstance.newValidator()

      // validate the provided mapping
      _ = validator.validate(new StreamSource(new StringReader(request.xml)))

      // the mapping conforms to the XML schema "src/main/resources/mappingXMLToStandoff.xsd"
      mappingXML = XML.loadString(request.xml)

      // get the default XSL transformation, if given (optional)
      defaultXSLTransformation <-
        mappingXML \ "defaultXSLTransformation" match {
          case defaultTrans: NodeSeq if defaultTrans.length == 1 =>
            // check if the IRI is valid
            val transIri = Iri
              .validateAndEscapeIri(
                defaultTrans.headOption
                  .getOrElse(throw BadRequestException("could not access <defaultXSLTransformation>"))
                  .text,
              )
              .getOrElse(
                throw BadRequestException(s"XSL transformation ${defaultTrans.head.text} is not a valid IRI"),
              )

            // try to obtain the XSL transformation to make sure that it really exists
            for {
              _ <- standoffMappingService.getXSLTransformation(transIri)
            } yield Some(transIri)
          case _ => ZIO.none
        }

      // create a collection of a all elements mappingElement
      mappingElementsXML = mappingXML \ "mappingElement"

      mappingElements: Seq[MappingElement] =
        mappingElementsXML.map { (curMappingEle: Node) =>
          // get the name of the XML tag
          val tagName = (curMappingEle \ "tag" \ "name").headOption
            .getOrElse(throw BadRequestException(s"no '<name>' given for node $curMappingEle"))
            .text

          // get the namespace the tag is defined in
          val tagNamespace = (curMappingEle \ "tag" \ "namespace").headOption
            .getOrElse(throw BadRequestException(s"no '<namespace>' given for node $curMappingEle"))
            .text

          // get the class the tag is combined with
          val className = (curMappingEle \ "tag" \ "class").headOption
            .getOrElse(throw BadRequestException(s"no '<classname>' given for node $curMappingEle"))
            .text

          // get the boolean indicating if the element requires a separator in the text once it is converted to standoff
          val separatorBooleanAsString =
            (curMappingEle \ "tag" \ "separatesWords").headOption
              .getOrElse(throw BadRequestException(s"no '<separatesWords>' given for node $curMappingEle"))
              .text

          val separatorRequired: Boolean = ValuesValidator
            .validateBoolean(separatorBooleanAsString)
            .getOrElse(
              throw BadRequestException(
                s"<separatesWords> could not be converted to Boolean: $separatorBooleanAsString",
              ),
            )

          // get the standoff class IRI
          val standoffClassIri =
            (curMappingEle \ "standoffClass" \ "classIri").headOption
              .getOrElse(throw BadRequestException(s"no '<classIri>' given for node $curMappingEle"))
              .text

          // get a collection containing all the attributes
          val attributeNodes: NodeSeq =
            curMappingEle \ "standoffClass" \ "attributes" \ "attribute"

          val attributes: Seq[MappingXMLAttribute] = attributeNodes.map { curAttributeNode =>
            // get the current attribute's name
            val attrName = (curAttributeNode \ "attributeName").headOption
              .getOrElse(throw BadRequestException(s"no '<attributeName>' given for attribute $curAttributeNode"))
              .text

            val attributeNamespace =
              (curAttributeNode \ "namespace").headOption
                .getOrElse(throw BadRequestException(s"no '<namespace>' given for attribute $curAttributeNode"))
                .text

            // get the standoff property IRI for the current attribute
            val propIri = (curAttributeNode \ "propertyIri").headOption
              .getOrElse(throw BadRequestException(s"no '<propertyIri>' given for attribute $curAttributeNode"))
              .text

            MappingXMLAttribute(
              attributeName = Iri
                .toSparqlEncodedString(attrName)
                .getOrElse(
                  throw BadRequestException(s"tagname $attrName contains invalid characters"),
                ),
              namespace = Iri
                .toSparqlEncodedString(attributeNamespace)
                .getOrElse(
                  throw BadRequestException(s"tagname $attributeNamespace contains invalid characters"),
                ),
              standoffProperty = Iri
                .validateAndEscapeIri(propIri)
                .getOrElse(
                  throw BadRequestException(s"standoff class IRI $standoffClassIri is not a valid IRI"),
                ),
              mappingXMLAttributeElementIri = StandoffMappingElementIri.makeNew(mappingIri),
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
                  throw BadRequestException(s"no '<type>' given for datatype"),
                )
                .text

              val dataType: StandoffDataTypeClasses.Value =
                StandoffDataTypeClasses.lookup(
                  dataTypeXML,
                  throw BadRequestException(s"Invalid data type provided for $tagName"),
                )
              val dataTypeAttribute: String =
                (datatypeMaybe \ "attributeName").headOption
                  .getOrElse(throw BadRequestException(s"no '<attributeName>' given for datatype"))
                  .text

              Some(
                MappingStandoffDatatypeClass(
                  datatype = dataType.toString, // safe because it is an enumeration
                  attributeName = Iri
                    .toSparqlEncodedString(dataTypeAttribute)
                    .getOrElse(
                      throw BadRequestException(s"tagname $dataTypeAttribute contains invalid characters"),
                    ),
                  mappingStandoffDataTypeClassElementIri = StandoffMappingElementIri.makeNew(mappingIri),
                ),
              )
            } else {
              None
            }

          MappingElement(
            tagName = Iri
              .toSparqlEncodedString(tagName)
              .getOrElse(
                throw BadRequestException(
                  s"tagname $tagName contains invalid characters",
                ),
              ),
            namespace = Iri
              .toSparqlEncodedString(tagNamespace)
              .getOrElse(
                throw BadRequestException(
                  s"namespace $tagNamespace contains invalid characters",
                ),
              ),
            className = Iri
              .toSparqlEncodedString(className)
              .getOrElse(
                throw BadRequestException(
                  s"classname $className contains invalid characters",
                ),
              ),
            standoffClass = Iri
              .validateAndEscapeIri(standoffClassIri)
              .getOrElse(
                throw BadRequestException(
                  s"standoff class IRI $standoffClassIri is not a valid IRI",
                ),
              ),
            attributes = attributes,
            standoffDataTypeClass = standoffDataTypeOption,
            mappingElementIri = StandoffMappingElementIri.makeNew(mappingIri),
            separatorRequired = separatorRequired,
          )

        }

      // transform mappingElements to the structure that is used internally to convert to or from standoff
      // in order to check for duplicates (checks are done during transformation)
      mappingXMLToStandoff: MappingXMLtoStandoff =
        StandoffMappingService.transformMappingElementsToMappingXMLtoStandoff(mappingElements, None)

      // get the standoff entities used in the mapping
      // checks if the standoff classes exist in the ontology
      // checks if the standoff properties exist in the ontology
      // checks if the attributes defined for XML elements have cardinalities for the standoff properties defined on the standoff class
      _ <- standoffMappingService.getStandoffEntitiesFromMappingV2(mappingXMLToStandoff)

      // check if the mapping IRI already exists
      getExistingMappingQuery  = GetMappingQuery.build(mappingIri.value)
      existingMappingResponse <- triplestore.query(Construct(getExistingMappingQuery))

      _ = if (existingMappingResponse.statements.nonEmpty) {
            throw BadRequestException(s"mapping IRI $mappingIri already exists")
          }

      createNewMappingSparql = CreateNewMappingQuery.build(
                                 dataNamedGraph = namedGraph,
                                 mappingIri = mappingIri.value,
                                 label = request.label,
                                 defaultXSLTransformation = defaultXSLTransformation,
                                 mappingElements = mappingElements,
                               )
      _ <- triplestore.query(Update(createNewMappingSparql))

      // check if the mapping has been created
      newMappingResponse <- triplestore.query(Construct(getExistingMappingQuery))

      _ = if (newMappingResponse.statements.isEmpty) {
            throw UpdateNotPerformedException(
              s"Resource $mappingIri was not created. Please report this as a possible bug.",
            )
          }

      // populate the mapping cache by reading it back through the service
      _ <- standoffMappingService.getMappingV2(mappingIri)
    } yield CreateMappingResponseV2(mappingIri.value, request.label, request.projectIri)).mapError {
      case validationException: SAXException =>
        BadRequestException(s"the provided mapping is invalid: ${validationException.getMessage}")

      case _: IOException => NotFoundException(s"The schema could not be found")

      case unknown: Exception =>
        BadRequestException(s"the provided mapping could not be handled correctly: ${unknown.getMessage}")
    }

    IriLocker.runWithIriLock(uuid, s"${request.projectIri.value}/mappings")(createMappingAndCheck)
  }
}

object StandoffResponderV2 {
  val layer = ZLayer.derive[StandoffResponderV2]
}
