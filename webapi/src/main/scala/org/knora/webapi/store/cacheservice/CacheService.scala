/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of the DaSCH Service Platform.
 *
 *  The DaSCH Service Platform  is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU Affero General Public
 *  License as published by the Free Software Foundation, either version 3
 *  of the License, or (at your option) any later version.
 *
 *  The DaSCH Service Platform is distributed in the hope that it will be
 *  useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 *  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with the DaSCH Service Platform.  If not, see
 *  <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.store.cacheservice

import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectIdentifierADM}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM}
import org.knora.webapi.messages.store.cacheservicemessages.{CacheServiceFlushDBACK, CacheServiceStatusResponse}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Cache Service Interface
  */
trait CacheService {
  def putUserADM(value: UserADM)(implicit ec: ExecutionContext): Future[Boolean]
  def getUserADM(identifier: UserIdentifierADM)(implicit ec: ExecutionContext): Future[Option[UserADM]]
  def putProjectADM(value: ProjectADM)(implicit ec: ExecutionContext): Future[Boolean]
  def getProjectADM(identifier: ProjectIdentifierADM)(implicit ec: ExecutionContext): Future[Option[ProjectADM]]
  def writeStringValue(key: String, value: String)(implicit ec: ExecutionContext): Future[Boolean]
  def getStringValue(maybeKey: Option[String])(implicit ec: ExecutionContext): Future[Option[String]]
  def removeValues(keys: Set[String])(implicit ec: ExecutionContext): Future[Boolean]
  def flushDB(requestingUser: UserADM)(implicit ec: ExecutionContext): Future[CacheServiceFlushDBACK]
  def ping()(implicit ec: ExecutionContext): Future[CacheServiceStatusResponse]
}
