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

package org.knora.webapi.messages.v2.responder.persistentmapmessages

import java.time.Instant
import java.util.UUID

import org.knora.webapi._
import org.knora.webapi.util.StringFormatter

/**
  * A trait for requests that can be sent to [[org.knora.webapi.responders.v2.PersistentMapResponderV2]].
  */
sealed trait PersistentMapResponderRequestV2

/**
  * Represents an entry in a [[PersistentMapV2]].
  *
  * @param key                  the entry's key, which must be a valid XML
  *                             [[https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName NCName]].
  * @param value                the entry's value.
  * @param lastModificationDate the entry's last modification date.
  */
case class PersistentMapEntryV2(key: String, value: String, lastModificationDate: Instant) {
    private val stringFormatter = StringFormatter.getGeneralInstance

    stringFormatter.validateNCName(key, throw InconsistentTriplestoreDataException(s"Invalid map entry key: $key"))
    stringFormatter.toSparqlEncodedString(value, throw InconsistentTriplestoreDataException(s"Invalid map entry value: $value"))

}

/**
  * Represents a persistent map of string keys to string values, stored in the triplestore.
  *
  * @param path                 the map's path, which must be a sequence of names separated by slashes (`/`). Each name must
  *                             be a valid XML [[https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName NCName]].
  * @param entries              the entries in the map.
  * @param lastModificationDate the map's last modification date.
  */
case class PersistentMapV2(path: String, entries: Set[PersistentMapEntryV2], lastModificationDate: Instant) {
    private val stringFormatter = StringFormatter.getGeneralInstance

    stringFormatter.validateMapPath(path, throw InconsistentTriplestoreDataException(s"Invalid map path: $path"))
}

/**
  * A request for a [[PersistentMapEntryV2]].
  *
  * @param mapPath     the map's path, which must be a sequence of names separated by slashes (`/`). Each name must
  *                    be a valid XML [[https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName NCName]].
  * @param mapEntryKey the map entry's key, which must be a valid XML
  *                    [[https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName NCName]].
  */
case class PersistentMapEntryGetRequestV2(mapPath: String, mapEntryKey: String) extends PersistentMapResponderRequestV2 {
    private val stringFormatter = StringFormatter.getGeneralInstance

    stringFormatter.validateMapPath(mapPath, throw BadRequestException(s"Invalid map path: $mapPath"))
    stringFormatter.validateNCName(mapEntryKey, throw BadRequestException(s"Invalid map entry key: $mapEntryKey"))
}

/**
  * A request for a [[PersistentMapV2]].
  *
  * @param mapPath the map's path, which must be a sequence of names separated by slashes (`/`). Each name must
  *                be a valid XML [[https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName NCName]].
  */
case class PersistentMapGetRequestV2(mapPath: String) extends PersistentMapResponderRequestV2 {
    private val stringFormatter = StringFormatter.getGeneralInstance

    stringFormatter.validateMapPath(mapPath, throw BadRequestException(s"Invalid map path: $mapPath"))
}

/**
  * A request to set a value in a `knora-base:Map`. The map will be created if it does not exist. A successful response
  * will be a [[PersistentMapEntryPutResponseV2]].
  *
  * @param mapPath      the map's path, which must be a sequence of names separated by slashes (`/`). Each name must
  *                     be a valid XML [[https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName NCName]].
  * @param mapEntryKey  the map entry's key, which must be a valid XML
  *                     [[https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName NCName]].
  * @param apiRequestID the ID of this API request.
  */
case class PersistentMapEntryPutRequestV2(mapPath: String, mapEntryKey: String, mapEntryValue: String, apiRequestID: UUID) extends PersistentMapResponderRequestV2 {
    private val stringFormatter = StringFormatter.getGeneralInstance

    stringFormatter.validateMapPath(mapPath, throw BadRequestException(s"Invalid map path: $mapPath"))
    stringFormatter.validateNCName(mapEntryKey, throw BadRequestException(s"Invalid map entry key: $mapEntryKey"))
    val sparqlEncodedMapEntryValue: String = stringFormatter.toSparqlEncodedString(mapEntryValue, throw BadRequestException(s"Invalid map entry value: $mapEntryValue"))
}

/**
  * A successful response to a [[PersistentMapEntryPutRequestV2]].
  */
case class PersistentMapEntryPutResponseV2()

/**
  * A request to delete a value from a `knora-base:Map`. If the map does not exist, a [[NotFoundException]] will be
  * returned. A successful response will be a [[PersistentMapEntryDeleteResponseV2]].
  *
  * @param mapPath      the map's path, which must be a sequence of names separated by slashes (`/`). Each name must
  *                     be a valid XML [[https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName NCName]].
  * @param mapEntryKey  the map entry's key, which must be a valid XML
  *                     [[https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName NCName]].
  * @param apiRequestID the ID of this API request.
  */
case class PersistentMapEntryDeleteRequestV2(mapPath: String, mapEntryKey: String, apiRequestID: UUID) extends PersistentMapResponderRequestV2 {
    private val stringFormatter = StringFormatter.getGeneralInstance

    stringFormatter.validateMapPath(mapPath, throw BadRequestException(s"Invalid map path: $mapPath"))
    stringFormatter.validateNCName(mapEntryKey, throw BadRequestException(s"Invalid map entry key: $mapEntryKey"))
}

/**
  * A successful response to a [[PersistentMapEntryDeleteRequestV2]].
  */
case class PersistentMapEntryDeleteResponseV2()

/**
  * A request to delete a `knora-base:Map` along with all its entries. If the map does not exist, a
  * [[NotFoundException]] will be returned. A successful response will be a [[PersistentMapDeleteResponseV2]].
  *
  * @param mapPath      the map's path, which must be a sequence of names separated by slashes (`/`). Each name must
  *                     be a valid XML [[https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName NCName]].
  * @param apiRequestID the ID of this API request.
  */
case class PersistentMapDeleteRequestV2(mapPath: String, apiRequestID: UUID) extends PersistentMapResponderRequestV2 {
    private val stringFormatter = StringFormatter.getGeneralInstance

    stringFormatter.validateMapPath(mapPath, throw BadRequestException(s"Invalid map path: $mapPath"))
}

/**
  * A successful response to a [[PersistentMapDeleteRequestV2]].
  */
case class PersistentMapDeleteResponseV2()
