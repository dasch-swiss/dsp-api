/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Chunk
import zio.Task

import dsp.valueobjects.LanguageCode
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.FamilyName
import org.knora.webapi.slice.admin.domain.model.GivenName
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.PasswordHash
import org.knora.webapi.slice.admin.domain.model.SystemAdmin
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
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

  def findByProjectMembership(projectIri: ProjectIri): Task[Chunk[KnoraUser]]

  def findByProjectAdminMembership(projectIri: ProjectIri): Task[Chunk[KnoraUser]]

  def findByGroupMembership(groupIri: GroupIri): Task[Chunk[KnoraUser]]

  /**
   * Saves a given user. Use the returned instance for further operations as the save operation might have changed the entity instance completely.
   *
   * @param user The [[KnoraUser]] to be saved, can be an update or a creation.
   * @return the saved entity.
   */
  def save(user: KnoraUser): Task[KnoraUser]
}

object KnoraUserRepo {
  object builtIn {
    private def makeBuiltIn(name: String, username: String) = KnoraUser(
      UserIri.unsafeFrom(s"http://www.knora.org/ontology/knora-admin#$name"),
      Username.unsafeFrom(username.toLowerCase),
      Email.unsafeFrom(s"${username.toLowerCase}@localhost"),
      FamilyName.unsafeFrom(username),
      GivenName.unsafeFrom("Knora"),
      PasswordHash.unsafeFrom("youcannotloginwiththispassword"),
      LanguageCode.en,
      UserStatus.Active,
      Chunk.empty[ProjectIri],
      Chunk.empty[GroupIri],
      SystemAdmin.IsNotSystemAdmin,
      Chunk.empty[ProjectIri],
    )

    /**
     * The system user is the owner of objects that are created by the system, rather than directly by the user,
     * such as link values for standoff resource references.
     */
    val SystemUser: KnoraUser = makeBuiltIn("SystemUser", "System")

    /**
     * Every user not logged-in is per default an anonymous user.
     */
    val AnonymousUser: KnoraUser = makeBuiltIn("AnonymousUser", "Anonymous")

    val all: Chunk[KnoraUser] = Chunk(SystemUser, AnonymousUser)

    def findOneBy(p: KnoraUser => Boolean): Option[KnoraUser] = all.find(p)

    def findAllBy(p: KnoraUser => Boolean): Chunk[KnoraUser] = all.filter(p)
  }
}
