/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.json._
import zio.prelude.Validation

import scala.util.matching.Regex

import dsp.errors.ValidationException

object Project {
  // A regex sub-pattern for project IDs, which must consist of 4 hexadecimal digits.
  private val ProjectIDPattern: String =
    """\p{XDigit}{4,4}"""

  // A regex for matching a string containing the project ID.
  private val ProjectIDRegex: Regex = ("^" + ProjectIDPattern + "$").r

  // TODO-mpro: longname, description, keywords, logo are missing enhanced validation

  /**
   * Project ShortCode value object.
   */
  sealed abstract case class ShortCode private (value: String)
  object ShortCode { self =>
    implicit val decoder: JsonDecoder[ShortCode] = JsonDecoder[String].mapOrFail { case value =>
      ShortCode.make(value).toEitherWith(e => e.head.getMessage())
    }
    implicit val encoder: JsonEncoder[ShortCode] =
      JsonEncoder[String].contramap((shortCode: ShortCode) => shortCode.value)

    def make(value: String): Validation[ValidationException, ShortCode] =
      if (value.isEmpty) {
        Validation.fail(ValidationException(ProjectErrorMessages.ShortCodeMissing))
      } else {
        ProjectIDRegex.matches(value.toUpperCase) match {
          case false => Validation.fail(ValidationException(ProjectErrorMessages.ShortCodeInvalid(value)))
          case true  => Validation.succeed(new ShortCode(value.toUpperCase) {})
        }
      }

    def make(value: Option[String]): Validation[ValidationException, Option[ShortCode]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * Project ShortName value object.
   */
  sealed abstract case class ShortName private (value: String)
  object ShortName { self =>
    implicit val decoder: JsonDecoder[ShortName] = JsonDecoder[String].mapOrFail { case value =>
      ShortName.make(value).toEitherWith(e => e.head.getMessage())
    }
    implicit val encoder: JsonEncoder[ShortName] =
      JsonEncoder[String].contramap((shortName: ShortName) => shortName.value)

    def make(value: String): Validation[ValidationException, ShortName] =
      if (value.isEmpty) {
        Validation.fail(ValidationException(ProjectErrorMessages.ShortNameMissing))
      } else {
        val validatedValue = Validation(
          V2ProjectIriValidation.validateAndEscapeProjectShortname(
            value,
            throw ValidationException(ProjectErrorMessages.ShortNameInvalid(value))
          )
        ).mapError(e => new ValidationException(e.getMessage()))
        validatedValue.map(new ShortName(_) {})
      }

    def make(value: Option[String]): Validation[ValidationException, Option[ShortName]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * Project Name value object.
   * (Formerly `Longname`)
   */
  // TODO-BL: [domain-model] this should be multi-lang-string, I suppose; needs real validation once value constraints are defined
  sealed abstract case class Name private (value: String)
  object Name { self =>
    implicit val decoder: JsonDecoder[Name] = JsonDecoder[String].mapOrFail { case value =>
      Name.make(value).toEitherWith(e => e.head.getMessage())
    }
    implicit val encoder: JsonEncoder[Name] =
      JsonEncoder[String].contramap((name: Name) => name.value)

    def make(value: String): Validation[ValidationException, Name] =
      if (value.isEmpty) {
        Validation.fail(ValidationException(ProjectErrorMessages.NameMissing))
      } else {
        Validation.succeed(new Name(value) {})
      }

    def make(value: Option[String]): Validation[ValidationException, Option[Name]] =
      value match {
        case None    => Validation.succeed(None)
        case Some(v) => self.make(v).map(Some(_))
      }
  }

  /**
   * ProjectDescription value object.
   */
  // TODO-BL: [domain-model] should probably be MultiLangString; should probably be called `Description` as it's clear that it's part of Project
  // ATM it can't be changed to MultiLangString, because that has the language tag required, whereas in V2, it's currently optional, so this would be a breaking change.
  sealed abstract case class ProjectDescription private (value: Seq[V2.StringLiteralV2]) // make it plural
  object ProjectDescription { self =>
    implicit val decoder: JsonDecoder[ProjectDescription] = JsonDecoder[Seq[V2.StringLiteralV2]].mapOrFail {
      case value =>
        ProjectDescription.make(value).toEitherWith(e => e.head.getMessage())
    }
    implicit val encoder: JsonEncoder[ProjectDescription] =
      JsonEncoder[Seq[V2.StringLiteralV2]].contramap((description: ProjectDescription) => description.value)

    def make(value: Seq[V2.StringLiteralV2]): Validation[ValidationException, ProjectDescription] =
      if (value.isEmpty) {
        Validation.fail(ValidationException(ProjectErrorMessages.ProjectDescriptionsMissing))
      } else {
        Validation.succeed(new ProjectDescription(value) {})
      }

    def make(value: Option[Seq[V2.StringLiteralV2]]): Validation[ValidationException, Option[ProjectDescription]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * Project Keywords value object.
   */
  sealed abstract case class Keywords private (value: Seq[String])
  object Keywords { self =>
    implicit val decoder: JsonDecoder[Keywords] = JsonDecoder[Seq[String]].mapOrFail { case value =>
      Keywords.make(value).toEitherWith(e => e.head.getMessage())
    }
    implicit val encoder: JsonEncoder[Keywords] =
      JsonEncoder[Seq[String]].contramap((keywords: Keywords) => keywords.value)

    def make(value: Seq[String]): Validation[ValidationException, Keywords] =
      if (value.isEmpty) {
        Validation.fail(ValidationException(ProjectErrorMessages.KeywordsMissing))
      } else {
        Validation.succeed(new Keywords(value) {})
      }

    def make(value: Option[Seq[String]]): Validation[ValidationException, Option[Keywords]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * Project Logo value object.
   */
  sealed abstract case class Logo private (value: String)
  object Logo { self =>
    implicit val decoder: JsonDecoder[Logo] = JsonDecoder[String].mapOrFail { case value =>
      Logo.make(value).toEitherWith(e => e.head.getMessage())
    }
    implicit val encoder: JsonEncoder[Logo] =
      JsonEncoder[String].contramap((logo: Logo) => logo.value)

    def make(value: String): Validation[ValidationException, Logo] =
      if (value.isEmpty) {
        Validation.fail(ValidationException(ProjectErrorMessages.LogoMissing))
      } else {
        Validation.succeed(new Logo(value) {})
      }
    def make(value: Option[String]): Validation[ValidationException, Option[Logo]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * ProjectSelfjoin value object.
   */
  sealed abstract case class ProjectSelfJoin private (value: Boolean)
  object ProjectSelfJoin { self =>
    implicit val decoder: JsonDecoder[ProjectSelfJoin] = JsonDecoder[Boolean].mapOrFail { case value =>
      ProjectSelfJoin.make(value).toEitherWith(e => e.head.getMessage())
    }
    implicit val encoder: JsonEncoder[ProjectSelfJoin] =
      JsonEncoder[Boolean].contramap((selfJoin: ProjectSelfJoin) => selfJoin.value)

    def make(value: Boolean): Validation[ValidationException, ProjectSelfJoin] =
      Validation.succeed(new ProjectSelfJoin(value) {})

    def make(value: Option[Boolean]): Validation[ValidationException, Option[ProjectSelfJoin]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * ProjectStatus value object.
   */
  sealed abstract case class ProjectStatus private (value: Boolean)
  object ProjectStatus { self =>
    implicit val decoder: JsonDecoder[ProjectStatus] = JsonDecoder[Boolean].mapOrFail { case value =>
      ProjectStatus.make(value).toEitherWith(e => e.head.getMessage())
    }
    implicit val encoder: JsonEncoder[ProjectStatus] =
      JsonEncoder[Boolean].contramap((status: ProjectStatus) => status.value)

    def make(value: Boolean): Validation[ValidationException, ProjectStatus] =
      Validation.succeed(new ProjectStatus(value) {})

    def make(value: Option[Boolean]): Validation[ValidationException, Option[ProjectStatus]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }
}

object ProjectErrorMessages {
  val ShortCodeMissing           = "ShortCode cannot be empty."
  val ShortCodeInvalid           = (v: String) => s"ShortCode is invalid: $v"
  val ShortNameMissing           = "Shortname cannot be empty."
  val ShortNameInvalid           = (v: String) => s"Shortname is invalid: $v"
  val NameMissing                = "Name cannot be empty."
  val NameInvalid                = (v: String) => s"Name is invalid: $v"
  val ProjectDescriptionsMissing = "Description cannot be empty."
  val ProjectDescriptionsInvalid = (v: String) => s"Description is invalid: $v"
  val KeywordsMissing            = "Keywords cannot be empty."
  val KeywordsInvalid            = (v: String) => s"Keywords are invalid: $v"
  val LogoMissing                = "Logo cannot be empty."
  val LogoInvalid                = (v: String) => s"Logo is invalid: $v"
}
