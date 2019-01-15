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
  * Contains constants used inside the 'org.knora.rapier.store' package.
  * These can be used from other packages by importing 'org.knora.rapier.store._'
  */
package object store {
    val STORE_MANAGER_ACTOR_NAME: String = "storeManager"
    val STORE_MANAGER_ACTOR_PATH: String = "/user/" + STORE_MANAGER_ACTOR_NAME

    val TRIPLESTORE_MANAGER_ACTOR_NAME: String = "triplestoreManager"
    val TRIPLESTORE_MANAGER_ACTOR_PATH: String = STORE_MANAGER_ACTOR_PATH + "/" + TRIPLESTORE_MANAGER_ACTOR_NAME

    val HTTP_TRIPLESTORE_ACTOR_NAME: String = "httpTriplestoreRouter"
    val EMBEDDED_JENA_ACTOR_NAME: String = "embeddedJenaTDB"
    val EMBEDDED_GRAPH_DB_ACTOR_NAME: String = "embeddedJenaGraphDB"
    val FAKE_TRIPLESTORE_ACTOR_NAME: String = "fakeTriplestore"
}
