/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import akka.pattern._

import scala.concurrent.Future

import dsp.errors.ForbiddenException
import org.knora.webapi.messages.admin.responder.storesmessages.ResetTriplestoreContentRequestADM
import org.knora.webapi.messages.admin.responder.storesmessages.ResetTriplestoreContentResponseADM
import org.knora.webapi.messages.admin.responder.storesmessages.StoreResponderRequestADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceFlushDB
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.ResetRepositoryContent
import org.knora.webapi.messages.store.triplestoremessages.ResetRepositoryContentACK
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.Responder.handleUnexpectedMessage

/**
 * This responder is used by [[org.knora.webapi.routing.admin.StoreRouteADM]], for piping through HTTP requests to the
 * 'Store Module'
 */
class StoresResponderADM(responderData: ResponderData) extends Responder(responderData) {

  /**
   * A user representing the Knora API server, used in those cases where a user is required.
   */
  private val systemUser = KnoraSystemInstances.Users.SystemUser

  /**
   * Receives a message extending [[StoreResponderRequestADM]], and returns an appropriate response message.
   */
  def receive(msg: StoreResponderRequestADM) = msg match {
    case ResetTriplestoreContentRequestADM(
          rdfDataObjects: List[RdfDataObject],
          prependDefaults: Boolean
        ) =>
      resetTriplestoreContent(rdfDataObjects, prependDefaults)
    case other => handleUnexpectedMessage(other, log, this.getClass.getName)
  }

  /**
   * This method send a [[ResetRepositoryContent]] message to the [[org.knora.webapi.store.triplestore.TriplestoreManager]].
   *
   * @param rdfDataObjects the payload consisting of a list of [[RdfDataObject]] send inside the message.
   * @return a future containing a [[ResetTriplestoreContentResponseADM]].
   */
  private def resetTriplestoreContent(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean = true
  ): Future[ResetTriplestoreContentResponseADM] = {

    log.debug(s"resetTriplestoreContent - called")

    for {
      // FIXME: need to call directly into the State service
      value: Boolean <- FastFuture.successful(settings.allowReloadOverHTTP)
      _ = if (!value) {
            throw ForbiddenException(
              "The ResetTriplestoreContent operation is not allowed. Did you start the server with the right flag?"
            )
          }

      resetResponse <- appActor
                         .ask(ResetRepositoryContent(rdfDataObjects, prependDefaults))
                         .mapTo[ResetRepositoryContentACK]
      _ = log.debug(s"resetTriplestoreContent - triplestore reset done - {}", resetResponse.toString)

      loadOntologiesResponse <- appActor
                                  .ask(
                                    LoadOntologiesRequestV2(
                                      requestingUser = systemUser
                                    )
                                  )
                                  .mapTo[SuccessResponseV2]
      _ = log.debug(s"resetTriplestoreContent - load ontology done - {}", loadOntologiesResponse.toString)

      _ <- appActor.ask(CacheServiceFlushDB(systemUser))
      _  = log.debug(s"resetTriplestoreContent - flushing Redis store done.")

      result = ResetTriplestoreContentResponseADM(message = "success")

    } yield result
  }

}
