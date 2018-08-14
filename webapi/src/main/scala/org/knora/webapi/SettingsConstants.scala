/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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
object SettingsConstants {

    val EmbeddedJenaTdbTsType = "embedded-jena-tdb"
    val EmbeddedGraphDbTsType = "embedded-jena-graphdb"
    val HttpGraphDbTsType = "graphdb"
    val HttpFusekiTsType = "fuseki"
}

object KnoraDispatchers {

    /**
      * Ask, future, and blocking operations should run on this dispatcher
      */
    val KnoraAskDispatcher = "knora-ask-dispatcher"

    /**
      * The store actors should run on this dispatcher
      */
    val KnoraStoreDispatcher = "knora-store-dispatcher"

    /**
      * The sipi actors should run on this dispatcher
      */
    val KnoraSipiDispatcher = "knora-sipi-dispatcher"

    /**
      * The V1 actors should run on this dispatcher
      */
    val KnoraV1Dispatcher = "knora-v1-dispatcher"

    /**
      * The V2 actors should run on this dispatcher
      */
    val KnoraV2Dispatcher = "knora-v2-dispatcher"

    /**
      * The Admin actors should run on this dispatcher
      */
    val KnoraAdminDispatcher = "knora-admin-dispatcher"
}
