/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
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
