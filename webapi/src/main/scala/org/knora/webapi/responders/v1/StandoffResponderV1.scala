/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v1

import zio._

import java.util.UUID

import dsp.errors.NotFoundException
import org.knora.webapi._
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.ontologymessages.ConvertOntologyClassV2ToV1
import org.knora.webapi.messages.v1.responder.ontologymessages.StandoffEntityInfoGetResponseV1
import org.knora.webapi.messages.v1.responder.standoffmessages._
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.iiif.errors.SipiException

/**
 * Responds to requests relating to the creation of mappings from XML elements and attributes to standoff classes and properties.
 */
trait StandoffResponderV1

final case class StandoffResponderV1Live(iriConverter: IriConverter, messageRelay: MessageRelay)
    extends StandoffResponderV1
    with MessageHandler {
  override def isResponsibleFor(message: ResponderRequest): Boolean =
    message.isInstanceOf[StandoffResponderRequestV1]

  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case CreateMappingRequestV1(xml, label, projectIri, mappingName, userProfile, uuid) =>
      createMappingV1(xml, label, projectIri, mappingName, userProfile, uuid)
    case GetMappingRequestV1(mappingIri, userProfile) =>
      getMappingV1(mappingIri, userProfile)
    case GetXSLTransformationRequestV1(xsltTextReprIri, userProfile) =>
      getXSLTransformation(xsltTextReprIri, userProfile)
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  /**
   * Retrieves a `knora-base:XSLTransformation` in the triplestore and requests the corresponding XSL file from Sipi.
   *
   * @param xslTransformationIri The IRI of the resource representing the XSL Transformation (a [[org.knora.webapi.messages.OntologyConstants.KnoraBase.XSLTransformation]]).
   *
   * @param userProfile          The client making the request.
   * @return a [[GetXSLTransformationResponseV1]].
   */
  def getXSLTransformation(
    xslTransformationIri: IRI,
    userProfile: UserADM
  ): Task[GetXSLTransformationResponseV1] =
    for {
      xslt <-
        messageRelay
          .ask[GetXSLTransformationResponseV2](GetXSLTransformationRequestV2(xslTransformationIri, userProfile))
          .mapBoth(
            {
              case e: NotFoundException =>
                SipiException(s"XSL transformation $xslTransformationIri not found: ${e.message}")
              case other => other
            },
            _.xslt
          )
    } yield GetXSLTransformationResponseV1(xslt)

  /**
   * Creates a mapping between XML elements and attributes to standoff classes and properties.
   * The mapping is used to convert XML documents to [[org.knora.webapi.messages.v1.responder.valuemessages.TextValueV1]] and back.
   *
   * @param xml                  the provided mapping.
   *
   * @param userProfile          the client that made the request.
   */
  private def createMappingV1(
    xml: String,
    label: String,
    projectIri: IRI,
    mappingName: String,
    userProfile: UserADM,
    apiRequestID: UUID
  ): Task[CreateMappingResponseV1] =
    for {
      projectSmartIri <- iriConverter.asSmartIri(projectIri)
      metadata         = CreateMappingRequestMetadataV2(label, projectSmartIri, mappingName)
      req              = CreateMappingRequestV2(metadata, CreateMappingRequestXMLV2(xml), userProfile, apiRequestID)
      iri             <- messageRelay.ask[CreateMappingResponseV2](req).map(_.mappingIri)
    } yield CreateMappingResponseV1(iri)

  /**
   * Gets a mapping either from the cache or by making a request to the triplestore.
   *
   * @param mappingIri  the IRI of the mapping to retrieve.
   *
   * @param userProfile the user making the request.
   * @return a [[MappingXMLtoStandoff]].
   */
  private def getMappingV1(mappingIri: IRI, userProfile: UserADM): Task[GetMappingResponseV1] =
    for {
      mappingResponse <- messageRelay.ask[GetMappingResponseV2](GetMappingRequestV2(mappingIri, userProfile))
      mappingIri       = mappingResponse.mappingIri
      mapping          = mappingResponse.mapping
      classInfoMap =
        ConvertOntologyClassV2ToV1.classInfoMapV2ToV1(mappingResponse.standoffEntities.standoffClassInfoMap)
      propertyInfoMap =
        ConvertOntologyClassV2ToV1.propertyInfoMapV2ToV1(mappingResponse.standoffEntities.standoffPropertyInfoMap)
      standoffEntities = StandoffEntityInfoGetResponseV1(classInfoMap, propertyInfoMap)
    } yield GetMappingResponseV1(mappingIri, mapping, standoffEntities)
}

object StandoffResponderV1Live {
  val layer: URLayer[IriConverter with MessageRelay, StandoffResponderV1] =
    ZLayer.fromZIO {
      for {
        ic      <- ZIO.service[IriConverter]
        mr      <- ZIO.service[MessageRelay]
        handler <- mr.subscribe(StandoffResponderV1Live(ic, mr))
      } yield handler
    }
}
