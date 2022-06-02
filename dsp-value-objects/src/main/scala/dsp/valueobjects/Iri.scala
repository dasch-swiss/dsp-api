/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation

sealed trait Iri
object Iri {

  /**
   * GroupIri value object.
   */
  sealed abstract case class GroupIri private (value: String) extends Iri
  object GroupIri { self =>
    def make(value: String): Validation[Throwable, GroupIri] =
      if (value.isEmpty) {
        Validation.fail(V2.BadRequestException(IriErrorMessages.GroupIriMissing))
      } else {
        val isUuid: Boolean = V2UuidValidation.hasUuidLength(value.split("/").last)

        if (!V2IriValidation.isKnoraGroupIriStr(value)) {
          Validation.fail(V2.BadRequestException(IriErrorMessages.GroupIriInvalid))
        } else if (isUuid && !V2UuidValidation.isUuidVersion4Or5(value)) {
          Validation.fail(V2.BadRequestException(IriErrorMessages.UuidVersionInvalid))
        } else {
          val validatedValue = Validation(
            V2IriValidation.validateAndEscapeIri(value, throw V2.BadRequestException(IriErrorMessages.GroupIriInvalid))
          )

          validatedValue.map(new GroupIri(_) {})
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[GroupIri]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * ListIri value object.
   */
  sealed abstract case class ListIri private (value: String) extends Iri
  object ListIri { self =>
    def make(value: String): Validation[Throwable, ListIri] =
      if (value.isEmpty) {
        Validation.fail(V2.BadRequestException(IriErrorMessages.ListIriMissing))
      } else {
        val isUuid: Boolean = V2UuidValidation.hasUuidLength(value.split("/").last)

        if (!V2IriValidation.isKnoraListIriStr(value)) {
          Validation.fail(V2.BadRequestException(IriErrorMessages.ListIriInvalid))
        } else if (isUuid && !V2UuidValidation.isUuidVersion4Or5(value)) {
          Validation.fail(V2.BadRequestException(IriErrorMessages.UuidVersionInvalid))
        } else {
          val validatedValue = Validation(
            V2IriValidation.validateAndEscapeIri(
              value,
              throw V2.BadRequestException(IriErrorMessages.ListIriInvalid)
            )
          )

          validatedValue.map(new ListIri(_) {})
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[ListIri]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * ProjectIri value object.
   */
  sealed abstract case class ProjectIri private (value: String) extends Iri
  object ProjectIri { self =>
    def make(value: String): Validation[Throwable, ProjectIri] =
      if (value.isEmpty) {
        Validation.fail(V2.BadRequestException(IriErrorMessages.ProjectIriMissing))
      } else {
        val isUuid: Boolean = V2UuidValidation.hasUuidLength(value.split("/").last)

        if (!V2IriValidation.isKnoraProjectIriStr(value)) {
          Validation.fail(V2.BadRequestException(IriErrorMessages.ProjectIriInvalid))
        } else if (isUuid && !V2UuidValidation.isUuidVersion4Or5(value)) {
          Validation.fail(V2.BadRequestException(IriErrorMessages.UuidVersionInvalid))
        } else {
          val validatedValue = Validation(
            V2IriValidation.validateAndEscapeProjectIri(
              value,
              throw V2.BadRequestException(IriErrorMessages.ProjectIriInvalid)
            )
          )

          validatedValue.map(new ProjectIri(_) {})
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[ProjectIri]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * UserIri value object.
   */
  sealed abstract case class UserIri private (value: String) extends Iri
  object UserIri { self =>
    def make(value: String): Validation[Throwable, UserIri] =
      if (value.isEmpty) {
        Validation.fail(V2.BadRequestException(IriErrorMessages.UserIriMissing))
      } else {
        val isUuid: Boolean = V2UuidValidation.hasUuidLength(value.split("/").last)

        if (!V2IriValidation.isKnoraUserIriStr(value)) {
          Validation.fail(V2.BadRequestException(IriErrorMessages.UserIriInvalid))
        } else if (isUuid && !V2UuidValidation.isUuidVersion4Or5(value)) {
          Validation.fail(V2.BadRequestException(IriErrorMessages.UuidVersionInvalid))
        } else {
          val validatedValue = Validation(
            V2IriValidation.validateAndEscapeUserIri(
              value,
              throw V2.BadRequestException(IriErrorMessages.UserIriInvalid)
            )
          )

          validatedValue.map(new UserIri(_) {})
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[UserIri]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }
}

object IriErrorMessages {
  val GroupIriMissing    = "Group IRI cannot be empty."
  val GroupIriInvalid    = "Group IRI is invalid."
  val ListIriMissing     = "List IRI cannot be empty."
  val ListIriInvalid     = "List IRI is invalid"
  val ProjectIriMissing  = "Project IRI cannot be empty."
  val ProjectIriInvalid  = "Project IRI is invalid."
  val UserIriMissing     = "User IRI cannot be empty."
  val UserIriInvalid     = "User IRI is invalid."
  val UuidVersionInvalid = "Invalid UUID used to create IRI. Only versions 4 and 5 are supported."
}
