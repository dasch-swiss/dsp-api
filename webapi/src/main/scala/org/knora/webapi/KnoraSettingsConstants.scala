/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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
  * 'SettingsConstants' contains constants of strings, we would generally expect to find in
  * the 'application.conf' file, which can be accessed by the application 'Settings'
  */

object TriplestoreTypes {

    val EmbeddedJenaTdb = "embedded-jena-tdb"
    val EmbeddedGraphDBSE= "embedded-jena-graphdb"
    val HttpGraphDBSE = "graphdb-se"
    val HttpGraphDBFree = "graphdb-free"
    val HttpFuseki = "fuseki"
}

object KnoraDispatchers {

    /**
      * All normal actors should run on this dispatcher (non-blocking)
      */
    val KnoraActorDispatcher = "knora-actor-dispatcher"

    /**
      * All blocking operations should run on this dispatcher (blocking)
      */
    val KnoraBlockingDispatcher = "knora-blocking-dispatcher"
}
