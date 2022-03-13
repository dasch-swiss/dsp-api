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
import org.knora.webapi.messages.store.cacheservicemessages.{
  CacheServiceFlushDBACK,
  CacheServiceStatusOK,
  CacheServiceStatusResponse
}
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
 * @param users a map of users
 * @param lut   a lookup table of username/email to IRI
 */
case class CacheServiceInMemImpl(users: TRef[Map[IRI, UserADM]], lut: TRef[Map[String, IRI]]) extends CacheService {

  private var cache: scala.collection.mutable.Map[Any, Any] =
    scala.collection.mutable.Map[Any, Any]()

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
      _ = ZIO.debug(s"Storing user to Cache: $value")
    } yield ()).commit

  /**
   * Retrieves the user stored under the identifier (either iri, username,
   * or email).
   *
   * @param identifier the user identifier.
   */
  def getUserADM(identifier: UserIdentifierADM): Task[Option[UserADM]] = {
    // The data is stored under the IRI key.
    // Additionally, the USERNAME and EMAIL keys point to the IRI key
    val resultFuture: Future[Option[UserADM]] = identifier.hasType match {
      case UserIdentifierType.Iri => FastFuture.successful(cache.get(identifier.toIri).map(_.asInstanceOf[UserADM]))
      case UserIdentifierType.Username => {
        cache.get(identifier.toUsername) match {
          case Some(iriKey) => FastFuture.successful(cache.get(iriKey).map(_.asInstanceOf[UserADM]))
          case None         => FastFuture.successful(None)
        }
      }
      case UserIdentifierType.Email =>
        cache.get(identifier.toEmail) match {
          case Some(iriKey) => FastFuture.successful(cache.get(iriKey).map(_.asInstanceOf[UserADM]))
          case None         => FastFuture.successful(None)
        }
    }
    resultFuture
  }

  /**
   * Stores the project under the IRI and additionally the IRI under the keys
   * of SHORTCODE and SHORTNAME:
   *
   * IRI -> byte array
   * shortname -> IRI
   * shortcode -> IRI
   *
   * @param value the stored value
   */
  def putProjectADM(value: ProjectADM)(implicit ec: ExecutionContext): Future[Boolean] = {
    cache(value.id) = value
    cache(value.shortcode) = value.id
    cache(value.shortname) = value.id
    FastFuture.successful(true)
  }

  /**
   * Retrieves the project stored under the identifier (either iri, shortname, or shortcode).
   *
   * @param identifier the project identifier.
   */
  def getProjectADM(identifier: ProjectIdentifierADM)(implicit ec: ExecutionContext): Future[Option[ProjectADM]] = {
    // The data is stored under the IRI key.
    // Additionally, the SHORTNAME and SHORTCODE keys point to the IRI key
    val resultFuture: Future[Option[ProjectADM]] = identifier.hasType match {
      case ProjectIdentifierType.IRI =>
        FastFuture.successful(cache.get(identifier.toIri).map(_.asInstanceOf[ProjectADM]))
      case ProjectIdentifierType.SHORTCODE =>
        cache.get(identifier.toShortcode) match {
          case Some(iriKey) => FastFuture.successful(cache.get(iriKey).map(_.asInstanceOf[ProjectADM]))
          case None         => FastFuture.successful(None)
        }
      case ProjectIdentifierType.SHORTNAME =>
        cache.get(identifier.toShortname) match {
          case Some(iriKey) => FastFuture.successful(cache.get(iriKey).map(_.asInstanceOf[ProjectADM]))
          case None         => FastFuture.successful(None)
        }
    }
    resultFuture
  }

  /**
   * Store string or byte array value under key.
   *
   * @param key   the key.
   * @param value the value.
   */
  def writeStringValue(key: String, value: String)(implicit ec: ExecutionContext): Future[Boolean] = {

    if (key.isEmpty)
      throw EmptyKey("The key under which the value should be written is empty. Aborting writing to in-memory cache.")

    if (value.isEmpty)
      throw EmptyValue("The string value is empty. Aborting writing to in-memory cache.")

    cache(key) = value
    FastFuture.successful(true)
  }

  /**
   * Get value stored under the key as a string.
   *
   * @param maybeKey the key.
   */
  def getStringValue(maybeKey: Option[String])(implicit ec: ExecutionContext): Future[Option[String]] =
    maybeKey match {
      case Some(key) =>
        FastFuture.successful(cache.get(key).map(_.asInstanceOf[String]))
      case None =>
        FastFuture.successful(None)
    }

  /**
   * Removes values for the provided keys. Any invalid keys are ignored.
   *
   * @param keys the keys.
   */
  def removeValues(keys: Set[String])(implicit ec: ExecutionContext): Future[Boolean] = {

    logger.debug("removeValues - {}", keys)
    keys foreach { key =>
      cache remove key
    }

    FastFuture.successful(true)
  }

  /**
   * Flushes (removes) all stored content from the in-memory cache.
   */
  def flushDB(requestingUser: UserADM)(implicit ec: ExecutionContext): Future[CacheServiceFlushDBACK] = {
    cache = scala.collection.mutable.Map[Any, Any]()
    FastFuture.successful(CacheServiceFlushDBACK())
  }

  /**
   * Pings the in-memory cache to see if it is available.
   */
  def ping()(implicit ec: ExecutionContext): Future[CacheServiceStatusResponse] =
    FastFuture.successful(CacheServiceStatusOK)

}

/**
 * Companion object providing the layer with an initialized implementation
 */
object CacheServiceInMemImpl {
  val layer: ZLayer[Any, Nothing, CacheService] = {
    ZLayer {
      for {
        state <- Ref.make(Map.empty[Any, Any])
      } yield CacheServiceInMemImpl(state)
    }
  }
}
