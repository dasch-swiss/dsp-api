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

package org.knora.webapi.responders.v2

import java.io.{File, StringReader, StringWriter}

import akka.pattern._
import org.apache.commons.io.FileUtils
import org.eclipse.rdf4j.rio._
import org.eclipse.rdf4j.rio.rdfxml.util.RDFXMLPrettyWriter
import org.knora.webapi.OntologyConstants.KnoraBase
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.{SparqlConstructRequest, SparqlConstructResponse}
import org.knora.webapi.messages.v2.responder.resourcemessages.{ResourcesGetRequestV2, ResourcesPreviewGetRequestV2, _}
import org.knora.webapi.messages.v2.responder.standoffmessages.{GetMappingRequestV2, GetMappingResponseV2}
import org.knora.webapi.responders.ResponderWithStandoffV2
import org.knora.webapi.twirl.StandoffTagV2
import org.knora.webapi.util.ActorUtil.{future2Message, handleUnexpectedMessage}
import org.knora.webapi.util.ConstructResponseUtilV2.{MappingAndXSLTransformation, ResourceWithValueRdfData}
import org.knora.webapi.util.standoff.{StandoffTagUtilV2, XMLUtil}
import org.knora.webapi.util.{ConstructResponseUtilV2, SmartIri}


import scala.concurrent.Future

class ResourcesResponderV2 extends ResponderWithStandoffV2 {

    def receive = {
        case ResourcesGetRequestV2(resIris, requestingUser) => future2Message(sender(), getResources(resIris, requestingUser), log)
        case ResourcesPreviewGetRequestV2(resIris, requestingUser) => future2Message(sender(), getResourcePreview(resIris, requestingUser), log)
        case ResourceTEIGetRequestV2(resIri, textProperty, requestingUser) => future2Message(sender(), getResourceAsTEI(resIri, textProperty, requestingUser), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }


    /**
      * Gets the requested resources from the triplestore.
      *
      * @param resourceIris the Iris of the requested resources.
      * @return a [[Map[IRI, ResourceWithValueRdfData]]] representing the resources.
      */
    private def getResourcesFromTriplestore(resourceIris: Seq[IRI], preview: Boolean, requestingUser: UserADM): Future[Map[IRI, ResourceWithValueRdfData]] = {

        // eliminate duplicate Iris
        val resourceIrisDistinct: Seq[IRI] = resourceIris.distinct

        for {
            resourceRequestSparql <- Future(queries.sparql.v2.txt.getResourcePropertiesAndValues(
                triplestore = settings.triplestoreType,
                resourceIris = resourceIrisDistinct,
                preview
            ).toString())

            // _ = println(resourceRequestSparql)

            resourceRequestResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(resourceRequestSparql)).mapTo[SparqlConstructResponse]

            // separate resources and values
            queryResultsSeparated: Map[IRI, ResourceWithValueRdfData] = ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(constructQueryResults = resourceRequestResponse, requestingUser = requestingUser)

            // check if all the requested resources were returned
            requestedButMissing = resourceIrisDistinct.toSet -- queryResultsSeparated.keySet

            _ = if (requestedButMissing.nonEmpty) {
                throw NotFoundException(
                    s"""Not all the requested resources from ${resourceIrisDistinct.mkString(", ")} could not be found:
                        maybe you do not have the right to see all of them or some are marked as deleted.
                        Missing: ${requestedButMissing.mkString(", ")}""".stripMargin)

            }
        } yield queryResultsSeparated

    }

    /**
      * Get one or several resources and return them as a sequence.
      *
      * @param resourceIris   the resources to query for.
      * @param requestingUser the the client making the request.
      * @return a [[ReadResourcesSequenceV2]].
      */
    private def getResources(resourceIris: Seq[IRI], requestingUser: UserADM): Future[ReadResourcesSequenceV2] = {

        // eliminate duplicate Iris
        val resourceIrisDistinct: Seq[IRI] = resourceIris.distinct

        for {

            queryResultsSeparated: Map[IRI, ResourceWithValueRdfData] <- getResourcesFromTriplestore(resourceIris = resourceIris, preview = false, requestingUser = requestingUser)

            // get the mappings
            mappingsAsMap <- getMappingsFromQueryResultsSeparated(queryResultsSeparated, requestingUser)

            resourcesResponse: Vector[ReadResourceV2] = resourceIrisDistinct.map {
                (resIri: IRI) =>
                    ConstructResponseUtilV2.createFullResourceResponse(resIri, queryResultsSeparated(resIri), mappings = mappingsAsMap)
            }.toVector

        } yield ReadResourcesSequenceV2(numberOfResources = resourceIrisDistinct.size, resources = resourcesResponse)

    }

    /**
      * Get the preview of a resource.
      *
      * @param resourceIris   the resource to query for.
      * @param requestingUser the the client making the request.
      * @return a [[ReadResourcesSequenceV2]].
      */
    private def getResourcePreview(resourceIris: Seq[IRI], requestingUser: UserADM): Future[ReadResourcesSequenceV2] = {

        // eliminate duplicate Iris
        val resourceIrisDistinct: Seq[IRI] = resourceIris.distinct

        for {
            queryResultsSeparated: Map[IRI, ResourceWithValueRdfData] <- getResourcesFromTriplestore(resourceIris = resourceIris, preview = true, requestingUser = requestingUser)

            resourcesResponse: Vector[ReadResourceV2] = resourceIrisDistinct.map {
                (resIri: IRI) =>
                    ConstructResponseUtilV2.createFullResourceResponse(resIri, queryResultsSeparated(resIri), mappings = Map.empty[IRI, MappingAndXSLTransformation])
            }.toVector

        } yield ReadResourcesSequenceV2(numberOfResources = resourceIrisDistinct.size, resources = resourcesResponse)

    }

    private def getResourceAsTEI(resourceIri: IRI, textProperty: SmartIri, requestingUser: UserADM): Future[ResourceTEIGetResponseV2] = {

        for {

            // get requested resource
            queryResultsSeparated: Map[IRI, ResourceWithValueRdfData] <- getResourcesFromTriplestore(resourceIris = Seq(resourceIri), preview = false, requestingUser = requestingUser)

            //_ = println(MessageUtil.toSource(queryResultsSeparated))

            // get TEI mapping
            teiMapping: GetMappingResponseV2 <- (responderManager ? GetMappingRequestV2(mappingIri = OntologyConstants.KnoraBase.TEIMapping, userProfile = requestingUser)).mapTo[GetMappingResponseV2]

            // get value object representing the text value with standoff
            valueObjectOption: Option[Seq[ConstructResponseUtilV2.ValueRdfData]] = queryResultsSeparated(resourceIri).valuePropertyAssertions.get(textProperty.toString)

            // body

            // get the value object the represents the resource's text
            valueObject: ConstructResponseUtilV2.ValueRdfData = valueObjectOption match {
                case Some(valObjs: Seq[ConstructResponseUtilV2.ValueRdfData]) =>

                    // make sure that the property has one instance and that it is of type TextValue and that is has standoff (markup)
                    if (valObjs.size == 1 && valObjs.head.valueObjectClass == OntologyConstants.KnoraBase.TextValue && valObjs.head.standoff.nonEmpty) {
                        valObjs.head
                    } else {
                        throw BadRequestException(s"$textProperty is expected to occur once for $resourceIri and to be of type ${OntologyConstants.KnoraBase.TextValue} with standoff (markup)")
                    }

                case None => throw BadRequestException(s"no value found for property ${textProperty.toString} for resource $resourceIri")
            }

            // convert standoff assertions to standoff tags
            standoffTags: Vector[StandoffTagV2] = StandoffTagUtilV2.createStandoffTagsV2FromSparqlResults(teiMapping.standoffEntities, valueObject.standoff)

            // create XML from standoff (temporary XML) that is going to be converted to TEI/XML
            tmpXml = StandoffTagUtilV2.convertStandoffTagV2ToXML(valueObject.assertions(KnoraBase.ValueHasString), standoffTags, teiMapping.mapping)

            teiXSLTFile: File = new File("src/main/resources/standoffToTEI.xsl")

            _ = if (!teiXSLTFile.canRead) throw NotFoundException("Cannot find XSL transformation for TEI: 'src/main/resources/standoffToTEI.xsl'")

            // apply XSL transformation to temporary XML to create the TEI/XML body
            xslt: String = FileUtils.readFileToString(teiXSLTFile, "UTF-8")

            teiXMLBody = XMLUtil.applyXSLTransformation(tmpXml, xslt)

            // header

            // get all the properties but the property representing the text for the body
            headerProps: Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]] = queryResultsSeparated(resourceIri).valuePropertyAssertions - textProperty.toString

            // collect Iris of referred resources
            referredResourceIris = headerProps.values.flatten.foldLeft(Seq.empty[IRI]) {

                (referredResIris: Seq[IRI], valObj: ConstructResponseUtilV2.ValueRdfData) =>

                    if (valObj.valueObjectClass == OntologyConstants.KnoraBase.LinkValue && !valueObject.incomingLink) {
                        val refResIRI: IRI = valObj.assertions(OntologyConstants.Rdf.Object)

                        referredResIris :+ refResIRI
                    } else {
                        referredResIris
                    }

            }

            headerInfos = queryResultsSeparated(resourceIri).copy(
                valuePropertyAssertions = headerProps
            )

            headerResource: ReadResourceV2 = ConstructResponseUtilV2.createFullResourceResponse(resourceIri, headerInfos, mappings = Map.empty[IRI, MappingAndXSLTransformation])

            headerJSONLD = ReadResourcesSequenceV2(1, Vector(headerResource)).toJsonLDDocument(ApiV2WithValueObjects, settings)

            rdfParser: RDFParser = Rio.createParser(RDFFormat.JSONLD)
            stringReader = new StringReader(headerJSONLD.toCompactString)
            stringWriter = new StringWriter()

            rdfWriter: RDFWriter = new RDFXMLPrettyWriter(stringWriter)

            _ = rdfParser.setRDFHandler(rdfWriter)
            _ = rdfParser.parse(stringReader, "")

            teiHeader = stringWriter.toString

            _ = println(teiHeader)

            header =
            s"""
               |<teiHeader>
               | <fileDesc>
               |     <titleStmt>
               |         <title>${headerResource.label}</title>
               |     </titleStmt>
               |     <publicationStmt>
               |         <p>
               |             This is the TEI/XML representation of a resource identified with the Iri $resourceIri.
               |         </p>
               |     </publicationStmt>
               |     <sourceDesc>
               |         <p>No source: this is an original work.</p>
               |     </sourceDesc>
               | </fileDesc>
               |</teiHeader>
            """.stripMargin

            tei = ResourceTEIGetResponseV2(header = header, body = teiXMLBody)

            //_ = println(tei.toXML)

        } yield tei


    }

}

