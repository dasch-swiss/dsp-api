/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.store.cacheservicemessages

import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectIdentifierADM}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM}
import org.knora.webapi.messages.store.StoreRequest

sealed trait CacheServiceRequest extends StoreRequest

/**
  * Message requesting to write project to cache.
  */
case class CacheServicePutProjectADM(value: ProjectADM) extends CacheServiceRequest

/**
  * Message requesting to retrieve project from cache.
  */
case class CacheServiceGetProjectADM(identifier: ProjectIdentifierADM) extends CacheServiceRequest

/**
  * Message requesting to write user to cache.
  */
case class CacheServicePutUserADM(value: UserADM) extends CacheServiceRequest

/**
  * Message requesting to retrieve user from cache.
  */
case class CacheServiceGetUserADM(identifier: UserIdentifierADM) extends CacheServiceRequest

/**
  * Message requesting to store a simple string under the supplied key.
  */
case class CacheServicePutString(key: String, value: String) extends CacheServiceRequest

/**
  * Message requesting to retrieve simple string stored under the key.
  */
case class CacheServiceGetString(key: Option[String]) extends CacheServiceRequest

/**
  * Message requesting to remove anything stored under the keys.
  */
case class CacheServiceRemoveValues(keys: Set[String]) extends CacheServiceRequest

/**
  * Message requesting to completely empty the cache (wipe everything).
  */
case class CacheServiceFlushDB(requestingUser: UserADM) extends CacheServiceRequest

/**
  * Message acknowledging the flush.
  */
case class CacheServiceFlushDBACK()

/**
  * Queries Cache Service status.
  */
case object CacheServiceGetStatus extends CacheServiceRequest

/**
  * Represents a response for [[CacheServiceGetStatus]].
  */
sealed trait CacheServiceStatusResponse

/**
  * Represents a positive response for [[CacheServiceGetStatus]].
  */
case object CacheServiceStatusOK extends CacheServiceStatusResponse

/**
  * Represents a negative response for [[CacheServiceGetStatus]].
  */
case object CacheServiceStatusNOK extends CacheServiceStatusResponse
