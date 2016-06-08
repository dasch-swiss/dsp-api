/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi

/**
  * Starts Knora by bringing everything into scope by using the cake pattern. The [[LiveCore]] trait provides
  * an actor system, which is used by methods defined in the [[KnoraService]] trait, which itself provides
  * three methods: ''checkActorSystem'', ''startService'', and ''stopService''.
  */
object Main extends App with LiveCore with KnoraService  {
    //Kamon.start()

    /* Check and wait until all actors are running */
    checkActorSystem

    val arglist = args.toList

    if (arglist.contains("loadDemoData")) StartupFlags.loadDemoData send true
    if (arglist.contains("allowResetTriplestoreContentOperationOverHTTP")) StartupFlags.allowResetTriplestoreContentOperationOverHTTP send true

    /* Start the HTTP layer, allowing access */
    startService

    sys.addShutdownHook(stopService)
}
