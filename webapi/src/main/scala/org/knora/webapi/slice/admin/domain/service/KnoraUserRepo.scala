/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Task

import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.common.repo.service.Repository

trait KnoraUserRepo extends Repository[KnoraUser, UserIri] {

  /**
   * Retrieves an user by its email address.
   *
   * @param id The identifier of type [[Email]].
   * @return the entity with the given id or [[None]] if none found.
   */
  def findByEmail(id: Email): Task[Option[KnoraUser]]

  final def existsByEmail(email: Email): Task[Boolean] =
    findByEmail(email).map(_.isDefined)

  /**
   * Retrieves an user by its username.
   *
   * @param id The identifier of type [[Username]].
   * @return the entity with the given id or [[None]] if none found.
   */
  def findByUsername(id: Username): Task[Option[KnoraUser]]

  final def existsByUsername(id: Username): Task[Boolean] =
    findByUsername(id).map(_.isDefined)

  /**
   * Saves a given user. Use the returned instance for further operations as the save operation might have changed the entity instance completely.
   *
   * @param user The [[KnoraUser]] to be saved, can be an update or a creation.
   * @return the saved entity.
   */
  def save(user: KnoraUser): Task[KnoraUser]
}
