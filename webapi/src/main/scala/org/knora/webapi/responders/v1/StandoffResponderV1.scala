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
import akka.pattern._
import akka.stream.ActorMaterializer
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.standoffmessages._
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter

import scala.concurrent.Future


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

        for {
            xsltTransformation <- (responderManager ? GetXSLTransformationRequestV2(xsltTextRepresentationIri = xslTransformationIri, userProfile = userProfile)).mapTo[GetXSLTransformationResponseV2]
        } yield GetXSLTransformationResponseV1(
            xslt = xsltTransformation.xslt
        )
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