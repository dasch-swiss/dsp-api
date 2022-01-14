/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Status}
import akka.event.LoggingReceive
import akka.routing.FromConfig
import org.knora.webapi.core.ActorMaker
import org.knora.webapi.exceptions.UnexpectedMessageException
import org.knora.webapi.messages.store.sipimessages.IIIFRequest
import org.knora.webapi.settings.{KnoraDispatchers, _}

/**
 * Makes requests to IIIF servers.
 */
class IIIFManager extends Actor with ActorLogging {
  this: ActorMaker =>

  /**
   * Constructs the [[SipiConnector]] actor (pool).
   */
  protected final def makeDefaultSipiConnector: ActorRef =
    makeActor(
      FromConfig.props(Props[SipiConnector]()).withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
      SipiConnectorActorName
    )

  /**
   * Subclasses can override this member to substitute a custom actor instead of the default SipiConnector.
   */
  protected lazy val sipiConnector: ActorRef = makeDefaultSipiConnector

  def receive = LoggingReceive {
    case sipiMessages: IIIFRequest => sipiConnector forward sipiMessages
    case other =>
      sender() ! Status.Failure(UnexpectedMessageException(s"SipiManager received an unexpected message: $other"))
  }

}
