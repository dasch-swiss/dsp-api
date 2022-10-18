/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation

import java.util.UUID
import scala.util.Try

import dsp.errors.ValidationException
import dsp.valueobjects.Iri
import zio.json.JsonCodec
import zio.json.DeriveJsonCodec
import zio.json.JsonDecoder
import zio.json.JsonEncoder

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

    implicit val decoder: JsonDecoder[UserId] = JsonDecoder[Iri.UserIri].mapOrFail { case iri =>
      UserId.fromIri(iri).toEitherWith(e => e.head.getMessage())
    }
    implicit val encoder: JsonEncoder[UserId] = JsonEncoder[String].contramap((userId: UserId) => userId.iri.value)

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

/**
 * Stores the project ID, i.e. UUID, IRI and short code of the project
 *
 * @param uuid      the UUID of the project
 * @param iri       the IRI of the project
 * @param shortCode the shortcode of the project
 */
abstract case class ProjectId private (
  uuid: UUID,
  iri: Iri.ProjectIri,
  shortCode: Project.ShortCode
)

/**
 * Companion object for ProjectId. Contains factory methods for creating ProjectId instances.
 */
object ProjectId {

  /**
   * Generates a ProjectId instance provided a Project IRI and a ShortCode. The UUID is extracted from the IRI.
   *
   * @param iri       the project IRI
   * @param shortCode the project short code (as defined by the ARK resolver)
   * @return a new ProjectId instance
   */
  def fromIri(iri: Iri.ProjectIri, shortCode: Project.ShortCode): Validation[ValidationException, ProjectId] = {
    val uuid: Try[UUID] = Try(UUID.fromString(iri.value.split("/").last))
    val projectId: Either[ValidationException, ProjectId] = uuid.toEither.fold(
      _ => Left(new ValidationException(IdErrorMessages.IriDoesNotContainUuid(iri))),
      uuid => Right(new ProjectId(uuid = uuid, iri = iri, shortCode = shortCode) {})
    )
    Validation.fromEither(projectId)
  }

  /**
   * Generates a ProjectId instance provided a UUID and a ShortCode. The Project IRI is generated on basis of the UUID.
   *
   * @param uuid      the project UUID
   * @param shortCode the project short code (as defined by the ARK resolver)
   * @return a new ProjectId instance
   */
  def fromUuid(uuid: UUID, shortCode: Project.ShortCode): Validation[ValidationException, ProjectId] = {
    val iri = Iri.ProjectIri.make(s"http://rdfh.ch/projects/${uuid}")
    iri.map(iri => new ProjectId(uuid = uuid, iri = iri, shortCode = shortCode) {})
  }

  /**
   * Generates a ProjectId instance with a new (random) UUID and an IRI which is created from a prefix and the UUID.
   *
   * @param shortCode the project short code (as defined by the ARK resolver)
   * @return a new ProjectId instance
   */
  def make(shortCode: Project.ShortCode): Validation[ValidationException, ProjectId] = {
    val uuid = UUID.randomUUID()
    val iri  = Iri.ProjectIri.make(s"http://rdfh.ch/projects/${uuid}")
    iri.map(iri => new ProjectId(uuid = uuid, iri = iri, shortCode = shortCode) {})
  }
}

object IdErrorMessages {
  def IriDoesNotContainUuid(iri: Iri) = s"No UUID can be extracted from IRI '${iri.value}'"
}
