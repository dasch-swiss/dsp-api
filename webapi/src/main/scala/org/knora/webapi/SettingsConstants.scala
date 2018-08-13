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
    val MyBlockingDispatcher = "my-blocking-dispatcher"
    val MyHttpTriplestoreConnectorDispatcher = "my-httpTriplestoreConnector-dispatcher"
    val MyV1Dispatcher = "my-v1-dispatcher"
    val MyV2Dispatcher = "my-v2-dispatcher"
    val MyAdminDispatcher = "my-admin-dispatcher"
}
