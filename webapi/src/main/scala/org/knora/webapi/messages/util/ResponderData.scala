/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
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

package org.knora.webapi.messages.util

import akka.actor.{ActorRef, ActorSystem}
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.store.cacheservice.settings.CacheServiceSettings

/**
 * Data needed to be passed to each responder.
 *
 * @param system   the actor system.
 * @param appActor the main application actor.
 */
case class ResponderData(
  system: ActorSystem,
  appActor: ActorRef,
  knoraSettings: KnoraSettingsImpl,
  cacheServiceSettings: CacheServiceSettings
)
