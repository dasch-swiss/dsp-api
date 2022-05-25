/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation

sealed trait Iri
object Iri {

  /**
   * GroupIRI value object.
   */
  sealed abstract case class GroupIRI private (value: String)
  object GroupIRI { self =>
    def make(value: String): Validation[Throwable, GroupIRI] =
      if (value.isEmpty) {
        Validation.fail(V2.BadRequestException(IriErrorMessages.GroupIriMissing))
      } else {
        val isUUID: Boolean = V2UuidValidation.hasUUIDLength(value.split("/").last)

        if (!V2IriValidation.isKnoraGroupIriStr(value)) {
          Validation.fail(V2.BadRequestException(IriErrorMessages.GroupIriInvalid))
        } else if (isUUID && !V2UuidValidation.isUUIDVersion4Or5(value)) {
          Validation.fail(V2.BadRequestException(IriErrorMessages.UuidInvalid))
        } else {
          val validatedValue = Validation(
            V2IriValidation.validateAndEscapeIri(value, throw V2.BadRequestException(IriErrorMessages.GroupIriInvalid))
          )

          validatedValue.map(new GroupIRI(_) {})
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[GroupIRI]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * ListIri value object.
   */
  sealed abstract case class ListIRI private (value: String)
  object ListIRI { self =>
    def make(value: String): Validation[Throwable, ListIRI] =
      if (value.isEmpty) {
        Validation.fail(V2.BadRequestException(IriErrorMessages.ListIriMissing))
      } else {
        val isUUID: Boolean = V2UuidValidation.hasUUIDLength(value.split("/").last)

        if (!V2IriValidation.isKnoraListIriStr(value)) {
          Validation.fail(V2.BadRequestException(IriErrorMessages.ListIriInvalid))
        } else if (isUUID && !V2UuidValidation.isUUIDVersion4Or5(value)) {
          Validation.fail(V2.BadRequestException(IriErrorMessages.UuidInvalid))
        } else {
          val validatedValue = Validation(
            V2IriValidation.validateAndEscapeIri(
              value,
              throw V2.BadRequestException(IriErrorMessages.ListIriInvalid)
            )
          )

          validatedValue.map(new ListIRI(_) {})
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[ListIRI]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * ProjectIRI value object.
   */
  sealed abstract case class ProjectIRI private (value: String)
  object ProjectIRI { self =>
    def make(value: String): Validation[Throwable, ProjectIRI] =
      if (value.isEmpty) {
        Validation.fail(V2.BadRequestException(IriErrorMessages.ProjectIriMissing))
      } else {
        val isUUID: Boolean = V2UuidValidation.hasUUIDLength(value.split("/").last)

        if (!V2IriValidation.isKnoraProjectIriStr(value)) {
          Validation.fail(V2.BadRequestException(IriErrorMessages.ProjectIriInvalid))
        } else if (isUUID && !V2UuidValidation.isUUIDVersion4Or5(value)) {
          Validation.fail(V2.BadRequestException(IriErrorMessages.UuidInvalid))
        } else {
          val validatedValue = Validation(
            V2IriValidation.validateAndEscapeProjectIri(
              value,
              throw V2.BadRequestException(IriErrorMessages.ProjectIriInvalid)
            )
          )

          validatedValue.map(new ProjectIRI(_) {})
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[ProjectIRI]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * UserIRI value object.
   */
  sealed abstract case class UserIRI private (value: String) extends Iri
  object UserIRI { self =>
    def make(value: String): Validation[Throwable, UserIRI] =
      if (value.isEmpty) {
        Validation.fail(V2.BadRequestException(IriErrorMessages.UserIriMissing))
      } else {
        val isUUID: Boolean = V2UuidValidation.hasUUIDLength(value.split("/").last)

        if (!V2IriValidation.isKnoraUserIriStr(value)) {
          Validation.fail(V2.BadRequestException(IriErrorMessages.UserIriInvalid))
        } else if (isUUID && !V2UuidValidation.isUUIDVersion4Or5(value)) {
          Validation.fail(V2.BadRequestException(IriErrorMessages.UuidInvalid))
        } else {
          val validatedValue = Validation(
            V2IriValidation.validateAndEscapeUserIri(
              value,
              throw V2.BadRequestException(IriErrorMessages.UserIriInvalid)
            )
          )

          validatedValue.map(new UserIRI(_) {})
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[UserIRI]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }
}

object IriErrorMessages {
  val GroupIriMissing   = "Group IRI cannot be empty."
  val GroupIriInvalid   = "Group IRI is invalid."
  val ListIriMissing    = "List IRI cannot be empty."
  val ListIriInvalid    = "List IRI cannot be empty."
  val ProjectIriMissing = "Project IRI cannot be empty."
  val ProjectIriInvalid = "Project IRI is invalid."
  val UserIriMissing    = "User IRI cannot be empty."
  val UserIriInvalid    = "User IRI is invalid."
  val UuidInvalid       = "Invalid UUID used to create IRI. Only versions 4 and 5 are supported."
}
