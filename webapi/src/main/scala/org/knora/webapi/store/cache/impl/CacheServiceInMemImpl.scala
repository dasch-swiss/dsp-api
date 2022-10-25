/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache.impl

import zio._
import zio.stm._

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierType
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierType
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceStatusOK
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceStatusResponse
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
case class CacheServiceInMemImpl(
  users: TMap[String, UserADM],
  projects: TMap[String, ProjectADM],
  lut: TMap[String, String] // sealed trait for key type
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
  def putUserADM(value: UserADM): Task[Unit] =
    (for {
      _ <- users.put(value.id, value)
      _ <- lut.put(value.username, value.id)
      _ <- lut.put(value.email, value.id)
    } yield ()).commit.tap(_ => ZIO.logDebug(s"Stored UserADM to Cache: ${value.id}"))

  /**
   * Retrieves the user stored under the identifier (either iri, username, or email).
   *
   * The data is stored under the IRI key.
   * Additionally, the USERNAME and EMAIL keys point to the IRI key
   *
   * @param identifier the user identifier.
   */
  def getUserADM(identifier: UserIdentifierADM): Task[Option[UserADM]] =
    (identifier.hasType match {
      case UserIdentifierType.Iri      => getUserByIri(identifier.toIri)
      case UserIdentifierType.Username => getUserByUsernameOrEmail(identifier.toUsername)
      case UserIdentifierType.Email    => getUserByUsernameOrEmail(identifier.toEmail)
    }).tap(_ => ZIO.logDebug(s"Retrieved UserADM from Cache: ${identifier}"))

  /**
   * Retrieves the user stored under the IRI.
   *
   * @param id the user's IRI.
   * @return an optional [[UserADM]].
   */
  def getUserByIri(id: String): UIO[Option[UserADM]] =
    users.get(id).commit

  /**
   * Retrieves the user stored under the username or email.
   *
   * @param usernameOrEmail of the user.
   * @return an optional [[UserADM]].
   */
  def getUserByUsernameOrEmail(usernameOrEmail: String): UIO[Option[UserADM]] =
    (for {
      iri  <- lut.get(usernameOrEmail).some
      user <- users.get(iri).some
    } yield user).commit.unsome // watch Spartan session about error. post example on Spartan channel

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
    (identifier.hasType match {
      case ProjectIdentifierType.IRI       => getProjectByIri(identifier.toIri)
      case ProjectIdentifierType.SHORTCODE => getProjectByShortcodeOrShortname(identifier.toShortcode)
      case ProjectIdentifierType.SHORTNAME => getProjectByShortcodeOrShortname(identifier.toShortname)
    }).tap(_ => ZIO.logDebug(s"Retrieved ProjectADM from Cache: $identifier"))

  /**
   * Retrieves the project stored under the IRI.
   *
   * @param id the project's IRI
   * @return an optional [[ProjectADM]].
   */
  def getProjectByIri(id: String) = projects.get(id).commit

  /**
   * Retrieves the project stored under a SHORTCODE or SHORTNAME.
   *
   * @param shortcodeOrShortname of the project.
   * @return an optional [[ProjectADM]]
   */
  def getProjectByShortcodeOrShortname(shortcodeOrShortname: String): UIO[Option[ProjectADM]] =
    (for {
      iri     <- lut.get(shortcodeOrShortname).some
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
  def flushDB(requestingUser: UserADM): Task[Unit] =
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

/**
 * Companion object providing the layer with an initialized implementation
 */
object CacheServiceInMemImpl {
  val layer: ZLayer[Any, Nothing, CacheService] =
    ZLayer {
      for {
        users    <- TMap.empty[String, UserADM].commit
        projects <- TMap.empty[String, ProjectADM].commit
        lut      <- TMap.empty[String, String].commit
      } yield CacheServiceInMemImpl(users, projects, lut)
    }.tap(_ => ZIO.logInfo(">>> In-Memory Cache Service Initialized <<<"))
}
