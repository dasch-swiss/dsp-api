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

package org.knora.webapi.messages.v2.responder.resourcemessages

import java.io.{StringReader, StringWriter}
import java.time.Instant
import java.util.UUID

import akka.actor.ActorSelection
import akka.event.LoggingAdapter
import akka.util.Timeout
import org.eclipse.rdf4j.rio.rdfxml.util.RDFXMLPrettyWriter
import org.eclipse.rdf4j.rio.{RDFFormat, RDFParser, RDFWriter, Rio}
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.jsonld._
import org.knora.webapi.util.standoff.{StandoffTagUtilV2, XMLUtil}
import org.knora.webapi.util.{ActorUtil, SmartIri, StringFormatter}

import scala.concurrent.{ExecutionContext, Future}

/**
  * An abstract trait for messages that can be sent to `ResourcesResponderV2`.
  */
sealed trait ResourcesResponderRequestV2 extends KnoraRequestV2 {
    /**
      * The user that made the request.
      */
    def requestingUser: UserADM
}

/**
  * Requests a description of a resource. A successful response will be a [[ReadResourcesSequenceV2]].
  *
  * @param resourceIris   the IRIs of the resources to be queried.
  * @param requestingUser the user making the request.
  */
case class ResourcesGetRequestV2(resourceIris: Seq[IRI], requestingUser: UserADM) extends ResourcesResponderRequestV2

/**
  * Requests a preview of one or more resources. A successful response will be a [[ReadResourcesSequenceV2]].
  *
  * @param resourceIris   the IRIs of the resources to obtain a preview for.
  * @param requestingUser the user making the request.
  */
case class ResourcesPreviewGetRequestV2(resourceIris: Seq[IRI], requestingUser: UserADM) extends ResourcesResponderRequestV2

/**
  * Requests a resource as TEI/XML. A successful response will be a [[ResourceTEIGetResponseV2]].
  *
  * @param resourceIri           the IRI of the resource to be returned in TEI/XML.
  * @param textProperty          the property representing the text (to be converted to the body of a TEI document).
  * @param mappingIri            the IRI of the mapping to be used to convert from standoff to TEI/XML, if any. Otherwise the standard mapping is assumed.
  * @param gravsearchTemplateIri the gravsearch template to query the metadata for the TEI header, if provided.
  * @param headerXSLTIri         the IRI of the XSL transformation to convert the resource's metadata to the TEI header.
  * @param requestingUser        the user making the request.
  */
case class ResourceTEIGetRequestV2(resourceIri: IRI, textProperty: SmartIri, mappingIri: Option[IRI], gravsearchTemplateIri: Option[IRI], headerXSLTIri: Option[IRI], requestingUser: UserADM) extends ResourcesResponderRequestV2

/**
  * Represents a Knora resource as TEI/XML.
  *
  * @param header the header of the TEI document, if given.
  * @param body   the body of the TEI document.
  */
case class ResourceTEIGetResponseV2(header: TEIHeader, body: TEIBody) {

    def toXML: String =
        s"""<?xml version="1.0" encoding="UTF-8"?>
           |<TEI version="3.3.0" xmlns="http://www.tei-c.org/ns/1.0">
           |${header.toXML}
           |${body.toXML}
           |</TEI>
        """.stripMargin

}

/**
  * Represents information that is going to be contained in the header of a TEI/XML document.
  *
  * @param headerInfo the resource representing the header information.
  * @param headerXSLT XSLT to be applied to the resource's metadata in RDF/XML.
  *
  */
case class TEIHeader(headerInfo: ReadResourceV2, headerXSLT: Option[String], settings: SettingsImpl) {

    def toXML: String = {

        if (headerXSLT.nonEmpty) {

            val headerJSONLD = ReadResourcesSequenceV2(1, Vector(headerInfo)).toJsonLDDocument(ApiV2WithValueObjects, settings)

            val rdfParser: RDFParser = Rio.createParser(RDFFormat.JSONLD)
            val stringReader = new StringReader(headerJSONLD.toCompactString)
            val stringWriter = new StringWriter()

            val rdfWriter: RDFWriter = new RDFXMLPrettyWriter(stringWriter)

            rdfParser.setRDFHandler(rdfWriter)
            rdfParser.parse(stringReader, "")

            val teiHeaderInfos = stringWriter.toString

            XMLUtil.applyXSLTransformation(teiHeaderInfos, headerXSLT.get)


        } else {
            s"""
               |<teiHeader>
               | <fileDesc>
               |     <titleStmt>
               |         <title>${headerInfo.label}</title>
               |     </titleStmt>
               |     <publicationStmt>
               |         <p>
               |             This is the TEI/XML representation of a resource identified by the Iri ${headerInfo.resourceIri}.
               |         </p>
               |     </publicationStmt>
               |     <sourceDesc>
               |        <p>Representation of the resource's text as TEI/XML</p>
               |     </sourceDesc>
               | </fileDesc>
               |</teiHeader>
         """.stripMargin
        }

    }

}

/**
  * Represents the actual text that is going to be converted to the body of a TEI document.
  *
  * @param bodyInfo   the content of the text value that will be converted to TEI.
  * @param teiMapping the mapping from standoff to TEI/XML.
  * @param bodyXSLT   the XSLT transformation that completes the generation of TEI/XML.
  */
case class TEIBody(bodyInfo: TextValueContentV2, teiMapping: MappingXMLtoStandoff, bodyXSLT: String) {

    def toXML: String = {
        if (bodyInfo.standoffAndMapping.isEmpty) throw BadRequestException(s"text is expected to have standoff markup")

        // create XML from standoff (temporary XML) that is going to be converted to TEI/XML
        val tmpXml = StandoffTagUtilV2.convertStandoffTagV2ToXML(bodyInfo.valueHasString, bodyInfo.standoffAndMapping.get.standoff, teiMapping)

        XMLUtil.applyXSLTransformation(tmpXml, bodyXSLT)
    }

}

/**
  * Represents a Knora resource. Any implementation of `ResourceV2` is API operation specific.
  */
sealed trait ResourceV2 {
    /**
      * The IRI of the resource class.
      */
    def resourceClassIri: SmartIri

    /**
      * The resource's `rdfs:label`.
      */
    def label: String

    /**
      * A map of property IRIs to [[IOValueV2]] objects.
      */
    def values: Map[SmartIri, Seq[IOValueV2]]
}

/**
  * Represents a Knora resource when being read back from the triplestore.
  *
  * @param resourceIri          the IRI of the resource.
  * @param label                the resource's label.
  * @param resourceClassIri     the class the resource belongs to.
  * @param attachedToUser       the user that created the resource.
  * @param attachedToProject    the project that the resource belongs to.
  * @param permissions          the permissions that the resource grants to user groups.
  * @param values               a map of property IRIs to values.
  * @param creationDate         the date when this resource was created.
  * @param lastModificationDate the date when this resource was last modified.
  * @param deletionInfo         if this resource has been marked as deleted, provides the date when it was
  *                             deleted and the reason why it was deleted.
  */
case class ReadResourceV2(resourceIri: IRI,
                          label: String,
                          resourceClassIri: SmartIri,
                          attachedToUser: IRI,
                          attachedToProject: IRI,
                          permissions: String,
                          values: Map[SmartIri, Seq[ReadValueV2]],
                          creationDate: Instant,
                          lastModificationDate: Option[Instant],
                          deletionInfo: Option[DeletionInfo]) extends ResourceV2 with KnoraReadV2[ReadResourceV2] {
    override def toOntologySchema(targetSchema: ApiV2Schema): ReadResourceV2 = {
        copy(
            resourceClassIri = resourceClassIri.toOntologySchema(targetSchema),
            values = values.map {
                case (propertyIri, readValues) =>
                    val propertyIriInTargetSchema = propertyIri.toOntologySchema(targetSchema)

                    // In the simple schema, use link properties instead of link value properties.
                    val adaptedPropertyIri = if (targetSchema == ApiV2Simple) {
                        val isLinkProp = readValues.forall {
                            readValue =>
                                readValue.valueContent match {
                                    case _: LinkValueContentV2 => true
                                    case _ => false
                                }
                        }

                        if (isLinkProp) {
                            propertyIriInTargetSchema.fromLinkValuePropToLinkProp
                        } else {
                            propertyIriInTargetSchema
                        }
                    } else {
                        propertyIriInTargetSchema
                    }

                    adaptedPropertyIri -> readValues.map(_.toOntologySchema(targetSchema))
            }
        )
    }

    def toJsonLD(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDObject = {
        if (!resourceClassIri.getOntologySchema.contains(targetSchema)) {
            throw DataConversionException(s"ReadClassInfoV2 for resource $resourceIri is not in schema $targetSchema")
        }

        val propertiesAndValuesAsJsonLD: Map[IRI, JsonLDArray] = values.map {
            case (propIri: SmartIri, readValues: Seq[ReadValueV2]) =>
                val valuesAsJsonLD: Seq[JsonLDValue] = readValues.map(_.toJsonLD(targetSchema, settings))
                propIri.toString -> JsonLDArray(valuesAsJsonLD)
        }

        val metadataForComplexSchema: Map[IRI, JsonLDValue] = if (targetSchema == ApiV2WithValueObjects) {
            val requiredMetadataForComplexSchema: Map[IRI, JsonLDValue] = Map(
                OntologyConstants.KnoraApiV2WithValueObjects.AttachedToUser -> JsonLDUtil.iriToJsonLDObject(attachedToUser),
                OntologyConstants.KnoraApiV2WithValueObjects.AttachedToProject -> JsonLDUtil.iriToJsonLDObject(attachedToProject),
                OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions -> JsonLDString(permissions),
                OntologyConstants.KnoraApiV2WithValueObjects.CreationDate -> JsonLDObject(
                    Map(
                        JsonLDConstants.TYPE -> JsonLDString(OntologyConstants.Xsd.DateTimeStamp),
                        JsonLDConstants.VALUE -> JsonLDString(creationDate.toString)
                    )
                )
            )

            val deletionInfoAsJsonLD: Map[IRI, JsonLDValue] = deletionInfo match {
                case Some(definedDeletionInfo) => definedDeletionInfo.toJsonLDFields(ApiV2WithValueObjects)
                case None => Map.empty[IRI, JsonLDValue]
            }

            val lastModDateAsJsonLD: Option[(IRI, JsonLDValue)] = lastModificationDate.map {
                definedLastModDate =>
                    OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate -> JsonLDObject(
                        Map(
                            JsonLDConstants.TYPE -> JsonLDString(OntologyConstants.Xsd.DateTimeStamp),
                            JsonLDConstants.VALUE -> JsonLDString(definedLastModDate.toString)
                        )
                    )
            }

            requiredMetadataForComplexSchema ++ deletionInfoAsJsonLD ++ lastModDateAsJsonLD
        } else {
            Map.empty[IRI, JsonLDValue]
        }

        JsonLDObject(
            Map(
                JsonLDConstants.ID -> JsonLDString(resourceIri),
                JsonLDConstants.TYPE -> JsonLDString(resourceClassIri.toString),
                OntologyConstants.Rdfs.Label -> JsonLDString(label)
            ) ++ propertiesAndValuesAsJsonLD ++ metadataForComplexSchema
        )
    }
}

/**
  * The value of a Knora property sent to Knora to be created in a new resource.
  *
  * @param valueContent the content of the new value. If the client wants to create a link, this must be a [[LinkValueContentV2]].
  * @param permissions  the permissions to be given to the new value. If not provided, these will be taken from defaults.
  */
case class CreateValueInNewResourceV2(valueContent: ValueContentV2,
                                      permissions: Option[String] = None) extends IOValueV2

/**
  * Represents a Knora resource to be created.
  *
  * @param resourceClassIri the class the resource belongs to.
  * @param label            the resource's label.
  * @param values           the resource's values.
  * @param projectIri       the IRI of the project that the resource should belong to.
  * @param permissions      the permissions to be given to the new resource. If not provided, these will be taken from defaults.
  */
case class CreateResourceV2(resourceClassIri: SmartIri,
                            label: String,
                            values: Map[SmartIri, Seq[CreateValueInNewResourceV2]],
                            projectIri: IRI,
                            permissions: Option[String] = None) extends ResourceV2 {
    lazy val flatValues: Iterable[CreateValueInNewResourceV2] = values.values.flatten
}

/**
  * Represents a request to create a resource.
  *
  * @param createResource the resource to be created.
  * @param requestingUser the user making the request.
  * @param apiRequestID   the API request ID.
  */
case class CreateResourceRequestV2(createResource: CreateResourceV2,
                                   requestingUser: UserADM,
                                   apiRequestID: UUID) extends ResourcesResponderRequestV2


object CreateResourceRequestV2 extends KnoraJsonLDRequestReaderV2[CreateResourceRequestV2] {
    /**
      * Converts JSON-LD input to a [[CreateResourceRequestV2]].
      *
      * @param jsonLDDocument   the JSON-LD input.
      * @param apiRequestID     the UUID of the API request.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a case class instance representing the input.
      */
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            requestingUser: UserADM,
                            responderManager: ActorSelection,
                            log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[CreateResourceRequestV2] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        for {
            // Get the resource class.
            resourceClassIri: SmartIri <- Future(jsonLDDocument.getTypeAsKnoraTypeIri)

            // Get the resource's rdfs:label.
            label: String = jsonLDDocument.requireStringWithValidation(OntologyConstants.Rdfs.Label, stringFormatter.toSparqlEncodedString)

            // Get the resource's project.
            projectIri: SmartIri = jsonLDDocument.requireIriInObject(OntologyConstants.KnoraApiV2WithValueObjects.AttachedToProject, stringFormatter.toSmartIriWithErr)

            // Get the resource's permissions.
            maybePermissions = jsonLDDocument.maybeStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions, stringFormatter.toSparqlEncodedString)

            // Get the resource's values.

            propertyIriStrs: Set[IRI] = jsonLDDocument.body.value.keySet --
                Set(
                    JsonLDConstants.ID,
                    JsonLDConstants.TYPE,
                    OntologyConstants.Rdfs.Label,
                    OntologyConstants.KnoraApiV2WithValueObjects.AttachedToProject,
                    OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions
                )

            valueFutures: Map[SmartIri, Seq[Future[CreateValueInNewResourceV2]]] = propertyIriStrs.map {
                propertyIriStr =>
                    val propertyIri = propertyIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid property IRI: <$propertyIriStr>"))
                    val valuesArray = jsonLDDocument.requireArray(propertyIriStr)

                    val propertyValues = valuesArray.value.map {
                        valueJsonLD =>
                            val valueJsonLDObject = valueJsonLD match {
                                case jsonLDObject: JsonLDObject => jsonLDObject
                                case _ => throw BadRequestException(s"Invalid JSON-LD as object of property <$propertyIriStr>")
                            }

                            for {
                                valueContent: ValueContentV2 <-
                                    ValueContentV2.fromJsonLDObject(
                                        jsonLDObject = valueJsonLDObject,
                                        requestingUser = requestingUser,
                                        responderManager = responderManager,
                                        log = log
                                    )

                                _ = if (valueJsonLDObject.value.get(JsonLDConstants.ID).nonEmpty) {
                                    throw BadRequestException("The @id of a value cannot be given in a request to create the value")
                                }

                                maybePermissions: Option[String] = valueJsonLDObject.maybeStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions, stringFormatter.toSparqlEncodedString)
                            } yield CreateValueInNewResourceV2(
                                valueContent = valueContent,
                                permissions = maybePermissions
                            )
                    }

                    propertyIri -> propertyValues
            }.toMap

            values: Map[SmartIri, Seq[CreateValueInNewResourceV2]] <- ActorUtil.sequenceSeqFuturesInMap(valueFutures)

        } yield CreateResourceRequestV2(
            createResource = CreateResourceV2(
                resourceClassIri = resourceClassIri,
                label = label,
                values = values,
                projectIri = projectIri.toString,
                permissions = maybePermissions
            ),
            requestingUser = requestingUser,
            apiRequestID = apiRequestID
        )
    }
}

/**
  * Represents a sequence of resources read back from Knora.
  *
  * @param numberOfResources the amount of resources returned.
  * @param resources         a sequence of resources.
  */
case class ReadResourcesSequenceV2(numberOfResources: Int, resources: Seq[ReadResourceV2]) extends KnoraResponseV2 with KnoraReadV2[ReadResourcesSequenceV2] {

    override def toOntologySchema(targetSchema: ApiV2Schema): ReadResourcesSequenceV2 = {
        copy(
            resources = resources.map(_.toOntologySchema(targetSchema))
        )
    }

    private def generateJsonLD(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        // Generate JSON-LD for the resources.

        val resourcesJsonObjects: Seq[JsonLDObject] = resources.map {
            resource: ReadResourceV2 => resource.toJsonLD(targetSchema = targetSchema, settings = settings)
        }

        // Make JSON-LD prefixes for the project-specific ontologies used in the response.

        val projectSpecificOntologiesUsed: Set[SmartIri] = resources.flatMap {
            resource =>
                val resourceOntology = resource.resourceClassIri.getOntologyFromEntity

                val propertyOntologies = resource.values.keySet.map {
                    property => property.getOntologyFromEntity
                }

                propertyOntologies + resourceOntology
        }.toSet.filter(!_.isKnoraBuiltInDefinitionIri)

        // Make the knora-api prefix for the target schema.

        val knoraApiPrefixExpansion = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion
        }

        // Make the JSON-LD document.

        val context = JsonLDUtil.makeContext(
            fixedPrefixes = Map(
                "rdf" -> OntologyConstants.Rdf.RdfPrefixExpansion,
                "rdfs" -> OntologyConstants.Rdfs.RdfsPrefixExpansion,
                "xsd" -> OntologyConstants.Xsd.XsdPrefixExpansion,
                OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> knoraApiPrefixExpansion
            ),
            knoraOntologiesNeedingPrefixes = projectSpecificOntologiesUsed
        )

        val body = JsonLDObject(Map(
            JsonLDConstants.GRAPH -> JsonLDArray(resourcesJsonObjects)
        ))

        JsonLDDocument(body = body, context = context)

    }

    def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        toOntologySchema(targetSchema).generateJsonLD(targetSchema, settings)
    }
}
