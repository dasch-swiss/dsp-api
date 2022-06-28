/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation
import dsp.errors.BadRequestException

import java.util.UUID

sealed trait Id
object Id {

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

    private val UserIriPrefix = "http://rdfh.ch/users/"

    /**
     * Generates a UserId instance with a new (random) UUID and a given IRI which is created from a prefix and the UUID.
     *
     * @return a new UserId instance
     */
    def fromIri(iri: Iri.UserIri): UserId = {
      val uuid: UUID = UUID.fromString(iri.value.split("/").last)
      new UserId(uuid, iri) {}
    }

    /**
     * Generates a UserId instance from a given UUID and an IRI which is created from a prefix and the UUID.
     *
     * @return a new UserId instance
     */
    def fromUuid(uuid: UUID): UserId = {
      val iri = Iri.UserIri.make(UserIriPrefix + uuid.toString).fold(e => throw e.head, v => v)
      new UserId(uuid, iri) {}
    }

    /**
     * Generates a UserId instance with a new (random) UUID and an IRI which is created from a prefix and the UUID.
     *
     * @return a new UserId instance
     */
    def make(): UserId = {
      val uuid: UUID = UUID.randomUUID()
      val iri        = Iri.UserIri.make(UserIriPrefix + uuid.toString).fold(e => throw e.head, v => v)
      new UserId(uuid, iri) {}
    }
  }
}
