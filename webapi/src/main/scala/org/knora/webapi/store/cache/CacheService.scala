/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache

import zio._
import zio.stm._

import org.knora.webapi.messages.admin.responder.projectsmessages.Project
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username

/**
 * In-Memory Cache implementation
 *
 * The state is divided into Refs used to store different types of objects.
 * A ref in itself is fiber (thread) safe, but to keep the cumulative state
 * consistent, all Refs need to be updated in a single transaction. This
 * requires STM (Software Transactional Memory) to be used.
 */
case class CacheService(
  users: TMap[UserIri, User],
  projects: TMap[ProjectIri, Project],
  mappingUsernameUserIri: TMap[Username, UserIri],
  mappingEmailUserIri: TMap[Email, UserIri],
  mappingShortcodeProjectIri: TMap[Shortcode, ProjectIri],
  mappingShortnameProjectIri: TMap[Shortname, ProjectIri],
) {

  /**
   * Stores the user under the IRI  and additionally the IRI
   *
   * users:
   *   IRI -> byte array
   *
   * lookupTableUsers:
   *   username -> IRI
   *   email -> IRI
   *
   * @param value the value to be stored
   */
  def putUser(value: User): Task[Unit] =
    (for {
      _ <- users.put(value.userIri, value)
      _ <- mappingUsernameUserIri.put(value.getUsername, value.userIri)
      _ <- mappingEmailUserIri.put(value.getEmail, value.userIri)
    } yield ()).commit

  def getUserByIri(iri: UserIri): Task[Option[User]] = users.get(iri).commit

  def getUserByUsername(username: Username): Task[Option[User]] =
    mappingUsernameUserIri.get(username).some.flatMap(users.get(_).some).commit.unsome

  def getUserByEmail(email: Email): Task[Option[User]] =
    mappingEmailUserIri.get(email).some.flatMap(users.get(_).some).commit.unsome

  /**
   * Invalidates the user stored under the IRI.
   * @param iri the user's IRI.
   */
  def invalidateUser(iri: UserIri): UIO[Unit] =
    (for {
      user <- users.get(iri).some
      _    <- users.delete(iri)
      _    <- mappingUsernameUserIri.delete(user.getUsername)
      _    <- mappingEmailUserIri.delete(user.getEmail)
    } yield ()).commit.ignore

  /**
   * Stores the project under the IRI and additionally the IRI under the keys
   * of Shortcode and Shortname:
   *
   * projects:
   *  IRI -> byte array
   *
   * lookupTableProjects:
   *  shortname -> IRI
   *  shortcode -> IRI
   *
   * @param value the stored value
   * @return [[Unit]]
   */
  def putProjectADM(value: Project): Task[Unit] =
    (for {
      _ <- projects.put(value.projectIri, value)
      _ <- mappingShortcodeProjectIri.put(value.getShortcode, value.projectIri)
      _ <- mappingShortnameProjectIri.put(value.getShortname, value.projectIri)
    } yield ()).commit

  /**
   * Retrieves the project stored under the identifier (either iri, shortname, or shortcode).
   *
   * The data is stored under the IRI key.
   * Additionally, the Shortcode and Shortname keys point to the IRI key
   *
   * @param identifier the project identifier.
   * @return an optional [[ProjectADM]]
   */
  def getProjectADM(identifier: ProjectIdentifierADM): Task[Option[Project]] =
    identifier match {
      case IriIdentifier(projectIri) => projects.get(projectIri).commit
      case ShortcodeIdentifier(code) =>
        mappingShortcodeProjectIri.get(code).some.flatMap(projects.get(_).some).commit.unsome
      case ShortnameIdentifier(name) =>
        mappingShortnameProjectIri.get(name).some.flatMap(projects.get(_).some).commit.unsome
    }

  /**
   * Invalidates the project stored under the IRI.
   * This includes removing the IRI, Shortcode and Shortname keys.
   * @param iri the project's IRI.
   */
  def invalidateProjectADM(iri: ProjectIri): UIO[Unit] =
    (for {
      project <- projects.get(iri).some
      _       <- projects.delete(iri)
      _       <- mappingShortcodeProjectIri.delete(project.getShortcode)
      _       <- mappingShortnameProjectIri.delete(project.getShortname)
    } yield ()).commit.ignore

  /**
   * Flushes (removes) all stored content from the in-memory cache.
   */
  def clearCache(): Task[Unit] =
    (for {
      _ <- users.removeIf((_, _) => true)
      _ <- projects.removeIf((_, _) => true)
      _ <- mappingUsernameUserIri.removeIf((_, _) => true)
      _ <- mappingEmailUserIri.removeIf((_, _) => true)
      _ <- mappingShortcodeProjectIri.removeIf((_, _) => true)
      _ <- mappingShortnameProjectIri.removeIf((_, _) => true)
    } yield ()).commit

}

object CacheService {
  val layer: ZLayer[Any, Nothing, CacheService] =
    ZLayer {
      for {
        users            <- TMap.empty[UserIri, User].commit
        projects         <- TMap.empty[ProjectIri, Project].commit
        usernameMapping  <- TMap.empty[Username, UserIri].commit
        emailMapping     <- TMap.empty[Email, UserIri].commit
        shortcodeMapping <- TMap.empty[Shortcode, ProjectIri].commit
        shortnameMapping <- TMap.empty[Shortname, ProjectIri].commit
      } yield CacheService(users, projects, usernameMapping, emailMapping, shortcodeMapping, shortnameMapping)
    }.tap(_ => ZIO.logInfo(">>> In-Memory Cache Service Initialized <<<"))
}
