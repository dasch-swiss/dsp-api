/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v1

import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.exceptions.NotFoundException
import org.knora.webapi.exceptions.SipiException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.v1.responder.ontologymessages.ConvertOntologyClassV2ToV1
import org.knora.webapi.messages.v1.responder.ontologymessages.StandoffEntityInfoGetResponseV1
import org.knora.webapi.messages.v1.responder.standoffmessages._
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.Responder.handleUnexpectedMessage

import java.util.UUID
import scala.concurrent.Future

/**
 * Responds to requests relating to the creation of mappings from XML elements and attributes to standoff classes and properties.
 */
class StandoffResponderV1(responderData: ResponderData) extends Responder(responderData) {

  /**
   * Receives a message of type [[StandoffResponderRequestV1]], and returns an appropriate response message.
   */
  def receive(msg: StandoffResponderRequestV1) = msg match {
    case CreateMappingRequestV1(xml, label, projectIri, mappingName, featureFactoryConfig, userProfile, uuid) =>
      createMappingV1(xml, label, projectIri, mappingName, featureFactoryConfig, userProfile, uuid)
    case GetMappingRequestV1(mappingIri, featureFactoryConfig, userProfile) =>
      getMappingV1(mappingIri, featureFactoryConfig, userProfile)
    case GetXSLTransformationRequestV1(xsltTextReprIri, featureFactoryConfig, userProfile) =>
      getXSLTransformation(xsltTextReprIri, featureFactoryConfig, userProfile)
    case other => handleUnexpectedMessage(other, log, this.getClass.getName)
  }

  val xsltCacheName = "xsltCache"

  /**
   * Retrieves a `knora-base:XSLTransformation` in the triplestore and requests the corresponding XSL file from Sipi.
   *
   * @param xslTransformationIri The IRI of the resource representing the XSL Transformation (a [[org.knora.webapi.messages.OntologyConstants.KnoraBase.XSLTransformation]]).
   * @param featureFactoryConfig the feature factory configuration.
   * @param userProfile          The client making the request.
   * @return a [[GetXSLTransformationResponseV1]].
   */
  private def getXSLTransformation(
    xslTransformationIri: IRI,
    featureFactoryConfig: FeatureFactoryConfig,
    userProfile: UserADM
  ): Future[GetXSLTransformationResponseV1] = {

    val xslTransformationFuture = for {
      xsltTransformation <- appActor
                              .ask(
                                GetXSLTransformationRequestV2(
                                  xsltTextRepresentationIri = xslTransformationIri,
                                  featureFactoryConfig = featureFactoryConfig,
                                  requestingUser = userProfile
                                )
                              )
                              .mapTo[GetXSLTransformationResponseV2]
    } yield GetXSLTransformationResponseV1(
      xslt = xsltTransformation.xslt
    )

    xslTransformationFuture.recover {
      case notFound: NotFoundException =>
        throw SipiException(s"XSL transformation $xslTransformationIri not found: ${notFound.message}")

      case other => throw other
    }
  }

  /**
   * Creates a mapping between XML elements and attributes to standoff classes and properties.
   * The mapping is used to convert XML documents to [[TextValueV1]] and back.
   *
   * @param xml                  the provided mapping.
   * @param featureFactoryConfig the feature factory configuration.
   * @param userProfile          the client that made the request.
   */
  private def createMappingV1(
    xml: String,
    label: String,
    projectIri: IRI,
    mappingName: String,
    featureFactoryConfig: FeatureFactoryConfig,
    userProfile: UserADM,
    apiRequestID: UUID
  ): Future[CreateMappingResponseV1] = {

    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val createMappingRequest = CreateMappingRequestV2(
      metadata = CreateMappingRequestMetadataV2(
        label = label,
        projectIri = projectIri.toSmartIri,
        mappingName = mappingName
      ),
      xml = CreateMappingRequestXMLV2(xml),
      featureFactoryConfig = featureFactoryConfig,
      requestingUser = userProfile,
      apiRequestID = apiRequestID
    )

    for {
      mappingResponse <- appActor.ask(createMappingRequest).mapTo[CreateMappingResponseV2]
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
   * @param featureFactoryConfig the feature factory configuration.
   * @param userProfile the user making the request.
   * @return a [[MappingXMLtoStandoff]].
   */
  private def getMappingV1(
    mappingIri: IRI,
    featureFactoryConfig: FeatureFactoryConfig,
    userProfile: UserADM
  ): Future[GetMappingResponseV1] =
    for {
      mappingResponse: GetMappingResponseV2 <- appActor
                                                 .ask(
                                                   GetMappingRequestV2(
                                                     mappingIri = mappingIri,
                                                     featureFactoryConfig = featureFactoryConfig,
                                                     requestingUser = userProfile
                                                   )
                                                 )
                                                 .mapTo[GetMappingResponseV2]
    } yield GetMappingResponseV1(
      mappingIri = mappingResponse.mappingIri,
      mapping = mappingResponse.mapping,
      standoffEntities = StandoffEntityInfoGetResponseV1(
        standoffClassInfoMap =
          ConvertOntologyClassV2ToV1.classInfoMapV2ToV1(mappingResponse.standoffEntities.standoffClassInfoMap),
        standoffPropertyInfoMap =
          ConvertOntologyClassV2ToV1.propertyInfoMapV2ToV1(mappingResponse.standoffEntities.standoffPropertyInfoMap)
      )
    )

}
