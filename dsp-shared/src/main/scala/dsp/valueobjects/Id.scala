/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.errors.BadRequestException
import zio.prelude.Validation

import java.util.UUID

sealed trait Id
object Id {

  /**
   * Stores the role's ID.
   *
   * @param uuid the role's UUID
   * @param iri the role's IRI
   */
  sealed abstract case class RoleId private (
    uuid: UUID,
    iri: Iri.RoleIri
  ) extends Id

  object RoleId {
    private val roleIriPrefix = "http://rdfh.ch/roles/"

    /**
     * Generates a RoleId instance with a UUID and a given IRI.
     *
     * @param iri the role's IRI used to create RoleId
     * @return new RoleId instance
     */
    def fromIri(iri: Iri.RoleIri): Validation[Throwable, RoleId] = {
      val uuid: UUID = UUID.fromString(iri.value.split("/").last)
      Validation.succeed(new RoleId(uuid, iri) {})
    }

    /**
     * Generates a RoleId instance with a UUID and a given IRI.
     *
     * @param uuid the UUID used to create RoleId
     * @return new RoleId instance
     */
    def fromUuid(uuid: UUID): Validation[Throwable, RoleId] = {
      val iri = Iri.RoleIri.make(roleIriPrefix + uuid.toString).fold(e => throw e.head, v => v)
      Validation.succeed(new RoleId(uuid, iri) {})
    }

    /**
     * Generates a RoleId instance with new random UUID and a new IRI.
     *
     * @return new RoleId instance
     */
    def make(): Validation[Throwable, RoleId] = {
      val uuid = UUID.randomUUID()
      val iri  = Iri.RoleIri.make(roleIriPrefix + uuid.toString).fold(e => throw e.head, v => v)
      Validation.succeed(new RoleId(uuid, iri) {})
    }
  }

  /**
   * Stores the user ID, i.e. UUID and IRI of the user
   *
   * @param uuid the UUID of the user
   * @param iri the IRI of the user
   */
  sealed abstract case class UserId private (
    uuid: UUID,
    iri: Iri.UserIri
  ) extends Id

  /**
   * Companion object for UserId. Contains factory methods for creating UserId instances.
   */
  object UserId {

    private val userIriPrefix = "http://rdfh.ch/users/"

    /**
     * Generates a UserId instance with a new (random) UUID and a given IRI which is created from a prefix and the UUID.
     *
     * @return a new UserId instance
     */
    def fromIri(iri: Iri.UserIri): Validation[Throwable, UserId] = {
      val uuid: UUID = UUID.fromString(iri.value.split("/").last)
      Validation.succeed(new UserId(uuid, iri) {})
    }

    /**
     * Generates a UserId instance from a given UUID and an IRI which is created from a prefix and the UUID.
     *
     * @return a new UserId instance
     */
    def fromUuid(uuid: UUID): Validation[Throwable, UserId] = {
      val iri = Iri.UserIri.make(userIriPrefix + uuid.toString).fold(e => throw e.head, v => v)
      Validation.succeed(new UserId(uuid, iri) {})
    }

    /**
     * Generates a UserId instance with a new (random) UUID and an IRI which is created from a prefix and the UUID.
     *
     * @return a new UserId instance
     */
    def make(): Validation[Throwable, UserId] = {
      val uuid: UUID = UUID.randomUUID()
      val iri        = Iri.UserIri.make(userIriPrefix + uuid.toString).fold(e => throw e.head, v => v)
      Validation.succeed(new UserId(uuid, iri) {})
    }
  }
}
