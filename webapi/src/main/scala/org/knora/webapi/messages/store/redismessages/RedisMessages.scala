/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.store.redismessages

import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectIdentifierADM}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM}

sealed trait RedisRequest

/**
  * Message equesting to write project to cache.
  */
case class RedisPutProjectADM(value: ProjectADM) extends RedisRequest

/**
  * Message requesting to retrieve project from cache.
  */
case class RedisGetProjectADM(identifier: ProjectIdentifierADM) extends RedisRequest

/**
  * Message requesting to write user to cache.
  */
case class RedisPutUserADM(value: UserADM) extends RedisRequest

/**
  * Message requesting to retrieve user from cache.
  */
case class RedisGetUserADM(identifier: UserIdentifierADM) extends RedisRequest

/**
  * Message requesting to store a simple string under the supplied key.
  */
case class RedisPutString(key: String, value: String) extends RedisRequest

/**
  * Message requesting to retrieve simple string stored under the key.
  */
case class RedisGetString(key: Option[String]) extends RedisRequest

/**
  * Message requesting to remove anything stored under the keys.
  */
case class RedisRemoveValues(keys: Set[String]) extends RedisRequest

/**
  * Message requesting to completely empty the cache (wipe everything).
  */
case class RedisFlushDB(requestingUser: UserADM) extends RedisRequest

/**
  * Message acknowledging the flush.
  */
case class RedisFlushDBACK()
