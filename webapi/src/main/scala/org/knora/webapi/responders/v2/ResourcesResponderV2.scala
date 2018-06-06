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

import java.io.File

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.pattern._
import akka.stream.ActorMaterializer
import org.apache.commons.io.FileUtils
import org.knora.webapi.OntologyConstants.KnoraBase
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.{SparqlConstructRequest, SparqlConstructResponse}
import org.knora.webapi.messages.v2.responder.resourcemessages.{ResourcesGetRequestV2, ResourcesPreviewGetRequestV2, _}
import org.knora.webapi.messages.v2.responder.searchmessages.GravsearchRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.{GetMappingRequestV2, GetMappingResponseV2, GetXSLTransformationRequestV2, GetXSLTransformationResponseV2}
import org.knora.webapi.responders.ResponderWithStandoffV2
import org.knora.webapi.twirl.StandoffTagV2
import org.knora.webapi.util.ActorUtil.{future2Message, handleUnexpectedMessage}
import org.knora.webapi.util.ConstructResponseUtilV2.{MappingAndXSLTransformation, ResourceWithValueRdfData}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.search.ConstructQuery
import org.knora.webapi.util.search.v2.GravsearchParserV2
import org.knora.webapi.util.standoff.{StandoffTagUtilV2, XMLUtil}
import org.knora.webapi.util.{ConstructResponseUtilV2, MessageUtil, SmartIri}

import scala.concurrent.Future
import scala.concurrent.duration._

class ResourcesResponderV2 extends ResponderWithStandoffV2 {

    implicit val materializer: ActorMaterializer = ActorMaterializer()

    def receive = {
        case ResourcesGetRequestV2(resIris, requestingUser) => future2Message(sender(), getResources(resIris, requestingUser), log)
        case ResourcesPreviewGetRequestV2(resIris, requestingUser) => future2Message(sender(), getResourcePreview(resIris, requestingUser), log)
        case ResourceTEIGetRequestV2(resIri, textProperty, mappingIri, gravsearchTemplateIri, requestingUser) => future2Message(sender(), getResourceAsTEI(resIri, textProperty, mappingIri, gravsearchTemplateIri, requestingUser), log)
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

    /**
      * Obtains a Gravsearch template from Sipi.
      *
      * @param gravsearchTemplateIri the Iri of the resource representing the Gravsearch template.
      * @param requestingUser the user making the request.
      * @return the Gravsearch template.
      */
    private def getGravsearchTemplate(gravsearchTemplateIri: IRI, requestingUser: UserADM) = {
        val gravsearchUrlFuture = for {
            gravsearchResponseV2: ReadResourcesSequenceV2 <- (responderManager ? ResourcesGetRequestV2(resourceIris = Vector(gravsearchTemplateIri), requestingUser = requestingUser)).mapTo[ReadResourcesSequenceV2]

            gravsearchFileValue: TextFileValueContentV2 = gravsearchResponseV2.resources.headOption match {
                case Some(resource: ReadResourceV2) if resource.resourceClass.toString == OntologyConstants.KnoraBase.TextRepresentation =>
                    resource.values.get(OntologyConstants.KnoraBase.HasTextFileValue.toSmartIri) match {
                        case Some(values: Seq[ReadValueV2]) if values.size == 1 => values.head match {
                            case value: ReadValueV2 => value.valueContent match {
                                case textRepr: TextFileValueContentV2 => textRepr
                                case other => throw InconsistentTriplestoreDataException(s"${OntologyConstants.KnoraBase.XSLTransformation} $gravsearchTemplateIri is supposed to have exactly one value of type ${OntologyConstants.KnoraBase.TextFileValue}")
                            }
                        }

                        case None => throw InconsistentTriplestoreDataException(s"${OntologyConstants.KnoraBase.XSLTransformation} has no property ${OntologyConstants.KnoraBase.HasTextFileValue}")
                    }

                case None => throw BadRequestException(s"Resource $gravsearchTemplateIri is not a ${OntologyConstants.KnoraBase.XSLTransformation}")
            }

            // check if `xsltFileValue` represents an XSL transformation
            _ = if (!(gravsearchFileValue.internalMimeType == "text/plain" && gravsearchFileValue.originalFilename.endsWith(".txt"))) {
                throw BadRequestException(s"$gravsearchTemplateIri does not have a file value referring to an XSL transformation")
            }

            gravSearchUrl: String = s"${settings.internalSipiFileServerGetUrl}/${gravsearchFileValue.internalFilename}"

        } yield gravSearchUrl

        val recoveredGravsearchUrlFuture = gravsearchUrlFuture.recover {
            case notFound: NotFoundException => throw BadRequestException(s"XSL transformation $gravsearchTemplateIri not found: ${notFound.message}")
        }

        for {
            gravsearchTemplateUrl <- recoveredGravsearchUrlFuture

            sipiResponseFuture: Future[HttpResponse] = for {

                // ask Sipi to return the XSL transformation file
                response: HttpResponse <- Http().singleRequest(
                    HttpRequest(
                        method = HttpMethods.GET,
                        uri = gravsearchTemplateUrl
                    )
                )

            } yield response

            sipiResponseFutureRecovered: Future[HttpResponse] = sipiResponseFuture.recoverWith {

                case noResponse: akka.stream.scaladsl.TcpIdleTimeoutException =>
                    // this problem is hardly the user's fault. Create a SipiException
                    throw SipiException(message = "Sipi not reachable", e = noResponse, log = log)


                // TODO: what other exceptions have to be handled here?
                // if Exception is used, also previous errors are caught here

            }

            sipiResponseRecovered: HttpResponse <- sipiResponseFutureRecovered

            httpStatusCode: StatusCode = sipiResponseRecovered.status

            messageBody <- sipiResponseRecovered.entity.toStrict(5.seconds)

            _ = if (httpStatusCode != StatusCodes.OK) {
                throw SipiException(s"Sipi returned status code ${httpStatusCode.intValue} with msg '${messageBody.data.decodeString("UTF-8")}'")
            }

            // get the XSL transformation
            gravsearchTemplate: String = messageBody.data.decodeString("UTF-8")


        } yield gravsearchTemplate

    }

    /**
      * Returns a resource as TEI/XML.
      * This makes only sense for resources that have a text value containing standoff that is to be converted to the TEI body.
      *
      * @param resourceIri the Iri of the resource to be converted to a TEI document (header and body).
      * @param textProperty the Iri of the property to be converted to the body of the TEI document.
      * @param mappingIri the Iri of the mapping to be used, if a custom mapping is provided.
      * @param gravsearchTemplateIri the Iri of the Gravsearch template to query for the metadata for the TEI header.
      * @param requestingUser the user making the request.
      * @return a [[ResourceTEIGetResponseV2]].
      */
    private def getResourceAsTEI(resourceIri: IRI, textProperty: SmartIri, mappingIri: Option[IRI], gravsearchTemplateIri: Option[IRI], requestingUser: UserADM): Future[ResourceTEIGetResponseV2] = {

        /**
          * Extract the text value to be converted to TEI/XML.
          *
          * @param readResourceSeq the resource
          * @return
          */
        def getTextValueFromReadResourceSeq(readResourceSeq: ReadResourcesSequenceV2): TextValueContentV2 = {

            if (readResourceSeq.resources.size != 1) throw BadRequestException(s"Expected exactly one resource, but ${readResourceSeq.resources.size} given")

            readResourceSeq.resources.head.values.get(textProperty) match {
                case Some(valObjs: Seq[ReadValueV2]) if valObjs.size == 1 =>
                    // make sure that the property has one instance and that it is of type TextValue and that is has standoff (markup)
                    valObjs.head.valueContent match {
                        case textValWithStandoff: TextValueContentV2 if textValWithStandoff.standoff.nonEmpty =>
                            textValWithStandoff

                        case _ => throw BadRequestException(s"$textProperty to be of type ${OntologyConstants.KnoraBase.TextValue} with standoff (markup)")
                    }

                case None => throw BadRequestException(s"$textProperty is expected to occur once on $resourceIri")
            }
        }

        for {

            // get requested resource
            queryResultsSeparated: Map[IRI, ResourceWithValueRdfData] <- getResourcesFromTriplestore(resourceIris = Seq(resourceIri), preview = false, requestingUser = requestingUser)

            // get the mappings
            mappingsAsMap <- getMappingsFromQueryResultsSeparated(queryResultsSeparated, requestingUser)

            resource: ReadResourcesSequenceV2 <- if (gravsearchTemplateIri.nonEmpty) {

                for {
                    template <- getGravsearchTemplate(gravsearchTemplateIri.get, requestingUser)

                    // insert actual resource Iri
                    gravsearchQuery = template.replace("$resourceIri", resourceIri)

                    // do a request to the SearchResponder
                    constructQuery: ConstructQuery = GravsearchParserV2.parseQuery(gravsearchQuery)

                    gravSearchResponse: ReadResourcesSequenceV2 <- (responderManager ? GravsearchRequestV2(constructQuery = constructQuery, requestingUser = requestingUser)).mapTo[ReadResourcesSequenceV2]

                    _ = if (gravSearchResponse.resources.size != 1) throw BadRequestException(s"Gravsearch query for $resourceIri should return one result, but ${gravSearchResponse.resources.size} given.")

                } yield gravSearchResponse

            } else {

                for {
                    // get requested resource
                    resource <- getResources(Vector(resourceIri), requestingUser)

                } yield resource
            }

            bodyTextValue = getTextValueFromReadResourceSeq(resource)

            headerInfoValues: ReadResourcesSequenceV2 = resource.copy(
                resources = Vector(resource.resources.head.copy(
                    values = resource.resources.head.values - textProperty
                ))
            )

            headerResourceSeq = ReadResourcesSequenceV2(
                numberOfResources = 1,
                resources = Vector(
                    resource.resources.head.copy(
                        values = resource.resources.head.values - textProperty
                    )
                )
            )

            // _ = println(headerResourceSeq)

            // get value object representing the text value with standoff
            valueObjectOption: Option[Seq[ConstructResponseUtilV2.ValueRdfData]] = queryResultsSeparated(resourceIri).valuePropertyAssertions.get(textProperty.toString)

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

            // header

            // get all the properties but the property representing the text for the body
            headerProps: Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]] = queryResultsSeparated(resourceIri).valuePropertyAssertions - textProperty.toString

            headerInfos = queryResultsSeparated(resourceIri).copy(
                valuePropertyAssertions = headerProps
            )

            headerResource: ReadResourceV2 = ConstructResponseUtilV2.createFullResourceResponse(resourceIri, headerInfos, mappings = mappingsAsMap)

            // body

            mappingToBeApplied = mappingIri match {
                case Some(mapping: IRI) => mapping

                case None => OntologyConstants.KnoraBase.TEIMapping
            }

            // get TEI mapping
            teiMapping: GetMappingResponseV2 <- (responderManager ? GetMappingRequestV2(mappingIri = mappingToBeApplied, userProfile = requestingUser)).mapTo[GetMappingResponseV2]

            // get XSLT from mapping
            xslt: String <- teiMapping.mappingIri match {
                case OntologyConstants.KnoraBase.TEIMapping =>
                    // standard standoff to TEI conversion

                    // use standard XSLT (built-in)
                    val teiXSLTFile: File = new File("src/main/resources/standoffToTEI.xsl")

                    if (!teiXSLTFile.canRead) throw NotFoundException("Cannot find XSL transformation for TEI: 'src/main/resources/standoffToTEI.xsl'")

                    // return the file's content
                    Future(FileUtils.readFileToString(teiXSLTFile, "UTF-8"))

                case otherMapping => teiMapping.mapping.defaultXSLTransformation match {
                    // custom standoff to TEI conversion

                    case Some(xslTransformationIri) =>
                        // get XSLT
                        for {
                            xslTransformation: GetXSLTransformationResponseV2 <- (responderManager ? GetXSLTransformationRequestV2(xslTransformationIri, userProfile = requestingUser)).mapTo[GetXSLTransformationResponseV2]
                        } yield xslTransformation.xslt


                    case None => throw BadRequestException(s"Default XSL Transformation expected for mapping $otherMapping")
                }
            }

            // convert standoff assertions to standoff tags
            standoffTags: Vector[StandoffTagV2] = StandoffTagUtilV2.createStandoffTagsV2FromSparqlResults(teiMapping.standoffEntities, valueObject.standoff)

            // create XML from standoff (temporary XML) that is going to be converted to TEI/XML
            tmpXml = StandoffTagUtilV2.convertStandoffTagV2ToXML(valueObject.assertions(KnoraBase.ValueHasString), standoffTags, teiMapping.mapping)

            // _ = println(tmpXml)

            teiXMLBody = XMLUtil.applyXSLTransformation(tmpXml, xslt)

            tei = ResourceTEIGetResponseV2(
                header = TEIHeader(
                    headerInfo = headerResource
                ),
                body = teiXMLBody,
                settings = settings
            )


        } yield tei


    }

}

