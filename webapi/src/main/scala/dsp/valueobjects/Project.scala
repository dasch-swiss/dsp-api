/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.json._
import zio.prelude.Validation

import scala.util.matching.Regex

import dsp.errors.ValidationException
import dsp.valueobjects.Iri

object Project {
  // A regex sub-pattern for project IDs, which must consist of 4 hexadecimal digits.
  private val ProjectIDPattern: String = """\p{XDigit}{4,4}"""

  // A regex for matching a string containing the project ID.
  private val ProjectIDRegex: Regex = ("^" + ProjectIDPattern + "$").r

  /**
   * Regex which matches string that:
   * - is 3-20 characters long,
   * - contains small and capital letters, numbers, special characters: `-` and `_`,
   * - cannot start with number nor allowed special characters.
   */
  private val shortnameRegex: Regex = "^[a-zA-Z][a-zA-Z0-9_-]{2,19}$".r

  /**
   * Check that the string represents a valid project shortname.
   *
   * @param shortname string to be checked.
   * @return the same string.
   */
  private def validateAndEscapeProjectShortname(shortname: String): Option[String] =
    shortnameRegex
      .findFirstIn(shortname)
      .flatMap(Iri.toSparqlEncodedString)

  /**
   * Project Shortcode value object.
   */
  sealed abstract case class Shortcode private (value: String)
  object Shortcode { self =>
    implicit val decoder: JsonDecoder[Shortcode] = JsonDecoder[String].mapOrFail { value =>
      Shortcode.make(value).toEitherWith(e => e.head.getMessage)
    }
    implicit val encoder: JsonEncoder[Shortcode] =
      JsonEncoder[String].contramap((shortcode: Shortcode) => shortcode.value)

    def unsafeFrom(str: String) = make(str)
      .getOrElse(throw new IllegalArgumentException(ProjectErrorMessages.ShortcodeInvalid(str)))

    def make(value: String): Validation[ValidationException, Shortcode] =
      if (value.isEmpty) {
        Validation.fail(ValidationException(ProjectErrorMessages.ShortcodeMissing))
      } else {
        ProjectIDRegex.matches(value.toUpperCase) match {
          case false => Validation.fail(ValidationException(ProjectErrorMessages.ShortcodeInvalid(value)))
          case true  => Validation.succeed(new Shortcode(value.toUpperCase) {})
        }
      }
  }

  /**
   * Project Shortname value object.
   */
  sealed abstract case class Shortname private (value: String)
  object Shortname { self =>
    implicit val decoder: JsonDecoder[Shortname] = JsonDecoder[String].mapOrFail { value =>
      Shortname.make(value).toEitherWith(e => e.head.getMessage)
    }
    implicit val encoder: JsonEncoder[Shortname] =
      JsonEncoder[String].contramap((shortname: Shortname) => shortname.value)

    def make(value: String): Validation[ValidationException, Shortname] =
      if (value.isEmpty) Validation.fail(ValidationException(ProjectErrorMessages.ShortnameMissing))
      else
        Validation
          .fromOption(validateAndEscapeProjectShortname(value))
          .mapError(_ => ValidationException(ProjectErrorMessages.ShortnameInvalid(value)))
          .map(new Shortname(_) {})

    def make(value: Option[String]): Validation[ValidationException, Option[Shortname]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * Project Name value object.
   * (Formerly `Longname`)
   */
  // TODO-BL: [domain-model] this should be multi-lang-string,
  sealed abstract case class Name private (value: String)
  object Name { self =>
    implicit val decoder: JsonDecoder[Name] = JsonDecoder[String].mapOrFail { value =>
      Name.make(value).toEitherWith(e => e.head.getMessage)
    }
    implicit val encoder: JsonEncoder[Name] =
      JsonEncoder[String].contramap((name: Name) => name.value)

    private def isLengthCorrect(name: String): Boolean = name.length > 2 && name.length < 257

    def make(value: String): Validation[ValidationException, Name] =
      if (value.isEmpty) Validation.fail(ValidationException(ProjectErrorMessages.NameMissing))
      else if (!isLengthCorrect(value)) Validation.fail(ValidationException(ProjectErrorMessages.NameInvalid))
      else Validation.succeed(new Name(value) {})

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
  sealed abstract case class ProjectDescription private (value: Seq[V2.StringLiteralV2])
  object ProjectDescription { self =>
    implicit val decoder: JsonDecoder[ProjectDescription] = JsonDecoder[Seq[V2.StringLiteralV2]].mapOrFail { value =>
      ProjectDescription.make(value).toEitherWith(e => e.head.getMessage)
    }
    implicit val encoder: JsonEncoder[ProjectDescription] =
      JsonEncoder[Seq[V2.StringLiteralV2]].contramap((description: ProjectDescription) => description.value)

    private def isLengthCorrect(descriptionsToCheck: Seq[V2.StringLiteralV2]): Boolean = {
      val checked = descriptionsToCheck.filter(d => d.value.length > 2 && d.value.length < 40961)
      descriptionsToCheck == checked
    }

    def make(value: Seq[V2.StringLiteralV2]): Validation[ValidationException, ProjectDescription] =
      if (value.isEmpty) Validation.fail(ValidationException(ProjectErrorMessages.ProjectDescriptionsMissing))
      else if (!isLengthCorrect(value))
        Validation.fail((ValidationException(ProjectErrorMessages.ProjectDescriptionsInvalid)))
      else Validation.succeed(new ProjectDescription(value) {})

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
    implicit val decoder: JsonDecoder[Keywords] = JsonDecoder[Seq[String]].mapOrFail { value =>
      Keywords.make(value).toEitherWith(e => e.head.getMessage)
    }
    implicit val encoder: JsonEncoder[Keywords] =
      JsonEncoder[Seq[String]].contramap((keywords: Keywords) => keywords.value)

    private def isLengthCorrect(keywordsToCheck: Seq[String]): Boolean = {
      val checked = keywordsToCheck.filter(k => k.length > 2 && k.length < 65)
      keywordsToCheck == checked
    }

    def make(value: Seq[String]): Validation[ValidationException, Keywords] =
      if (value.isEmpty) Validation.fail(ValidationException(ProjectErrorMessages.KeywordsMissing))
      else if (!isLengthCorrect(value)) Validation.fail(ValidationException(ProjectErrorMessages.KeywordsInvalid))
      else Validation.succeed(new Keywords(value) {})

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
    implicit val decoder: JsonDecoder[Logo] = JsonDecoder[String].mapOrFail { value =>
      Logo.make(value).toEitherWith(e => e.head.getMessage)
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
    implicit val decoder: JsonDecoder[ProjectSelfJoin] = JsonDecoder[Boolean].mapOrFail { value =>
      ProjectSelfJoin.make(value).toEitherWith(e => e.head.getMessage)
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

    val deleted = new ProjectStatus(false) {}
    val active  = new ProjectStatus(true) {}

    implicit val decoder: JsonDecoder[ProjectStatus] = JsonDecoder[Boolean].mapOrFail { value =>
      ProjectStatus.make(value).toEitherWith(e => e.head.getMessage)
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
  val ShortcodeMissing           = "Shortcode cannot be empty."
  val ShortcodeInvalid           = (v: String) => s"Invalid project shortcode: $v"
  val ShortnameMissing           = "Shortname cannot be empty."
  val ShortnameInvalid           = (v: String) => s"Shortname is invalid: $v"
  val NameMissing                = "Name cannot be empty."
  val NameInvalid                = "Name must be 3 to 256 characters long."
  val ProjectDescriptionsMissing = "Description cannot be empty."
  val ProjectDescriptionsInvalid = "Description must be 3 to 40960 characters long."
  val KeywordsMissing            = "Keywords cannot be empty."
  val KeywordsInvalid            = "Keywords must be 3 to 64 characters long."
  val LogoMissing                = "Logo cannot be empty."
  val LogoInvalid                = (v: String) => s"Logo is invalid: $v"
}
