/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cacheservice.impl

import akka.http.scaladsl.util.FastFuture
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.projectsmessages.{
  ProjectADM,
  ProjectIdentifierADM,
  ProjectIdentifierType
}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM, UserIdentifierType}
import org.knora.webapi.messages.store.cacheservicemessages.{CacheServiceStatusOK, CacheServiceStatusResponse}
import org.knora.webapi.store.cacheservice.api.{CacheService, EmptyKey, EmptyValue}
import zio._
import zio.stm._

import scala.concurrent.{ExecutionContext, Future}

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
  users: TRef[Map[String, UserADM]],
  projects: TRef[Map[String, ProjectADM]],
  lut: TRef[Map[String, String]]
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
      _ <- users.update(_ + (value.id -> value))
      _ <- lut.update(_ + (value.username -> value.id))
      _ <- lut.update(_ + (value.email -> value.id))
      _  = ZIO.debug(s"Storing user to Cache: $value")
    } yield ()).commit

  /**
   * Retrieves the user stored under the identifier (either iri, username, or email).
   *
   * The data is stored under the IRI key.
   * Additionally, the USERNAME and EMAIL keys point to the IRI key
   *
   * @param identifier the user identifier.
   */
  def getUserADM(identifier: UserIdentifierADM): Task[Option[UserADM]] =
    identifier.hasType match {
      case UserIdentifierType.Iri      => getUserByIri(identifier.toIri)
      case UserIdentifierType.Username => getUserByUsernameOrEmail(identifier.toUsername)
      case UserIdentifierType.Email    => getUserByUsernameOrEmail(identifier.toEmail)
    }

  /**
   * Retrieves the user stored under the IRI.
   *
   * @param id the user's IRI.
   * @return an optional [[UserADM]].
   */
  def getUserByIri(id: String): ZIO[Any, Nothing, Option[UserADM]] =
    users.get.map(_.get(id)).commit

  /**
   * Retrieves the user stored under the username or email.
   *
   * @param usernameOrEmail of the user.
   * @return an optional [[UserADM]].
   */
  def getUserByUsernameOrEmail(usernameOrEmail: String): ZIO[Any, Nothing, Option[UserADM]] =
    for {
      maybeIri  <- lut.get.map(_.get(usernameOrEmail)).commit
      maybeUser <- getUserByIri(maybeIri.getOrElse("-")) //FIXME: not cool
    } yield maybeUser

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
  def putProjectADM(value: ProjectADM)(implicit ec: ExecutionContext): Task[Unit] =
    (for {
      _ <- projects.update(_ + (value.id -> value))
      _ <- lut.update(_ + (value.shortname -> value.id))
      _ <- lut.update(_ + (value.shortcode -> value.id))
      _  = ZIO.debug(s"Storing project to Cache: $value")
    } yield ()).commit

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
    identifier.hasType match {
      case ProjectIdentifierType.IRI       => getProjectByIri(identifier.toIri)
      case ProjectIdentifierType.SHORTCODE => getProjectByShortcodeOrShortname(identifier.toShortcode)
      case ProjectIdentifierType.SHORTNAME => getProjectByShortcodeOrShortname(identifier.toShortname)
    }

  /**
   * Retrieves the project stored under the IRI.
   *
   * @param id the project's IRI
   * @return an optional [[ProjectADM]].
   */
  def getProjectByIri(id: String) =
    projects.get.map(_.get(id)).commit

  /**
   * Retrieves the project stored under a SHORTCODE or SHORTNAME.
   *
   * @param shortcodeOrShortname of the project.
   * @return an optional [[ProjectADM]]
   */
  def getProjectByShortcodeOrShortname(shortcodeOrShortname: String) =
    for {
      maybeIri <- lut.get.map(_.get(shortcodeOrShortname)).commit
      project  <- getProjectByIri(maybeIri.getOrElse("-"))
    } yield project

  /**
   * Store string or byte array value under key.
   *
   * @param key   the key.
   * @param value the value.
   */
  def writeStringValue(key: String, value: String): Task[Unit] = {

    val emptyKeyError   = EmptyKey("The key under which the value should be written is empty. Aborting write to cache.")
    val emptyValueError = EmptyValue("The string value is empty. Aborting write to cache.")

    for {
      key <- if (key.isEmpty()) Task.fail(emptyKeyError)
             else Task.succeed(key)
      value <- if (value.isEmpty()) Task.fail(emptyValueError)
               else Task.succeed(value)
    } yield lut.get.map(_ + (key -> value)).commit
  }

  /**
   * Get value stored under the key as a string.
   *
   * @param maybeKey the key.
   * @return an optional [[String]].
   */
  def getStringValue(maybeKey: Option[String]): Task[Option[String]] = {
    val noKeyError = EmptyKey("No key provided.")
    maybeKey match {
      case Some(key) => lut.get.map(_.get(key)).commit
      case None      => Task.fail(noKeyError)
    }
  }

  /**
   * Removes values for the provided keys. Any invalid keys are ignored.
   *
   * @param keys the keys.
   */
  def removeValues(keys: Set[String]): Task[Unit] =
    for {
      _ <- ZIO.logDebug(s"removing keys: $keys")
      _ <- ZIO.foreach(keys)(key => lut.update(_ - key).commit) // FIXME: is this realy thread safe?
    } yield ()

  /**
   * Flushes (removes) all stored content from the in-memory cache.
   */
  def flushDB(requestingUser: UserADM): Task[Unit] =
    (for {
      _ <- users.update(_ => Map.empty[String, UserADM])
      _ <- projects.update(_ => Map.empty[String, ProjectADM])
      _ <- lut.update(_ => Map.empty[String, String])
    } yield ()).commit

  /**
   * Pings the in-memory cache to see if it is available.
   */
  def ping()(implicit ec: ExecutionContext): Task[CacheServiceStatusResponse] =
    Task.succeed(CacheServiceStatusOK)
}

/**
 * Companion object providing the layer with an initialized implementation
 */
object CacheServiceInMemImpl {
  val layer: ZLayer[Any, Nothing, CacheService] = {
    ZLayer {
      for {
        users    <- TRef.make(Map.empty[String, UserADM]).commit
        projects <- TRef.make(Map.empty[String, ProjectADM]).commit
        lut      <- TRef.make(Map.empty[String, String]).commit
      } yield CacheServiceInMemImpl(users, projects, lut)
    }
  }
}
