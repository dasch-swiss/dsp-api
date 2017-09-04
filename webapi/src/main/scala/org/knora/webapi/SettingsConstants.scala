/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

    val EMBEDDED_JENA_TDB_TS_TYPE = "embedded-jena-tdb"
    val EMBEDDED_GRAPH_DB_TS_TYPE = "embedded-jena-graphdb"
    val HTTP_GRAPH_DB_TS_TYPE = "graphdb"
    val HTTP_GRAPH_DB_FREE_TS_TYPE = "graphdb-free"
    val HTTP_FUSEKI_TS_TYPE = "fuseki"
}
