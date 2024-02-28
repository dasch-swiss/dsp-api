/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache.api

import zio.*
import zio.macros.accessible

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username

/**
 * Cache Service Interface
 */
@accessible
trait CacheService {

  def putUser(value: User): Task[Unit]

  def getUserByIri(iri: UserIri): Task[Option[User]]

  def getUserByUsername(username: Username): Task[Option[User]]

  def getUserByEmail(email: Email): Task[Option[User]]

  /**
   * Invalidates the user stored under the IRI.
   *
   * @param iri the user's IRI.
   */
  def invalidateUser(iri: UserIri): UIO[Unit]

  def putProjectADM(value: ProjectADM): Task[Unit]

  def getProjectADM(identifier: ProjectIdentifierADM): Task[Option[ProjectADM]]

  def invalidateProjectADM(identifier: ProjectIri): UIO[Unit]

  def clearCache(): Task[Unit]
}
