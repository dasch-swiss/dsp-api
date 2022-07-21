/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.errors.BadRequestException
import zio.prelude.Validation

object Role {

  /**
   * RoleName value object.
   */
  sealed abstract case class RoleName private (value: String)
  object RoleName { self =>
    def make(value: String): Validation[Throwable, RoleName] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(RoleErrorMessages.RoleNameMissing))
      } else {
        val validatedValue = Validation(
          V2IriValidation.toSparqlEncodedString(
            value,
            throw BadRequestException(RoleErrorMessages.RoleNameInvalid)
          )
        )

        validatedValue.map(new RoleName(_) {})
      }

    def make(value: Option[String]): Validation[Throwable, Option[RoleName]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * RoleDescription value object.
   */
  sealed abstract case class RoleDescription private (value: Seq[V2.StringLiteralV2])
  object RoleDescription { self =>
    def make(value: Seq[V2.StringLiteralV2]): Validation[Throwable, RoleDescription] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(RoleErrorMessages.RoleDescriptionMissing))
      } else {
        val validatedDescriptions = Validation(value.map { description =>
          val validatedDescription =
            V2IriValidation.toSparqlEncodedString(
              description.value,
              throw BadRequestException(RoleErrorMessages.RoleDescriptionInvalid)
            )
          V2.StringLiteralV2(value = validatedDescription, language = description.language)
        })
        validatedDescriptions.map(new RoleDescription(_) {})
      }

    def make(value: Option[Seq[V2.StringLiteralV2]]): Validation[Throwable, Option[RoleDescription]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }
}

object Permission extends Enumeration {
  type Permission = Value

  val View   = Value("view")
  val Create = Value("create")
  val Modify = Value("modify")
  val Delete = Value("delete")
  val Admin  = Value("admin")
}

object RoleErrorMessages {
  val RoleNameMissing        = "Role name cannot be empty."
  val RoleNameInvalid        = "Role name is invalid."
  val RoleDescriptionMissing = "Role description cannot be empty."
  val RoleDescriptionInvalid = "Role description is invalid."
}
