/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.store.iiif

import akka.actor.ActorRef
import org.knora.webapi.LiveActorMaker
import org.knora.webapi.store.SipiConnectorActorName

/**
  * A subclass of [[IIIFManager]] that allows tests to substitute standard connector for a custom one.
  *
  * @param mockStoreConnectors a [[Map]] containing the mock connectors to be used instead of the live ones.
  *                            The name of the actor (a constant from [[org.knora.webapi.store]] is used as the
  *                            key in the map.
  */
class MockableIIIFManager(mockStoreConnectors: Map[String, ActorRef]) extends IIIFManager with LiveActorMaker {

    /**
      * Initialised to the value of the key 'SipiConnectorActorName' in `mockStoreConnectors` if provided, otherwise
      * the default SipiConnector is used.
      */
    override lazy val sipiConnector: ActorRef = mockStoreConnectors.getOrElse(SipiConnectorActorName, makeDefaultSipiConnector)

}
