/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache.api

import zio.*
import zio.macros.accessible

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceStatusResponse
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri

/**
 * Cache Service Interface
 */
@accessible
trait CacheService {
  def putUserADM(value: User): Task[Unit]
  def getUserByIriADM(iri: UserIri): Task[Option[User]]
  def putProjectADM(value: ProjectADM): Task[Unit]
  def getProjectADM(identifier: ProjectIdentifierADM): Task[Option[ProjectADM]]
  def invalidateProjectADM(identifier: KnoraProject.ProjectIri): UIO[Unit]
  def putStringValue(key: String, value: String): Task[Unit]
  def getStringValue(key: String): Task[Option[String]]
  def removeValues(keys: Set[String]): Task[Unit]
  def flushDB(requestingUser: User): Task[Unit]
  val getStatus: UIO[CacheServiceStatusResponse]
}
