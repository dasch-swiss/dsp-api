/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache.impl

import zio.*
import zio.stm.*

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.*
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceStatusOK
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceStatusResponse
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.cache.api.EmptyKey
import org.knora.webapi.store.cache.api.EmptyValue

/**
 * In-Memory Cache implementation
 *
 * The state is divided into Refs used to store different types of objects.
 * A ref in itself is fiber (thread) safe, but to keep the cumulative state
 * consistent, all Refs need to be updated in a single transaction. This
 * requires STM (Software Transactional Memory) to be used.
 *
 * @param users a map of users.
 * @param projects a map of projects.
 * @param lut   a lookup table of username/email to IRI.
 */
case class CacheServiceLive(
  users: TMap[String, User],
  projects: TMap[String, ProjectADM],
  lut: TMap[String, String]
) extends CacheService {

  /**
   * Stores the user under the IRI (inside 'users') and additionally the IRI
   * under the keys of USERNAME and EMAIL (inside the 'lut'):
   *
   * IRI -> byte array
   * username -> IRI
   * email -> IRI
   *
   * @param value the value to be stored
   */
  def putUser(value: User): Task[Unit] =
    (for {
      _ <- users.put(value.id, value)
      _ <- lut.put(value.username, value.id)
      _ <- lut.put(value.email, value.id)
    } yield ()).commit

  override def getUserByIri(iri: UserIri): Task[Option[User]] = users.get(iri.value).commit

  override def getUserByUsername(username: Username): Task[Option[User]] = getUserByLookupKey(username.value)

  override def getUserByEmail(email: Email): Task[Option[User]] = getUserByLookupKey(email.value)

  private def getUserByLookupKey(key: String): UIO[Option[User]] =
    lut.get(key).some.flatMap(users.get(_).some).commit.unsome

  /**
   * Invalidates the user stored under the IRI.
   * @param iri the user's IRI.
   */
  override def invalidateUser(iri: UserIri): UIO[Unit] =
    (for {
      user <- users.get(iri.value).some
      _    <- users.delete(iri.value)
      _    <- users.delete(user.username)
      _    <- users.delete(user.email)
      _    <- lut.delete(iri.value)
      _    <- lut.delete(user.username)
      _    <- lut.delete(user.email)
    } yield ()).commit.ignore

  /**
   * Stores the project under the IRI and additionally the IRI under the keys
   * of SHORTCODE and SHORTNAME:
   *
   * IRI -> byte array
   * shortname -> IRI
   * shortcode -> IRI
   *
   * @param value the stored value
   * @return [[Unit]]
   */
  def putProjectADM(value: ProjectADM): Task[Unit] =
    (for {
      _ <- projects.put(value.id, value)
      _ <- lut.put(value.shortname, value.id)
      _ <- lut.put(value.shortcode, value.id)
    } yield ()).commit.tap(_ => ZIO.logDebug(s"Stored ProjectADM to Cache: ${value.id}"))

  /**
   * Retrieves the project stored under the identifier (either iri, shortname, or shortcode).
   *
   * The data is stored under the IRI key.
   * Additionally, the SHORTCODE and SHORTNAME keys point to the IRI key
   *
   * @param identifier the project identifier.
   * @return an optional [[ProjectADM]]
   */
  def getProjectADM(identifier: ProjectIdentifierADM): Task[Option[ProjectADM]] =
    (identifier match {
      case IriIdentifier(value)       => getProjectByIri(value)
      case ShortcodeIdentifier(value) => getProjectByShortcode(value)
      case ShortnameIdentifier(value) => getProjectByShortname(value)
    }).tap(_ => ZIO.logDebug(s"Retrieved ProjectADM from Cache: $identifier"))

  /**
   * Invalidates the project stored under the IRI.
   * This includes removing the IRI, Shortcode and Shortname keys.
   * @param iri the project's IRI.
   */
  def invalidateProjectADM(iri: ProjectIri): UIO[Unit] =
    (for {
      project  <- projects.get(iri.value).some
      shortcode = project.shortcode
      shortname = project.shortname
      _        <- projects.delete(iri.value)
      _        <- projects.delete(shortcode)
      _        <- projects.delete(shortname)
      _        <- lut.delete(iri.value)
      _        <- lut.delete(shortcode)
      _        <- lut.delete(shortname)
    } yield ()).commit.ignore

  /**
   * Retrieves the project by the IRI.
   *
   * @param iri the project's IRI
   * @return an optional [[ProjectADM]].
   */
  def getProjectByIri(iri: ProjectIri) = projects.get(iri.value).commit

  /**
   * Retrieves the project by the SHORTNAME.
   *
   * @param shortname of the project.
   * @return an optional [[ProjectADM]]
   */
  def getProjectByShortname(shortname: KnoraProject.Shortname): UIO[Option[ProjectADM]] =
    (for {
      iri     <- lut.get(shortname.value).some
      project <- projects.get(iri).some
    } yield project).commit.unsome

  /**
   * Retrieves the project by the SHORTCODE.
   *
   * @param shortcode of the project.
   * @return an optional [[ProjectADM]]
   */
  def getProjectByShortcode(shortcode: KnoraProject.Shortcode): UIO[Option[ProjectADM]] =
    (for {
      iri     <- lut.get(shortcode.value).some
      project <- projects.get(iri).some
    } yield project).commit.unsome

  /**
   * Store string or byte array value under key.
   *
   * @param key   the key.
   * @param value the value.
   */
  def putStringValue(key: String, value: String): Task[Unit] = {

    val emptyKeyError   = EmptyKey("The key under which the value should be written is empty. Aborting write to cache.")
    val emptyValueError = EmptyValue("The string value is empty. Aborting write to cache.")

    (for {
      key   <- if (key.isEmpty()) ZIO.fail(emptyKeyError) else ZIO.succeed(key)
      value <- if (value.isEmpty()) ZIO.fail(emptyValueError) else ZIO.succeed(value)
      _     <- lut.put(key, value).commit
    } yield ()).tap(_ => ZIO.logDebug(s"Wrote key: $key with value: $value to cache."))
  }

  /**
   * Get value stored under the key as a string.
   *
   * @param maybeKey the key.
   * @return an optional [[String]].
   */
  def getStringValue(key: String): Task[Option[String]] =
    lut.get(key).commit.tap(value => ZIO.logDebug(s"Retrieved key: $key with value: $value from cache."))

  /**
   * Removes values for the provided keys. Any invalid keys are ignored.
   *
   * @param keys the keys.
   */
  def removeValues(keys: Set[String]): Task[Unit] =
    (for {
      _ <- ZIO.foreach(keys)(key => lut.delete(key).commit) // FIXME: is this realy thread safe?
    } yield ()).tap(_ => ZIO.logDebug(s"Removed keys from cache: $keys"))

  /**
   * Flushes (removes) all stored content from the in-memory cache.
   */
  def flushDB(requestingUser: User): Task[Unit] =
    (for {
      _ <- users.foreach((k, _) => users.delete(k))
      _ <- projects.foreach((k, _) => projects.delete(k))
      _ <- lut.foreach((k, _) => lut.delete(k))
    } yield ()).commit.tap(_ => ZIO.logDebug("Flushed in-memory cache"))

  /**
   * Pings the in-memory cache to see if it is available.
   */
  val getStatus: UIO[CacheServiceStatusResponse] =
    ZIO.succeed(CacheServiceStatusOK)
}

object CacheServiceLive {
  val layer: ZLayer[Any, Nothing, CacheService] =
    ZLayer {
      for {
        users    <- TMap.empty[String, User].commit
        projects <- TMap.empty[String, ProjectADM].commit
        lut      <- TMap.empty[String, String].commit
      } yield CacheServiceLive(users, projects, lut)
    }.tap(_ => ZIO.logInfo(">>> In-Memory Cache Service Initialized <<<"))
}
