/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import akka.pattern._
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.util.rdf.{RdfFeatureFactory, RdfFormatUtil, RdfModel, Turtle}
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.metadatamessages._
import org.knora.webapi.responders.Responder.handleUnexpectedMessage
import org.knora.webapi.responders.{IriLocker, Responder}

import scala.concurrent.Future

/**
 * Responds to requests dealing with project metadata.
 */
class MetadataResponderV2(responderData: ResponderData) extends Responder(responderData) {

  /**
   * Receives a message of type [[MetadataResponderRequestV2]], and returns an appropriate response message.
   */
  def receive(msg: MetadataResponderRequestV2) = msg match {
    case getRequest: MetadataGetRequestV2 => getProjectMetadata(projectADM = getRequest.projectADM)
    case putRequest: MetadataPutRequestV2 => putProjectMetadata(putRequest)
    case other                            => handleUnexpectedMessage(other, log, this.getClass.getName)
  }

  /**
   * GET metadata graph of a project.
   *
   * @param projectADM     the project whose metadata is requested.
   * @return a [[MetadataGetResponseV2]].
   */
  private def getProjectMetadata(projectADM: ProjectADM): Future[MetadataGetResponseV2] = {
    val graphIri: IRI = stringFormatter.projectMetadataNamedGraphV2(projectADM)

    for {
      metadataGraph <- (storeManager ? NamedGraphDataRequest(graphIri)).mapTo[NamedGraphDataResponse]
    } yield MetadataGetResponseV2(metadataGraph.turtle)

  }

  /**
   * PUT metadata graph of a project. Every time a new metdata information is given for a project, it overwrites the
   * previous metadata graph.
   *
   * @param request  the request to put the metadata graph into the triplestore.
   * @return a [[SuccessResponseV2]].
   */
  private def putProjectMetadata(request: MetadataPutRequestV2): Future[SuccessResponseV2] = {
    val metadataGraphIRI: IRI = stringFormatter.projectMetadataNamedGraphV2(request.projectADM)
    val graphContent: String = request.toTurtle(request.featureFactoryConfig)

    def makeTaskFuture: Future[SuccessResponseV2] =
      for {
        // Create the project metadata graph.
        _ <- (storeManager ?
          InsertGraphDataContentRequest(
            graphContent = graphContent,
            graphName = metadataGraphIRI
          )).mapTo[InsertGraphDataContentResponse]

        // Check if the created metadata graph has the same content as the one in the request.
        createdMetadata: MetadataGetResponseV2 <- getProjectMetadata(request.projectADM)

        rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(request.featureFactoryConfig)

        savedModel: RdfModel = rdfFormatUtil.parseToRdfModel(
          rdfStr = createdMetadata.turtle,
          rdfFormat = Turtle
        )

        _ = if (!savedModel.isIsomorphicWith(request.rdfModel)) {
          throw AssertionException("Project metadata was stored, but is not correct. Please report this a bug.")
        }
      } yield SuccessResponseV2(s"Project metadata was stored for project <${request.projectIri}>.")

    for {
      // Create the metadata graph holding an update lock on the graph IRI so that no other client can
      // create or update the same graph simultaneously.
      taskResult <- IriLocker.runWithIriLock(
        request.apiRequestID,
        metadataGraphIRI,
        () => makeTaskFuture
      )
    } yield taskResult
  }
}
