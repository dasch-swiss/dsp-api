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
    val StoreManagerActorName: String = "storeManager"
    val StoreManagerActorPath: String = "/user/" + StoreManagerActorName

    val TriplestoreManagerActorName: String = "triplestoreManager"
    val TriplestoreManagerActorPath: String = StoreManagerActorPath + "/" + TriplestoreManagerActorName

    val SipiManagerActorName: String = "sipiManager"
    val SipiManagerActorPath: String = StoreManagerActorPath + "/" + SipiManagerActorName

    val SipiConnectorActorName: String = "sipiConnector"


    val HttpTriplestoreActorName: String = "httpTriplestoreRouter"
    val EmbeddedJenaActorName: String = "embeddedJenaTDB"
    val EmbeddedGraphDBActorNAme: String = "embeddedJenaGraphDB"
    val FakeTriplestoreActorName: String = "fakeTriplestore"
}
