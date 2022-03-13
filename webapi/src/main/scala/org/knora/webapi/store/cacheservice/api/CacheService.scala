/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cacheservice.api

import zio._
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectIdentifierADM}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM}
import org.knora.webapi.messages.store.cacheservicemessages.{CacheServiceFlushDBACK, CacheServiceStatusResponse}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Cache Service Interface
 */
trait CacheService {
  def putUserADM(value: UserADM): Task[Unit]
  def getUserADM(identifier: UserIdentifierADM): Task[Option[UserADM]]
  def putProjectADM(value: ProjectADM): Task[Boolean]
  def getProjectADM(identifier: ProjectIdentifierADM): Task[Option[ProjectADM]]
  def writeStringValue(key: String, value: String): Task[Boolean]
  def getStringValue(maybeKey: Option[String]): Task[Option[String]]
  def removeValues(keys: Set[String]): Task[Boolean]
  def flushDB(requestingUser: UserADM): Task[CacheServiceFlushDBACK]
  def ping(): Task[CacheServiceStatusResponse]
}

object CacheService {
  def putUserADM(value: UserADM): ZIO[CacheService, Throwable, Unit] =
    ZIO.serviceWithZIO[CacheService](_.putUserADM(value))

  def getUserADM(identifier: UserIdentifierADM): ZIO[CacheService, Throwable, Option[UserADM]] =
    ZIO.serviceWithZIO[CacheService](_.getUserADM(identifier))

  def putProjectADM(value: ProjectADM): ZIO[CacheService, Throwable, Boolean] =
    ZIO.serviceWithZIO[CacheService](_.putProjectADM(value))

  def getProjectADM(identifier: ProjectIdentifierADM): ZIO[CacheService, Throwable, Option[ProjectADM]] =
    ZIO.serviceWithZIO[CacheService](_.getProjectADM(identifier))

  def writeStringValue(key: String, value: String): ZIO[CacheService, Throwable, Boolean] =
    ZIO.serviceWithZIO[CacheService](_.writeStringValue(key, value))

  def getStringValue(maybeKey: Option[String]): ZIO[CacheService, Throwable, Option[String]] =
    ZIO.serviceWithZIO[CacheService](_.getStringValue(maybeKey))

  def removeValues(keys: Set[String]): ZIO[CacheService, Throwable, Boolean] =
    ZIO.serviceWithZIO[CacheService](_.removeValues(keys))

  def flushDB(requestingUser: UserADM): ZIO[CacheService, Throwable, CacheServiceFlushDBACK] =
    ZIO.serviceWithZIO[CacheService](_.flushDB(requestingUser))

  def ping(): ZIO[CacheService, Throwable, CacheServiceStatusResponse] =
    ZIO.serviceWithZIO[CacheService](_.ping())
}
