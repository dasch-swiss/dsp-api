/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
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
