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

import java.util.UUID

import akka.actor.Status
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.pattern._
import akka.stream.ActorMaterializer
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.resourcemessages.{LocationV1, ResourceFullGetRequestV1, ResourceFullResponseV1}
import org.knora.webapi.messages.v1.responder.standoffmessages._
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{CacheUtil, StringFormatter}

import scala.concurrent.Future
import scala.concurrent.duration._


/**
  * Responds to requests relating to the creation of mappings from XML elements and attributes to standoff classes and properties.
  */
class StandoffResponderV1 extends Responder {

    implicit val materializer: ActorMaterializer = ActorMaterializer()

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
    private def getXSLTransformation(xslTransformationIri: IRI, userProfile: UserADM): Future[GetXSLTransformationResponseV1] = {

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

            // for \\PI to be able to communicate with SIPI, we need to use SIPI's internal url
            internalTextLocationPath = textLocation.path.replace(settings.externalSipiBaseUrl, settings.internalSipiBaseUrl)
            // _ = println("StandoffResponderV1 - getXSLTransformation - original textLocation.path: {}", textLocation.path)
            // _ = println("StandoffResponderV1 - getXSLTransformation - internalTextLocationPath: {}", internalTextLocationPath)

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
    private def createMappingV1(xml: String, label: String, projectIri: IRI, mappingName: String, userProfile: UserADM, apiRequestID: UUID): Future[CreateMappingResponseV1] = {

        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val createMappingRequest = CreateMappingRequestV2(
            metadata = CreateMappingRequestMetadataV2(
                label = label,
                projectIri = projectIri.toSmartIri,
                mappingName = mappingName),
            xml = CreateMappingRequestXMLV2(
                xml = xml
            ),
            userProfile = userProfile,
            apiRequestID = apiRequestID)

        for {
            mappingResponse <- (responderManager ? createMappingRequest).mapTo[CreateMappingResponseV2]
        } yield CreateMappingResponseV1(
            mappingResponse.mappingIri
        )

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
    private def getMappingV1(mappingIri: IRI, userProfile: UserADM): Future[GetMappingResponseV1] = {

        for {
            mappingResponse <- (responderManager ? GetMappingRequestV2(mappingIri = mappingIri, userProfile = userProfile)).mapTo[GetMappingResponseV2]
        } yield GetMappingResponseV1(
            mappingIri = mappingResponse.mappingIri,
            mapping = mappingResponse.mapping,
            standoffEntities = mappingResponse.standoffEntities
        )

    }
}