/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders

import akka.actor._
import org.knora.webapi.core.{ActorMaker, LiveActorMaker}
import org.knora.webapi.messages.util.ResponderData

/**
 * A subclass of [[ResponderManager]] that allows tests to substitute custom responders for the standard ones.
 *
 * @param mockRespondersOrStoreConnectors a [[Map]] containing the mock responders to be used instead of the live ones.
 *                                        The name of the actor (a constant from [[org.knora.webapi.responders]] is
 *                                        used as the key in the map.
 * @param appActor                        the main application actor.
 */
class MockableResponderManager(
  mockRespondersOrStoreConnectors: Map[String, ActorRef],
  appActor: ActorRef,
  responderData: ResponderData
) extends ResponderManager(appActor, responderData)
    with LiveActorMaker {
  this: ActorMaker =>

}
