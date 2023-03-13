/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import zio.ZIO

import scala.concurrent.Future

import dsp.errors.NotFoundException
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.ConstructResponseUtilV2.MappingAndXSLTransformation
import org.knora.webapi.messages.util.ConstructResponseUtilV2.ResourceWithValueRdfData
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.v2.responder.standoffmessages.GetMappingRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetMappingResponseV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetXSLTransformationRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetXSLTransformationResponseV2
import org.knora.webapi.responders.Responder
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.store.iiif.errors.SipiException

/**
 * An abstract class with standoff utility methods for v2 responders.
 */
abstract class ResponderWithStandoffV2(
  responderData: ResponderData,
  runtime: zio.Runtime[ConstructResponseUtilV2]
) extends Responder(responderData.actorDeps) {

  /**
   * Gets mappings referred to in query results [[Map[IRI, ResourceWithValueRdfData]]].
   *
   * @param queryResultsSeparated query results referring to mappings.
   *
   * @param requestingUser        the user making the request.
   * @return the referred mappings.
   */
  protected def getMappingsFromQueryResultsSeparated(
    queryResultsSeparated: Map[IRI, ResourceWithValueRdfData],
    requestingUser: UserADM
  ): Future[Map[IRI, MappingAndXSLTransformation]] = {

    // collect the Iris of the mappings referred to in the resources' text values
    val mappingIris: Set[IRI] = queryResultsSeparated.flatMap { case (_, assertions: ResourceWithValueRdfData) =>
      UnsafeZioRun.runOrThrow(
        ZIO
          .service[ConstructResponseUtilV2]
          .map(_.getMappingIrisFromValuePropertyAssertions(assertions.valuePropertyAssertions))
      )(runtime)
    }.toSet

    // get all the mappings
    val mappingResponsesFuture: Vector[Future[GetMappingResponseV2]] = mappingIris.map { mappingIri: IRI =>
      for {
        mappingResponse: GetMappingResponseV2 <- appActor
                                                   .ask(
                                                     GetMappingRequestV2(
                                                       mappingIri = mappingIri,
                                                       requestingUser = requestingUser
                                                     )
                                                   )
                                                   .mapTo[GetMappingResponseV2]
      } yield mappingResponse
    }.toVector

    for {
      mappingResponses: Vector[GetMappingResponseV2] <- Future.sequence(mappingResponsesFuture)

      // get the default XSL transformations
      mappingsWithFuture: Vector[Future[(IRI, MappingAndXSLTransformation)]] =
        mappingResponses.map { mapping: GetMappingResponseV2 =>
          for {
            // if given, get the default XSL transformation
            xsltOption: Option[String] <-
              if (mapping.mapping.defaultXSLTransformation.nonEmpty) {
                val xslTransformationFuture = for {
                  xslTransformation: GetXSLTransformationResponseV2 <-
                    appActor
                      .ask(
                        GetXSLTransformationRequestV2(
                          mapping.mapping.defaultXSLTransformation.get,
                          requestingUser = requestingUser
                        )
                      )
                      .mapTo[GetXSLTransformationResponseV2]
                } yield Some(xslTransformation.xslt)

                xslTransformationFuture.recover {
                  case notFound: NotFoundException =>
                    throw SipiException(
                      s"Default XSL transformation <${mapping.mapping.defaultXSLTransformation.get}> not found for mapping <${mapping.mappingIri}>: ${notFound.message}"
                    )

                  case other => throw other
                }
              } else {
                FastFuture.successful(None)
              }
          } yield mapping.mappingIri -> MappingAndXSLTransformation(
            mapping = mapping.mapping,
            standoffEntities = mapping.standoffEntities,
            XSLTransformation = xsltOption
          )

        }

      mappings: Vector[(IRI, MappingAndXSLTransformation)] <- Future.sequence(mappingsWithFuture)
      mappingsAsMap: Map[IRI, MappingAndXSLTransformation]  = mappings.toMap
    } yield mappingsAsMap

  }

}
