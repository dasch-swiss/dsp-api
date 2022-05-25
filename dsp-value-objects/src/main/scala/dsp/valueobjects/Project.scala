/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation

sealed trait Project
object Project {

  // TODO-mpro: longname, description, keywords, logo are missing enhanced validation

  /**
   * Project Shortcode value object.
   */
  sealed abstract case class Shortcode private (value: String)
  object Shortcode { self =>
    def make(value: String): Validation[Throwable, Shortcode] =
      if (value.isEmpty) {
        Validation.fail(V2.BadRequestException(ProjectErrorMessages.ShortcodeMissing))
      } else {
        val validatedValue: Validation[Throwable, String] = Validation(
          V2ProjectIriValidation.validateProjectShortcode(
            value,
            throw V2.BadRequestException(ProjectErrorMessages.ShortcodeInvalid)
          )
        )
        validatedValue.map(new Shortcode(_) {})
      }

    def make(value: Option[String]): Validation[Throwable, Option[Shortcode]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * Project Shortname value object.
   */
  sealed abstract case class Shortname private (value: String)
  object Shortname { self =>
    def make(value: String): Validation[Throwable, Shortname] =
      if (value.isEmpty) {
        Validation.fail(V2.BadRequestException(ProjectErrorMessages.ShortnameMissing))
      } else {
        val validatedValue = Validation(
          V2ProjectIriValidation.validateAndEscapeProjectShortname(
            value,
            throw V2.BadRequestException(ProjectErrorMessages.ShortnameInvalid)
          )
        )
        validatedValue.map(new Shortname(_) {})
      }

    def make(value: Option[String]): Validation[Throwable, Option[Shortname]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * Project Longname value object.
   */
  sealed abstract case class Longname private (value: String)
  object Longname { self =>
    def make(value: String): Validation[Throwable, Longname] =
      if (value.isEmpty) {
        Validation.fail(V2.BadRequestException(ProjectErrorMessages.LongnameMissing))
      } else {
        Validation.succeed(new Longname(value) {})
      }

    def make(value: Option[String]): Validation[Throwable, Option[Longname]] =
      value match {
        case None    => Validation.succeed(None)
        case Some(v) => self.make(v).map(Some(_))
      }
  }

  /**
   * ProjectDescription value object.
   */
  sealed abstract case class ProjectDescription private (value: Seq[V2.StringLiteralV2]) // make it plural
  object ProjectDescription { self =>
    def make(value: Seq[V2.StringLiteralV2]): Validation[Throwable, ProjectDescription] =
      if (value.isEmpty) {
        Validation.fail(V2.BadRequestException(ProjectErrorMessages.ProjectDescriptionMissing))
      } else {
        Validation.succeed(new ProjectDescription(value) {})
      }

    def make(value: Option[Seq[V2.StringLiteralV2]]): Validation[Throwable, Option[ProjectDescription]] =
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
    def make(value: Seq[String]): Validation[Throwable, Keywords] =
      if (value.isEmpty) {
        Validation.fail(V2.BadRequestException(ProjectErrorMessages.KeywordsMissing))
      } else {
        Validation.succeed(new Keywords(value) {})
      }

    def make(value: Option[Seq[String]]): Validation[Throwable, Option[Keywords]] =
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
    def make(value: String): Validation[Throwable, Logo] =
      if (value.isEmpty) {
        Validation.fail(V2.BadRequestException(ProjectErrorMessages.LogoMissing))
      } else {
        Validation.succeed(new Logo(value) {})
      }
    def make(value: Option[String]): Validation[Throwable, Option[Logo]] =
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
    def make(value: Boolean): Validation[Throwable, ProjectSelfJoin] =
      Validation.succeed(new ProjectSelfJoin(value) {})

    def make(value: Option[Boolean]): Validation[Throwable, Option[ProjectSelfJoin]] =
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
    def make(value: Boolean): Validation[Throwable, ProjectStatus] =
      Validation.succeed(new ProjectStatus(value) {})

    def make(value: Option[Boolean]): Validation[Throwable, Option[ProjectStatus]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }
}

object ProjectErrorMessages {
  val ShortcodeMissing          = "Shortcode cannot be empty."
  val ShortcodeInvalid          = "Shortcode is invalid."
  val ShortnameMissing          = "Shortname cannot be empty."
  val ShortnameInvalid          = "Shortname is invalid."
  val LongnameMissing           = "Longname cannot be empty."
  val LongnameInvalid           = "Longname is invalid."
  val ProjectDescriptionMissing = "Description cannot be empty." //make it plural
  val ProjectDescriptionInvalid = "Description is invalid."      //make it plural
  val KeywordsMissing           = "Keywords cannot be empty."
  val KeywordsInvalid           = "Keywords are invalid."
  val LogoMissing               = "Logo cannot be empty."
  val LogoInvalid               = "Logo is invalid."
}
