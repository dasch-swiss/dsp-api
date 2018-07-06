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

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.pattern._
import akka.stream.ActorMaterializer
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.{SparqlConstructRequest, SparqlConstructResponse}
import org.knora.webapi.messages.v1.responder.valuemessages.KnoraCalendarV1
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.messages.v2.responder.searchmessages.GravsearchRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.{GetMappingRequestV2, GetMappingResponseV2, GetXSLTransformationRequestV2, GetXSLTransformationResponseV2}
import org.knora.webapi.responders.ResponderWithStandoffV2
import org.knora.webapi.util.ActorUtil.{future2Message, handleUnexpectedMessage}
import org.knora.webapi.util.ConstructResponseUtilV2.{MappingAndXSLTransformation, ResourceWithValueRdfData}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.search.ConstructQuery
import org.knora.webapi.util.search.gravsearch.GravsearchParser
import org.knora.webapi.util.{ConstructResponseUtilV2, FileUtil, SmartIri}

import scala.concurrent.Future
import scala.concurrent.duration._

class ResourcesResponderV2 extends ResponderWithStandoffV2 {

    implicit val materializer: ActorMaterializer = ActorMaterializer()

    override def receive: Receive = {
        case ResourcesGetRequestV2(resIris, requestingUser) => future2Message(sender(), getResources(resIris, requestingUser), log)
        case ResourcesPreviewGetRequestV2(resIris, requestingUser) => future2Message(sender(), getResourcePreview(resIris, requestingUser), log)
        case ResourceTEIGetRequestV2(resIri, textProperty, mappingIri, gravsearchTemplateIri, headerXSLTIri, requestingUser) => future2Message(sender(), getResourceAsTEI(resIri, textProperty, mappingIri, gravsearchTemplateIri, headerXSLTIri, requestingUser), log)
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
      * @param requestingUser        the user making the request.
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
      * @param resourceIri           the Iri of the resource to be converted to a TEI document (header and body).
      * @param textProperty          the Iri of the property (text value with standoff) to be converted to the body of the TEI document.
      * @param mappingIri            the Iri of the mapping to be used to convert standoff to XML, if a custom mapping is provided. The mapping is expected to contain an XSL transformation.
      * @param gravsearchTemplateIri the Iri of the Gravsearch template to query for the metadata for the TEI header. The resource Iri is expected to be represented by the placeholder '$resourceIri' in a BIND.
      * @param headerXSLTIri         the Iri of the XSL template to convert the metadata properties to the TEI header.
      * @param requestingUser        the user making the request.
      * @return a [[ResourceTEIGetResponseV2]].
      */
    private def getResourceAsTEI(resourceIri: IRI, textProperty: SmartIri, mappingIri: Option[IRI], gravsearchTemplateIri: Option[IRI], headerXSLTIri: Option[String], requestingUser: UserADM): Future[ResourceTEIGetResponseV2] = {

        /**
          * Extract the text value to be converted to TEI/XML.
          *
          * @param readResourceSeq the resource which is expected to hold the text value.
          * @return a [[TextValueContentV2]] representing the text value to be converted to TEI/XML.
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

        /**
          * Given a resource's values, convert the date values to Gregorian.
          *
          * @param values the values to be processed.
          * @return the resource's values with date values converted to Gregorian.
          */
        def convertDateToGregorian(values: Map[SmartIri, Seq[ReadValueV2]]): Map[SmartIri, Seq[ReadValueV2]] = {
            values.map {
                case (propIri: SmartIri, valueObjs: Seq[ReadValueV2]) =>

                    propIri -> valueObjs.map {

                        // convert all dates to Gregorian calendar dates (standardization)
                        valueObj: ReadValueV2 =>
                            valueObj.valueContent match {
                                case dateContent: DateValueContentV2 =>
                                    // date value

                                    valueObj.copy(
                                        valueContent = dateContent.copy(
                                            // act as if this was a Gregorian date
                                            valueHasCalendar = KnoraCalendarV1.GREGORIAN
                                        )
                                    )

                                case linkContent: LinkValueContentV2 if linkContent.nestedResource.nonEmpty =>
                                    // recursively process the values of the nested resource

                                    valueObj.copy(
                                        valueContent = linkContent.copy(
                                            nestedResource = Some(
                                                linkContent.nestedResource.get.copy(
                                                    // recursive call
                                                    values = convertDateToGregorian(linkContent.nestedResource.get.values)
                                                )
                                            )
                                        )
                                    )

                                case _ => valueObj
                            }
                    }

            }
        }

        for {

            // get the requested resource
            resource: ReadResourcesSequenceV2 <- if (gravsearchTemplateIri.nonEmpty) {

                // check that there is an XSLT to create the TEI header
                if (headerXSLTIri.isEmpty) throw BadRequestException(s"When a Gravsearch template Iri is provided, also a header XSLT Iri has to be provided.")

                for {
                    // get the template
                    template <- getGravsearchTemplate(gravsearchTemplateIri.get, requestingUser)

                    // insert actual resource Iri, replacing the placeholder
                    gravsearchQuery = template.replace("$resourceIri", resourceIri)

                    // parse the Gravsearch query
                    constructQuery: ConstructQuery = GravsearchParser.parseQuery(gravsearchQuery)

                    // do a request to the SearchResponder
                    gravSearchResponse: ReadResourcesSequenceV2 <- (responderManager ? GravsearchRequestV2(constructQuery = constructQuery, requestingUser = requestingUser)).mapTo[ReadResourcesSequenceV2]

                    // exactly one resource is expected
                    _ = if (gravSearchResponse.resources.size != 1) throw BadRequestException(s"Gravsearch query for $resourceIri should return one result, but ${gravSearchResponse.resources.size} given.")

                } yield gravSearchResponse

            } else {
                // no Gravsearch template is provided

                // check that there is no XSLT for the header since there is no Gravsearch template
                if (headerXSLTIri.nonEmpty) throw BadRequestException(s"When no Gravsearch template Iri is provided, no header XSLT Iri is expected to be provided either.")

                for {
                    // get requested resource
                    resource <- getResources(Vector(resourceIri), requestingUser)

                } yield resource
            }

            // get the value object representing the text value that is to be mapped to the body of the TEI document
            bodyTextValue: TextValueContentV2 = getTextValueFromReadResourceSeq(resource)

            // the ext value is expected to have standoff markup
            _ = if (bodyTextValue.standoff.isEmpty) throw BadRequestException(s"Property $textProperty of $resourceIri is expected to have standoff markup")

            // get all the metadata but the text property for the TEI header
            headerResource = resource.resources.head.copy(
                values = convertDateToGregorian(resource.resources.head.values - textProperty)
            )

            // get the XSL transformation for the TEI header
            headerXSLT: Option[String] <- if (headerXSLTIri.nonEmpty) {
                for {
                    xslTransformation: GetXSLTransformationResponseV2 <- (responderManager ? GetXSLTransformationRequestV2(headerXSLTIri.get, userProfile = requestingUser)).mapTo[GetXSLTransformationResponseV2]
                } yield Some(xslTransformation.xslt)
            } else {
                Future(None)
            }

            // get the Iri of the mapping to convert standoff markup to TEI/XML
            mappingToBeApplied = mappingIri match {
                case Some(mapping: IRI) =>
                    // a custom mapping is provided
                    mapping

                case None =>
                    // no mapping is provided, assume the standard case (standard standoff entites only)
                    OntologyConstants.KnoraBase.TEIMapping
            }

            // get mapping to convert standoff markup to TEI/XML
            teiMapping: GetMappingResponseV2 <- (responderManager ? GetMappingRequestV2(mappingIri = mappingToBeApplied, userProfile = requestingUser)).mapTo[GetMappingResponseV2]

            // get XSLT from mapping for the TEI body
            bodyXslt: String <- teiMapping.mappingIri match {
                case OntologyConstants.KnoraBase.TEIMapping =>
                    // standard standoff to TEI conversion

                    // use standard XSLT (built-in)
                    val teiXSLTFile: String = FileUtil.readTextResource("standoffToTEI.xsl")

                    // return the file's content
                    Future(teiXSLTFile)

                case otherMapping => teiMapping.mapping.defaultXSLTransformation match {
                    // custom standoff to TEI conversion

                    case Some(xslTransformationIri) =>
                        // get XSLT for the TEI body.
                        for {
                            xslTransformation: GetXSLTransformationResponseV2 <- (responderManager ? GetXSLTransformationRequestV2(xslTransformationIri, userProfile = requestingUser)).mapTo[GetXSLTransformationResponseV2]
                        } yield xslTransformation.xslt


                    case None => throw BadRequestException(s"Default XSL Transformation expected for mapping $otherMapping")
                }
            }

            tei = ResourceTEIGetResponseV2(
                header = TEIHeader(
                    headerInfo = headerResource,
                    headerXSLT = headerXSLT,
                    settings = settings
                ),
                body = TEIBody(
                    bodyInfo = bodyTextValue,
                    bodyXSLT = bodyXslt,
                    teiMapping = teiMapping.mapping
                )
            )

        } yield tei

    }
}

